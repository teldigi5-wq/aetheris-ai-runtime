import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {buildPairedEvaluationProtocol,summarizePairedResearch} from './w5l_paired_evaluation_protocol.mjs';
const briefs=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')).briefs;
const HEAD='a'.repeat(40);
const manifest=()=>buildPairedEvaluationProtocol({briefs,sourceCommitSha:HEAD});
const empty=p=>p.planned.map(x=>({projectSlug:x.projectSlug,mode:x.mode,
 outcome:'NOT_RUN',failureCode:null,reportedDesignCalls:null,
 reportedOutputTokens:null,reportedElapsedMs:null}));
test('paired protocol pins actual held-out identities, two modes and unequal request budgets',()=>{
 const p=manifest();
 assert.equal(p.planned.length,6);
 assert.equal(p.sourceCommitSha,HEAD);
 assert.match(p.protocolSha256,/^[a-f0-9]{64}$/);
 assert.equal(manifest().protocolSha256,p.protocolSha256);
 assert.deepEqual([...new Set(p.planned.map(x=>x.projectSlug))],briefs.map(x=>x.projectSlug));
 assert.deepEqual(p.methods.map(x=>x.designCallsMax),[2,3]);
 assert.equal(p.modelTag,'qwen2.5:0.5b');
 assert.equal(p.modelRequestsMade,0);
 assert.equal(p.executionOrder,'SERIAL_FIXED_BRIEF_THEN_METHOD');
 assert.equal(p.modelInvocationAuthorized,false);
 assert.equal(p.releaseGo,false);
 assert.throws(()=>buildPairedEvaluationProtocol({briefs,sourceCommitSha:'b'.repeat(64)}),/SOURCE_SHA_NOT_GIT_COMMIT/);
 const changed=structuredClone(briefs);
 changed[0].projectSlug='other';
 assert.throws(()=>buildPairedEvaluationProtocol({briefs:changed,sourceCommitSha:HEAD}),/BRIEF_MATRIX_CHANGED/);
});
test('empty and previously unrun staged arm produce NO comparison or claimed real model success',()=>{
 const p=manifest(),obs=empty(p);
 const zero=summarizePairedResearch(p,obs);
 assert.equal(zero.allSixCellsReported,false);
 assert.equal(zero.observedAcceptanceDifference,null);
 assert.equal(zero.realProviderEvidenceVerified,false);
 assert.equal(zero.ownerExperimentApproved,false);
 assert.equal(zero.statisticallyPowered,false);
 assert.equal(zero.stage45Go,false);
 // Sanitized historical categories used as illustrative, unverified
 // observations. The historical real run is not imported or attested.
 const codes=['DESIGN_ABNORMAL_FINISH','DESIGN_SCHEMA_REJECTED','DESIGN_RESPONSE_BUDGET'];
 for(let i=0;i<3;i++){
   obs[2*i]={...obs[2*i],outcome:'REJECTED',failureCode:codes[i],
    reportedDesignCalls:1,reportedOutputTokens:0,reportedElapsedMs:0};
 }
 const summary=summarizePairedResearch(p,obs);
 assert.equal(summary.methods[0].rejected,3);
 assert.equal(summary.methods[1].notRun,3);
 assert.equal(summary.observedAcceptanceDifference,null);
 assert.equal(summary.assessment,'SELF_REPORTED_ONLY_NOT_A_REAL_MODEL_VERDICT');
});
test('complete synthetic data allows descriptive difference but never a winner or GO',()=>{
 const p=manifest(),obs=empty(p);
 for(let i=0;i<obs.length;i++)obs[i]={...obs[i],
   outcome:i===2?'REJECTED':'ACCEPTED',
   failureCode:i===2?'DESIGN_SCHEMA_REJECTED':null,
   reportedDesignCalls:i%2===0?1:3,
   reportedOutputTokens:500,reportedElapsedMs:1000};
 const summary=summarizePairedResearch(p,obs);
 assert.equal(summary.allSixCellsReported,true);
 assert.equal(summary.methods[0].accepted,2);
 assert.equal(summary.methods[1].accepted,3);
 assert.equal(summary.observedAcceptanceDifference,1);
 assert.equal(summary.cumulativeReportedElapsedMs,6000);
 assert.equal(summary.comparableBudget,false);
 assert.equal(summary.realProviderEvidenceVerified,false);
 assert.equal(summary.originalDesignQualityVerified,false);
 assert.equal(summary.stage45Go,false);
});
test('research summaries reject untrusted raw content, extra keys, missing cells and authority injections',()=>{
 const p=manifest(),obs=empty(p);
 const reject=(mutator,error)=>{
   const x=structuredClone(obs);
   mutator(x);
   assert.throws(()=>summarizePairedResearch(p,x),error);
 };
 reject(x=>x.pop(),/INCOMPLETE_PAIRED_MATRIX/);
 reject(x=>x[0].mode='three_stage',/UNTRUSTED_RESEARCH_OBSERVATION/);
 reject(x=>x[0].ownerApproved=true,/UNTRUSTED_RESEARCH_OBSERVATION/);
 reject(x=>x[0].rawModelOutput='secret',/UNTRUSTED_RESEARCH_OBSERVATION/);
 reject(x=>x[0].reportedElapsedMs=1000,/FABRICATED_NOT_RUN_METRICS/);
 reject(x=>{x[0].outcome='ACCEPTED';x[0].failureCode=null;
   x[0].reportedDesignCalls=0;x[0].reportedOutputTokens=0;x[0].reportedElapsedMs=0;
 },/INVALID_BOUNDED_OBSERVATION/);
 reject(x=>{x[0].outcome='REJECTED';x[0].failureCode='INJECTED_AUTHORITY';
   x[0].reportedDesignCalls=1;x[0].reportedOutputTokens=0;x[0].reportedElapsedMs=500;
 },/INVALID_BOUNDED_OBSERVATION/);
 const changed=structuredClone(p);
 changed.planned[0].designCallsMax=1000;
 assert.throws(()=>summarizePairedResearch(changed,obs),/PROTOCOL_MATRIX_CHANGED/);
});
test('paired comparison has no network, model execution or filesystem writes',async()=>{
 const source=await readFile(new URL('./w5l_paired_evaluation_protocol.mjs',import.meta.url),'utf8');
 for(const token of ['fetch(', 'requestLocalOllama', 'writeFile(', 'createWriteStream',
 'process.env','https://','child_process','workflow_dispatch']){
   assert(!source.includes(token),'side-effect or external authority token '+token);
 }
 assert(source.includes('modelRequestsMade:0'));
 assert(source.includes('realProviderEvidenceVerified:false'));
});

