/**
 * Local-only Chrome DevTools Performance trace inspection.
 * Explicit V1 markers: E6_TRACE_V1_RUN_<1..3>_{START,APEX,END,VERIFIED}.
 * Reads no resource content into output; DOES NOT measure actual GPU render time.
 */
import {gunzipSync} from 'node:zlib';
import {readSingleLocalFile} from './review_local_raf_evidence.mjs';

export const KIND='E6_CHROME_DEVTOOLS_TRACE_SCREEN_V1';
const MAX_DISK_BYTES=64*1024*1024;
const MAX_DECOMPRESSED_BYTES=128*1024*1024;
const MAX_EVENTS=1250000;
const MIN_MARK_SPAN_MS=1000,MAX_MARK_SPAN_MS=45000;
const MARK=/^E6_TRACE_V1_RUN_([1-3])_(START|APEX|END|VERIFIED)$/;
const MAIN_THREAD=/^(CrRendererMain|RendererMain|MainThread)$/;
const TASK_NAMES=new Set(['RunTask','ThreadControllerImpl::RunTask']);
const validNumber=x=>typeof x==='number'&&Number.isFinite(x)&&x>=0;
const blocked=code=>{throw Error(code);};
const object=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const threadKey=e=>String(e.pid)+':'+String(e.tid);
const markName=e=>{
 const candidates=[e.name,e.args?.data?.name,e.args?.name];
 return candidates.find(v=>typeof v==='string'&&MARK.test(v))||null;
};
/**
 * Distinguish a trace *data review* from a device certification.
 * Chrome's trace event format is not a contractual GPU timing API.
 */
