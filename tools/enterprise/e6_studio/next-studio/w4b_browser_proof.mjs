/* W4-B real Chromium visual evidence: software SwiftShader, NOT a real-device benchmark.
 * Check byte-verified model, distinct camera framing, CPU Blender still, and fallbacks.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';
const here=dirname(fileURLToPath(import.meta.url));
const repo=join(here,'../../../..');
const origin='http://127.0.0.1:19174';
const url=origin+'/studies/signal-fold/';
const assetPath='/assets/arcloom-signal-fold.glb';
const stillPath='/assets/signal-fold-reference.png';
const evidence=JSON.parse(await readFile(join(here,'target/e6-w4b-arcloom/assets/w4b-art-direction-evidence.json'),'utf8'));
const actualGlb=await readFile(join(here,'target/e6-w4b-arcloom/assets/arcloom-signal-fold.glb'));
const hash=v=>createHash('sha256').update(v).digest('hex');
const output=join(repo,'target/e6-studio/w4b-visual-evidence');
const server=spawn('python3',['-m','http.server','19174','--bind','127.0.0.1',
 '--directory',join(here,'target/e6-w4b-arcloom')],{stdio:'ignore'});
let browser;
async function readiness(){
 for(let i=0;i<85;i++){
  if(server.exitCode!==null)throw Error('W4B_SERVER_EXIT');
  try{if((await fetch(url,{signal:AbortSignal.timeout(1000)})).ok)return;}catch{}
  await delay(120);
 }
 throw Error('W4B_SERVER_NOT_READY');
}
async function inspect(config){
 const ctx=await browser.newContext({viewport:{width:config.w,height:config.h},
   reducedMotion:config.motion,javaScriptEnabled:config.js,serviceWorkers:'block',
   deviceScaleFactor:1});
 const page=await ctx.newPage(),external=[],issues=[],modelRequests=[];
 try{
  if(config.noWebgl)await page.addInitScript(()=>{
   const native=HTMLCanvasElement.prototype.getContext;
   HTMLCanvasElement.prototype.getContext=function(kind,...args){
    if(kind==='webgl'||kind==='webgl2'||kind==='experimental-webgl')return null;
    return native.call(this,kind,...args);
   };
  });
  page.on('pageerror',error=>issues.push(String(error)));
  page.on('console',msg=>{if(msg.type()==='error')issues.push(msg.text());});
  await page.route('**/*',route=>{
   const href=route.request().url();
   if(!href.startsWith(origin+'/')){external.push(href);return route.abort();}
   if(new URL(href).pathname===assetPath){
    modelRequests.push(href);
    if(config.tamper){
     const damaged=Buffer.from(actualGlb);damaged[damaged.length-12]^=0x01;
     return route.fulfill({status:200,body:damaged,contentType:'model/gltf-binary'});
    }
   }
   return route.continue();
  });
  assert.equal((await page.goto(url,{waitUntil:'load'}))?.status(),200);
  assert.equal(await page.locator('[data-w4a-scene]').count(),1);
  assert.equal(await page.locator('section.story-chapter').count(),3);
  assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),0);
  assert.equal(modelRequests.length,0,'Model must not preload');
  const back=page.locator('[data-w4a-fallback]');
  assert.equal(await back.count(),1);
  assert.equal(await back.evaluate(el=>getComputedStyle(el).backgroundImage.includes('signal-fold-reference.png')),true,
    'Static fallback must use actual local Blender studio still');
  const reference=await page.request.get(origin+stillPath);
  assert.equal(reference.status(),200);
  assert.equal(hash(await reference.body()),evidence.renderedFallbackSha256);
  if(!config.js||config.motion==='reduce'){
   if(config.js)await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='reduced-static');
   assert.equal(await back.isVisible(),true);
   assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),0);
   assert.equal(modelRequests.length,0);
  }else{
   await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='static-ready');
   await page.locator('[data-w4a-activate]').click();
   if(config.tamper||config.noWebgl){
    await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='static-fallback',{},{timeout:18000});
    assert.equal(await back.isVisible(),true);
    assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),0);
    assert.equal(modelRequests.length,config.noWebgl?0:1);
   }else{
    await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='webgl-loaded',{},{timeout:30000});
    assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),1);
    assert.equal(modelRequests.length,1);
    assert.equal(await page.locator('[data-w4a-scene]').getAttribute('data-w4b-model-hash'),evidence.modelSha256);
    const frames=[];
    for(const t of [.12,.54,.90]){
     await page.evaluate(t=>{
      const el=document.querySelector('[data-w4a-scene]');
      const top=scrollY+el.getBoundingClientRect().top;
      scrollTo({top:Math.max(0,top-innerHeight+t*(el.getBoundingClientRect().height+innerHeight)),behavior:'instant'});
     },t);
     await page.waitForTimeout(420);
     const data=await page.locator('[data-w4a-scene]').evaluate(el=>({
       progress:Number(el.dataset.progress),x:Number(el.dataset.w4bCameraX),
       fov:Number(el.dataset.w4bCameraFov),count:Number(el.dataset.w4bRenderedFrames)
     }));
     assert(Number.isFinite(data.x)&&Number.isFinite(data.fov));
     assert(data.count>0);
     const picture=await page.locator('[data-w4a-viewport] canvas').screenshot({animations:'disabled'});
     assert(picture.length>3000);
     const name=config.name+'-shot-'+frames.length+'.png';
     await writeFile(join(output,name),picture);
     frames.push({name,sha:hash(picture),...data,size:picture.length});
    }
    assert(Math.abs(frames[0].x-frames[2].x)>.3,'Three authored camera compositions must move camera');
    assert.notEqual(frames[0].sha,frames[2].sha,'Real screenshots must visually differ');
    await page.locator('[data-w4a-pause]').click();
    assert.equal(await page.locator('[data-w4a-pause]').getAttribute('aria-pressed'),'true');
    await page.locator('[data-w4a-pause]').click();
    assert.equal(await page.locator('[data-w4a-pause]').getAttribute('aria-pressed'),'false');
    await page.locator('[data-w4a-viewport] canvas').evaluate(el=>
      el.dispatchEvent(new Event('webglcontextlost',{cancelable:true})));
    await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='static-fallback');
    assert.equal(await back.isVisible(),true);
    console.log('E6_W4B_REAL_CAMERA_SCREENSHOT_EVIDENCE '+JSON.stringify({profile:config.name,frames}));
   }
  }
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
  assert(overflow<=2,'Horizontal overflow '+config.name+': '+overflow);
  assert.deepEqual(external,[],'Unexpected external request');
  assert.deepEqual(issues,[],'Browser error');
  console.log('E6_W4B_BROWSER_PASS '+JSON.stringify({
   profile:config.name,modelRequests:modelRequests.length,
   staticFromActualBlender:true,contextLossFallback:!config.noWebgl&&!config.tamper&&config.js&&config.motion!=='reduce',
   modelVerifiedBeforeParse:!config.tamper&&config.js&&config.motion!=='reduce'&&!config.noWebgl,
   physicalGpuMeasured:false,visualApproved:false,releaseGo:false
  }));
 }finally{await ctx.close();}
}
try{
 await mkdir(output,{recursive:true});
 await readiness();
 browser=await chromium.launch({headless:true,args:[
  '--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle','--use-angle=swiftshader'
 ]});
 for(const c of [
  {name:'desktop',w:1440,h:900,js:true,motion:'no-preference'},
  {name:'mobile',w:390,h:844,js:true,motion:'no-preference'},
  {name:'reduced',w:390,h:844,js:true,motion:'reduce'},
  {name:'nojs',w:390,h:844,js:false,motion:'no-preference'},
  {name:'nowebgl',w:390,h:844,js:true,motion:'no-preference',noWebgl:true},
  {name:'tampered-model',w:900,h:800,js:true,motion:'no-preference',tamper:true}
 ])await inspect(c);
}finally{
 if(browser)await browser.close();
 if(server.exitCode===null&&server.signalCode===null){
  server.kill('SIGTERM');await once(server,'exit');
 }
}
