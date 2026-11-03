/** Owner-labeled cloud CPU experiment CLI. NEVER called by normal PR CI.
 * commands: authorize, live-approval, summarize, missing-report, unverified-report.
 * Fixed paths; no client/model controlled filenames or secrets.
 */
import {lstat,mkdir,writeFile,open,realpath} from 'node:fs/promises';
import {constants} from 'node:fs';
import {createHash} from 'node:crypto';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {authorizeLabelEvent,authorizeCurrentOwnerPullRequest,summarizeWithSavedSourceAudit,summarizeMissingCaseReport,summarizeUnverifiableCaseReport} from './w5j_cpu_trial_gate.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const fail=x=>{throw Error('E6_W5J_'+x);};
// GitHub runner paths are data, not authority. Reject lexical traversal and
// intermediate symlinks before opening a bounded, no-follow evidence file.
// The final file is opened with O_NOFOLLOW and pinned by its descriptor.
// This preflight does not make untrusted parent directories race-proof.
const checkRunnerPath=async(path,{directory=false}={})=>{
 const root='/home/runner/work/_temp';
 if(typeof path!=='string'||path!==resolve(path)||
    !(directory?path===root||path.startsWith(root+'/'):path.startsWith(root+'/')))
   fail('UNTRUSTED_RUNNER_EVIDENCE_PATH');
 const parent=directory?path:dirname(path);
 let canonical;
 try{canonical=await realpath(parent);}
 catch{fail('UNTRUSTED_RUNNER_EVIDENCE_PATH');}
 if(canonical!==parent)fail('UNTRUSTED_RUNNER_EVIDENCE_PATH');
};
// Pin the inode before inspecting it: lstat(path) followed by readFile(path)
// permits a concurrent last-component symlink/file swap. O_NOFOLLOW blocks
// symlinks on the opened leaf; an FD read stays on that same file.
const readSmall=async(path,maxBytes,{includeBytes=false}={})=>{
 const handle=await open(path,constants.O_RDONLY|constants.O_NOFOLLOW);
 try{
  const before=await handle.stat();
  if(!before.isFile()||before.size>maxBytes||before.size===0)
   fail('UNSAFE_EVIDENCE_FILE');
  // Allocate only the validated cap and refuse concurrent growth/truncation.
  const raw=Buffer.alloc(before.size);
  let total=0;
  while(total<raw.length){
   const {bytesRead}=await handle.read(raw,total,raw.length-total,total);
   if(bytesRead===0)fail('UNSAFE_EVIDENCE_FILE');
   total+=bytesRead;
  }
  const extra=Buffer.alloc(1);
  if((await handle.read(extra,0,1,total)).bytesRead!==0)
   fail('UNSAFE_EVIDENCE_FILE');
  const after=await handle.stat();
  if(after.size!==before.size||after.mtimeMs!==before.mtimeMs)
   fail('UNSAFE_EVIDENCE_FILE');
  let parsed;
  try{parsed=JSON.parse(raw.toString('utf8'));}catch{fail('MALFORMED_EVIDENCE_JSON');}
  return includeBytes?{value:parsed,raw}:parsed;
 }finally{await handle.close();}
};
async function main(){
 const mode=process.argv[2];
 if(process.argv.length!==3||!['authorize','live-approval','summarize','missing-report','unverified-report'].includes(mode))
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
  await checkRunnerPath(eventPath);
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
 if(mode==='live-approval'){
  const temp=process.env.RUNNER_TEMP;
  if(typeof temp!=='string'||!/^\/home\/runner\/work\/_temp(?:\/[a-zA-Z0-9_.-]+)*$/.test(temp))
   fail('UNTRUSTED_LIVE_APPROVAL_TEMP_PATH');
  await checkRunnerPath(temp,{directory:true});
  const live=await readSmall(join(temp,'w5j-live-owner-pr.json'),200_000);
  const result=authorizeCurrentOwnerPullRequest(live,{
   actor:process.env.GITHUB_ACTOR,
   repository:process.env.GITHUB_REPOSITORY,
   expectedPr:Number(process.env.E6_W5J_APPROVED_PR_NUMBER),
   headSha:head
  });
  console.log('E6_W5J_LIVE_OWNER_LABEL_AND_HEAD_STILL_APPROVED '+JSON.stringify({
   approvedPr:result.approvedPr,headSha:result.headSha,
   gpuAuthorized:false,releaseGo:false
  }));
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
 if(mode==='unverified-report'){
  // No report bytes are read. Never echo potentially corrupted model output.
  const target=join(here,'target');
  const targetInfo=await lstat(target);
  if(targetInfo.isSymbolicLink()||!targetInfo.isDirectory())
    fail('UNSAFE_TARGET_PARENT');
  const sourceDir=join(target,'e6-w5c-local-css-research');
  const dirInfo=await lstat(sourceDir);
  if(dirInfo.isSymbolicLink()||!dirInfo.isDirectory())
    fail('UNSAFE_SOURCE_DIRECTORY');
  const sourceInfo=await lstat(join(sourceDir,'source-code-trial-report.json'));
  if(sourceInfo.isSymbolicLink()||!sourceInfo.isFile()||
     sourceInfo.size<=0)
    fail('NO_NONEMPTY_SOURCE_REPORT');
  const receipt=summarizeUnverifiableCaseReport({
    sourceSha:head,caseReportPresent:true
  });
  const evidenceDir=join(target,'e6-w5j-cloud-cpu-review');
  await mkdir(evidenceDir,{recursive:false,mode:0o700});
  await writeFile(join(evidenceDir,'unverifiable-case-report.json'),
    JSON.stringify(receipt,null,2)+'\n',{flag:'wx',mode:0o600});
  console.log('E6_W5J_NONEMPTY_REPORT_UNVERIFIABLE_NO_RELEASE '+JSON.stringify({
    casesVerified:0,attemptsKnown:false,releaseGo:false
  }));
  return;
 }
 const batch=await readSmall(join(here,'w5b_heldout_briefs.json'),30_000);
 const report=await readSmall(
  join(here,'target/e6-w5c-local-css-research/source-code-trial-report.json'),
  120_000,{includeBytes:true});
 const sum=await summarizeWithSavedSourceAudit(report.value,batch,{
  sourceSha:head,reportRawBytes:report.raw,
  trialRoot:join(here,'target/e6-w5c-local-css-research')
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
