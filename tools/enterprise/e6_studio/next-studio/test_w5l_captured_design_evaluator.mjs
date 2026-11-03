import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {evaluateCapturedPairedDesign} from './w5l_captured_design_evaluator.mjs';
import {buildPrompt} from './w5a_local_ollama.mjs';
import {buildStagedDesignPrompt} from './w5k_staged_design_research.mjs';
import {buildDesignRepairPrompt} from './w5h_bounded_model_repair.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
const briefs=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')).briefs;
const sha=x=>createHash('sha256').update(x,'utf8').digest('hex');
const HEAD='a'.repeat(40),MODEL='qwen2.5:0.5b';
const reply=(phase,prompt,payload)=>({phase,promptSha256:sha(prompt),
 envelope:{model:MODEL,done:true,done_reason:'stop',
  response:JSON.stringify(payload),eval_count:150}});
function fixtures(){
 return briefs.flatMap((brief,i)=>{
  const full=syntheticDesign(brief,i);
  const {pages,...identity}=full;
  const home={version:1,projectSlug:brief.projectSlug,page:pages[0]};
  const approach={version:1,projectSlug:brief.projectSlug,page:pages[1]};
  return [
   {projectSlug:brief.projectSlug,mode:'single_compact',reportedElapsedMs:1000,
    attempts:[reply('design',buildPrompt(brief),full)]},
   {projectSlug:brief.projectSlug,mode:'three_stage',reportedElapsedMs:1500,
    attempts:[
     reply('identity',buildStagedDesignPrompt(brief,'identity'),identity),
     reply('home',buildStagedDesignPrompt(brief,'home',{identity}),home),
     reply('approach',buildStagedDesignPrompt(brief,'approach',{identity,home}),approach)
    ]}
  ];
 });
}
const evaluate=captures=>evaluateCapturedPairedDesign({briefs,sourceCommitSha:HEAD,captures});
test('six captured fixture cells derive accepted design-only observations with no real attestation',()=>{
 const a=evaluate(fixtures());
 assert.equal(a.comparison.allSixCellsReported,true);
 assert.deepEqual(a.comparison.methods.map(x=>x.accepted),[3,3]);
 assert.equal(a.capturedEnvelopesExamined,12);
 assert.equal(a.comparison.cumulativeReportedElapsedMs,7500);
 assert.equal(a.receipt.observations.length,6);
 assert.equal(a.receipt.observations[0].reportedDesignCalls,1);
 assert.equal(a.receipt.observations[1].reportedDesignCalls,3);
 assert.equal(a.providerAuthenticityEstablished,false);
 assert.equal(a.actualInferenceIndependentlyAttested,false);
 assert.equal(a.comparison.realProviderEvidenceVerified,false);
 assert.equal(a.comparison.stage45Go,false);
 assert.deepEqual(a,evaluate(fixtures()));
});
test('a valid compact retry requires exact sanitized repair prompt and rejects replay',()=>{
 const captures=fixtures(),brief=briefs[0];
 const bad=syntheticDesign(brief,0);bad.art.accent=bad.art.surface;
 const failure='E6_W5A_MODEL_SCHEMA_OR_CONTRAST_REJECTED';
 captures[0].attempts=[
  reply('design',buildPrompt(brief),bad),
  reply('design',buildDesignRepairPrompt(brief,failure),syntheticDesign(brief,0))
 ];
 assert.equal(evaluate(captures).comparison.methods[0].accepted,3);
 assert.equal(evaluate(captures).receipt.observations[0].reportedDesignCalls,2);
 const extra=fixtures();extra[0].attempts.push(reply('design',buildPrompt(brief),syntheticDesign(brief,0)));
 assert.throws(()=>evaluate(extra),/UNNECESSARY_EXTRA_CAPTURE/);
 const forged=fixtures();forged[0].attempts[0].promptSha256='f'.repeat(64);
 assert.throws(()=>evaluate(forged),/INVALID_CAPTURE_ENVELOPE_OR_PROMPT/);
});
test('provider abnormal finish is a rejection, never a valid retry or design acceptance',()=>{
 const x=fixtures();x[0].attempts[0].envelope.done_reason='length';
 const result=evaluate(x);
 assert.equal(result.receipt.observations[0].failureCode,'DESIGN_ABNORMAL_FINISH');
 assert.equal(result.comparison.methods[0].rejected,1);
 assert.equal(result.comparison.realProviderEvidenceVerified,false);
 const bad=fixtures();bad[0].attempts[0].envelope.done_reason='length';
 bad[0].attempts.push(reply('design',buildPrompt(briefs[0]),syntheticDesign(briefs[0],0)));
 assert.throws(()=>evaluate(bad),/UNAUTHORIZED_RETRY_AFTER_PROVIDER_FAILURE/);
});
test('invalid stage cannot continue with another model phase or fake acceptance',()=>{
 const x=fixtures();const invalid=JSON.parse(x[1].attempts[0].envelope.response);
 invalid.art.accent=invalid.art.surface;
 x[1].attempts[0].envelope.response=JSON.stringify(invalid);
 assert.throws(()=>evaluate(x),/POST_REJECTION_CAPTURE/);
 x[1].attempts=x[1].attempts.slice(0,1);
 const report=evaluate(x);
 assert.equal(report.receipt.observations[1].failureCode,'DESIGN_SCHEMA_REJECTED');
 assert.equal(report.comparison.methods[1].rejected,1);
 assert.equal(report.capturedEnvelopesExamined,10);
});
test('missing captures are NOT_RUN, never model failures or accepted designs',()=>{
 const x=fixtures();x[4].attempts=[];x[4].reportedElapsedMs=null;
 const y=evaluate(x);
 assert.equal(y.receipt.observations[4].outcome,'NOT_RUN');
 assert.equal(y.comparison.observedAcceptanceDifference,null);
 const wrong=fixtures();wrong[4].attempts=[];
 assert.throws(()=>evaluate(wrong),/FABRICATED_UNRUN_ELAPSED/);
});
test('bad project, model tag, prompt, overlong response, injected fields and aggregate time fail closed',()=>{
 const cases=[
  x=>{x[0].projectSlug='another';},
  x=>{x[0].attempts[0].envelope.model='unapproved-model:5b';},
  x=>{x[1].attempts[1].promptSha256='0'.repeat(64);},
  x=>{x[0].attempts[0].envelope.response='x'.repeat(36001);},
  x=>{x[0].attempts[0].ownerOverride=true;},
  x=>{x[0].attempts[0].envelope.secret='injected';}
 ];
 for(const change of cases){
  const x=fixtures();change(x);
  assert.throws(()=>evaluate(x),/CAPTURE_MATRIX_CHANGED|INVALID_CAPTURE_ENVELOPE_OR_PROMPT/);
 }
 const overflow=fixtures();for(const x of overflow)x.reportedElapsedMs=600000;
 assert.throws(()=>evaluate(overflow),/AGGREGATE_JOB_TIME_BUDGET_EXCEEDED/);
});
test('pure evaluator has no model transport, writes, clock, environment or authorization',async()=>{
 const source=await readFile(new URL('./w5l_captured_design_evaluator.mjs',import.meta.url),'utf8');
 for(const forbidden of ['fetch(', 'requestLocalOllama', 'writeFile(', 'createWriteStream',
   'process.env','child_process','workflow_dispatch','https://','Date.now('])
  assert(!source.includes(forbidden),forbidden);
 assert(source.includes('providerAuthenticityEstablished:false'));
 assert(source.includes('actualInferenceIndependentlyAttested:false'));
});
