#!/usr/bin/env node
/** Local-owner ONLY: non-headless Chrome real-hardware scroll frame-pacing trace.
 * No CI invocation; no automatic upload; no VRAM/thermal/GPU-frame-time claim.
 * Usage after explicit opt-in build: node owner_gpu_benchmark.mjs --confirm-local-hardware
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createServer} from 'node:http';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {chromium} from 'playwright';
import {KIND,evaluateHardwareEvidence} from './owner_gpu_policy.mjs';

if(process.argv.slice(2).join(' ')!=='--confirm-local-hardware'){
 throw Error('E6_OWNER_EXPLICIT_LOCAL_OPT_IN_REQUIRED: pass --confirm-local-hardware');
}
if(process.env.CI||process.env.GITHUB_ACTIONS){
 throw Error('E6_OWNER_HARDWARE_EVIDENCE_CANNOT_RUN_IN_CI');
}
const app=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(app,'../../../../');
const reportDir=path.join(root,'target/e6-studio/owner-gpu-local');
const proof=JSON.parse(await fs.readFile(path.join(root,'target/e6-studio/blender-preview-stage.json'),'utf8'));
const glb=await fs.readFile(path.join(app,'out/e6-blender-preview.glb'));
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
assert.equal(proof.kind,'E6_BLENDER_OPT_IN_RESEARCH_PREVIEW_ONLY');
assert.equal(proof.stage45Go,false);
assert.equal(sha(glb),proof.previewGlbSha256,'Incorrect or stale local Blender preview GLB');
await fs.access(path.join(app,'out/blender-preview/index.html'));
const commit=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();
assert.match(commit,/^[0-9a-f]{40}$/);
await fs.mkdir(reportDir,{recursive:true});
const reportPath=path.join(reportDir,'owner-local-frame-trace.json');
const assessmentPath=path.join(reportDir,'assessment.json');
for(const file of [reportPath,assessmentPath]){
 try{await fs.access(file);throw Error('E6_OWNER_REFUSE_EVIDENCE_OVERWRITE: '+file)}
 catch(e){if(e?.code!=='ENOENT')throw e}
}
const origin='http://127.0.0.1:19171';
// Bind only to loopback. No external server or dependency is needed.
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8',
 '.css':'text/css; charset=utf-8','.json':'application/json; charset=utf-8',
 '.glb':'model/gltf-binary','.png':'image/png','.jpg':'image/jpeg',
 '.svg':'image/svg+xml','.woff2':'font/woff2','.ico':'image/x-icon'};
const exportDir=path.resolve(app,'out');
const server=createServer(async(req,res)=>{
 try{
  const route=new URL(req.url||'/',origin).pathname;
  const tail=route.endsWith('/')?route+'index.html':route;
  const target=path.resolve(exportDir,'.'+decodeURIComponent(tail));
  if(!target.startsWith(exportDir+path.sep))throw Error('E6_PATH_DENIED');
  const body=await fs.readFile(target);
  res.writeHead(200,{'content-type':mime[path.extname(target)]||'application/octet-stream',
   'x-content-type-options':'nosniff','cache-control':'no-store'});
  res.end(body);
 }catch{res.writeHead(404,{'content-type':'text/plain'});res.end('Not found')}
});
let browser;
const report={
 schemaVersion:1,kind:KIND,
 captureMode:'owner-local-headed-chrome',headless:false,emulated:false,ci:false,
 exactGitCommit:commit,glbSha256:proof.previewGlbSha256,
 capturedAt:new Date().toISOString(),
 viewport:null,webgl2:false,renderer:null,browserVersion:null,runs:[],
 externalRequests:0,browserErrors:0,webglLosses:0,
 flags:{gpuTimeMeasured:false,gpuVramMeasured:false,thermalsMeasured:false,
  physicalMobileTested:false,realReleaseApproved:false}
};
try{
 await new Promise((resolve,reject)=>{
  server.once('error',reject);
  server.listen(19171,'127.0.0.1',resolve);
 });
 // DO NOT use bundled Chromium, SwiftShader flags, remote debugging or headless.
 // Chrome must be installed on the physical owner workstation and show a window.
 browser=await chromium.launch({channel:'chrome',headless:false,args:['--disable-extensions']});
 report.browserVersion='Chrome/'+browser.version();
 const ctx=await browser.newContext({viewport:{width:1440,height:900},
  deviceScaleFactor:1,reducedMotion:'no-preference',serviceWorkers:'block'});
 const page=await ctx.newPage();
 page.on('pageerror',()=>report.browserErrors++);
 page.on('console',m=>{if(m.type()==='error')report.browserErrors++});
 page.on('requestfailed',()=>report.browserErrors++);
 page.on('crash',()=>report.browserErrors++);
 await page.route('**/*',route=>{
  if(!route.request().url().startsWith(origin+'/')){
   report.externalRequests++;return route.abort();
  }
  return route.continue();
 });
 const resp=await page.goto(origin+'/blender-preview/',{waitUntil:'load'});
 assert.equal(resp?.status(),200);
 await page.waitForFunction(()=>document.querySelector('#studio-story')?.dataset.engine==='r3f-loaded',null,{timeout:30000});
 report.viewport=await page.evaluate(()=>({width:innerWidth,height:innerHeight,dpr:devicePixelRatio}));
 const gpu=await page.evaluate(()=>{
  const canvas=document.querySelector('canvas');
  const gl=canvas?.getContext('webgl2');
  if(!gl)return {webgl2:false,renderer:null};
  const info=gl.getExtension('WEBGL_debug_renderer_info');
  return {webgl2:true,renderer:info?{
   unmaskedRenderer:String(gl.getParameter(info.UNMASKED_RENDERER_WEBGL)),
   unmaskedVendor:String(gl.getParameter(info.UNMASKED_VENDOR_WEBGL))
  }:null};
 });
 report.webgl2=gpu.webgl2;
 report.renderer=gpu.renderer;
 // Explicit hard fail: missing GPU identity cannot become accepted evidence.
 if(!gpu.renderer)throw Error('E6_GPU_UNMASKED_RENDERER_UNAVAILABLE');
 const detect=evaluateHardwareEvidence({...report,runs:[]});
 if(detect.reasons.includes('E6_GPU_HARDWARE_RENDERER_UNVERIFIED')){
  throw Error('E6_SOFTWARE_OR_UNKNOWN_GPU_REJECTED:'+gpu.renderer.unmaskedRenderer);
 }
 for(let repeat=0;repeat<3;repeat++){
  await page.evaluate(()=>scrollTo({top:0,behavior:'instant'}));
  await page.waitForTimeout(300);
  const sample=await page.evaluate(async()=>{
   const target=document.querySelector('#studio-story');
   const start=scrollY+target.getBoundingClientRect().top;
   const span=innerHeight*2.2;
   const intervals=[],progress=[];
   let longTaskCount=0,maxLongTaskMs=0,contextLosses=0;
   const canvas=document.querySelector('canvas');
   const onLoss=()=>{contextLosses++};
   canvas?.addEventListener('webglcontextlost',onLoss);
   const observer=typeof PerformanceObserver==='function' &&
    PerformanceObserver.supportedEntryTypes?.includes('longtask')
    ?new PerformanceObserver(list=>{
      for(const x of list.getEntries()){longTaskCount++;maxLongTaskMs=Math.max(maxLongTaskMs,x.duration)}
     }):null;
   observer?.observe({entryTypes:['longtask']});
   // Warm up outside measurement, then record requestAnimationFrame intervals
   // while driving the actual ScrollTrigger with a 180-frame forward + reverse.
   for(let i=0;i<48;i++)await new Promise(resolve=>requestAnimationFrame(resolve));
   let previous=null;
   for(let i=0;i<361;i++){
    const now=await new Promise(resolve=>requestAnimationFrame(resolve));
    if(previous!==null)intervals.push(now-previous);
    previous=now;
    const t=i<=180?i/180:(360-i)/180;
    scrollTo({top:start+span*t,behavior:'instant'});
    const p=Number(target.dataset.progress);
    if(Number.isFinite(p))progress.push(p);
   }
   // Flush any buffered long-task observations, never claim GPU timer values.
   for(let i=0;i<3;i++)await new Promise(resolve=>requestAnimationFrame(resolve));
   observer?.takeRecords().forEach(x=>{longTaskCount++;maxLongTaskMs=Math.max(maxLongTaskMs,x.duration)});
   observer?.disconnect();
   canvas?.removeEventListener('webglcontextlost',onLoss);
   return {
    frameIntervalsMs:intervals,
    progress:{minimum:Math.min(...progress),maximum:Math.max(...progress)},
    reverseObserved:progress.length>200&&progress.at(-1)<progress[Math.floor(progress.length/2)]-.35,
    longTaskCount,maxLongTaskMs,
    longTaskObserverSupported:Boolean(observer),contextLosses
   };
  });
  if(!sample.longTaskObserverSupported)throw Error('E6_LONGTASK_OBSERVER_UNAVAILABLE');
  report.webglLosses+=sample.contextLosses;
  delete sample.contextLosses;
  delete sample.longTaskObserverSupported;
  report.runs.push(sample);
  console.log('E6_OWNER_LOCAL_RAF_RUN',repeat+1,'samples',sample.frameIntervalsMs.length,
   'progress',sample.progress);
 }
 await ctx.close();
 const assessment=evaluateHardwareEvidence(report);
 // Always record an explicit NO_GO assessment even when the trace passes.
 await fs.writeFile(reportPath,JSON.stringify(report,null,2)+'\n',{flag:'wx'});
 await fs.writeFile(assessmentPath,JSON.stringify(assessment,null,2)+'\n',{flag:'wx'});
 console.log('E6_OWNER_LOCAL_EVIDENCE_WRITTEN',reportPath);
 console.log('E6_OWNER_LOCAL_ASSESSMENT',JSON.stringify(assessment));
 if(!assessment.acceptedForResearch)process.exitCode=2;
}finally{
 if(browser)await browser.close();
 server.close();
}
