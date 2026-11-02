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
  return {full,identity,home:{version:1,projectSlug:brief.projectSlug,page:pages[0]},
    approach:{version:1,projectSlug:brief.projectSlug,page:pages[1]}};
};
function fake(objects,reason='stop'){
 const requests=[];
 return {requests,transport:async(url,opts)=>{
   assert.equal(url,'http://127.0.0.1:11434/api/generate');
   const req=JSON.parse(opts.body);requests.push(req);
   assert.equal(req.options.num_gpu,0);
   assert.equal(req.options.num_predict,3000);
   assert.equal(req.stream,false);assert.equal(req.format,'json');
   if(requests.length>objects.length)throw Error('MORE_THAN_THREE_ATTEMPTS');
   return new Response(JSON.stringify({model:MODEL,done:true,done_reason:reason,
     response:JSON.stringify(objects[requests.length-1]),eval_count:150}),{status:200});
 }};
}
test('staged offline assembly retains exact W5-A contract across three heldout briefs',async()=>{
 for(const [index,brief] of batch.briefs.entries()){
   const {full,identity,home,approach}=parts(brief,index),m=fake([identity,home,approach]);
   const r=await evaluateStagedDesignOffline({brief,model:MODEL,transport:m.transport});
   assert.deepEqual(r.proposal,full);
   assert.equal(validateModelProposal(brief,r.proposal).size,2);
   assert.equal(r.receipt.requestsIssued,3);
   assert.equal(m.requests.length,3);
   assert.equal(r.receipt.realModelAuthorshipAttested,false);
   assert.equal(r.receipt.releaseGo,false);
   for(const [i,phase] of ['identity','home','approach'].entries()){
     const prompt=buildStagedDesignPrompt(brief,phase);
     assert(prompt.length<8000);
     assert(prompt.includes(JSON.stringify(brief)));
     assert.equal(m.requests[i].prompt,prompt);
     assert.match(r.receipt.phases[i].rawReplySha256,/^[a-f0-9]{64}$/);
   }
 }
});
test('staged research denies default live transport or unrecognized phase',async()=>{
 const brief=batch.briefs[0];
 await assert.rejects(evaluateStagedDesignOffline({brief,model:MODEL}),/INJECTED_FAKE_TRANSPORT_REQUIRED/);
 await assert.rejects(evaluateStagedDesignOffline({brief,model:MODEL,transport:fetch}),
   /INJECTED_FAKE_TRANSPORT_REQUIRED/);
 assert.throws(()=>buildStagedDesignPrompt(brief,'deploy'),/UNKNOWN_PHASE/);
});
test('staged assembly refuses extra fields, cross-brief authorship, wrong routes, unsafe content and contrast',()=>{
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
test('abnormal provider completion is rejected on first staged response without later requests',async()=>{
 const brief=batch.briefs[0],{identity,home,approach}=parts(brief,0);
 const m=fake([identity,home,approach],'length');
 await assert.rejects(evaluateStagedDesignOffline({brief,model:MODEL,transport:m.transport}),
   /MODEL_GENERATION_NOT_NORMALLY_FINISHED/);
 assert.equal(m.requests.length,1);
});
