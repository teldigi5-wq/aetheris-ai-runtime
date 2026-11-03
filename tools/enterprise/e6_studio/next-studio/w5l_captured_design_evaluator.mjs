/** W5-L: deterministic assessment of externally captured, UNATTESTED model
 * responses. No network/model calls or artifact writes. It derives bounded
 * design-only observations using the production W5-A/H/K validators.
 */
import {createHash} from 'node:crypto';
import {buildPairedEvaluationProtocol,summarizePairedResearch} from './w5l_paired_evaluation_protocol.mjs';
import {createPairedResearchReceipt} from './w5l_research_receipt.mjs';
import {buildPrompt,parseProviderEnvelope} from './w5a_local_ollama.mjs';
import {buildDesignRepairPrompt} from './w5h_bounded_model_repair.mjs';
import {buildStagedDesignPrompt,assembleStagedDesign} from './w5k_staged_design_research.mjs';
import {validateModelIdentity,validateModelPage,validateModelProposal} from './w5a_model_design_compiler.mjs';
const fail=x=>{throw Error('E6_W5L_'+x);};
const digest=x=>createHash('sha256').update(x,'utf8').digest('hex');
const exact=(v,keys)=>!!v&&typeof v==='object'&&!Array.isArray(v)&&
 [Object.prototype,null].includes(Object.getPrototypeOf(v))&&
 Reflect.ownKeys(v).length===keys.length&&
 keys.every(k=>Object.hasOwn(v,k)&&Object.hasOwn(Object.getOwnPropertyDescriptor(v,k)||{},'value'))&&
 Reflect.ownKeys(v).every(k=>typeof k==='string'&&keys.includes(k));
