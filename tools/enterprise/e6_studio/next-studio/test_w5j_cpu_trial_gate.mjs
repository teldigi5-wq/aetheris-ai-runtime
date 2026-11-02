import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {authorizeLabelEvent,summarizeObservedTrial} from './w5j_cpu_trial_gate.mjs';
import {evaluateCodeHeldout} from './w5c_local_code_trial.mjs';
import {fakeCssSourceProvider} from './w5c_test_doubles.mjs';
import {MODEL} from './w5b_simulated_provider.mjs';
import {requestLocalOllama,buildPrompt} from './w5a_local_ollama.mjs';
import {briefs,proposalFor} from './w5a_test_doubles.mjs';
const batch=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8'));
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const SHA='a'.repeat(64),PR=275,OWNER='teldigi5-wq';
const event=()=>({
 action:'labeled',label:{name:'e6-w5j-three-brief-cpu-approved'},
 pull_request:{number:PR,state:'open',draft:true,user:{login:OWNER},
  base:{ref:'feat/e6-w5i-local-inference-completion-20261102'},
  head:{sha:SHA,repo:{full_name:'teldigi5-wq/aetheris-ai-runtime'}}}
});
const args=()=>({actor:OWNER,repository:'teldigi5-wq/aetheris-ai-runtime',
 expectedPr:PR,headSha:SHA});
test('only the owner-labeled exact same-repo draft PR can authorize model downloads',()=>{
 const x=authorizeLabelEvent(event(),args());
 assert.equal(x.gpuAuthorization,false);
 assert.equal(x.productionAuthorization,false);
 const changes=[
  e=>{e.action='synchronize';},
  e=>{e.label.name='e6-w5d-cpu-smoke-approved';},
  e=>{e.pull_request.number=274;},
  e=>{e.pull_request.state='closed';},
  e=>{e.pull_request.draft=false;},
  e=>{e.pull_request.user.login='untrusted';},
  e=>{e.pull_request.head.repo.full_name='attacker/other';},
  e=>{e.pull_request.base.ref='main';},
  e=>{e.pull_request.head.sha='b'.repeat(64);}
 ];
 for(const edit of changes){
  const v=event();edit(v);
  assert.throws(()=>authorizeLabelEvent(v,args()),/NOT_OWNER_AUTHORIZED_PR_LABEL_EVENT/);
 }
 assert.throws(()=>authorizeLabelEvent(event(),{...args(),actor:'untrusted'}),/NOT_OWNER_AUTHORIZED_PR_LABEL_EVENT/);
 assert.throws(()=>authorizeLabelEvent(event(),{...args(),expectedPr:274}),/NOT_OWNER_AUTHORIZED_PR_LABEL_EVENT/);
});
test('CPU-only fixed local generation request cannot enable GPUs even from model prompts',async()=>{
 const out=[],value=proposalFor(briefs[0]);
 const transport=async(url,init)=>{
  assert.equal(url,'http://127.0.0.1:11434/api/generate');
  const req=JSON.parse(init.body);out.push(req);
  return new Response(JSON.stringify({
   model:MODEL,done:true,done_reason:'stop',eval_count:42,response:JSON.stringify(value)
  }),{status:200});
 };
 const result=await requestLocalOllama(buildPrompt(briefs[0]),MODEL,{transport});
 assert.equal(result.providerStopReasonVerified,true);
 assert.equal(out.length,1);
 assert.equal(out[0].options.num_gpu,0);
 assert.equal(out[0].keep_alive,'0');
 assert.equal(out[0].stream,false);
});
async function report(badIndex=-1){
 const x=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,
  transport:fakeCssSourceProvider({briefs:batch.briefs,badIndex})});
 const v=structuredClone(x.report);
 // This represents ONLY a FAKE test envelope modified to exercise the
 // self-reported case gate. It is not evidence of real provider execution.
 v.providerWasActualLoopbackRequest=true;
 for(const c of v.cases)if(c.accepted){
  c.designStopReasonVerified=true;c.cssStopReasonVerified=true;
  c.designReportedStopReason='stop';c.cssReportedStopReason='stop';
 }
 return v;
}
test('exact three-case synthetic report can be summarized but NEVER attests real weights or release',async()=>{
 const r=summarizeObservedTrial(await report(),batch.briefs,{sourceSha:SHA});
 assert.equal(r.accepted,3);
 assert.equal(r.rejected,0);
 assert.equal(r.candidateStatus,'ALL_THREE_SOURCE_CANDIDATES_FOR_REVIEW');
 assert.equal(r.modelWeightsCryptographicallyAttested,false);
 assert.equal(r.independentModelAuthorshipVerified,false);
 assert.equal(r.stage45Go,false);
 assert.equal(r.releaseGo,false);
 assert.equal(r.cases.length,3);
 for(const c of r.cases)assert.equal(c.sourceIntegrityProvenBySelfReport,false);
});
test('partial or malicious CSS output stays visible as a REJECTION, not success',async()=>{
 const r=summarizeObservedTrial(await report(1),batch.briefs,{sourceSha:SHA});
 assert.equal(r.accepted,2);
 assert.equal(r.rejected,1);
 assert.equal(r.cases[1].accepted,false);
 assert.match(r.cases[1].rejectionCode,/^E6_W5H_/);
 assert.equal(r.candidateStatus,'INCOMPLETE_REJECTED_OR_TRUNCATED');
 assert.equal(r.releaseGo,false);
});
test('fake provider flag without a real localhost request is never accepted as observed trial',async()=>{
 const v=await report();v.providerWasActualLoopbackRequest=false;
 assert.throws(()=>summarizeObservedTrial(v,batch.briefs,{sourceSha:SHA}),/INVALID_TRIAL_EVIDENCE/);
});
test('never upgrade missing stop reasons, invented model authorship or GPU evidence to acceptance',async()=>{
 for(const change of [
  r=>{r.cases[0].designStopReasonVerified=false;},
  r=>{r.cases[0].cssReportedStopReason='length';},
  r=>{r.cases[0].designTokenBudget=9999;},
  r=>{r.cases[1].cssRequestAttempts=3;},
  r=>{r.modelRuntimeAttested=true;},
  r=>{r.modelAuthorshipIndependentlyAttested=true;},
  r=>{r.physicalGpuMeasured=true;},
  r=>{r.ownerApproved=true;},
  r=>{r.stage45Go=true;},
  r=>{r.cases[1].modelAuthoredCssCandidateSha256='fake';}
 ]){
  const v=await report();change(v);
  assert.throws(()=>summarizeObservedTrial(v,batch.briefs,{sourceSha:SHA}),/E6_W5J_/);
 }
});
test('refuse incomplete case sets, reused identities and malformed observed origin',async()=>{
 for(const change of [
  r=>{r.cases.pop();},
  r=>{r.cases[1].projectSlug=r.cases[0].projectSlug;},
  r=>{r.accepted=2;r.rejected=0;},
  r=>{r.runtime.endpoint='https://external';},
  r=>{r.runtime.reportedModelDigest='unknown';},
  r=>{r.cases[0].modelReportedName='different';}
 ]){
  const v=await report();change(v);
  assert.throws(()=>summarizeObservedTrial(v,batch.briefs,{sourceSha:SHA}),/E6_W5J_/);
 }
});