export function evaluateChromeTrace(trace){
 if(!Array.isArray(trace)&&!object(trace))blocked('E6_TRACE_WRAPPER_INVALID');
 const events=Array.isArray(trace)?trace:trace.traceEvents;
 if(!Array.isArray(events)||events.length<12||events.length>MAX_EVENTS)
  blocked('E6_TRACE_EVENTS_UNSUPPORTED_OR_OVERSIZE');
 const marks=new Map(),mainThreads=new Set();
 let invalid=0;
 for(const e of events){
  if(!object(e)) {invalid++;continue;}
  if(e.ph==='M'&&e.name==='thread_name'&&MAIN_THREAD.test(e.args?.name||'')&&
     Number.isInteger(e.tid)&&Number.isInteger(e.pid)){
   mainThreads.add(threadKey(e));
  }
  const n=markName(e);
  if(!n)continue;
  // Only explicit browser UserTiming markers from this page are considered.
  if(!String(e.cat||'').includes('blink.user_timing')&&
     !String(e.cat||'').includes('devtools.timeline'))continue;
  if(!validNumber(e.ts))blocked('E6_TRACE_MARK_TIMESTAMP_INVALID');
  if(marks.has(n))blocked('E6_TRACE_DUPLICATE_WORKLOAD_MARK:'+n);
  marks.set(n,e.ts);
 }
 if(invalid>0)blocked('E6_TRACE_MALFORMED_EVENTS');
 const windows=[];
 let last=Number.NEGATIVE_INFINITY;
 for(let i=1;i<=3;i++){
  const times=['START','APEX','END','VERIFIED'].map(phase=>
    marks.get('E6_TRACE_V1_RUN_'+i+'_'+phase));
  if(!times.every(validNumber))blocked('E6_TRACE_MISSING_VERIFIED_WORKLOAD_MARKS:'+i);
  if(times[0]<=last||!(times[0]<times[1]&&times[1]<times[2]&&times[2]<times[3]))
   blocked('E6_TRACE_MARK_SEQUENCE_INVALID:'+i);
  const elapsedMs=(times[2]-times[0])/1000;
  if(elapsedMs<MIN_MARK_SPAN_MS||elapsedMs>MAX_MARK_SPAN_MS)
   blocked('E6_TRACE_WORKLOAD_DURATION_INVALID:'+i);
  windows.push({start:times[0],end:times[3],elapsedMs});
  last=times[3];
 }
 const perRun=[],mainCovered=mainThreads.size>0;
 for(let i=0;i<3;i++){
  const w=windows[i];
  let eligibleTasks=0,longTasks=0,maxTaskMs=0,over200Ms=0;
  for(const e of events){
   if(!object(e)||e.ph!=='X'||!TASK_NAMES.has(e.name)||
      !validNumber(e.ts)||!validNumber(e.dur))continue;
   if(!mainCovered||!mainThreads.has(threadKey(e)))continue;
   if(e.ts<w.start||e.ts>w.end)continue;
   const ms=e.dur/1000;
   eligibleTasks++;
   if(ms>50)longTasks++;
   if(ms>200)over200Ms++;
   maxTaskMs=Math.max(maxTaskMs,ms);
  }
  perRun.push({run:i+1,markedWorkloadMs:Math.round(w.elapsedMs*10)/10,
   measuredMainThreadTaskEvents:eligibleTasks,
   tasksOver50Ms:longTasks,tasksOver200Ms:over200Ms,
   maxObservedMainThreadTaskMs:Math.round(maxTaskMs*10)/10});
 }
 // No detected tasks may indicate missing trace category, not a fast browser.
 const taskCoverage=mainCovered&&perRun.every(v=>v.measuredMainThreadTaskEvents>0);
 return {
  kind:KIND,
  verifiedUserTimingSequence:true,
  workloadRuns:3,markerCount:marks.size,
  taskCoverage:taskCoverage?'MAIN_THREAD_TASK_EVENTS_PRESENT':'INSUFFICIENT_MAIN_THREAD_TASK_EVENTS',
  mainThreadMetadataPresent:mainCovered,
  preliminaryMainThreadReviewPossible:taskCoverage,
  runs:perRun,
  taskThresholdMs:50,
  caveat:'TASK_DURATIONS_MAY_OVERLAP_OR_USE_NESTED_EVENTS; NO_EXCLUSIVE_TOTALS',
  traceScope:'CHROME_DEVTOOLS_USER_TIMINGS_AND_RECORDED_MAIN_THREAD_TASKS',
  actualGpuRenderTimeMeasured:false,
  browserPresentedFpsCertified:false,
  thermalsMeasured:false,vramMeasured:false,mobileDeviceMeasured:false,
  sourceCommitAuthenticated:false,
  independentHumanReviewCompleted:false,
  task7Completed:false,task8Completed:false,stage45Go:false,releaseGo:false
 };
}
export async function reviewLocalChromeTrace(filename){
 const file=await readSingleLocalFile(filename,MAX_DISK_BYTES);
 let bytes=file.buffer;
 if(bytes.length>=2&&bytes[0]===0x1f&&bytes[1]===0x8b){
  try{bytes=gunzipSync(bytes,{maxOutputLength:MAX_DECOMPRESSED_BYTES});}
  catch{blocked('E6_TRACE_GZIP_UNSUPPORTED_OR_OVERSIZE');}
 }
 if(bytes.length>MAX_DECOMPRESSED_BYTES)blocked('E6_TRACE_DECOMPRESSED_OVERSIZE');
 let payload;
 try{payload=JSON.parse(bytes.toString('utf8'));}
 catch{blocked('E6_TRACE_JSON_INVALID');}
 const outcome=evaluateChromeTrace(payload);
 return {
  ...outcome,
  inputFileSha256:file.sha256,
  inputFileSizeBytes:file.buffer.length,
  // NEVER include event args, URLs, screenshots, stacks, raw hostnames or paths.
  privateRawTraceMustNotBeUploaded:true
 };
}
if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href){
 const args=process.argv.slice(2);
 if(args.length!==2||args[0]!=='--trace'){
  console.error('Usage: node review_local_chrome_trace.mjs --trace <local-trace.json[.gz]>');
  process.exitCode=2;
 }else{
  try{process.stdout.write(JSON.stringify(await reviewLocalChromeTrace(args[1]),null,2)+'\n');}
  catch(error){
   console.error('E6_LOCAL_CHROME_TRACE_BLOCKED:',error instanceof Error?error.message:'invalid');
   process.exitCode=1;
  }
 }
}
