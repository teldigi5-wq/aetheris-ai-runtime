import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {requestLocalOllama,buildPrompt,parseProviderEnvelope} from './w5a_local_ollama.mjs';
import {requestValidatedDesign,requestValidatedCss} from './w5h_bounded_model_repair.mjs';
import {evaluateCodeHeldout} from './w5c_local_code_trial.mjs';
import {briefs,proposalFor} from './w5a_test_doubles.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
import {fakeCssSourceProvider} from './w5c_test_doubles.mjs';
import {MODEL} from './w5b_simulated_provider.mjs';
const batch=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8'));
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const brief=briefs[0],design=proposalFor(brief);
const cssValue={version:1,projectSlug:brief.projectSlug,cssSource:EXAMPLE_CODES[0]};
function fakeResponse(value,{reason='stop',tokens=34,model=MODEL}={}){
 return new Response(JSON.stringify({model,done:true,done_reason:reason,
  eval_count:tokens,response:JSON.stringify(value)}),{status:200});
}
function capture(value,settings={}){
 const calls=[];
 const transport=async(url,opts)=>{
  assert.equal(url,'http://127.0.0.1:11434/api/generate');
  calls.push(JSON.parse(opts.body));
  return fakeResponse(value,settings);
 };
 return {calls,transport};
}
test('real provider request has independently bounded design vs restricted-CSS token budgets',async()=>{
 const m=capture(design);
 const first=await requestLocalOllama(buildPrompt(brief),MODEL,{transport:m.transport,phase:'design'});
 assert.equal(m.calls[0].options.num_predict,3000);
 assert.equal(m.calls[0].options.num_ctx,6144);
 assert.equal(first.providerReportedStopReason,'stop');
 assert.equal(first.providerStopReasonVerified,true);
 assert.equal(first.inferencePhase,'design');
 assert.equal(first.maximumOutputTokens,3000);
 assert.equal(first.providerReportedOutputTokens,34);
 const cssMock=capture(cssValue);
 const second=await requestLocalOllama('Write restricted CSS JSON',MODEL,{transport:cssMock.transport,phase:'css'});
 assert.equal(cssMock.calls[0].options.num_predict,900);
 assert.equal(cssMock.calls[0].options.num_ctx,3072);
 assert.equal(second.inferencePhase,'css');
 assert.equal(second.maximumOutputTokens,900);
 assert.equal(second.providerStopReasonVerified,true);
});
test('reject unrecognized phases before a provider fetch or any model command',async()=>{
 let invoked=0;
 const transport=async()=>{invoked++;throw Error('UNEXPECTED_PROVIDER_IO');};
 for(const phase of ['shell','script','unrestricted-css','',null,{},'deploy']){
  await assert.rejects(requestLocalOllama('safe fixed prompt',MODEL,{transport,phase}),/UNAPPROVED_INFERENCE_PHASE/);
 }
 assert.equal(invoked,0);
});
test('reject done=true with length termination even when model returned valid JSON',async()=>{
 const m=capture(design,{reason:'length'});
 await assert.rejects(requestLocalOllama(buildPrompt(brief),MODEL,{transport:m.transport}),
   /MODEL_GENERATION_NOT_NORMALLY_FINISHED/);
 assert.equal(m.calls.length,1);
});
test('reject unusual stop reasons and malformed output token counts rather than treating as clean completion',()=>{
 const ok={model:MODEL,done:true,response:JSON.stringify(design),done_reason:'stop'};
 for(const reason of ['length','unload','user_abort',''])assert.throws(
  ()=>parseProviderEnvelope({...ok,done_reason:reason},MODEL),
  /MODEL_GENERATION_NOT_NORMALLY_FINISHED/);
 for(const tokens of [0,-1,1.1,'60',3002,Number.NaN])assert.throws(
  ()=>parseProviderEnvelope({...ok,eval_count:tokens},MODEL),
  /INVALID_GENERATION_TOKEN_COUNT/);
 assert.equal(parseProviderEnvelope({...ok,eval_count:3000},MODEL).maximumOutputTokens,3000);
});
test('older Ollama envelope without stop reason is explicitly UNVERIFIED rather than falsely attested',()=>{
 const legacy=parseProviderEnvelope({model:MODEL,done:true,response:JSON.stringify(design)},MODEL);
 assert.equal(legacy.providerStopReasonVerified,false);
 assert.equal(legacy.providerReportedStopReason,null);
 assert.equal(legacy.providerReportedOutputTokens,null);
});
test('truncated design reply cannot trigger validator repair or a second provider call',async()=>{
 let calls=0;
 const transport=async()=>{calls++;return fakeResponse(design,{reason:'length'});};
 await assert.rejects(
  requestValidatedDesign(brief,MODEL,{transport}),
  /MODEL_GENERATION_NOT_NORMALLY_FINISHED/);
 assert.equal(calls,1);
});
test('truncated CSS reply cannot trigger validator repair or a second provider call',async()=>{
 let calls=0;
 const transport=async()=>{calls++;return fakeResponse(cssValue,{reason:'length'});};
 await assert.rejects(
  requestValidatedCss(brief,design,MODEL,{transport}),
  /MODEL_GENERATION_NOT_NORMALLY_FINISHED/);
 assert.equal(calls,1);
});
test('CSS accepted source retains CSS-phase telemetry and budgets and never enables release',async()=>{
 const m=capture(cssValue,{reason:'stop',tokens:88});
 const accepted=await requestValidatedCss(brief,design,MODEL,{transport:m.transport});
 assert.equal(accepted.attempts,1);
 assert.equal(accepted.inferencePhase,'css');
 assert.equal(accepted.maximumOutputTokens,900);
 assert.equal(accepted.providerStopReasonVerified,true);
 assert.equal(accepted.providerReportedOutputTokens,88);
 assert.equal(accepted.firstRejectedValidationCode,null);
});
test('three held-out HUMAN mock cases still run, with unknown stop reason not misreported as proven model execution',async()=>{
 const result=await evaluateCodeHeldout({
  batch,model:MODEL,baseCss:css,
  transport:fakeCssSourceProvider({briefs:batch.briefs})});
 assert.equal(result.report.accepted,3);
 assert.equal(result.report.rejected,0);
 for(const x of result.report.cases){
  assert.equal(x.designTokenBudget,3000);
  assert.equal(x.cssTokenBudget,900);
  assert.equal(x.designStopReasonVerified,false);
  assert.equal(x.cssStopReasonVerified,false);
  assert.equal(x.modelRuntimeAttested,false);
  assert.equal(x.releaseGo,false);
 }
 assert.equal(result.report.providerWasActualLoopbackRequest,false);
 assert.equal(result.report.stage45Go,false);
});
