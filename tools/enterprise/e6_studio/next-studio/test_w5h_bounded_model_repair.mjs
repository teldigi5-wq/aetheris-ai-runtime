import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {requestValidatedDesign,requestValidatedCss} from './w5h_bounded_model_repair.mjs';
import {buildPrompt} from './w5a_local_ollama.mjs';
import {contrast,validateModelProposal} from './w5a_model_design_compiler.mjs';
import {proposalFor,briefs} from './w5a_test_doubles.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
import {MODEL,syntheticDesign,fakeLocalProvider} from './w5b_simulated_provider.mjs';
import {fakeCssSourceProvider} from './w5c_test_doubles.mjs';
import {evaluateCodeHeldout} from './w5c_local_code_trial.mjs';
const brief=briefs[0],good=proposalFor(brief);
const batch=JSON.parse(await readFile(new URL('w5b_heldout_briefs.json',import.meta.url),'utf8'));
const baseCss=await readFile(new URL('w5a_base.css',import.meta.url),'utf8');
const reply=(obj,model=MODEL)=>new Response(JSON.stringify({
 model,done:true,response:JSON.stringify(obj)
}),{status:200});
function designTransport(candidates){
 const requests=[];
 return {requests,transport:async(url,options)=>{
  assert.equal(url,'http://127.0.0.1:11434/api/generate');
  const req=JSON.parse(options.body);requests.push(req);
  const candidate=candidates[Math.min(requests.length-1,candidates.length-1)];
  return reply(candidate);
 }};
}
test('local design prompt matches the FOUR enforced WCAG contrast combinations',()=>{
 const p=buildPrompt(brief);
 for(const pair of ['ink-on-paper','accent-on-paper','ink-on-surface','accent-on-surface'])
  assert(p.includes(pair),pair);
});
test('first valid design returns with one model call and no extra repair or privilege',async()=>{
 const m=designTransport([good]);
 const r=await requestValidatedDesign(brief,MODEL,{transport:m.transport});
 assert.equal(m.requests.length,1);
 assert.equal(r.attempts,1);
 assert.equal(r.firstRejectedValidationCode,null);
 assert.equal(r.repairAttempted,false);
 assert.match(r.initialPromptSha256,/^[a-f0-9]{64}$/);
 assert.equal(r.initialPromptSha256,r.attemptedPromptSha256);
});
test('one unsafe schema proposal receives exactly one sanitized repair with no replay of model text',async()=>{
 const invalid=structuredClone(good);
 const marker='REJECTED_SECRET_PAYLOAD_DO_NOT_ECHO';
 invalid.untrustedToolInstructions=marker;
 const m=designTransport([invalid,good]);
 const r=await requestValidatedDesign(brief,MODEL,{transport:m.transport});
 assert.equal(m.requests.length,2);
 assert.equal(r.attempts,2);
 assert.equal(r.repairAttempted,true);
 assert.equal(r.firstRejectedValidationCode,'E6_W5A_MODEL_SCHEMA_OR_CONTRAST_REJECTED');
 assert.notEqual(r.attemptedPromptSha256,r.initialPromptSha256);
 assert(!m.requests[1].prompt.includes(marker));
 assert(m.requests[1].prompt.includes(r.firstRejectedValidationCode));
 assert(!m.requests[1].prompt.includes('untrustedToolInstructions'));
 assert.equal(r.retryBudgetExhausted,false);
});
test('two schema failures remain rejected and cannot obtain a third attempt',async()=>{
 const bad=structuredClone(good);bad.art.accent=bad.art.surface;
 const m=designTransport([bad,bad,good]);
 await assert.rejects(requestValidatedDesign(brief,MODEL,{transport:m.transport}),
  /E6_W5H_VALIDATION_REJECTED_AFTER_ONE_SAFE_RETRY/);
 assert.equal(m.requests.length,2);
});
test('malformed provider and HTTP failures are never retried or silently repaired',async()=>{
 let calls=0;
 const transport=async()=>{calls++;return new Response('failed',{status:503});};
 await assert.rejects(requestValidatedDesign(brief,MODEL,{transport}),/E6_W5A_MODEL_UNAVAILABLE_OR_HTTP_ERROR/);
 assert.equal(calls,1);
});
test('CSS source receives one bounded canonical repair after one invalid selector',async()=>{
 const bad='@import url(https://malicious.example/x); '+EXAMPLE_CODES[0];
 const candidate=cssSource=>({version:1,projectSlug:brief.projectSlug,cssSource});
 const m=designTransport([candidate(bad),candidate(EXAMPLE_CODES[0])]);
 const r=await requestValidatedCss(brief,good,MODEL,{transport:m.transport});
 assert.equal(r.attempts,2);
 assert.equal(m.requests.length,2);
 assert.equal(r.firstRejectedValidationCode,'E6_W5C_INVALID_OR_UNSAFE_SOURCE');
 assert(!m.requests[1].prompt.includes('malicious.example'));
 assert(!m.requests[1].prompt.includes(bad));
 assert(r.value.includes('.hero h1'));
});
test('two unsafe CSS responses fail closed even when third would be valid',async()=>{
 const bad={version:1,projectSlug:brief.projectSlug,cssSource:'@import url(https://evil.example/x);'};
 const goodCss={version:1,projectSlug:brief.projectSlug,cssSource:EXAMPLE_CODES[0]};
 const m=designTransport([bad,bad,goodCss]);
 await assert.rejects(requestValidatedCss(brief,good,MODEL,{transport:m.transport}),
  /E6_W5H_VALIDATION_REJECTED_AFTER_ONE_SAFE_RETRY/);
 assert.equal(m.requests.length,2);
});
test('three held-out fake-provider valid model proposals require no repair and remain research-only',async()=>{
 const r=await evaluateCodeHeldout({batch,model:MODEL,baseCss,
  transport:fakeCssSourceProvider({briefs:batch.briefs})});
 assert.equal(r.report.accepted,3);
 assert.equal(r.report.rejected,0);
 assert.equal(r.report.providerWasActualLoopbackRequest,false);
 assert.equal(r.report.modelRuntimeAttested,false);
 for(const c of r.report.cases){
  assert.equal(c.designRequestAttempts,1);
  assert.equal(c.cssRequestAttempts,1);
  assert.equal(c.deterministicRepairBudgetPerPhase,1);
  assert.equal(c.rawRejectedModelOutputSaved,false);
  assert.equal(c.designFirstRejectionCode,null);
  assert.equal(c.cssFirstRejectionCode,null);
  assert.equal(c.releaseGo,false);
 }
});
test('W5-C rejects continuously unsafe injected fake CSS after exactly two bounded CSS requests',async()=>{
 let cssCalls=0;
 const first=fakeCssSourceProvider({briefs:batch.briefs,badIndex:1});
 const transport=async(url,options)=>{
   if(url.endsWith('/api/generate')){
     const req=JSON.parse(options.body);
     if(req.prompt.includes('BRIEF_AND_DESIGN:')&&req.prompt.includes(batch.briefs[1].projectSlug))
       cssCalls++;
   }
   return first(url,options);
 };
 const r=await evaluateCodeHeldout({batch,model:MODEL,baseCss,transport});
 assert.equal(r.report.accepted,2);
 assert.equal(r.report.rejected,1);
 assert.equal(cssCalls,2);
 assert.match(r.report.cases[1].rejectionCode,/^E6_W5H_/);
 assert.equal(r.report.stage45Go,false);
 assert.equal(r.report.releaseGo,false);
});

