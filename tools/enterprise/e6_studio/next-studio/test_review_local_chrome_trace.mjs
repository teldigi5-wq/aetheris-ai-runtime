import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,symlink,rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {gzipSync} from 'node:zlib';
import {evaluateChromeTrace,reviewLocalChromeTrace,KIND} from './review_local_chrome_trace.mjs';

const origin=2_000_000;
const marker=(name,ts)=>({
 name:'E6_TRACE_V1_RUN_'+name,
 ph:'R',cat:'blink.user_timing',pid:10,tid:21,ts,
 args:{data:{privateUrl:'http://localhost/private-password-example'}}
});
function fixture({withTasks=true,withThread=true}={}){
 const a=[];
 if(withThread)a.push({name:'thread_name',ph:'M',pid:10,tid:21,args:{name:'CrRendererMain'}});
 for(let i=1;i<=3;i++){
  const t=origin+(i-1)*8_000_000;
  a.push(marker(i+'_START',t),marker(i+'_APEX',t+3_000_000),
   marker(i+'_END',t+6_000_000),marker(i+'_VERIFIED',t+6_001_000));
  if(withTasks){
   a.push({name:'RunTask',ph:'X',pid:10,tid:21,cat:'toplevel',
    ts:t+250_000,dur:16_000,args:{url:'http://private.invalid/auth?token=secret'}});
   a.push({name:'RunTask',ph:'X',pid:10,tid:21,cat:'toplevel',
    ts:t+1_000_000,dur:55_000,args:{privateEmail:'private@example.org'}});
  }
 }
 return {traceEvents:a,metadata:{sensitive:'NEVER OUTPUT'}};
}
async function withTrace(data,fn,{gzip=false}={}){
 const dir=await mkdtemp(join(tmpdir(),'e6-trace-test-'));
 try{
  const p=join(dir,gzip?'trace.json.gz':'trace.json');
  await writeFile(p,gzip?gzipSync(Buffer.from(JSON.stringify(data))):JSON.stringify(data));
  await fn(p,dir);
 }finally{await rm(dir,{force:true,recursive:true});}
}
test('three ordered scroll markers + main thread evidence reviewed, never GPU-certified',()=>{
 const x=evaluateChromeTrace(fixture());
 assert.equal(x.kind,KIND);
 assert.equal(x.verifiedUserTimingSequence,true);
 assert.equal(x.markerCount,12);
 assert.equal(x.workloadRuns,3);
 assert.equal(x.taskCoverage,'MAIN_THREAD_TASK_EVENTS_PRESENT');
 assert.equal(x.runs.length,3);
 assert.equal(x.runs[0].markedWorkloadMs,6000);
 assert.equal(x.runs[0].measuredMainThreadTaskEvents,2);
 assert.equal(x.runs[0].tasksOver50Ms,1);
 assert.equal(x.runs[0].maxObservedMainThreadTaskMs,55);
 for(const key of ['actualGpuRenderTimeMeasured','browserPresentedFpsCertified',
  'thermalsMeasured','vramMeasured','mobileDeviceMeasured','sourceCommitAuthenticated',
  'independentHumanReviewCompleted','task7Completed','task8Completed',
  'stage45Go','releaseGo'])assert.equal(x[key],false,key);
});
test('raw event-array trace format also accepted',()=>{
 assert.equal(evaluateChromeTrace(fixture().traceEvents).verifiedUserTimingSequence,true);
});
test('missing start/apex/end/verified markers are rejected instead of claiming trace coverage',()=>{
 for(const suffix of ['START','APEX','END','VERIFIED']){
  const f=fixture();f.traceEvents=f.traceEvents.filter(x=>x.name!=='E6_TRACE_V1_RUN_2_'+suffix);
  assert.throws(()=>evaluateChromeTrace(f),/MISSING_VERIFIED_WORKLOAD_MARKS/,suffix);
 }
});
test('duplicate marker, invalid timestamp and non-user-timing spoof not accepted',()=>{
 let f=fixture();f.traceEvents.push({...f.traceEvents[1]});
 assert.throws(()=>evaluateChromeTrace(f),/DUPLICATE_WORKLOAD_MARK/);
 f=fixture();f.traceEvents[1].ts=-5;
 assert.throws(()=>evaluateChromeTrace(f),/MARK_TIMESTAMP_INVALID/);
 f=fixture();f.traceEvents[1].cat='network';
 assert.throws(()=>evaluateChromeTrace(f),/MISSING_VERIFIED_WORKLOAD_MARKS/);
});
test('incorrect order, replayed runs and implausible duration rejected',()=>{
 let f=fixture();const end=f.traceEvents.find(e=>e.name==='E6_TRACE_V1_RUN_1_END');
 const apex=f.traceEvents.find(e=>e.name==='E6_TRACE_V1_RUN_1_APEX');
 end.ts=apex.ts-1;
 assert.throws(()=>evaluateChromeTrace(f),/MARK_SEQUENCE_INVALID/);
 f=fixture();
 f.traceEvents.find(e=>e.name==='E6_TRACE_V1_RUN_1_END').ts=origin+50_000;
 assert.throws(()=>evaluateChromeTrace(f),/WORKLOAD_DURATION_INVALID|MARK_SEQUENCE_INVALID/);
 f=fixture();
 f.traceEvents.find(e=>e.name==='E6_TRACE_V1_RUN_2_START').ts=origin+500_000;
 assert.throws(()=>evaluateChromeTrace(f),/MARK_SEQUENCE_INVALID/);
});
test('absent main thread metadata or task track means INCONCLUSIVE, not a performance pass',()=>{
 let x=evaluateChromeTrace(fixture({withThread:false}));
 assert.equal(x.taskCoverage,'INSUFFICIENT_MAIN_THREAD_TASK_EVENTS');
 assert.equal(x.preliminaryMainThreadReviewPossible,false);
 x=evaluateChromeTrace(fixture({withTasks:false}));
 assert.equal(x.preliminaryMainThreadReviewPossible,false);
});
test('other threads and nested category events are not mislabeled as main thread RunTask',()=>{
 const f=fixture();
 f.traceEvents.push({name:'RunTask',ph:'X',ts:2_500_000,dur:800_000,pid:10,tid:22,cat:'toplevel'});
 f.traceEvents.push({name:'FunctionCall',ph:'X',ts:2_500_000,dur:800_000,pid:10,tid:21,cat:'devtools.timeline'});
 const x=evaluateChromeTrace(f);
 assert.equal(x.runs[0].maxObservedMainThreadTaskMs,55);
 assert.equal(x.runs[0].measuredMainThreadTaskEvents,2);
});
test('bounded exported trace JSON and gzip processed locally, output strips sensitive strings',()=>withTrace(fixture(),async p=>{
 const x=await reviewLocalChromeTrace(p);
 assert.equal(x.verifiedUserTimingSequence,true);
 assert.match(x.inputFileSha256,/^[0-9a-f]{64}$/);
 assert.equal(x.privateRawTraceMustNotBeUploaded,true);
 const output=JSON.stringify(x);
 for(const secret of ['private.example','private@example.org','secret','localhost','password'])
  assert(!output.includes(secret),secret);
},{gzip:true}));
test('broken JSON is rejected before displaying any successful result',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'e6-broken-trace-'));
 try{
  const p=join(dir,'bad.json');
  await writeFile(p,'{"traceEvents":[');
  await assert.rejects(reviewLocalChromeTrace(p),/TRACE_JSON_INVALID/);
 }finally{await rm(dir,{force:true,recursive:true});}
});
test('non-regular or symlink trace files blocked',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'e6-trace-symlink-'));
 try{
  const p=join(dir,'trace.json');
  const sym=join(dir,'link.json');
  await writeFile(p,JSON.stringify(fixture()));
  await symlink(p,sym);
  await assert.rejects(reviewLocalChromeTrace(sym),/TYPE_OR_SIZE_INVALID/);
 }finally{await rm(dir,{force:true,recursive:true});}
});
test('reject short, malformed and excessive trace event sequences',()=>{
 for(const x of [null,{},false,[],{traceEvents:[1,2,3]}])
  assert.throws(()=>evaluateChromeTrace(x),/WRAPPER_INVALID|EVENTS_UNSUPPORTED_OR_OVERSIZE/);
 const f=fixture();
 f.traceEvents.push(13);
 assert.throws(()=>evaluateChromeTrace(f),/MALFORMED_EVENTS/);
});