const repairable=new Set([
 'E6_W5A_MODEL_SCHEMA_OR_CONTRAST_REJECTED',
 'E6_W5A_INVALID_PAGE_ARCHITECTURE',
 'E6_W5A_UNTRUSTED_PAGE_CONTENT',
 'E6_W5A_INCOMPLETE_PAGE_IDENTITY'
]);
const classify=error=>{
 const c=error?.message;
 if(c==='E6_W5A_MODEL_GENERATION_NOT_NORMALLY_FINISHED')
   return 'DESIGN_ABNORMAL_FINISH';
 if(c==='E6_W5A_MODEL_JSON_PARSE_REJECTED')return 'DESIGN_SCHEMA_REJECTED';
 if(repairable.has(c)||['E6_W5K_INVALID_IDENTITY','E6_W5K_INVALID_PAGE',
   'E6_W5L_INVALID_CAPTURED_PAGE_STRUCTURE'].includes(c))
   return 'DESIGN_SCHEMA_REJECTED';
 fail('UNRECOGNIZED_CAPTURE_FAILURE');
};
const readAttempt=(a,phase,prompt,model)=>{
 if(!exact(a,['phase','promptSha256','envelope'])||a.phase!==phase||
    a.promptSha256!==digest(prompt)||
    !exact(a.envelope,['model','done','done_reason','response','eval_count'])||
    a.envelope.model!==model||a.envelope.done!==true||
    typeof a.envelope.done_reason!=='string'||a.envelope.done_reason.length>40||
    typeof a.envelope.response!=='string'||a.envelope.response.length>36_000||
    !Number.isInteger(a.envelope.eval_count)||a.envelope.eval_count<1||
    a.envelope.eval_count>3001||
    Buffer.byteLength(JSON.stringify(a.envelope),'utf8')>48_000)
   fail('INVALID_CAPTURE_ENVELOPE_OR_PROMPT');
 return parseProviderEnvelope(a.envelope,model,{phase:'design'});
};
const validatePart=(brief,part,phase)=>{
 if(phase==='identity'){
   validateModelIdentity(brief,part);
   return;
 }
 if(!exact(part,['version','projectSlug','page'])||
    part.version!==1||part.projectSlug!==brief.projectSlug||
    !exact(part.page,['path','navLabel','heading','lede','sections'])||
    part.page.path!==(phase==='home'?'/':'/approach'))
   fail('INVALID_CAPTURED_PAGE_STRUCTURE');
 validateModelPage(part.page);
};
function evaluateCell(brief,plan,cell,model){
 if(!exact(cell,['projectSlug','mode','reportedElapsedMs','attempts'])||
    cell.projectSlug!==plan.projectSlug||cell.mode!==plan.mode||
    !Array.isArray(cell.attempts)||
    Object.getPrototypeOf(cell.attempts)!==Array.prototype||
    cell.attempts.length>plan.designCallsMax||
    Reflect.ownKeys(cell.attempts).length!==cell.attempts.length+1)
   fail('CAPTURE_MATRIX_CHANGED');
 const count=cell.attempts.length;
 if(count===0){
   if(cell.reportedElapsedMs!==null)fail('FABRICATED_UNRUN_ELAPSED');
   return {projectSlug:plan.projectSlug,mode:plan.mode,outcome:'NOT_RUN',
    failureCode:null,reportedDesignCalls:null,
    reportedOutputTokens:null,reportedElapsedMs:null};
 }
 if(!Number.isInteger(cell.reportedElapsedMs)||
    cell.reportedElapsedMs<0||cell.reportedElapsedMs>3_300_000)
   fail('INVALID_REPORTED_CAPTURE_DURATION');
 const parts=[],phases=['identity','home','approach'];
 let reportedOutputTokens=0,failed=null,firstCompactValidation=null;
 for(let i=0;i<count;i++){
   if(!Object.hasOwn(cell.attempts,i)||failed!==null)
     fail('POST_REJECTION_OR_MISSING_CAPTURE');
   const compact=plan.mode==='single_compact';
   const phase=compact?'design':phases[i];
   let prompt;
   if(compact){
     prompt=i===0?buildPrompt(brief):
       buildDesignRepairPrompt(brief,firstCompactValidation);
   }else{
     prompt=buildStagedDesignPrompt(brief,phase,{
       identity:parts[0],home:parts[1]
     });
   }
   let parsed;
   try{parsed=readAttempt(cell.attempts[i],phase,prompt,model);}
   catch(error){
     // Untrusted capture structure/prompt deviations MUST NOT be counted as
     // model mistakes. Only narrow provider stop/JSON errors are outcomes.
     if(!['E6_W5A_MODEL_GENERATION_NOT_NORMALLY_FINISHED',
           'E6_W5A_MODEL_JSON_PARSE_REJECTED'].includes(error?.message))
       throw error;
     failed=classify(error);
     if(i+1<count)fail('UNAUTHORIZED_RETRY_AFTER_PROVIDER_FAILURE');
   }
   reportedOutputTokens+=cell.attempts[i].envelope.eval_count;
   if(failed!==null)break;
   try{
     if(compact)validateModelProposal(brief,parsed.design);
     else validatePart(brief,parsed.design,phase);
     if(!compact)parts.push(parsed.design);
     if(compact||phase==='approach')
       {if(!compact)assembleStagedDesign(brief,...parts);}
   }catch(error){
     const code=error?.message;
     if(compact&&i===0&&repairable.has(code)&&i+1<count){
       firstCompactValidation=code;
       continue;
     }
     if(!repairable.has(code)&&
        !['E6_W5K_INVALID_IDENTITY','E6_W5K_INVALID_PAGE',
          'E6_W5L_INVALID_CAPTURED_PAGE_STRUCTURE'].includes(code))
       throw error;
     failed=classify(error);
     if(i+1<count)fail('POST_REJECTION_CAPTURE');
   }
   if(compact&&failed===null&&i+1<count)
     fail('UNNECESSARY_EXTRA_CAPTURE');
 }
 if(failed===null){
   const required=plan.mode==='single_compact'?null:3;
   if(required!==null&&count!==required)fail('INCOMPLETE_STAGED_CAPTURE');
 }
 return {projectSlug:plan.projectSlug,mode:plan.mode,
  outcome:failed===null?'ACCEPTED':'REJECTED',
  failureCode:failed,reportedDesignCalls:count,
  reportedOutputTokens,reportedElapsedMs:cell.reportedElapsedMs};
}
export function evaluateCapturedPairedDesign({briefs,sourceCommitSha,captures}={}){
 const protocol=buildPairedEvaluationProtocol({briefs,sourceCommitSha});
 if(!Array.isArray(captures)||Object.getPrototypeOf(captures)!==Array.prototype||
    captures.length!==6||Reflect.ownKeys(captures).length!==7)
   fail('CAPTURE_MATRIX_CHANGED');
 const observations=protocol.planned.map((plan,i)=>{
   if(!Object.hasOwn(captures,i))fail('CAPTURE_MATRIX_CHANGED');
   return evaluateCell(briefs[Math.floor(i/2)],plan,captures[i],protocol.modelTag);
 });
 const summary=summarizePairedResearch(protocol,observations);
 const receipt=createPairedResearchReceipt(protocol,observations);
 return Object.freeze({
  kind:'E6_W5L_UNATTESTED_CAPTURE_ASSESSMENT_V1',
  receipt,comparison:summary,
  capturedEnvelopesExamined:captures.reduce((n,c)=>n+c.attempts.length,0),
  providerAuthenticityEstablished:false,
  actualInferenceIndependentlyAttested:false,
  providerRuntimeMeasured:false,
  modelWeightsAttested:false,
  humanOriginalityApproved:false,
  ownerExperimentAuthorized:false,
  stage45Go:false,releaseGo:false
 });
}