test('post-trial compact design prompt keeps source ownership, all strict fields and four contrast pairings',()=>{
 const safePalettes=[
   ['#F6F1E8','#E5DDD0','#172821','#693526'],
   ['#0C1E22','#1E3336','#F5F0E8','#FFD59D'],
   ['#FBF8F1','#E8E6E0','#24282E','#51466E']
 ];
 const heldout=batch.briefs;
 assert.equal(heldout.length,3);
 for(const b of heldout){
   const prompt=buildPrompt(b);
   assert(prompt.length<=8_000,'model request budget exceeded');
   assert(prompt.includes(JSON.stringify(b)));
   for(const schemaField of ['projectSlug','siteName','tagline','description','art','pages',
     'paper','ink','accent','surface','composition','motif','density',
     'navLabel','heading','lede','sections','eyebrow','title','body','alignment'])
     assert(prompt.includes(schemaField),'missing field '+schemaField);
   for(const pair of ['ink-on-paper','accent-on-paper','ink-on-surface','accent-on-surface'])
     assert(prompt.includes(pair));
   assert(prompt.includes('EXACTLY TWO distinct sections'));
   assert(prompt.includes('body 65-105 chars'));
   assert(prompt.includes('SINGLE complete JSON'));
   assert(prompt.includes('No markdown or commentary'));
   assert(prompt.includes('NO URLs, HTML, CSS, JavaScript, SVG'));
 }
 for(const [paper,surface,ink,accent] of safePalettes){
   for(const [a,b] of [[paper,ink],[paper,accent],[surface,ink],[surface,accent]])
     assert(contrast(a,b)>=4.5,'example palette violates AA');
   assert(buildPrompt(heldout[0]).includes([paper,surface,ink,accent].join('/')));
 }
});
test('compactness prompt does not alter strict compiled design contract or repair attempts',async()=>{
 const b=batch.briefs[0], goodDesign=syntheticDesign(b,0);
 const fixed=structuredClone(goodDesign);
 fixed.art.accent=fixed.art.surface;
 assert.throws(()=>validateModelProposal(b,fixed),/MODEL_SCHEMA_OR_CONTRAST_REJECTED/);
 assert.equal(validateModelProposal(b,goodDesign).size,2);
 const m=designTransport([fixed,goodDesign]);
 const result=await requestValidatedDesign(b,MODEL,{transport:m.transport});
 assert.equal(result.attempts,2);
 assert.equal(result.maximumOutputTokens,3000);
 assert.equal(m.requests.length,2);
 for(const request of m.requests){
   assert.equal(request.options.num_predict,3000);
   assert.equal(request.options.num_gpu,0);
   assert.equal(request.format,'json');
   assert.equal(request.stream,false);
 }
});
