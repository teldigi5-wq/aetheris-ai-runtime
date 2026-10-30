/* E6 Task 4: actual Next/R3F/GSAP camera continuity under software Chromium.
 * No real device GPU, design approval or Stage45 release certification.
 */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {setTimeout as delay} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const base=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(base,'../../../../');
const out=path.join(root,'target/e6-studio/timeline-evidence');
const origin='http://127.0.0.1:19119';
const server=spawn('python3',['-m','http.server','19119','--bind','127.0.0.1','--directory',path.join(base,'out')],{stdio:'ignore'});
const sha=data=>createHash('sha256').update(data).digest('hex');
const report={kind:'E6_AUTHORED_SCROLL_CAMERA_RESEARCH',rendering:'CHROMIUM_SWIFTSHADER_SOFTWARE',
  ownerGpuVerified:false,hardwareFpsVerified:false,independentVisualApproval:false,
  protectedMainMerged:false,productionDeployed:false,stage45Go:false,cases:[],failures:[]};
let browser;
async function ready(){
 for(let i=0;i<75;i++){
  if(server.exitCode!==null)throw Error('E6_TIMELINE_SERVER_EXIT');
  try{const x=await fetch(origin+'/',{signal:AbortSignal.timeout(1100)});if(x.ok)return;}catch{}
  await delay(140);
 }
 throw Error('E6_TIMELINE_SERVER_NOT_READY');
}
async function inspect(page){
 return page.evaluate(()=>{
  const el=document.querySelector('#studio-story');
  const stage=el.querySelector('.visual-panel').getBoundingClientRect();
  const area=el.getBoundingClientRect();
  const number=k=>Number(el?.getAttribute(k));
  return {
    progress:number('data-progress'),
    shot:el?.getAttribute('data-shot'),
    cameraShot:el?.getAttribute('data-camera-shot'),
    cameraX:number('data-camera-x'),
    cameraY:number('data-camera-y'),
    cameraZ:number('data-camera-z'),
    fov:number('data-camera-fov'),
    engine:el?.getAttribute('data-engine'),
    canvas:document.querySelectorAll('canvas').length,
    chapters:document.querySelectorAll('.chapters article').length,
    horizontalOverflow:document.documentElement.scrollWidth>innerWidth+2,
    contrastScrim:getComputedStyle(el,'::before').backgroundImage.includes('linear-gradient'),
    stageTopFraction:(stage.top-area.top)/area.height,
    smooth:document.querySelector('button[aria-pressed]')?.getAttribute('aria-pressed'),
  };
 });
}
async function step(page,start,t){
 await page.evaluate(({start,t})=>scrollTo({top:start+innerHeight*2.2*t,behavior:'instant'}),{start,t});
 await page.waitForTimeout(680);
 const info=await inspect(page);
 assert(info.progress>=0&&info.progress<=1);
 assert(info.fov>=30-0.01&&info.fov<=38+0.01);
 assert.equal(info.shot,info.cameraShot);
 assert.equal(info.engine,'r3f-loaded');
 assert.equal(info.horizontalOverflow,false);
 assert(info.canvas>=1);
 for(const component of ['cameraX','cameraY','cameraZ','fov'])assert(Number.isFinite(info[component]));
 return info;
}
try{
 await fs.mkdir(out,{recursive:true});
 await ready();
 browser=await chromium.launch({headless:true,args:[
  '--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle','--use-angle=swiftshader',
 ]});
 for(const cfg of [
  {name:'desktop',width:1440,height:900,mobile:false,motion:'no-preference'},
  {name:'mobile',width:390,height:844,mobile:true,motion:'no-preference'},
  {name:'reduced',width:390,height:844,mobile:true,motion:'reduce'},
 ]){
  const ctx=await browser.newContext({viewport:{width:cfg.width,height:cfg.height},isMobile:cfg.mobile,
    hasTouch:cfg.mobile,deviceScaleFactor:1,reducedMotion:cfg.motion,serviceWorkers:'block'});
  const page=await ctx.newPage(),errors=[],outside=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.route('**/*',route=>{
   if(route.request().url().startsWith(origin+'/'))return route.continue();
   outside.push(route.request().url());return route.abort();
  });
  const response=await page.goto(origin+'/',{waitUntil:'load'});
  assert.equal(response?.status(),200);
  if(cfg.motion==='reduce'){
   await page.waitForFunction(()=>document.querySelector('#studio-story')?.dataset.engine==='reduced-static');
   const current=await inspect(page);
   assert.equal(current.canvas,0);
   assert.equal(current.shot,'arrival');
   assert.equal(current.fov,38);
   assert.equal(current.smooth,'false');
   assert.equal(await page.locator('button[aria-pressed]').isDisabled(),true);
   assert.equal(current.chapters,3);
   assert.equal(current.contrastScrim,true,'Reduced-motion story must retain readable contrast');
   assert(current.stageTopFraction>0.25,'Mobile static model must not occupy header copy');
   report.cases.push({name:cfg.name,staticOnly:true,noWebglCanvas:true});
  } else {
   await page.waitForFunction(()=>document.querySelector('#studio-story')?.dataset.engine==='r3f-loaded',null,{timeout:22000});
   const initial=await inspect(page);
   assert.equal(initial.shot,'arrival');
   assert.equal(initial.cameraShot,'arrival');
   assert.equal(initial.fov,38);
   assert.equal(initial.smooth,'false');
   assert.equal(initial.contrastScrim,true,'Camera imagery must be darkened beneath narrative copy');
   if(cfg.mobile)assert(initial.stageTopFraction>0.25,'Mobile 3D must start beneath editorial header');
   else assert(Math.abs(initial.stageTopFraction)<0.05,'Desktop scene should fill its editorial stage');
   const start=await page.evaluate(()=>{
    const el=document.querySelector('#studio-story');
    return scrollY+el.getBoundingClientRect().top;
   });
   const middle=await step(page,start,0.44);
   assert(middle.progress>0.15&&middle.progress<0.8,'ScrollTrigger middle playhead not applied');
   assert.equal(middle.shot,'material');
   assert(middle.fov<38&&middle.fov>30);
   const middleShot=await page.locator('canvas').first().screenshot({animations:'disabled'});
   const middleViewport=await page.screenshot({fullPage:false,animations:'disabled'});
   const last=await step(page,start,0.95);
   assert(last.progress>0.67,'Precision act missing');
   assert.equal(last.shot,'precision');
   assert(last.fov<middle.fov);
   const lastShot=await page.locator('canvas').first().screenshot({animations:'disabled'});
   const lastViewport=await page.screenshot({fullPage:false,animations:'disabled'});
   const reverse=await step(page,start,0.10);
   assert(reverse.progress<middle.progress);
   assert.equal(reverse.shot,'arrival');
   assert(reverse.fov>middle.fov);
   const reverseShot=await page.locator('canvas').first().screenshot({animations:'disabled'});
   const reverseViewport=await page.screenshot({fullPage:false,animations:'disabled'});
   assert.notEqual(sha(middleShot),sha(lastShot));
   assert.notEqual(sha(lastShot),sha(reverseShot));
   for(const [n,bytes,viewport] of [['material',middleShot,middleViewport],['precision',lastShot,lastViewport],['reverse-arrival',reverseShot,reverseViewport]]){
    await fs.writeFile(path.join(out,cfg.name+'-'+n+'.png'),bytes);
    await fs.writeFile(path.join(out,cfg.name+'-'+n+'-viewport.png'),viewport);
   }
   report.cases.push({name:cfg.name,threeActs:true,reversible:true,canvas:initial.canvas,
    progress:[initial.progress,middle.progress,last.progress,reverse.progress],
    fov:[initial.fov,middle.fov,last.fov,reverse.fov],noExternalRequests:true,
    screenshots:6,fullViewportCompositionCaptured:true});
  }
  assert.deepEqual(errors,[],'Browser JS/console errors');
  assert.deepEqual(outside,[],'Untrusted external request');
  console.log('E6_THREE_ACTS_REVERSE_BROWSER_PASS',cfg.name);
  await ctx.close();
 }
 assert.equal(report.cases.length,3);
} catch(error){
 report.failures.push(String(error?.stack||error));
 throw error;
} finally {
 if(browser)await browser.close();
 server.kill('SIGTERM');
 await fs.mkdir(out,{recursive:true});
 await fs.writeFile(path.join(out,'report.json'),JSON.stringify(report,null,2)+'\n');
 console.log('E6_AUTHOR_TIMELINE_RESULT',JSON.stringify({cases:report.cases.length,failures:report.failures.length,stage45Go:false}));
}
