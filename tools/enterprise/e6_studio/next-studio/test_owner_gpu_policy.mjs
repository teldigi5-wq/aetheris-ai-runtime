import test from 'node:test';
import assert from 'node:assert/strict';
import {KIND,evaluateHardwareEvidence,percentile} from './owner_gpu_policy.mjs';
const sha='a'.repeat(64),commit='b'.repeat(40);
const sample=(ms=16.667)=>({
 frameIntervalsMs:Array.from({length:360},()=>ms),
 progress:{minimum:0,maximum:1},reverseObserved:true,
 longTaskCount:0,maxLongTaskMs:0
});
const base=()=>({
 schemaVersion:1,kind:KIND,captureMode:'owner-local-headed-chrome',
 headless:false,ci:false,emulated:false,webgl2:true,
 renderer:{unmaskedVendor:'NVIDIA Corporation',
           unmaskedRenderer:'ANGLE (NVIDIA GeForce RTX 4050, D3D11)'},
 browserVersion:'Chrome/156.0.7777.99',viewport:{width:1440,height:900,dpr:1},
 runs:[sample(),sample(),sample()],
 exactGitCommit:commit,glbSha256:sha,capturedAt:new Date().toISOString(),
 externalRequests:0,browserErrors:0,webglLosses:0
});
test('valid local renderer, sampled scroll and provenance permit RESEARCH ONLY',()=>{
 const x=evaluateHardwareEvidence(base());
 assert.equal(x.acceptedForResearch,true);
 assert.equal(x.reasons.length,0);
 assert.equal(x.runs.length,3);
 assert.equal(x.runs[0].frames,360);
 assert.equal(x.runs[0].p95Ms,16.667);
 assert.equal(x.releaseGo,false);
 assert.equal(x.hardwareCertified,false);
 assert.equal(x.deviceMatrixComplete,false);
 assert.equal(x.stage45Go,false);
 assert(x.unsupportedEvidence.includes('GPU_THERMALS'));
});
test('percentile uses ordered nearest-rank, never interpolated optimism',()=>{
 assert.equal(percentile([100,3,2,1,4],0.95),100);
 assert.equal(percentile([2,4,1,3],0.5),2);
 assert.throws(()=>percentile([],0.95),/EMPTY_INTERVALS/);
});
test('reject software renderer variants, even with perfect frame intervals',()=>{
 for(const text of ['Google SwiftShader','Mesa llvmpipe LLVM 19','Microsoft WARP',
  'ANGLE (Vulkan lavapipe)','Software Rasterizer','VMware Virtual SVGA']){
  const p=base();p.renderer.unmaskedRenderer=text;
  const result=evaluateHardwareEvidence(p);
  assert.equal(result.acceptedForResearch,false,text);
  assert(result.reasons.includes('E6_GPU_HARDWARE_RENDERER_UNVERIFIED'));
  assert.equal(result.releaseGo,false);
 }
});
test('reject headless, CI and emulated captures',()=>{
 for(const flag of ['headless','ci','emulated']){
  const p=base();p[flag]=true;
  assert.equal(evaluateHardwareEvidence(p).acceptedForResearch,false,flag);
 }
 const p=base();p.captureMode='cloud-browser';
 assert.equal(evaluateHardwareEvidence(p).acceptedForResearch,false);
});
test('reject missing runs, insufficient samples and forged reverse-only workload',()=>{
 const p=base();p.runs.pop();assert.equal(evaluateHardwareEvidence(p).acceptedForResearch,false);
 p.runs.push(sample());p.runs[0].frameIntervalsMs=[16.6];
 assert(evaluateHardwareEvidence(p).reasons.some(x=>x.startsWith('E6_FRAME_SAMPLE_INVALID')));
 p.runs[0]=sample();p.runs[1].reverseObserved=false;
 assert(evaluateHardwareEvidence(p).reasons.some(x=>x.startsWith('E6_SCROLL_WORKLOAD_UNVERIFIED')));
});
test('reject over-budget p95, p99, dropped intervals and long tasks',()=>{
 const p=base();
 p.runs[0].frameIntervalsMs=Array(360).fill(45);
 assert(evaluateHardwareEvidence(p).reasons.some(x=>x.startsWith('E6_FRAME_PACING_BUDGET_NOT_MET')));
 p.runs[0]=sample();
 p.runs[1].maxLongTaskMs=350;
 assert(evaluateHardwareEvidence(p).reasons.some(x=>x.startsWith('E6_LONGTASK_OVER_BUDGET')));
});
test('reject unproven source, non-Chrome version, network, browser errors',()=>{
 for(const [key,value] of [['exactGitCommit','release'],
  ['glbSha256','bad'],['browserVersion','Chromium/100'],['externalRequests',1],
  ['browserErrors',1],['webglLosses',1],['webgl2',false]]){
  const p=base();p[key]=value;
  assert.equal(evaluateHardwareEvidence(p).acceptedForResearch,false,key);
 }
});
test('reject malformed viewport and impossible frames',()=>{
 const p=base();
 p.viewport.width=200;assert.equal(evaluateHardwareEvidence(p).acceptedForResearch,false);
 p.viewport.width=1440;p.runs[0].frameIntervalsMs[0]=NaN;
 assert.equal(evaluateHardwareEvidence(p).acceptedForResearch,false);
});
test('no inputs, including forged release flags, can flip Stage45 GO',()=>{
 const p=base();Object.assign(p,{releaseGo:true,stage45Go:true,
  ownerApproved:true,physicalGpuMeasured:true,thermalsMeasured:true});
 const x=evaluateHardwareEvidence(p);
 assert.equal(x.releaseGo,false);
 assert.equal(x.stage45Go,false);
 assert.equal(x.ownerAccepted,false);
 assert.equal(x.hardwareCertified,false);
});