test('55-minute ceiling constrains aggregate serial observations, not only each cell',()=>{
 const p=manifest(),obs=empty(p);
 for(let i=0;i<obs.length;i++)obs[i]={...obs[i],
   outcome:'ACCEPTED',failureCode:null,reportedDesignCalls:i%2===0?1:3,
   reportedOutputTokens:100,reportedElapsedMs:600_000};
 assert.throws(()=>summarizePairedResearch(p,obs),/AGGREGATE_JOB_TIME_BUDGET_EXCEEDED/);
 // Exactly at the whole-job ceiling, bounded self-reports remain explicitly unverified.
 for(let i=0;i<obs.length;i++)obs[i].reportedElapsedMs=550_000;
 const s=summarizePairedResearch(p,obs);
 assert.equal(s.cumulativeReportedElapsedMs,3_300_000);
 assert.equal(s.realProviderEvidenceVerified,false);
 assert.equal(s.releaseGo,false);
});
test('re-hashing an altered protocol cannot forge model, clock budget, plan, order or source scope',()=>{
 const p=manifest(),obs=empty(p);
 const rehash=x=>{
  const {protocolSha256,...other}=x;
  x.protocolSha256=createHash('sha256').update(JSON.stringify(other)).digest('hex');
  return x;
 };
 for(const change of [
  x=>{x.modelTag='unauthorized:large';},
  x=>{x.runtimeVersion='99.99.99';},
  x=>{x.jobWallClockCeilingMinutes=10_000;},
  x=>{x.executionOrder='PARALLEL_UNBOUNDED';},
  x=>{x.methods[1].designCallsMax=100;},
  x=>{x.planned[0].projectSlug='unapproved-brief';},
  x=>{x.planned[0].designCallsMax=99;},
  x=>{x.planned[0].outputTokenCeilingPerCall=30_000;},
  x=>{x.modelRequestsMade=1;},
  x=>{x.ownerApproved=true;}
 ]){
  const forged=rehash((()=>{const x=structuredClone(p);change(x);return x;})());
  assert.throws(()=>summarizePairedResearch(forged,obs),
    /PROTOCOL_NOT_ACCEPTED|PROTOCOL_MATRIX_CHANGED/);
 }
});
test('reported design output tokens require actual claimed design calls and accepted output',()=>{
 const p=manifest(),obs=empty(p);
 const failRow=x=>assert.throws(()=>summarizePairedResearch(p,x),/INVALID_BOUNDED_OBSERVATION/);
 const fabricated=structuredClone(obs);
 fabricated[0]={...fabricated[0],outcome:'REJECTED',failureCode:'DESIGN_MODEL_UNAVAILABLE',
   reportedDesignCalls:0,reportedOutputTokens:100,reportedElapsedMs:0};
 failRow(fabricated);
 const emptyAccepted=structuredClone(obs);
 emptyAccepted[0]={...emptyAccepted[0],outcome:'ACCEPTED',failureCode:null,
   reportedDesignCalls:1,reportedOutputTokens:0,reportedElapsedMs:10};
 failRow(emptyAccepted);
});
