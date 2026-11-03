/** W5-L CPU capture adapter (INERT until invoked with an explicit transport).
 * Six fixed brief/method cells, model-only JSON. This is not a CLI, workflow,
 * owner authorization, independent model attestation or release pathway.
 * No raw model response is returned or persisted; run in an isolated,
 * explicitly approved experiment and retain only sanitized metadata.
 */
import {performance} from 'node:perf_hooks';
import {buildPairedEvaluationProtocol} from './w5l_paired_evaluation_protocol.mjs';
import {evaluateCapturedPairedDesign} from './w5l_captured_design_evaluator.mjs';
import {buildPrompt,requestLocalOllama} from './w5a_local_ollama.mjs';
import {buildDesignRepairPrompt} from './w5h_bounded_model_repair.mjs';
import {buildStagedDesignPrompt,assembleStagedDesign} from './w5k_staged_design_research.mjs';
import {validateModelIdentity,validateModelPage,validateModelProposal} from './w5a_model_design_compiler.mjs';
import {createHash} from 'node:crypto';
const sha=x=>createHash('sha256').update(x,'utf8').digest('hex');
const fail=x=>{throw Error('E6_W5L_'+x);};
const repairs=new Set([
 'E6_W5A_MODEL_SCHEMA_OR_CONTRAST_REJECTED',
 'E6_W5A_INVALID_PAGE_ARCHITECTURE',
 'E6_W5A_UNTRUSTED_PAGE_CONTENT',
 'E6_W5A_INCOMPLETE_PAGE_IDENTITY'
]);
const terminalProvider=new Set([
 'E6_W5A_MODEL_GENERATION_NOT_NORMALLY_FINISHED',
 'E6_W5A_MODEL_JSON_PARSE_REJECTED'
]);
const LIMIT_MS=55*60*1000,MAX_ENVELOPE_BYTES=48_000;
function assertPart(brief,part,phase){
 if(phase==='identity'){
   validateModelIdentity(brief,part);return;
 }
 if(!part||typeof part!=='object'||Array.isArray(part)||
    Reflect.ownKeys(part).length!==3||
    part.version!==1||part.projectSlug!==brief.projectSlug||
    part.page?.path!==(phase==='home'?'/':'/approach'))
   fail('CAPTURED_PAGE_INVALID');
 validateModelPage(part.page);
}
function redactEnvelope(raw){
 if(!raw||typeof raw!=='object'||Array.isArray(raw)||
    typeof raw.model!=='string'||typeof raw.done!=='boolean'||
    typeof raw.done_reason!=='string'||typeof raw.response!=='string'||
    !Number.isInteger(raw.eval_count))
   fail('UNTRUSTED_PROVIDER_CAPTURE');
 // Always discard unknown fields, including model-returned or server data.
 return {model:raw.model,done:raw.done,done_reason:raw.done_reason,
   response:raw.response,eval_count:raw.eval_count};
}
async function captureOne(prompt,model,transport){
 let envelope=null;
 const wrapper=async(url,request)=>{
   const response=await transport(url,request);
   // The W5-A request policy separately verifies the fixed loopback URL,
   // method, model name, CPU-only options, and the 180-second timeout.
   if(!response||response.status!==200||!response.body)return response;
   const chunks=[];let count=0;
   for await(const chunk of response.body){
     const bytes=Buffer.from(chunk);count+=bytes.length;
     if(count>MAX_ENVELOPE_BYTES)fail('PROVIDER_CAPTURE_TOO_LARGE');
     chunks.push(bytes);
   }
   const raw=Buffer.concat(chunks);
   let parsed;
   try{parsed=JSON.parse(raw.toString('utf8'));}
   catch{fail('PROVIDER_CAPTURE_NOT_JSON');}
   envelope=redactEnvelope(parsed);
   return new Response(raw,{status:200,headers:{'content-type':'application/json'}});
 };
 let result=null,terminal=null;
 try{result=await requestLocalOllama(prompt,model,{transport:wrapper,phase:'design'});}
 catch(error){
   if(!envelope||!terminalProvider.has(error?.message))throw error;
   terminal=error.message;
 }
 if(!envelope)fail('MISSING_CAPTURED_ENVELOPE');
 return {envelope,result,terminal};
}
/** transport is required (never defaults to the real network). Calling this
 * function with a fake transport produces NO model inference evidence.
 */
