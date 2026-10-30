/* Actual Chromium browser checks for exported Next.js + R3F + GSAP research.
 * CI is software-WebGL only. Do not claim production 60fps/GPU or Blender.
 */
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';
import { chromium } from 'playwright';

const project=path.dirname(fileURLToPath(import.meta.url));
const origin='http://127.0.0.1:19086';
const reportDir=path.resolve(project,'../../../../target/e6-studio/next-r3f-evidence');
const report={schemaVersion:1,kind:'E6_NEXT_REACT_R3F_GSAP_RESEARCH_CONTROL',
  serverRendered:true,buildTarget:'STATIC_RESEARCH_EXPORT',
  softwareWebGLNotOwnerGPU:true,modelCalled:false,
  trueGpu60fpsCertified:false,ownerApproved:false,stage45Go:false,
  cases:[],failures:[]};
const server=spawn('python3',['-m','http.server','19086','--bind','127.0.0.1','--directory',
  path.join(project,'out')],{stdio:'ignore'});
let browser;

async function serverReady(){
  for(let i=0;i<65;i++){
    if(server.exitCode!==null)throw Error('E6_NEXT_SERVER_EXIT');
    try{
      const response=await fetch(origin+'/',{signal:AbortSignal.timeout(1000)});
      if(response.ok)return;
    }catch{}
    await delay(140);
  }
  throw Error('E6_NEXT_SERVER_UNAVAILABLE');
}
async function context(width,height,motion){
  const c=await browser.newContext({
    viewport:{width,height},reducedMotion:motion,serviceWorkers:'block',
    deviceScaleFactor:width<600?2:1,
  });
  const page=await c.newPage(),remote=[],errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.route('**/*',route=>{
    if(route.request().url().startsWith(origin+'/'))return route.continue();
    remote.push(route.request().url());return route.abort();
  });
  return {c,page,errors,remote};
}
async function inspect(page){
  return page.evaluate(()=>{
    const story=document.querySelector('#studio-story');
    return {
      engine:story?.getAttribute('data-engine'),
      shot:story?.getAttribute('data-shot'),
      progress:Number(story?.getAttribute('data-progress')),
      reduced:matchMedia('(prefers-reduced-motion: reduce)').matches,
      chapters:document.querySelectorAll('.chapters article').length,
      main:document.querySelectorAll('main').length,
      nav:document.querySelectorAll('.nav-right a').length,
      width:document.documentElement.scrollWidth,
      innerWidth,
      canvas:document.querySelectorAll('canvas').length,
      smooth:document.querySelector('button[aria-pressed]')?.getAttribute('aria-pressed'),
      ready:document.querySelector('.tech-state')?.textContent,
      skip:document.querySelector('a.skip-link')?.getAttribute('href'),
    };
  });
}
try{
  await fs.mkdir(reportDir,{recursive:true});
  await serverReady();
  browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--enable-unsafe-swiftshader',
    '--use-gl=angle','--use-angle=swiftshader',
  ]});
  for(const cfg of [
    {name:'desktop',w:1440,h:900,motion:'no-preference'},
    {name:'mobile',w:390,h:844,motion:'no-preference'},
    {name:'reduced',w:390,h:844,motion:'reduce'},
  ]){
    const {c,page,errors,remote}=await context(cfg.w,cfg.h,cfg.motion);
    const loaded=await page.goto(origin+'/',{waitUntil:'load'});
    assert.equal(loaded?.status(),200);
    await page.waitForSelector('#studio-story');
    if(cfg.name!=='reduced'){
      await page.waitForFunction(
        ()=>document.querySelector('#studio-story')?.getAttribute('data-engine')==='r3f-loaded',
        {timeout:18000});
    }
    const first=await inspect(page);
    assert.equal(first.main,1);
    assert.equal(first.chapters,3);
    assert.equal(first.nav,2);
    assert.equal(first.skip,'#studio-story');
    assert.equal(first.smooth,'false','Lenis must not automatically activate');
    assert(first.width<=first.innerWidth+2,'Unexpected horizontal overflow');
    if(cfg.name==='reduced'){
      assert.equal(first.engine,'reduced-static');
      assert.equal(first.canvas,0,'Reduced-motion must not allocate WebGL canvas');
      assert.equal(first.shot,'arrival');
      assert.equal(await page.locator('button[aria-pressed]').isDisabled(),true);
    }else{
      assert.equal(first.engine,'r3f-loaded');
      assert(first.canvas>=1,'Expected actual R3F Canvas');
      assert.match(first.ready||'',/demand rendering/);
      const start=await page.evaluate(()=>{
        const el=document.querySelector('#studio-story');
        return window.scrollY+el.getBoundingClientRect().top;
      });
      await page.evaluate((y)=>window.scrollTo({top:y+innerHeight*1.2,behavior:'instant'}),start);
      await page.waitForTimeout(800);
      const moved=await inspect(page);
      assert(moved.progress>.10,'GSAP ScrollTrigger did not drive scene');
      assert.notEqual(moved.shot,'arrival','No scroll-directed composition change');
      await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));
      await page.waitForTimeout(500);
      const back=await inspect(page);
      assert(back.progress<moved.progress,'Reverse scroll was not tracked');
      report.cases.push({name:cfg.name,canvas:first.canvas,
        scrollProgress:moved.progress,reverseProgress:back.progress,
        originalAsset:'pinned-owned-GLB',r3fLoaded:true,noExternalRequests:remote.length===0});
    }
    await page.screenshot({path:path.join(reportDir,cfg.name+'.png'),fullPage:false});
    assert.deepEqual(remote,[],'Unexpected external resource request');
    assert.deepEqual(errors,[],'Browser console/page exception');
    if(cfg.name==='reduced')report.cases.push({
      name:cfg.name,canvas:0,reducedMotionStatic:true,noExternalRequests:true});
    console.log('E6_NEXT_R3F_GSAP_ACTUAL_CHROMIUM_PASS',cfg.name);
    await c.close();
  }
} catch(error) {
  report.failures.push(String(error?.stack||error));
  throw error;
} finally {
  if(browser)await browser.close();
  server.kill('SIGTERM');
  await fs.mkdir(reportDir,{recursive:true});
  await fs.writeFile(path.join(reportDir,'report.json'),JSON.stringify(report,null,2)+'\n');
  console.log('E6_NEXT_RESEARCH_RESULT',JSON.stringify({
    cases:report.cases.length,errors:report.failures.length,stage45Go:false,
  }));
}
