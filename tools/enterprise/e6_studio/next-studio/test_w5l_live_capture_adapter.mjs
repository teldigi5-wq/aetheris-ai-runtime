import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {collectBoundedPairedDesign} from './w5l_live_capture_adapter.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
const briefs=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')).briefs;
const SHA='a'.repeat(40);
const response=obj=>new Response(JSON.stringify({model:'qwen2.5:0.5b',
 done:true,done_reason:'stop',response:JSON.stringify(obj),eval_count:150}),{status:200});
function stub({invalidFirst=false,abnormalFirst=false}={}){
 const requests=[];
 return {requests,transport:async(url,opts)=>{
  assert.equal(url,'http://127.0.0.1:11434/api/generate');
  assert.equal(opts.method,'POST');
  const params=JSON.parse(opts.body);
  assert.equal(params.model,'qwen2.5:0.5b');
  assert.equal(params.options.num_gpu,0);
  assert.equal(params.stream,false);
  assert.equal(params.format,'json');
  requests.push(params);
  const index=briefs.findIndex(b=>params.prompt.includes(JSON.stringify(b)));
  assert(index>=0);
  const full=syntheticDesign(briefs[index],index);
  const {pages,...identity}=full;
  let item;
  if(params.prompt.includes('Return ONLY brand identity:'))
    item=identity;
  else if(params.prompt.includes('page.path="/approach"'))
    item={version:1,projectSlug:briefs[index].projectSlug,page:pages[1]};
  else if(params.prompt.includes('page.path="/"'))
    item={version:1,projectSlug:briefs[index].projectSlug,page:pages[0]};
  else item=full;
  if(invalidFirst&&requests.length===1){
    item=structuredClone(full);
    item.art.accent=item.art.surface;
  }
  if(abnormalFirst&&requests.length===1){
    return new Response(JSON.stringify({model:'qwen2.5:0.5b',
      done:true,done_reason:'length',response:JSON.stringify(item),
      eval_count:150}),{status:200});
  }
  return response(item);
 }};
}
const run=transport=>collectBoundedPairedDesign({
 briefs,sourceCommitSha:SHA,transport,beforeRequest:async()=>true
});
test('injected fake only: 3 pairs and 12 bounded calls, sanitized output only',async()=>{
 const fake=stub(),s=await run(fake.transport);
 assert.equal(fake.requests.length,12);
 assert.deepEqual(s.comparison.methods.map(x=>x.accepted),[3,3]);
 assert.equal(s.capturedEnvelopesExamined,12);
 assert.equal(s.receipt.observations[1].reportedDesignCalls,3);
 assert.equal(s.comparison.allSixCellsReported,true);
 assert.equal(s.modelRequestsIndependentlyVerified,false);
 assert.equal(s.realProviderProvenanceEstablished,false);
 assert.equal(s.ownerExperimentApproved,false);
 assert.equal(s.releaseGo,false);
 assert(!JSON.stringify(s).includes('The warmth of an imagined'));
 assert(!JSON.stringify(s).includes('art'));
});
test('compact deterministic validation failure triggers one sanitized retry',async()=>{
 const fake=stub({invalidFirst:true});
 const s=await run(fake.transport);
 assert.equal(fake.requests.length,13);
 assert.equal(s.receipt.observations[0].outcome,'ACCEPTED');
 assert.equal(s.receipt.observations[0].reportedDesignCalls,2);
 assert(fake.requests[1].prompt.includes('E6_W5A_MODEL_SCHEMA_OR_CONTRAST_REJECTED'));
 assert(!fake.requests[1].prompt.includes('"response"'));
});
test('provider abnormal completion ends first cell with no unauthorized retry',async()=>{
 const fake=stub({abnormalFirst:true});
 const s=await run(fake.transport);
 assert.equal(fake.requests.length,12);
 assert.equal(s.receipt.observations[0].outcome,'REJECTED');
 assert.equal(s.receipt.observations[0].failureCode,'DESIGN_ABNORMAL_FINISH');
 assert.equal(s.receipt.observations[0].reportedDesignCalls,1);
 assert.equal(s.modelRuntimeAttested,false);
});
test('no explicit injected transport, invalid clock or fixed job cap fail closed',async()=>{
 await assert.rejects(collectBoundedPairedDesign({
   briefs,sourceCommitSha:SHA
 }),/EXPLICIT_TRANSPORT_APPROVAL_AND_CLOCK_REQUIRED/);
 const fake=stub();
 await assert.rejects(collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,transport:fake.transport,beforeRequest:async()=>true,clock:()=>NaN
 }),/NONMONOTONIC_CAPTURE_CLOCK/);
 assert.equal(fake.requests.length,0);
 let ticks=0;const bounded=stub();
 await assert.rejects(collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,transport:bounded.transport,beforeRequest:async()=>true,
  clock:()=>{ticks+=1;return ticks>3?1_260_100:ticks;}
 }),/TOTAL_RESEARCH_TIME_EXCEEDED/);
 assert(bounded.requests.length<=1);
});
test('bad provider shape, HTTP failures and untrusted byte sizes abort, not fabricate',async()=>{
 const bad=(body,status=200)=>async()=>new Response(body,{status});
 const ctx=fn=>collectBoundedPairedDesign({
   briefs,sourceCommitSha:SHA,transport:fn,beforeRequest:async()=>true
 });
 await assert.rejects(ctx(bad('server error',503)),/E6_W5A_/);
 // The canonical W5-A transport boundary deliberately masks exceptions
 // thrown while collecting provider bytes, rather than exposing raw errors.
 await assert.rejects(ctx(bad('not JSON')),/E6_W5A_PROVIDER_TRANSPORT_FAILURE/);
 await assert.rejects(ctx(bad('x'.repeat(49_000))),/E6_W5A_PROVIDER_TRANSPORT_FAILURE/);
 await assert.rejects(ctx(bad(JSON.stringify({model:'qwen2.5:0.5b',
   done:true,done_reason:'stop',eval_count:150,response:'bad'}))),
   /E6_W5A_UNVERIFIED_PROVIDER_ENVELOPE/);
});
test('adapter has no implicit execution, network imports, logs or filesystem writes',async()=>{
 const src=await readFile(new URL('./w5l_live_capture_adapter.mjs',import.meta.url),'utf8');
 for(const forbidden of ['fetch(', 'writeFile(', 'createWriteStream',
   'process.env','child_process','workflow_dispatch','console.log',
   'fs/promises','https://'])
    assert(!src.includes(forbidden),forbidden);
 assert(src.includes('modelRequestsIndependentlyVerified:false'));
 assert(src.includes('ownerExperimentApproved:false'));
});

