/** W5-L future owner-gated CPU CLI; no inference without a labeled
 * exact-commit GitHub PR event and a separate current-PR read-back.
 * No user/model-controlled filenames, tool execution or secret prompt input.
 */
import {open,lstat,mkdir,writeFile,realpath} from 'node:fs/promises';
import {constants} from 'node:fs';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {authorizePairedCpuLabelEvent,authorizeLivePairedCpuPr} from './w5l_owner_cpu_gate.mjs';
import {buildPairedEvaluationProtocol} from './w5l_paired_evaluation_protocol.mjs';
import {collectBoundedPairedDesign} from './w5l_live_capture_adapter.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const ROOT='/home/runner/work/_temp';
const fail=x=>{throw Error('E6_W5L_'+x);};
const readSmall=async(path,limit)=>{
 const handle=await open(path,constants.O_RDONLY|constants.O_NOFOLLOW);
 try{
  const before=await handle.stat();
  if(!before.isFile()||before.size<1||before.size>limit)
   fail('UNSAFE_RUNNER_INPUT_FILE');
  const raw=Buffer.alloc(before.size);let offset=0;
  while(offset<raw.length){
   const part=await handle.read(raw,offset,raw.length-offset,offset);
   if(part.bytesRead<1)fail('UNSAFE_RUNNER_INPUT_FILE');
   offset+=part.bytesRead;
  }
  if((await handle.read(Buffer.alloc(1),0,1,offset)).bytesRead!==0)
   fail('UNSAFE_RUNNER_INPUT_FILE');
  const after=await handle.stat();
  if(after.size!==before.size||after.mtimeMs!==before.mtimeMs)
   fail('UNSAFE_RUNNER_INPUT_FILE');
  try{return JSON.parse(raw.toString('utf8'));}
  catch{fail('MALFORMED_RUNNER_INPUT_JSON');}
 }finally{await handle.close();}
};
const trustedRunnerPath=async(path,{directory=false}={})=>{
 if(typeof path!=='string'||path!==resolve(path)||
    !(directory?path===ROOT||path.startsWith(ROOT+'/'):path.startsWith(ROOT+'/')))
   fail('UNTRUSTED_RUNNER_INPUT_PATH');
 const parent=directory?path:dirname(path);
 let canonical;
 try{canonical=await realpath(parent);}
 catch{fail('UNTRUSTED_RUNNER_INPUT_PATH');}
 if(canonical!==parent)fail('UNTRUSTED_RUNNER_INPUT_PATH');
};
const ctx=()=>{
 if(process.argv.length!==3||
    !['authorize','live-approval','run'].includes(process.argv[2])||
    process.env.GITHUB_EVENT_NAME!=='pull_request'||
    process.env.GITHUB_REPOSITORY!=='teldigi5-wq/aetheris-ai-runtime'||
    process.env.GITHUB_ACTOR!=='teldigi5-wq'||
    process.env.E6_W5L_ENABLE_OWNER_LABELED_CPU_TRIAL!=='YES'||
    process.env.E6_W5L_APPROVED_PR_NUMBER!=='275'||
    !/^[a-f0-9]{40}$/.test(process.env.E6_W5L_CHECKED_OUT_PR_HEAD||''))
   fail('OWNER_GITHUB_CPU_CONTEXT_REQUIRED');
 return {actor:process.env.GITHUB_ACTOR,
  repository:process.env.GITHUB_REPOSITORY,expectedPr:275,
  headSha:process.env.E6_W5L_CHECKED_OUT_PR_HEAD};
};
async function verifyApproval(requireLive){
 const context=ctx(),eventPath=process.env.GITHUB_EVENT_PATH;
 await trustedRunnerPath(eventPath);
 const event=await readSmall(eventPath,100_000);
 authorizePairedCpuLabelEvent(event,context);
 if(requireLive){
  const temp=process.env.RUNNER_TEMP;
  if(typeof temp!=='string'||!/^\/home\/runner\/work\/_temp(?:\/[a-zA-Z0-9_.-]+)*$/.test(temp))
   fail('RUNNER_TEMP_NOT_TRUSTED');
  await trustedRunnerPath(temp,{directory:true});
  const path=join(temp,'w5l-live-owner-pr.json');
  await trustedRunnerPath(path);
  authorizeLivePairedCpuPr(await readSmall(path,200_000),context);
 }
 return context;
}
async function saveSanitized(record){
 const target=join(here,'target');
 try{
  const st=await lstat(target);
  if(!st.isDirectory()||st.isSymbolicLink())fail('UNSAFE_RESEARCH_TARGET');
 }catch(error){
  if(error?.code!=='ENOENT')throw error;
  await mkdir(target,{mode:0o700});
 }
 const dest=join(target,'e6-w5l-owner-cpu-review');
 await mkdir(dest,{mode:0o700}); // exclusive, never overwrite old evidence
 await writeFile(join(dest,'paired-design-metadata.json'),
   JSON.stringify(record,null,2)+'\n',{flag:'wx',mode:0o600});
}
async function main(){
 const mode=process.argv[2],context=await verifyApproval(mode!=='authorize');
 if(mode==='authorize'){
  process.stdout.write('E6_W5L_FRESH_EXACT_HEAD_OWNER_EVENT_VERIFIED_NO_INFERENCE\n');return;
 }
 if(mode==='live-approval'){
  process.stdout.write('E6_W5L_CURRENT_OWNER_LABEL_AND_HEAD_STILL_APPROVED\n');return;
 }
 // This command is ONLY for separately approved, ephemeral GitHub CPU runners.
 if(process.env.OLLAMA_HOST!=='127.0.0.1:11434'||
    process.env.OLLAMA_LLM_LIBRARY!=='cpu'||
    process.env.OLLAMA_NUM_PARALLEL!=='1'||
    process.env.OLLAMA_MAX_LOADED_MODELS!=='1'||
    process.env.CUDA_VISIBLE_DEVICES!=='-1'||
    process.env.HIP_VISIBLE_DEVICES!=='-1'||
    process.env.MODEL_TAG!=='qwen2.5:0.5b'||
    !/^[0-9]{1,24}$/.test(process.env.GITHUB_RUN_ID||''))
   fail('PINNED_EPHEMERAL_CPU_RUNNER_REQUIRED');
 const batch=await readSmall(join(here,'w5b_heldout_briefs.json'),30_000);
 if(!batch||typeof batch!=='object'||Array.isArray(batch)||
    Reflect.ownKeys(batch).length!==3||
    batch.kind!=='E6_W5B_HELDOUT_FICTIONAL_BRIEFS_V1'||
    batch.author!=='HUMAN_DEFINED_EVAL_ONLY')
   fail('UNAPPROVED_HELDOUT_BRIEFS');
 const manifest=buildPairedEvaluationProtocol({
  briefs:batch.briefs,sourceCommitSha:context.headSha
 });
 if(manifest.modelTag!==process.env.MODEL_TAG||manifest.planned.length!==6)
   fail('SOURCE_MODEL_OR_MATRIX_CHANGED');
 // fetch is deliberately supplied here and nowhere in normal PR CI.
 // The fixed W5-A API is loopback and num_gpu=0 for every request.
 const assessed=await collectBoundedPairedDesign({
  briefs:batch.briefs,sourceCommitSha:context.headSha,transport:fetch
 });
 if(assessed.comparison.allSixCellsReported!==true)
   fail('INCOMPLETE_PAIRED_MODEL_OBSERVATIONS');
 const artifact={
  kind:'E6_W5L_OWNER_CPU_UNATTESTED_METADATA_V1',
  sourceCommitSha:context.headSha,
  reportedGithubRunId:process.env.GITHUB_RUN_ID,
  receipt:assessed.receipt,comparison:assessed.comparison,
  captureCount:assessed.capturedEnvelopesExamined,
  providerResponsesIndependentlyAuthenticated:false,
  modelWeightsAttested:false,originalityVerified:false,
  manualVisualReviewCompleted:false,ownerReleaseApproved:false,
  stage45Go:false,releaseGo:false
 };
 await saveSanitized(artifact);
 process.stdout.write('E6_W5L_PAIRED_CPU_SANITIZED_RESULT_ONLY '+
  JSON.stringify({sourceCommitSha:context.headSha,
   recordedCells:6,receiptSha256:assessed.receipt.receiptSha256,
   reportedOutcomes:assessed.receipt.observations.map(x=>x.outcome),
   modelWeightsAttested:false,releaseGo:false})+'\n');
}
main().catch(e=>{
 const code=typeof e?.message==='string'&&
  /^E6_W5[ABCHKL]_[A-Z0-9_]{1,96}$/.test(e.message)?
   e.message:'E6_W5L_UNVERIFIED_CPU_FAILURE';
 // Never print raw provider responses, prompts, stacks or GitHub credentials.
 console.error('E6_W5L_OWNER_CPU_BLOCKED',code);
 process.exitCode=1;
});
