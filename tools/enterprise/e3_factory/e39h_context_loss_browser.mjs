/** E3.9H isolated Chromium context-loss AND reload-recovery browser evidence.
 * This is NOT AI-authored website evaluation, GPU frame-time certification,
 * production GPU resilience, or permission to control an owner's device.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {chromium} from 'playwright';

const root=path.dirname(fileURLToPath(import.meta.url));
const dir=path.join(root,'e39h/reference');
const url=pathToFileURL(path.join(dir,'index.html')).href;
const evidence=path.resolve(root,'../../../target/e3-factory/e39h-context-loss');
const hash=data=>createHash('sha256').update(data).digest('hex');
const report={
  class:'E39H_TRUSTED_WEBGL_CONTEXT_LOSS_AND_RELOAD_BROWSER_CONTROL',
  genuineModelCalls:0,modelGeneratedWebsite:false,realCustomerSite:false,
  heldoutEvaluation:false,productionFeatureEnabled:false,
  ownerHardwareBenchmarked:false,performanceSloProven:false,
  webglAutoRecoveryImplemented:false,explicitReloadRequired:true,
  stage45Go:false,scenarios:[],failure:null
};
let browser;
try {
  await fs.mkdir(evidence,{recursive:true});
  browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--enable-unsafe-swiftshader',
    '--use-gl=angle','--use-angle=swiftshader'
  ]});
  for (const cfg of [
    {name:'desktop',width:1280,height:800,mobile:false},
    {name:'mobile',width:390,height:844,mobile:true}
  ]) {
    const context=await browser.newContext({
      viewport:{width:cfg.width,height:cfg.height},deviceScaleFactor:1,
      isMobile:cfg.mobile,hasTouch:cfg.mobile,
      reducedMotion:'no-preference',serviceWorkers:'block'
    });
    const page=await context.newPage();
    const errors=[];
    const requests=[];
    page.on('pageerror',e=>errors.push(e.message));
    page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
    await page.route('**/*',async route=>{
      const target=route.request().url();
      if(target.startsWith('file:') && target.startsWith(pathToFileURL(dir).href))
        return route.continue();
      requests.push(target);
      return route.abort();
    });
    const response=await page.goto(url,{waitUntil:'load'});
    assert.equal(response?.status(),200);
    await page.waitForFunction(()=>document.querySelector('#chapter')
      ?.dataset.motionState==='webgl2-active');
    await page.evaluate(()=>window.scrollTo(0,document.querySelector('#chapter').offsetTop+250));
    await page.waitForTimeout(175);
    const original=await page.evaluate(()=>{
      const c=document.querySelector('#study-canvas');
      const gl=c.getContext('webgl2');
      const ext=gl?.getExtension('WEBGL_lose_context');
      if(!gl || !ext || gl.isContextLost())return {supported:false};
      ext.loseContext();
      return {supported:true};
    });
    assert.equal(original.supported,true,'Real WEBGL_lose_context extension missing');
    await page.waitForFunction(()=>document.querySelector('#chapter')
      ?.dataset.motionState==='context-lost',{timeout:5000});
    const after=await page.evaluate(()=>{
      const chapter=document.querySelector('#chapter');
      return {
        mode:chapter.dataset.motionState,
        fallback:!document.querySelector('#static-fallback').hidden,
        canvasHidden:document.querySelector('#study-canvas').hidden,
        frameCount:Number(chapter.dataset.frameCount),
        status:document.querySelector('#motion-status').textContent,
        copyVisible:document.querySelector('#chapter-title').getBoundingClientRect().width>0
      };
    });
    assert.equal(after.mode,'context-lost');
    assert.equal(after.fallback,true);
    assert.equal(after.canvasHidden,true);
    assert.equal(after.copyVisible,true);
    assert.match(after.status,/static study remains available/);
    await page.evaluate(()=>{
      window.scrollTo(0,Math.max(0,document.body.scrollHeight-innerHeight));
      window.dispatchEvent(new Event('resize'));
    });
    await page.waitForTimeout(180);
    const frozen=await page.evaluate(()=>({
      frames:Number(document.querySelector('#chapter').dataset.frameCount),
      fallback:!document.querySelector('#static-fallback').hidden
    }));
    assert.equal(frozen.frames,after.frameCount,'Lost context must stop rendering');
    assert.equal(frozen.fallback,true);
    const snapshot=await page.screenshot({fullPage:false});
    await fs.writeFile(path.join(evidence,cfg.name+'-context-loss.png'),snapshot);
    // A genuine page reload is the only supported recovery mechanism.
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(()=>document.querySelector('#chapter')
      ?.dataset.motionState==='webgl2-active',{timeout:5000});
    const recovered=await page.evaluate(()=>{
      const c=document.querySelector('#study-canvas');
      return {
        context:!!c.getContext('webgl2')&&!c.getContext('webgl2').isContextLost(),
        mode:document.querySelector('#chapter').dataset.motionState,
        fallback:!document.querySelector('#static-fallback').hidden,
        frames:Number(document.querySelector('#chapter').dataset.frameCount)
      };
    });
    assert.equal(recovered.context,true);
    assert.equal(recovered.fallback,false);
    assert(recovered.frames>0);
    assert.deepEqual(errors,[]);
    assert.deepEqual(requests,[],'No external network requests permitted');
    report.scenarios.push({viewport:cfg.name,
      width:cfg.width,height:cfg.height,
      lostMode:after.mode,
      canvasHiddenDuringLoss:after.canvasHidden,
      stoppedFrames:frozen.frames===after.frameCount,
      recoveredAfterReload:recovered.context && recovered.mode==='webgl2-active',
      noExternalRequests:requests.length===0,consoleErrors:errors.length,
      staticFallbackScreenshotSha256:hash(snapshot)});
    await context.close();
  }
  // Reduced motion must avoid allocating even a single WebGL context.
  {
    const context=await browser.newContext({
      viewport:{width:390,height:844},reducedMotion:'reduce'
    });
    await context.addInitScript(()=>{
      window.__webglAllocationAttempts=0;
      const original=HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext=function(type,...args){
        if(type==='webgl2')window.__webglAllocationAttempts++;
        return original.call(this,type,...args);
      };
    });
    const page=await context.newPage();
    await page.goto(url,{waitUntil:'load'});
    const reduced=await page.evaluate(()=>({
      attempted:window.__webglAllocationAttempts,
      mode:document.querySelector('#chapter').dataset.motionState,
      staticShown:!document.querySelector('#static-fallback').hidden,
      title:document.querySelector('#chapter-title').textContent
    }));
    assert.equal(reduced.attempted,0);
    assert.equal(reduced.mode,'reduced-static');
    assert.equal(reduced.staticShown,true);
    assert(reduced.title.length>0);
    report.scenarios.push({viewport:'reduced-motion',
      webglAllocationAttempts:0,staticFallback:true});
    await context.close();
  }
} catch(error) {
  report.failure=String(error?.stack||error);
  throw error;
} finally {
  if(browser)await browser.close();
  await fs.mkdir(evidence,{recursive:true});
  await fs.writeFile(path.join(evidence,'report.json'),JSON.stringify(report,null,2)+'\n');
  console.log('E39H_CONTEXT_LOSS_CONTROL',JSON.stringify({
    ok:report.failure===null,scenarios:report.scenarios.length,
    noModelClaims:report.genuineModelCalls===0
  }));
}