test('approval revoked after first response prevents next model request and repair',async()=>{
 const f=stub();let checks=0;
 await assert.rejects(collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,transport:f.transport,
  beforeRequest:async()=>++checks===1
 }),/MODEL_REQUEST_APPROVAL_REVOKED/);
 assert.equal(checks,2);assert.equal(f.requests.length,1);
 const retry=stub({invalidFirst:true});let trials=0;
 await assert.rejects(collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,transport:retry.transport,
  beforeRequest:async()=>++trials===1
 }),/MODEL_REQUEST_APPROVAL_REVOKED/);
 assert.equal(trials,2);assert.equal(retry.requests.length,1);
});
test('no approval callback denies before any model request',async()=>{
 const fake=stub();
 await assert.rejects(collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,transport:fake.transport
 }),/EXPLICIT_TRANSPORT_APPROVAL_AND_CLOCK_REQUIRED/);
 assert.equal(fake.requests.length,0);
});

test('approval network latency cannot bias per-method response elapsed time',async()=>{
 const fake=stub();
 let elapsed=0;
 const output=await collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,clock:()=>elapsed,
  beforeRequest:async()=>{elapsed+=700;return true;},
  transport:async(url,opts)=>{
   elapsed+=120;
   return fake.transport(url,opts);
  }
 });
 assert.equal(fake.requests.length,12);
 assert.deepEqual(output.comparison.methods.map(m=>m.reportedElapsedMs),[360,1080]);
 assert.equal(output.comparison.cumulativeReportedElapsedMs,1440);
 assert.equal(elapsed,9840);
 let launched=0,clock=0;
 const bounded=stub();
 await assert.rejects(collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,clock:()=>clock,
  beforeRequest:async()=>{clock+=1_260_000;return true;},
  transport:async(url,opts)=>{launched++;return bounded.transport(url,opts);}
 }),/TOTAL_RESEARCH_TIME_EXCEEDED/);
 assert.equal(launched,0);
});
test('owner study hard-stops at 21m before the 55m workflow timeout',async()=>{
 const source=await readFile(new URL('./w5l_live_capture_adapter.mjs',import.meta.url),'utf8');
 assert(source.includes('const LIMIT_MS=21*60*1000'));
 const fake=stub();
 let millis=0;
 await assert.rejects(collectBoundedPairedDesign({
  briefs,sourceCommitSha:SHA,clock:()=>millis,
  beforeRequest:async()=>{millis=21*60*1000;return true;},
  transport:fake.transport
 }),/TOTAL_RESEARCH_TIME_EXCEEDED/);
 assert.equal(fake.requests.length,0);
});
test('owner workflow bounds archive retries and leaves a timeout cushion for evidence',async()=>{
 const wf=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5l-optin-paired-cpu.yml',import.meta.url),'utf8');
 assert(wf.includes('timeout-minutes: 55'));
 assert(wf.includes('timeout --signal=TERM --kill-after=10s 510s curl --fail --location --retry 2'));
 assert(wf.includes('timeout --signal=TERM --kill-after=10s 600s'));
 const trial=wf.slice(wf.indexOf('      - name: Attempt exactly three held-out compact-versus-staged pairs on CPU'));
 assert(trial.includes('timeout-minutes: 29'));
 assert(trial.indexOf('timeout-minutes: 29')<trial.indexOf('node w5l_owner_cpu_cli.mjs run'));
});
