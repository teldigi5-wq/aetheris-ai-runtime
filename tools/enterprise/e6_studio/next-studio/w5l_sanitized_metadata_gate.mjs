/** W5-L upload gate: local, READ-ONLY JSON allowlist. Never attest model origin.
 * Reject every unexpected byte before publishing an owner CPU receipt.
 */
import {open,lstat,readdir} from 'node:fs/promises';
import {constants} from 'node:fs';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {isDeepStrictEqual} from 'node:util';
import {buildPairedEvaluationProtocol,summarizePairedResearch} from './w5l_paired_evaluation_protocol.mjs';
import {createPairedResearchReceipt} from './w5l_research_receipt.mjs';
import {makeUnverifiablePairedCpuAttempt} from './w5l_owner_cpu_gate.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const fail=code=>{throw Error('E6_W5L_UPLOAD_'+code);};
const exact=(value,keys)=>value!==null&&typeof value==='object'&&
 !Array.isArray(value)&&Object.getPrototypeOf(value)===Object.prototype&&
 Object.keys(value).length===keys.length&&
 Object.keys(value).every(k=>keys.includes(k))&&keys.every(k=>Object.hasOwn(value,k));
const releaseFields={providerResponsesIndependentlyAuthenticated:false,
 modelWeightsAttested:false,originalityVerified:false,
 manualVisualReviewCompleted:false,ownerReleaseApproved:false,
 stage45Go:false,releaseGo:false};
const successKeys=['kind','sourceCommitSha','reportedGithubRunId','receipt',
 'comparison','captureCount',...Object.keys(releaseFields)];
