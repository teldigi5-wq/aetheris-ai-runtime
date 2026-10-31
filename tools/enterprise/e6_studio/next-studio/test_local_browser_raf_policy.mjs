import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {KIND,evaluateLocalBrowserRaf} from './local_browser_raf_policy.mjs';

const run=(ms=16.667)=>({
 frameIntervalsMs:Array(360).fill(ms),
 progressMinimum:0,progressMaximum:1,
 progressStart:0,progressMidpoint:1,progressEnd:0,
 observedScrollMinPx:600,observedScrollMaxPx:2800,scrollRangePx:2200,
 reverseObserved:true,
 longTaskObserverSupported:true,longTaskCount:0,maxLongTaskMs:0
});
const valid=()=>({
 kind:KIND,schemaVersion:2,captureMode:'user-click-visible-loopback-browser',
 localhost:true,capturedAt:new Date().toISOString(),engine:'r3f-loaded',
 webgl2:true,glbSha256:'a'.repeat(64),browserErrorCount:0,contextLossCount:0,
 viewport:{width:1440,height:900,dpr:1},runs:[run(),run(),run()]
});
test('good synthetic rAF intervals screen as diagnostic only, NEVER hardware approval',()=>{
 const s=evaluateLocalBrowserRaf(valid());
 assert.equal(s.diagnosticValid,true);
 assert.equal(s.screenBudgetMet,true);
 assert.equal(s.runs.length,3);
 for(const key of ['physicalHardwareCertified','gpuTimeMeasured','gpuVramMeasured',
  'thermalsMeasured','physicalMobileTested','task7Completed','task8Completed',
  'stage45Go','releaseGo','hardwareIdentityVerified',
  'sourceCheckoutIndependentlyVerified'])assert.equal(s[key],false,key);
 assert.match(s.scope,/NOT_GPU_FRAME_TIME/);
});
test('reject missing and malformed reports with a no-go invariant',()=>{
 for(const input of [null,[],false,3,'test']){
  const s=evaluateLocalBrowserRaf(input);
  assert.equal(s.diagnosticValid,false);
  assert.equal(s.stage45Go,false);
  assert.equal(s.releaseGo,false);
 }
});
test('reject remote capture, missing owner click and failed WebGL engine',()=>{
 for(const [k,v] of [['localhost',false],
  ['captureMode','background-auto-capture'],
  ['engine','webgl-unavailable'],['webgl2',false]]){
  const d=valid();d[k]=v;
  assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false,k);
 }
});
test('reject fewer runs, missing frames, impossible values and missing reverse',()=>{
 let d=valid();d.runs.pop();
 assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false);
 d=valid();d.runs[0].frameIntervalsMs=[16.7];
 assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false);
 d=valid();d.runs[1].frameIntervalsMs[12]=NaN;
 assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false);
 d=valid();d.runs[2].reverseObserved=false;
 assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false);
 d=valid();d.runs[0].progressMaximum=.5;
 assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false);
});
test('reject missing long-task provenance and browser/context errors',()=>{
 for(const [k,v] of [['longTaskObserverSupported',false],
  ['longTaskCount',-1],['maxLongTaskMs',-1]]){
  const d=valid();d.runs[0][k]=v;
  assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false,k);
 }
 for(const key of ['browserErrorCount','contextLossCount']){
  const d=valid();d[key]=1;
  assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false,key);
 }
});
test('high rAF latency never passes preliminary pacing budget',()=>{
 const d=valid();d.runs[1]=run(55);
 const s=evaluateLocalBrowserRaf(d);
 assert.equal(s.diagnosticValid,true);
 assert.equal(s.screenBudgetMet,false);
 assert(s.runs[1].p95Ms>33.3);
 assert.equal(s.stage45Go,false);
});
test('require correctly bounded viewport, digest and fresh capture date',()=>{
 for(const [key,value] of [['glbSha256','bad'],['capturedAt','2020-01-01T00:00:00Z']]){
  const d=valid();d[key]=value;
  assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false,key);
 }
 const d=valid();d.viewport.width=10;
 assert.equal(evaluateLocalBrowserRaf(d).diagnosticValid,false);
});
test('forged approval, audit and hardware flags cannot change outcome',()=>{
 const d=valid();Object.assign(d,{stage45Go:true,releaseGo:true,hardwareCertified:true,
  task7Completed:true,task8Completed:true,sourceApproved:true});
 const s=evaluateLocalBrowserRaf(d);
 assert.equal(s.stage45Go,false);
 assert.equal(s.releaseGo,false);
 assert.equal(s.task7Completed,false);
 assert.equal(s.task8Completed,false);
});
test('browser UI is isolated to opt-in preview and has manual start/cancel/download',async()=>{
 const here=new URL('./',import.meta.url);
 const page=await fs.readFile(new URL('./app/blender-preview/page.tsx',here),'utf8');
 const ui=await fs.readFile(new URL('./components/LocalBrowserRafCapture.tsx',here),'utf8');
 const fallback=page.indexOf('NEXT_PUBLIC_E6_BLENDER_PREVIEW')>=0;
 assert(fallback);
 assert(page.includes('<LocalBrowserRafCapture/>'));
 for(const text of ['loopback()','onClick={run}','Cancel capture','Save JSON on this device',
   'window.requestAnimationFrame','window.scrollTo','crypto.subtle.digest',
   'sourceCommitAttested:false','actualGpuTimeMeasured:false','externalUpload:false',
   'document.visibilityState']){
   assert(ui.includes(text),text);
 }
 assert(!ui.includes('sendBeacon('));
 assert(!ui.includes('XMLHttpRequest'));
 assert(!ui.includes('navigator.geolocation'));
});


