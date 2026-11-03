import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rm,symlink,readFile} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {inspectSanitizedCpuArtifact} from './w5l_sanitized_metadata_gate.mjs';
import {buildPairedEvaluationProtocol,summarizePairedResearch} from './w5l_paired_evaluation_protocol.mjs';
import {createPairedResearchReceipt} from './w5l_research_receipt.mjs';
import {makeUnverifiablePairedCpuAttempt} from './w5l_owner_cpu_gate.mjs';
const SHA='a'.repeat(40),RUN='41643658425';
const briefFile=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8'));
const briefs=briefFile.briefs;
const makeSuccess=()=>{
 const protocol=buildPairedEvaluationProtocol({briefs,sourceCommitSha:SHA});
 const rows=protocol.planned.map((p,i)=>({
  projectSlug:p.projectSlug,mode:p.mode,outcome:'ACCEPTED',
  failureCode:null,reportedDesignCalls:i%2?3:1,
  reportedOutputTokens:i%2?450:150,reportedElapsedMs:i%2?360:120
 }));
 return {
  kind:'E6_W5L_OWNER_CPU_UNATTESTED_METADATA_V1',
  sourceCommitSha:SHA,reportedGithubRunId:RUN,
  receipt:createPairedResearchReceipt(protocol,rows),
  comparison:summarizePairedResearch(protocol,rows),
  captureCount:12,
  providerResponsesIndependentlyAuthenticated:false,
  modelWeightsAttested:false,originalityVerified:false,
  manualVisualReviewCompleted:false,ownerReleaseApproved:false,
  stage45Go:false,releaseGo:false
 };
};
const failCase=()=>makeUnverifiablePairedCpuAttempt({
 sourceCommitSha:SHA,reportedGithubRunId:RUN
});
async function fixture(){
 const base=await mkdtemp(join(tmpdir(),'w5l-upload-test-'));
 const dir=join(base,'review');
 await mkdir(dir);
 const path=join(dir,'paired-design-metadata.json');
 const write=async obj=>writeFile(path,JSON.stringify(obj,null,2)+'\n');
 return {base,dir,path,write,cleanup:()=>rm(base,{recursive:true,force:true})};
}
const check=directory=>inspectSanitizedCpuArtifact({
 directory,sourceCommitSha:SHA,githubRunId:RUN,briefs
});
test('only exact sanitized complete six-cell record is accepted for schema review',async()=>{
 const f=await fixture();try{
  await f.write(makeSuccess());
  const result=await check(f.dir);
  assert.equal(result.completeSixCells,true);
  assert.equal(result.schemaOnlyNotAuthenticity,true);
  assert.equal(result.releaseGo,false);
 }finally{await f.cleanup();}
});
test('only canonical incomplete attempt with unknown counts is accepted',async()=>{
 const f=await fixture();try{
  await f.write(failCase());
  const result=await check(f.dir);
  assert.equal(result.completeSixCells,false);
  assert.equal(result.stage45Go,false);
 }finally{await f.cleanup();}
});
test('added keys, raw model text or fabricated release fields never upload',async()=>{
 const f=await fixture();try{
  for(const update of [
   r=>{r.rawModelResponse='SENSITIVE_PRIVATE_MODEL_TEXT';},
   r=>{r.receipt.observations[0].rawPrompt='SENSITIVE_PRIVATE_MODEL_TEXT';},
   r=>{r.comparison.rawSample='SENSITIVE_PRIVATE_MODEL_TEXT';},
   r=>{r.releaseGo=true;},
   r=>{r.modelWeightsAttested=true;},
   r=>{r.captureCount=13;}
  ]){
   const next=structuredClone(makeSuccess());update(next);
   await f.write(next);
   await assert.rejects(check(f.dir),/E6_W5L_/);
  }
  const failed={...failCase(),attemptedModelRequests:1};
  await f.write(failed);
  await assert.rejects(check(f.dir),/UNVERIFIABLE_REPORT_NOT_CANONICAL/);
 }finally{await f.cleanup();}
});
test('rehashed forged outcomes still fail canonical consistency checks',async()=>{
 const f=await fixture();try{
  const next=structuredClone(makeSuccess());
  next.receipt.observations[0].outcome='REJECTED';
  next.receipt.observations[0].failureCode='DESIGN_SCHEMA_REJECTED';
  await f.write(next);
  await assert.rejects(check(f.dir),/E6_W5L_/);
  const rebuilt=structuredClone(makeSuccess());
  rebuilt.comparison.methods[0].accepted=0;
  await f.write(rebuilt);
  await assert.rejects(check(f.dir),/SUCCESS_RECEIPT_OR_COMPARISON_NOT_CANONICAL/);
 }finally{await f.cleanup();}
});
test('duplicate keys and arbitrary JSON bytes denied even if parsed value looks safe',async()=>{
 const f=await fixture();try{
  const original=JSON.stringify(failCase(),null,2)+'\n';
  await writeFile(f.path,original.replace('  "kind":',
   '  "rawModelOutput":"PRIVATE_TEXT",\n  "kind":'));
  await assert.rejects(check(f.dir),/ARTIFACT_NONCANONICAL_OR_DUPLICATE_JSON/);
  await writeFile(f.path,original.replace('  "kind":',
   '  "kind":"E6_W5L_UNVERIFIABLE_CPU_ATTEMPT_V1",\n  "kind":'));
  await assert.rejects(check(f.dir),/ARTIFACT_NONCANONICAL_OR_DUPLICATE_JSON/);
 }finally{await f.cleanup();}
});
test('symlinks, extra artifacts, missing or oversized evidence are denied',async()=>{
 const f=await fixture();try{
  await f.write(failCase());
  await writeFile(join(f.dir,'untrusted-extra.txt'),'raw model');
  await assert.rejects(check(f.dir),/UNEXPECTED_OR_MISSING_UPLOAD_FILES/);
  await rm(join(f.dir,'untrusted-extra.txt'));
  await rm(f.path);
  await assert.rejects(check(f.dir),/UNEXPECTED_OR_MISSING_UPLOAD_FILES/);
  const outside=join(f.base,'outside.json');
  await writeFile(outside,JSON.stringify(failCase(),null,2)+'\n');
  await symlink(outside,f.path);
  await assert.rejects(check(f.dir),/ELOOP|E6_W5L_/);
  await rm(f.path);
  await writeFile(f.path,'x'.repeat(40_001));
  await assert.rejects(check(f.dir),/ARTIFACT_FILE_SIZE_OR_TYPE_UNTRUSTED/);
 }finally{await f.cleanup();}
});
test('forged source sha and run ID never treated as current experiment',async()=>{
 const f=await fixture();try{
  await f.write(makeSuccess());
  await assert.rejects(inspectSanitizedCpuArtifact({
   directory:f.dir,sourceCommitSha:'b'.repeat(40),githubRunId:RUN,briefs
  }),/ARTIFACT_COMMIT_OR_RUN_NOT_PINNED/);
  await assert.rejects(inspectSanitizedCpuArtifact({
   directory:f.dir,sourceCommitSha:SHA,githubRunId:'999',briefs
  }),/ARTIFACT_COMMIT_OR_RUN_NOT_PINNED/);
  await assert.rejects(inspectSanitizedCpuArtifact({
   directory:f.dir,sourceCommitSha:'notsha',githubRunId:RUN,briefs
  }),/ARTIFACT_REVIEW_CONTEXT_INVALID/);
 }finally{await f.cleanup();}
});
test('gate code uses no inference, network, writing or secrets',async()=>{
 const source=await readFile(new URL('./w5l_sanitized_metadata_gate.mjs',import.meta.url),'utf8');
 for(const prohibited of ['fetch(', 'writeFile(', 'requestLocalOllama','child_process',
  'process.env.GH_READ_TOKEN','model-download','exec('])
  assert(!source.includes(prohibited),prohibited);
});