const maxBytes=40_000;
const fileName='paired-design-metadata.json';
const requiredDir=async path=>{
 const entry=await lstat(path);
 if(!entry.isDirectory()||entry.isSymbolicLink())
  fail('ARTIFACT_DIRECTORY_UNTRUSTED');
};
const readStrict=async path=>{
 const handle=await open(path,constants.O_RDONLY|constants.O_NOFOLLOW);
 try{
  const first=await handle.stat();
  if(!first.isFile()||first.size<3||first.size>maxBytes)
   fail('ARTIFACT_FILE_SIZE_OR_TYPE_UNTRUSTED');
  const raw=Buffer.alloc(first.size);
  let cursor=0;
  while(cursor<raw.length){
   const {bytesRead}=await handle.read(raw,cursor,raw.length-cursor,cursor);
   if(bytesRead<1)fail('ARTIFACT_FILE_CHANGED_DURING_READ');
   cursor+=bytesRead;
  }
  if((await handle.read(Buffer.alloc(1),0,1,cursor)).bytesRead!==0)
   fail('ARTIFACT_FILE_CHANGED_DURING_READ');
  const after=await handle.stat();
  if(after.size!==first.size||after.mtimeMs!==first.mtimeMs||
     after.ino!==first.ino||after.dev!==first.dev)
   fail('ARTIFACT_FILE_CHANGED_DURING_READ');
  const bytes=raw.toString('utf8');
  let value;
  try{value=JSON.parse(bytes);}catch{fail('ARTIFACT_JSON_INVALID');}
  // The producer emits canonical pretty JSON. Byte-for-byte canonicality
  // rejects duplicate JSON members that a permissive parser would discard,
  // preventing hidden raw model text in an otherwise clean parsed object.
  if(bytes!==JSON.stringify(value,null,2)+'\n')
   fail('ARTIFACT_NONCANONICAL_OR_DUPLICATE_JSON');
  return value;
 }finally{await handle.close();}
};
export async function inspectSanitizedCpuArtifact({directory,sourceCommitSha,githubRunId,briefs}={}){
 if(typeof directory!=='string'||!/^[0-9a-f]{40}$/.test(sourceCommitSha||'')||
    !/^[0-9]{1,24}$/.test(githubRunId||''))
  fail('ARTIFACT_REVIEW_CONTEXT_INVALID');
 await requiredDir(dirname(directory));
 await requiredDir(directory);
 const files=await readdir(directory);
 if(files.length!==1||files[0]!==fileName)
  fail('UNEXPECTED_OR_MISSING_UPLOAD_FILES');
 const record=await readStrict(join(directory,fileName));
 if(record?.sourceCommitSha!==sourceCommitSha||
    record?.reportedGithubRunId!==githubRunId)
  fail('ARTIFACT_COMMIT_OR_RUN_NOT_PINNED');
 if(record.kind==='E6_W5L_UNVERIFIABLE_CPU_ATTEMPT_V1'){
  const expected=makeUnverifiablePairedCpuAttempt({
   sourceCommitSha,reportedGithubRunId:githubRunId
  });
  if(!isDeepStrictEqual(record,expected))fail('UNVERIFIABLE_REPORT_NOT_CANONICAL');
  return Object.freeze({kind:'E6_W5L_UPLOAD_SCHEMA_GATE_V1',
   validatedKind:record.kind,completeSixCells:false,
   schemaOnlyNotAuthenticity:true,releaseGo:false,stage45Go:false});
 }
 if(record.kind!=='E6_W5L_OWNER_CPU_UNATTESTED_METADATA_V1'||
    !exact(record,successKeys)||
    !Object.entries(releaseFields).every(([key,v])=>record[key]===v)||
    !Number.isInteger(record.captureCount)||record.captureCount<6||
    record.captureCount>15)
  fail('SUCCESS_REPORT_UNTRUSTED_SHAPE_OR_CLAIMS');
 const protocol=buildPairedEvaluationProtocol({
  briefs,sourceCommitSha
 });
 const expectedReceipt=createPairedResearchReceipt(protocol,record.receipt?.observations);
 const expectedComparison=summarizePairedResearch(protocol,record.receipt.observations);
 const attempts=record.receipt.observations.reduce(
  (sum,x)=>sum+x.reportedDesignCalls,0
 );
 if(!expectedComparison.allSixCellsReported||
    attempts!==record.captureCount||
    !isDeepStrictEqual(record.receipt,expectedReceipt)||
    !isDeepStrictEqual(record.comparison,expectedComparison))
  fail('SUCCESS_RECEIPT_OR_COMPARISON_NOT_CANONICAL');
 return Object.freeze({kind:'E6_W5L_UPLOAD_SCHEMA_GATE_V1',
  validatedKind:record.kind,completeSixCells:true,
  schemaOnlyNotAuthenticity:true,releaseGo:false,stage45Go:false});
}
async function main(){
 if(process.argv.length!==2||
    process.env.GITHUB_ACTIONS!=='true'||
    process.env.GITHUB_EVENT_NAME!=='pull_request'||
    process.env.GITHUB_REPOSITORY!=='teldigi5-wq/aetheris-ai-runtime'||
    process.env.GITHUB_ACTOR!=='teldigi5-wq'||
    process.env.E6_W5L_ENABLE_OWNER_LABELED_CPU_TRIAL!=='YES')
  fail('EPHEMERAL_APPROVED_RUNNER_REQUIRED');
 const {readFile}=await import('node:fs/promises');
 const batch=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
 const result=await inspectSanitizedCpuArtifact({
  directory:join(here,'target','e6-w5l-owner-cpu-review'),
  sourceCommitSha:process.env.E6_W5L_CHECKED_OUT_PR_HEAD,
  githubRunId:process.env.GITHUB_RUN_ID,
  briefs:batch.briefs
 });
 process.stdout.write('E6_W5L_UPLOAD_ONLY_SCHEMA_VERIFIED_NO_MODEL_ATTESTATION '+
  JSON.stringify(result)+'\n');
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===process.argv[1])
 main().catch(()=>{console.error('E6_W5L_UPLOAD_BLOCKED');
  process.exitCode=1;});
