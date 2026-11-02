/** Owner-labeled cloud CPU experiment CLI. NEVER called by normal PR CI.
 * commands: authorize, summarize, missing-report (only after failed trial).
 * Fixed paths; no client/model controlled filenames or secrets.
 */
import {readFile,lstat,mkdir,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {authorizeLabelEvent,summarizeObservedTrial,summarizeMissingCaseReport} from './w5j_cpu_trial_gate.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const fail=x=>{throw Error('E6_W5J_'+x);};
const readSmall=async(path,maxBytes,{includeBytes=false}={})=>{
 const info=await lstat(path);
 if(!info.isFile()||info.isSymbolicLink()||info.size>maxBytes||info.size===0)
   fail('UNSAFE_EVIDENCE_FILE');
 const raw=await readFile(path);
 let parsed;
 try{parsed=JSON.parse(raw.toString('utf8'));}catch{fail('MALFORMED_EVIDENCE_JSON');}
 return includeBytes?{value:parsed,raw}:parsed;
};
async function main(){
 const mode=process.argv[2];
 if(process.argv.length!==3||!['authorize','summarize','missing-report'].includes(mode))
  fail('INVALID_FIXED_COMMAND');
 if(process.env.GITHUB_EVENT_NAME!=='pull_request'||
    process.env.GITHUB_REPOSITORY!=='teldigi5-wq/aetheris-ai-runtime'||
    process.env.E6_W5J_ENABLE_OWNER_LABELED_CPU_TRIAL!=='YES')
  fail('NOT_APPROVED_CLOUD_CPU_CONTEXT');
 const head=process.env.E6_W5J_CHECKED_OUT_PR_HEAD;
 if(mode==='authorize'){
  const eventPath=process.env.GITHUB_EVENT_PATH;
  if(typeof eventPath!=='string'||!eventPath||
     !eventPath.startsWith('/home/runner/work/_temp/'))
    fail('UNTRUSTED_GITHUB_EVENT_PATH');
  const event=await readSmall(eventPath,100_000);
  const info=authorizeLabelEvent(event,{
   actor:process.env.GITHUB_ACTOR,
   repository:process.env.GITHUB_REPOSITORY,
   expectedPr:Number(process.env.E6_W5J_APPROVED_PR_NUMBER),
   headSha:head
  });
  console.log('E6_W5J_CLOUD_CPU_OWNER_APPROVAL_VERIFIED '+JSON.stringify({
   reviewedHeadSha:info.reviewedHeadSha,approvedPr:info.expectedPr,
   gpuAuthorized:false,releaseGo:false}));
  return;
 }
 if(mode==='missing-report'){
  const target=join(here,'target');
  try{
    const parent=await lstat(target);
    if(parent.isSymbolicLink()||!parent.isDirectory())fail('UNSAFE_TARGET_PARENT');
  }catch(e){
    if(e.code!=='ENOENT')throw e;
    await mkdir(target,{mode:0o700});
  }
  let reportAvailable=false;
  try{
    const stat=await lstat(join(here,'target/e6-w5c-local-css-research/source-code-trial-report.json'));
    if(stat.isSymbolicLink()||!stat.isFile())fail('UNSAFE_EVIDENCE_FILE');
    reportAvailable=stat.size>0;
  }catch(e){if(e.code!=='ENOENT')throw e;}
  const result=summarizeMissingCaseReport({sourceSha:head,caseReportAvailable:reportAvailable});
  const evidenceDir=join(here,'target/e6-w5j-cloud-cpu-review');
  await mkdir(evidenceDir,{recursive:false,mode:0o700});
  await writeFile(join(evidenceDir,'missing-case-report.json'),
    JSON.stringify(result,null,2)+'\n',{flag:'wx',mode:0o600});
  console.log('E6_W5J_MISSING_REPORT_NO_MODEL_CASE_CLAIM '+JSON.stringify({
    reportAvailable:false,caseAttemptsKnown:false,releaseGo:false
  }));
  return;
 }
 const batch=await readSmall(join(here,'w5b_heldout_briefs.json'),30_000);
 const report=await readSmall(
  join(here,'target/e6-w5c-local-css-research/source-code-trial-report.json'),
  120_000,{includeBytes:true});
 const sum=summarizeObservedTrial(report.value,batch.briefs,{
  sourceSha:head,reportRawBytes:report.raw
 });
 const evidenceDir=join(here,'target/e6-w5j-cloud-cpu-review');
 await mkdir(evidenceDir,{recursive:false,mode:0o700});
 await writeFile(join(evidenceDir,'three-brief-summary.json'),
  JSON.stringify(sum,null,2)+'\n',{flag:'wx',mode:0o600});
 console.log('E6_W5J_REAL_LOOPBACK_ATTEMPT_SUMMARY '+JSON.stringify({
  cases:3,accepted:sum.accepted,rejected:sum.rejected,
  reviewStatus:sum.candidateStatus,
  actualWeightsCryptographicallyAttested:false,releaseGo:false}));
}
main().catch(e=>{
 console.error('E6_W5J_OWNER_CPU_PREVIEW_BLOCKED',
   /^E6_W5J_[A-Z_]+$/.test(e?.message)?e.message:'E6_W5J_UNVERIFIED_FAILURE');
 process.exitCode=1;
});