export async function collectBoundedPairedDesign({
 briefs,sourceCommitSha,transport,beforeRequest,clock=()=>performance.now()
}={}){
 if(typeof transport!=='function'||typeof beforeRequest!=='function'||
    typeof clock!=='function')
   fail('EXPLICIT_TRANSPORT_APPROVAL_AND_CLOCK_REQUIRED');
 const protocol=buildPairedEvaluationProtocol({briefs,sourceCommitSha});
 const model=protocol.modelTag,captures=[];
 let previous=-1;
 const now=()=>{
   const value=clock();
   if(typeof value!=='number'||!Number.isFinite(value)||value<0||value<previous)
     fail('NONMONOTONIC_CAPTURE_CLOCK');
   previous=value;return value;
 };
 const start=now();
 for(let index=0;index<protocol.planned.length;index++){
   const plan=protocol.planned[index],brief=briefs[Math.floor(index/2)];
   const attempts=[];
   // Sum bounded model transport and parsing, excluding authorization latency.
   // The absolute 55-minute deadline still includes ALL elapsed time.
   let modelRequestElapsedMs=0;
   let identity,home,accepted=false,rejected=false,retryCode=null;
   const stages=plan.mode==='three_stage'?['identity','home','approach']:['design','design'];
   for(let i=0;i<stages.length;i++){
     if(now()-start>=LIMIT_MS)fail('TOTAL_RESEARCH_TIME_EXCEEDED');
     const phase=stages[i],compact=phase==='design';
     const prompt=compact?
       (i===0?buildPrompt(brief):buildDesignRepairPrompt(brief,retryCode)):
       buildStagedDesignPrompt(brief,phase,{identity,home});
     if(prompt.length>8000)fail('PROMPT_TOO_LARGE');
     // The owner-approved caller must revalidate the live PR before every
     // model request, including an allowed compact-design retry.
     if(await beforeRequest()!==true)fail('MODEL_REQUEST_APPROVAL_REVOKED');
     if(now()-start>=LIMIT_MS)fail('TOTAL_RESEARCH_TIME_EXCEEDED');
     const modelRequestStarted=now();
     const observed=await captureOne(prompt,model,transport);
     const modelRequestFinished=now();
     modelRequestElapsedMs+=Math.ceil(modelRequestFinished-modelRequestStarted);
     attempts.push({phase,promptSha256:sha(prompt),envelope:observed.envelope});
     if(modelRequestFinished-start>LIMIT_MS)fail('TOTAL_RESEARCH_TIME_EXCEEDED');
     if(observed.terminal){rejected=true;break;}
     try{
       const value=observed.result.design;
       if(compact)validateModelProposal(brief,value);
       else if(phase==='identity'){assertPart(brief,value,phase);identity=value;}
       else if(phase==='home'){assertPart(brief,value,phase);home=value;}
       else{
         assertPart(brief,value,phase);
         assembleStagedDesign(brief,identity,home,value);
       }
       if(compact||phase==='approach'){accepted=true;break;}
     }catch(error){
       if(compact&&i===0&&repairs.has(error?.message)){
         retryCode=error.message;continue;
       }
       if(!repairs.has(error?.message)&&
          error?.message!=='E6_W5L_CAPTURED_PAGE_INVALID'&&
          error?.message!=='E6_W5K_INVALID_PAGE'&&
          error?.message!=='E6_W5K_INVALID_IDENTITY')
         throw error;
       rejected=true;break;
     }
   }
   if(!accepted&&!rejected)fail('INCOMPLETE_PHASE_SEQUENCE');
   captures.push({projectSlug:plan.projectSlug,mode:plan.mode,
     reportedElapsedMs:modelRequestElapsedMs,attempts});
 }
 const assessed=evaluateCapturedPairedDesign({briefs,sourceCommitSha,captures});
 return Object.freeze({
   kind:'E6_W5L_TRANSPORT_INJECTED_CAPTURE_SUMMARY_V1',
   sourceCommitSha,receipt:assessed.receipt,comparison:assessed.comparison,
   capturedEnvelopesExamined:assessed.capturedEnvelopesExamined,
   modelRequestsIndependentlyVerified:false,
   modelWeightsAttested:false,modelRuntimeAttested:false,
   humanVisualApproved:false,ownerExperimentApproved:false,
   realProviderProvenanceEstablished:false,
   stage45Go:false,releaseGo:false
 });
}
