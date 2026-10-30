/* E6 Task 3 extension: actual Chromium screenshot controls for two Blender GLBs.
 * The R3F page and tracked /research-panel.glb are unchanged.
 * Strictly software-WebGL; screenshots are not hardware GPU measurements.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';

const APP=path.dirname(fileURLToPath(import.meta.url));
const ROOT=path.resolve(APP,'../../../../');
const ASSETS=path.join(ROOT,'target/e6-studio/blender-proof');
const EVIDENCE=path.join(ROOT,'target/e6-studio/browser-visual-parity');
const origin='http://127.0.0.1:19113';
const digest=x=>createHash('sha256').update(x).digest('hex');
const configs=[
 {name:'desktop-front',width:1280,height:800,mobile:false,mid:false},
 {name:'desktop-detail',width:1280,height:800,mobile:false,mid:true},
 {name:'mobile-front',width:390,height:844,mobile:true,mid:false},
];
const controls=[
 {name:'original',file:'aetheris-command-module.glb'},
 {name:'repeat',file:'aetheris-command-module.glb'},
 {name:'candidate',file:'aetheris-command-module-no-uv.glb'},
];
const report={
 kind:'E6_TWO_BLENDER_GLB_CHROMIUM_SCREENSHOT_INPUT',
 controlShaValidated:false,
 actualWebGLBrowser:true,
 rendering:'SWIFTSHADER_SOFTWARE_NOT_OWNER_GPU',
 screenshotDiffIndependent:false,
 visualProductionAcceptance:false,
 hardwarePerformanceCertified:false,
 cdnTransferMeasured:false,
 websiteAssetChanged:false,
 ownerApproved:false,
 stage45Go:false,
 captures:[],
 failures:[],
};
let browser;
const server=spawn('python3',['-m','http.server','19113','--bind','127.0.0.1','--directory',path.join(APP,'out')],{stdio:'ignore'});

async function ready(){
 for(let i=0;i<70;i++){
  if(server.exitCode!==null)throw Error('E6_VISUAL_SERVER_EXITED');
  try {
   const res=await fetch(origin+'/',{signal:AbortSignal.timeout(1000)});
   if(res.ok)return;
  }catch{}
  await delay(150);
 }
 throw Error('E6_VISUAL_SERVER_NOT_READY');
}
async function shoot(cfg,control,data){
 const context=await browser.newContext({
   viewport:{width:cfg.width,height:cfg.height},deviceScaleFactor:1,
   isMobile:cfg.mobile,hasTouch:cfg.mobile,
   reducedMotion:'no-preference',serviceWorkers:'block',
 });
 const page=await context.newPage();
 const errors=[],outside=[];
 let replaced=0;
 page.on('pageerror',e=>errors.push(String(e)));
 page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
 await page.route('**/*',route=>{
   if(route.request().url().startsWith(origin+'/'))return route.continue();
   outside.push(route.request().url());
   return route.abort();
 });
 await page.route('**/research-panel.glb',route=>{
   const uri=new URL(route.request().url());
   assert.equal(uri.origin,origin);
   assert.equal(uri.pathname,'/research-panel.glb');
   replaced++;
   return route.fulfill({status:200,contentType:'model/gltf-binary',body:data});
 });
 const loaded=await page.goto(origin+'/',{waitUntil:'load'});
 assert.equal(loaded?.status(),200);
 await page.waitForFunction(()=>document.querySelector('#studio-story')?.getAttribute('data-engine')==='r3f-loaded',null,{timeout:25000});
 if(cfg.mid){
   const top=await page.evaluate(()=>{
     const el=document.querySelector('#studio-story');
     return scrollY+el.getBoundingClientRect().top;
   });
   await page.evaluate(y=>window.scrollTo({top:y+innerHeight*1.2,behavior:'instant'}),top);
   await page.waitForFunction(()=>Number(document.querySelector('#studio-story')?.getAttribute('data-progress'))>.1,null,{timeout:10000});
 }else{
   await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));
 }
 await page.waitForTimeout(500);
 const state=await page.evaluate(()=>({
   progress:Number(document.querySelector('#studio-story').dataset.progress),
   shot:document.querySelector('#studio-story').dataset.shot,
   engine:document.querySelector('#studio-story').dataset.engine,
   chapters:document.querySelectorAll('.chapters article').length,
   horizontalOverflow:document.documentElement.scrollWidth>innerWidth+2,
   canvasCount:document.querySelectorAll('canvas').length,
 }));
 assert.equal(state.engine,'r3f-loaded');
 assert.equal(state.chapters,3);
 assert(state.canvasCount>=1);
 assert.equal(state.horizontalOverflow,false);
 if(cfg.mid)assert(state.progress>.1);
 assert.equal(replaced,1,'Did not replace exactly one local GLB request');
 assert.deepEqual(errors,[],'Browser exception or console error');
 assert.deepEqual(outside,[],'Outside network request denied');
 const file=cfg.name+'-'+control.name+'.png';
 const bytes=await page.locator('canvas').first().screenshot({animations:'disabled'});
 assert(bytes.length>1000,'Empty canvas screenshot');
 await fs.writeFile(path.join(EVIDENCE,file),bytes);
 report.captures.push({
   scene:cfg.name,variant:control.name,assetDigest:digest(data),
   screenshot:file,screenshotSha256:digest(bytes),screenshotBytes:bytes.length,
   softwareWebgl:true,networkRequestsOutside:0,errors:0,...state,
 });
 await context.close();
 console.log('E6_SOFTWARE_WEBGL_SCREENSHOT_CAPTURE_PASS',cfg.name,control.name);
}
try{
 await fs.mkdir(EVIDENCE,{recursive:true});
 const manifest=JSON.parse(await fs.readFile(path.join(ASSETS,'asset-size-comparison.json'),'utf8'));
 assert.equal(manifest.kind,'E6_BLENDER_NO_UV_OFFLINE_SIZE_COMPARISON');
 assert.equal(manifest.trianglePositionNormalDataEqual,true);
 assert.equal(manifest.ownerApproved,false);
 assert.equal(manifest.stage45Go,false);
 const original=await fs.readFile(path.join(ASSETS,'aetheris-command-module.glb'));
 const candidate=await fs.readFile(path.join(ASSETS,'aetheris-command-module-no-uv.glb'));
 assert.equal(digest(original),manifest.originalSha256);
 assert.equal(digest(candidate),manifest.candidateSha256);
 assert.equal(original.length,manifest.originalRawBytes);
 assert.equal(candidate.length,manifest.candidateRawBytes);
 report.controlShaValidated=true;
 await ready();
 browser=await chromium.launch({headless:true,args:[
  '--enable-webgl','--enable-unsafe-swiftshader',
  '--use-gl=angle','--use-angle=swiftshader',
 ]});
 for(const cfg of configs)
  for(const control of controls)
   await shoot(cfg,control,control.name==='candidate'?candidate:original);
 assert.equal(report.captures.length,9);
} catch(e) {
 report.failures.push(String(e?.stack||e));
 throw e;
} finally {
 if(browser)await browser.close();
 server.kill('SIGTERM');
 await fs.mkdir(EVIDENCE,{recursive:true});
 await fs.writeFile(path.join(EVIDENCE,'capture-report.json'),JSON.stringify(report,null,2)+'\n');
 console.log('E6_VISUAL_CAPTURE_RESULT',JSON.stringify({count:report.captures.length,failures:report.failures.length,stage45Go:false}));
}
