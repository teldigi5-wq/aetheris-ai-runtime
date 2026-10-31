import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,symlink,link,rm,readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {KIND,evaluateLocalBrowserRaf} from './local_browser_raf_policy.mjs';
import {reviewLocalRafEvidence,readSingleLocalFile} from './review_local_raf_evidence.mjs';
const sha=x=>createHash('sha256').update(x).digest('hex');
const makeRun=()=>({frameIntervalsMs:Array(360).fill(16.7),progressMinimum:0,
 progressMaximum:1,progressStart:0,progressMidpoint:.989,progressEnd:0,
 observedScrollMinPx:848,observedScrollMaxPx:2454.4,scrollRangePx:1606,
 reverseObserved:true,longTaskObserverSupported:true,longTaskCount:0,maxLongTaskMs:0});
function fixture(digest,offsetMs=0){
 const report={kind:KIND,schemaVersion:2,captureMode:'user-click-visible-loopback-browser',
  localhost:true,capturedAt:new Date(Date.now()+offsetMs).toISOString(),
  engine:'r3f-loaded',webgl2:true,
  renderer:{unmaskedRenderer:'ANGLE (Intel UHD Graphics, D3D11)',unmaskedVendor:'Google Inc. (Intel)'},
  glbSha256:digest,viewport:{width:1536,height:730,dpr:1.25},
  browserErrorCount:0,contextLossCount:0,runs:[makeRun(),makeRun(),makeRun()],
  metadata:{browser:'Chrome/156.0.0.0',sourceCommitAttested:false,
   actualGpuTimeMeasured:false,externalUpload:false}};
 return {report,assessment:evaluateLocalBrowserRaf(report)};
}
async function setup(){
 const dir=await mkdtemp(join(tmpdir(),'e6-offline-review-'));
 const assetPath=join(dir,'module.glb');
 const asset=Buffer.alloc(83140,83);await writeFile(assetPath,asset);
 const digest=sha(asset);
 const reportPaths=[join(dir,'1.json'),join(dir,'2.json')];
 const samples=[fixture(digest,-30000),fixture(digest,-10000)];
 await Promise.all(reportPaths.map((p,i)=>writeFile(p,JSON.stringify(samples[i]))));
 return {dir,assetPath,reportPaths,samples,digest,
  close:()=>rm(dir,{recursive:true,force:true})};
}
async function withCase(fn){const f=await setup();try{await fn(f);}finally{await f.close();}}
test('two complete reports accepted for local screening with hard release gates',()=>withCase(async f=>{
 const x=await reviewLocalRafEvidence(f);
 assert.equal(x.diagnosticReviewValid,true);
 assert.equal(x.preliminaryRafScreenMet,true);
 assert.equal(x.totalFrames,2160);
 assert.equal(x.captureCount,2);
 assert.equal(x.runCount,6);
 assert.equal(x.metrics.over33_3Count,0);
 assert.equal(x.asset.sha256,f.digest);
 for(const key of ['physicalGpuCertified','realMobileCertified','deviceMatrixComplete',
  'task7Completed','task8Completed','stage45Go','releaseGo'])assert.equal(x[key],false,key);
 assert(!JSON.stringify(x).includes('ANGLE (Intel UHD'));
}));
test('too few or too many local samples rejected',()=>withCase(async f=>{
 await assert.rejects(reviewLocalRafEvidence({...f,reportPaths:f.reportPaths.slice(0,1)}),/2_TO_8/);
 await assert.rejects(reviewLocalRafEvidence({...f,reportPaths:Array(9).fill(f.reportPaths[0])}),/2_TO_8/);
}));
test('duplicate identical file SHA and timestamp rejected',()=>withCase(async f=>{
 await assert.rejects(reviewLocalRafEvidence({...f,reportPaths:[f.reportPaths[0],f.reportPaths[0]]}),/DUPLICATE_FILE_CONTENT/);
 const second=fixture(f.digest,-30000);
 second.report.capturedAt=f.samples[0].report.capturedAt;
 second.report.metadata.testMarker='synthetic-different-bytes';
 await writeFile(f.reportPaths[1],JSON.stringify(second));
 await assert.rejects(reviewLocalRafEvidence(f),/DUPLICATE_CAPTURE_TIME/);
}));
test('V1 stationary evidence is rejected even with stable 16.7ms samples',()=>withCase(async f=>{
 f.samples[0].report.kind='E6_LOCAL_BROWSER_RAF_DIAGNOSTIC_V1';
 f.samples[0].report.schemaVersion=1;
 await writeFile(f.reportPaths[0],JSON.stringify(f.samples[0]));
 await assert.rejects(reviewLocalRafEvidence(f),/V2_REQUIRED/);
}));
test('different physical GLB digest fails even if report self-asserts old SHA',()=>withCase(async f=>{
 await writeFile(f.assetPath,Buffer.alloc(83140,82));
 await assert.rejects(reviewLocalRafEvidence(f),/GLB_DIGEST_MISMATCH/);
}));
test('forged embedded assessment cannot claim approval',()=>withCase(async f=>{
 f.samples[0].assessment.stage45Go=true;
 await writeFile(f.reportPaths[0],JSON.stringify(f.samples[0]));
 await assert.rejects(reviewLocalRafEvidence(f),/EMBEDDED_ASSESSMENT_MISMATCH/);
}));
test('different viewport and renderers cannot be combined in one cohort',()=>withCase(async f=>{
 f.samples[1].report.viewport.width=960;
 f.samples[1].assessment=evaluateLocalBrowserRaf(f.samples[1].report);
 await writeFile(f.reportPaths[1],JSON.stringify(f.samples[1]));
 await assert.rejects(reviewLocalRafEvidence(f),/MIXED_DEVICE_OR_VIEWPORT/);
}));
test('software renderer rejected rather than reported as physical device',()=>withCase(async f=>{
 f.samples[1].report.renderer.unmaskedRenderer='Google SwiftShader';
 f.samples[1].assessment=evaluateLocalBrowserRaf(f.samples[1].report);
 await writeFile(f.reportPaths[1],JSON.stringify(f.samples[1]));
 await assert.rejects(reviewLocalRafEvidence(f),/SOFTWARE_RENDERER_REJECTED/);
}));
test('incomplete scroll or a late frame fails the local screening gate',()=>withCase(async f=>{
 f.samples[0].report.runs[0].progressEnd=1;
 f.samples[0].assessment=evaluateLocalBrowserRaf(f.samples[0].report);
 await writeFile(f.reportPaths[0],JSON.stringify(f.samples[0]));
 await assert.rejects(reviewLocalRafEvidence(f),/CAPTURE_POLICY_REJECTED/);
 f.samples[0].report.runs[0].progressEnd=0;
 f.samples[0].report.runs[0].frameIntervalsMs.fill(55);
 f.samples[0].assessment=evaluateLocalBrowserRaf(f.samples[0].report);
 await writeFile(f.reportPaths[0],JSON.stringify(f.samples[0]));
 await assert.rejects(reviewLocalRafEvidence(f),/CAPTURE_POLICY_REJECTED/);
}));
test('reject final-component symlinks, hardlinked evidence and oversize reports',()=>withCase(async f=>{
 const sym=join(f.dir,'symbolic.json');await symlink(f.reportPaths[0],sym);
 await assert.rejects(readSingleLocalFile(sym,256*1024),/TYPE_OR_SIZE_INVALID/);
 const hard=join(f.dir,'hard.json');await link(f.reportPaths[0],hard);
 await assert.rejects(readSingleLocalFile(hard,256*1024),/TYPE_OR_SIZE_INVALID/);
 await writeFile(f.reportPaths[1],Buffer.alloc(257*1024,66));
 await assert.rejects(readSingleLocalFile(f.reportPaths[1],256*1024),/TYPE_OR_SIZE_INVALID/);
}));
test('no file, network, or hidden-upload side effects present in offline CLI',async()=>{
 const source=await readFile(new URL('./review_local_raf_evidence.mjs',import.meta.url),'utf8');
 for(const bad of ['fetch(', 'XMLHttpRequest','sendBeacon(','writeFile(','https://','FormData('])
  assert(!source.includes(bad),bad);
 assert(source.includes('sourceCheckoutIndependentlyVerified:false')===false);
 assert(source.includes('exactSourceCommitIndependentlyVerified:false'));
});