test('regression: user real-browser report with stationary progress 1.000 is REJECTED, despite 16.7ms frames',()=>{
 const d=valid();
 for(const sample of d.runs)Object.assign(sample,{
  progressMinimum:1,progressMaximum:1,
  progressStart:1,progressMidpoint:1,progressEnd:1,
  observedScrollMinPx:2900,observedScrollMaxPx:2900,
  reverseObserved:false
 });
 const a=evaluateLocalBrowserRaf(d);
 assert.equal(a.diagnosticValid,false);
 assert.equal(a.screenBudgetMet,false);
 assert.equal(a.reasons.length,3);
 assert(a.reasons.every(x=>x.startsWith('E6_RAF_REVERSE_SCROLL_UNVERIFIED')));
 assert.equal(a.stage45Go,false);
});
test('progress range alone cannot forge start→midpoint→end or actual scrollY motion',()=>{
 for(const [key,value] of [
  ['progressStart',1],['progressMidpoint',0.2],['progressEnd',0.9],
  ['observedScrollMinPx',2700],['observedScrollMaxPx',1500],
  ['scrollRangePx',0]]){
  const d=valid();d.runs[1][key]=value;
  const result=evaluateLocalBrowserRaf(d);
  assert.equal(result.diagnosticValid,false,key);
  assert(result.reasons.includes('E6_RAF_REVERSE_SCROLL_UNVERIFIED:1'),key);
 }
});
test('V1 evidence must not be silently accepted under upgraded V2 schema',()=>{
 const d=valid();d.kind='E6_LOCAL_BROWSER_RAF_DIAGNOSTIC_V1';d.schemaVersion=1;
 const result=evaluateLocalBrowserRaf(d);
 assert.equal(result.diagnosticValid,false);
 assert(result.reasons.includes('E6_RAF_SCHEMA_INVALID'));
});
test('real pinned ScrollTrigger source determines scroll range, no bounding-rect arithmetic',async()=>{
 const here=new URL('./',import.meta.url);
 const ui=await fs.readFile(new URL('./components/LocalBrowserRafCapture.tsx',here),'utf8');
 assert(ui.includes('ScrollTrigger.getAll().find(trigger=>trigger.trigger===story)'));
 assert(ui.includes('const rangeStart=activeTrigger?.start'));
 assert(ui.includes('const rangeEnd=activeTrigger?.end'));
 assert(ui.includes('start+span*fraction'));
 assert(ui.includes('GSAP scroll workload did not travel'));
 assert(!ui.includes('window.scrollY+story.getBoundingClientRect().top'));
});
