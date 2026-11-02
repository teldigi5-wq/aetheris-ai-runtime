import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {buildStagedDesignPrompt,assembleStagedDesign,evaluateStagedDesignOffline} from './w5k_staged_design_research.mjs';
import {validateModelProposal} from './w5a_model_design_compiler.mjs';
import {MODEL,syntheticDesign} from './w5b_simulated_provider.mjs';
const batch=JSON.parse(await readFile(new URL('w5b_heldout_briefs.json',import.meta.url),'utf8'));
const parts=(brief,index)=>{
 const full=syntheticDesign(brief,index);
 const {pages,...identity}=full;
 return {full,identity,
   home:{version:1,projectSlug:brief.projectSlug,page:pages[0]},
   approach:{version:1,projectSlug:brief.projectSlug,page:pages[1]}};
};
function fixtures(identity,home,approach){
 return [identity,home,approach].map(value=>({
  model:MODEL,done:true,done_reason:'stop',response:JSON.stringify(value),eval_count:150
 }));
}
const evaluate=(brief,envelopes)=>evaluateStagedDesignOffline({brief,model:MODEL,envelopes});
test('pure offline W5-K assembly retains all final W5-A rules on three synthetic briefs',()=>{
 for(const [i,brief] of batch.briefs.entries()){
  const {full,identity,home,approach}=parts(brief,i);
  const response=evaluate(brief,fixtures(identity,home,approach));
  assert.deepEqual(response.proposal,full);
  assert.equal(validateModelProposal(brief,response.proposal).size,2);
  assert.equal(response.receipt.fixturesExamined,3);
  assert.equal(response.receipt.modelRequestsMade,0);
  assert.equal(response.receipt.networkTransportSupported,false);
  assert.equal(response.receipt.realModelAuthorshipAttested,false);
  assert.equal(response.receipt.releaseGo,false);
  for(const [idx,phase] of ['identity','home','approach'].entries()){
   const prompt=buildStagedDesignPrompt(brief,phase);
   assert(prompt.length<8000);
   assert(prompt.includes(JSON.stringify(brief)));
   assert.equal(response.receipt.phases[idx].phase,phase);
   assert.match(response.receipt.phases[idx].rawReplySha256,/^[a-f0-9]{64}$/);
   assert.match(response.receipt.phases[idx].promptSha256,/^[a-f0-9]{64}$/);
  }
 }
});
test('offline evaluator cannot receive injected fetch, wrapped network transport or extra authority',()=>{
 const brief=batch.briefs[0],{identity,home,approach}=parts(brief,0);
 const envelopes=fixtures(identity,home,approach);
 for(const opts of [
   {brief,model:MODEL,transport:fetch},
   {brief,model:MODEL,transport:async(...args)=>fetch(...args)},
   {brief,model:MODEL,envelopes,transport:async(...args)=>fetch(...args)},
   {brief,model:MODEL,envelopes,ownerApproved:true},
   {brief,model:MODEL}
 ])assert.throws(()=>evaluateStagedDesignOffline(opts),/PURE_OFFLINE_CONTEXT_REQUIRED/);
 assert.throws(()=>buildStagedDesignPrompt(brief,'deploy'),/UNKNOWN_PHASE/);
});
test('pure staged assembly denies extra keys, cross-brief identity, wrong routes, scripts and contrast',()=>{
 const brief=batch.briefs[0],{identity,home,approach}=parts(brief,0);
 const reject=(a,b,c)=>assert.throws(()=>assembleStagedDesign(brief,a,b,c),/E6_W5[AK]_/);
 assert.equal(assembleStagedDesign(brief,identity,home,approach).pages.length,2);
 reject({...identity,ownerOverride:true},home,approach);
 reject({...identity,projectSlug:'another-brief'},home,approach);
 reject(identity,{...home,projectSlug:'other'},approach);
 reject(identity,{...home,page:{...home.page,path:'/approach'}},approach);
 reject(identity,home,{...approach,page:{...approach.page,sections:[]}});
 reject(identity,home,{...approach,page:{...approach.page,owner:true}});
 const unsafe=structuredClone(identity);unsafe.art.accent=unsafe.art.surface;
 reject(unsafe,home,approach);
 const script=structuredClone(home);script.page.sections[0].body='<script>alert(1)</script>';
 reject(identity,script,approach);
});
test('non-stop, missing, malformed, oversized and wrong-model fixture envelopes fail closed',()=>{
 const brief=batch.briefs[0],{identity,home,approach}=parts(brief,0);
 const original=fixtures(identity,home,approach);
 const reject=(mutator,pattern)=>{
  const bad=structuredClone(original);mutator(bad);
  assert.throws(()=>evaluate(brief,bad),pattern);
 };
 reject(v=>{v[0].done_reason='length';},/INVALID_SYNTHETIC_ENVELOPE/);
 reject(v=>{delete v[0].done_reason;},/INVALID_SYNTHETIC_ENVELOPE/);
 reject(v=>{v[1].model='wrong';},/INVALID_SYNTHETIC_ENVELOPE/);
 reject(v=>{v[1].extra='owner';},/INVALID_SYNTHETIC_ENVELOPE/);
 reject(v=>{v[1].eval_count='100';},/INVALID_SYNTHETIC_ENVELOPE/);
 reject(v=>{v[1].eval_count=3002;},/INVALID_SYNTHETIC_ENVELOPE/);
 reject(v=>{v[1].response='{invalid JSON';},/INVALID_PROVIDER_JSON|MODEL_JSON_PARSE_REJECTED|UNVERIFIED_PROVIDER_ENVELOPE/);
 reject(v=>{v[1].response='x'.repeat(36001);},/INVALID_SYNTHETIC_ENVELOPE/);
 reject(v=>{v[2].response=JSON.stringify({...approach,page:{...approach.page,path:'/'}});},/INVALID_PAGE/);
 assert.throws(()=>evaluate(brief,original.slice(0,2)),/EXACT_THREE_FIXTURES_REQUIRED/);
 const annotated=structuredClone(original);annotated.ownerOverride=true;
 assert.throws(()=>evaluate(brief,annotated),/EXACT_THREE_FIXTURES_REQUIRED/);
});
test('offline research source excludes requestLocalOllama, fetch calls and side-effect modules',async()=>{
 const source=await readFile(new URL('./w5k_staged_design_research.mjs',import.meta.url),'utf8');
 assert(!source.includes('requestLocalOllama'));
 assert(!source.includes('transport('));
 assert(!source.includes('fetch('));
 assert(!source.includes("from 'node:fs"));
 assert(!source.includes('process.env'));
 assert(!source.includes("createWriteStream"));
 assert(source.includes("parseProviderEnvelope(fixture,model"));
 assert(source.includes("modelRequestsMade:0"));
});
