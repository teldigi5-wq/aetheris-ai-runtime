/** Inert E3.9H reference positive-control: real Chromium WebGL2 pixels under scroll.
 * Browser/source observations are NOT proof of model-authored code, professional
 * art direction, owner-PC GPU performance or a deployable luxury website.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const HERE=path.dirname(fileURLToPath(import.meta.url));
const ROOT=path.resolve(HERE,'../../..');
const SITE=path.join(HERE,'e39h/reference');
const OUT=path.join(ROOT,'target/e3-factory/e39h-evidence');
const PORT=18941;
const URL='http://127.0.0.1:'+PORT+'/index.html';
const hash=data=>createHash('sha256').update(data).digest('hex');
const server=spawn('python3',['-m','http.server',String(PORT),'--bind','127.0.0.1','--directory',SITE],{stdio:['ignore','pipe','pipe']});
let browser;
const records=[];
const checks=[];
let failure;
async function ready(){
 for(let n=0;n<50;n++){
   if(server.exitCode!==null)throw Error('E39H_LOCAL_SERVER_EXITED');
   try {const r=await fetch(URL,{signal:AbortSignal.timeout(1500)});if(r.status===200)return;} catch {}
   await delay(150);
 }
 throw Error('E39H_LOCAL_SERVER_UNAVAILABLE');
}
async function gotoPhase(page,fraction){
 await page.evaluate(f=>{
   const chapter=document.querySelector('#chapter');
   const range=Math.max(1,chapter.offsetHeight-innerHeight);
   window.scrollTo({top:chapter.offsetTop+range*f,behavior:'instant'});
 },fraction);
 await page.waitForTimeout(250);
 return page.evaluate(()=>{
   const chapter=document.querySelector('#chapter'),canvas=document.querySelector('#study-canvas');
   const gl=canvas.getContext('webgl2');
   return {
     mode:chapter.dataset.motionState,
     p:Number(chapter.dataset.progress),
     frames:Number(chapter.dataset.frameCount),
     hasRealContext:!!gl && !gl.isContextLost(),
     glVersion:gl?.getParameter(gl.VERSION)||'',
     canvasImage:canvas.toDataURL('image/png'),
     overflow:document.documentElement.scrollWidth>innerWidth+1,
     skipLink:document.querySelector('.skip-link')?.getAttribute('href'),
     cta:document.querySelector('.intro .text-link')?.getAttribute('href'),
   };
 });
}
try {
 await ready();
 await fs.mkdir(OUT,{recursive:true});
 browser=await chromium.launch({headless:true,args:[
  '--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle','--use-angle=swiftshader'
 ]});
 for (const cfg of [
   {name:'desktop',width:1280,height:800,mobile:false},
   {name:'mobile',width:390,height:844,mobile:true}
 ]) {
   const context=await browser.newContext({viewport:{width:cfg.width,height:cfg.height},
     isMobile:cfg.mobile,hasTouch:cfg.mobile,deviceScaleFactor:1,reducedMotion:'no-preference'});
   const page=await context.newPage();
   const errors=[];
   page.on('pageerror',e=>errors.push(String(e.message)));
   page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
   const response=await page.goto(URL,{waitUntil:'load',timeout:20000});
   assert.equal(response?.status(),200);
   assert.match(await page.title(),/E3.9H/);
   await page.locator('#main').waitFor();
   const early=await gotoPhase(page,.08);
   const late=await gotoPhase(page,.87);
   const reversed=await gotoPhase(page,.07);
   assert.equal(early.mode,'webgl2-active','Scene did not initialize WebGL2 at '+cfg.name);
   assert(early.hasRealContext && /WebGL 2/.test(early.glVersion), 'Missing actual WebGL2 context');
   assert(late.p>.73 && early.p<.25,'Scroll progress not controlled by real viewport geometry');
   assert(reversed.p<.25,'Reverse scroll failed');
   assert(late.frames>early.frames && reversed.frames>late.frames,'WebGL render was not repeated');
   assert.notEqual(hash(early.canvasImage),hash(late.canvasImage),'Actual WebGL canvas pixels did not change with scroll');
   assert.notEqual(hash(reversed.canvasImage),hash(late.canvasImage),'Actual reverse scroll did not change canvas pixels');
   assert.equal(early.skipLink,'#main');
   assert.equal(early.cta,'#chapter');
   assert.equal(late.overflow,false,'Horizontal overflow at '+cfg.name);
   assert.deepEqual(errors,[],'Browser errors');
   const screenshot=await page.screenshot({fullPage:false});
   await fs.writeFile(path.join(OUT,cfg.name+'-scene.png'),screenshot);
   records.push({name:cfg.name,viewport:[cfg.width,cfg.height],actualWebGL2:true,
     scrollEarly:early.p,scrollLate:late.p,scrollReversed:reversed.p,
     pixelShaEarly:hash(early.canvasImage),pixelShaLate:hash(late.canvasImage),
     pixelShaReversed:hash(reversed.canvasImage),screenshotSha256:hash(screenshot),
     browserErrors:errors});
   checks.push('Real '+cfg.name+' Chromium WebGL2 pixel changes and reverse scroll');
   await context.close();
 }
 {
   const ctx=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'});
   const page=await ctx.newPage();
   await page.goto(URL);
   const data=await page.evaluate(()=>({mode:document.querySelector('#chapter').dataset.motionState,
     canvasHidden:document.querySelector('#study-canvas').hidden,
     fallbackShown:!document.querySelector('#static-fallback').hidden,
     heading:document.querySelector('#chapter-title').textContent}));
   assert.equal(data.mode,'reduced-static');
   assert.equal(data.canvasHidden,true);
   assert.equal(data.fallbackShown,true);
   assert(data.heading.length>0);
   checks.push('Reduced-motion fully readable static path');
   await ctx.close();
 }
 {
   const ctx=await browser.newContext({viewport:{width:390,height:844}});
   await ctx.addInitScript(()=>{
     const original=HTMLCanvasElement.prototype.getContext;
     HTMLCanvasElement.prototype.getContext=function(type,...args){
       if(type==='webgl2')return null;
       return original.call(this,type,...args);
     };
   });
   const page=await ctx.newPage();
   await page.goto(URL);
   const data=await page.evaluate(()=>({mode:document.querySelector('#chapter').dataset.motionState,
     fallbackShown:!document.querySelector('#static-fallback').hidden,
     h:document.querySelector('#chapter-title').textContent}));
   assert.equal(data.mode,'no-webgl');
   assert.equal(data.fallbackShown,true);
   assert(data.h.length>0);
   checks.push('Forced no-WebGL browser control shows static content');
   await ctx.close();
 }
} catch(e) {
 failure=String(e?.stack||e);
 throw e;
} finally {
 const report={
  evidenceClass:'E39H_DISPOSABLE_WEBGL2_POSITIVE_CONTROL_BROWSER_OBSERVATIONS',
  stage:'E3.9H_RESEARCH_ONLY',
  checks,records,failed:!!failure,error:failure||null,
  referenceWrittenForThisTest:true,independentHumanAuthoredDesignProven:false,
  autonomousModelGeneratedCode:false,modelRepairsMeasured:false,
  agentRuntimeCapabilityCertified:false, realOwnerHardwareBenchmarked:false,
  coreWebVitalsCertified:false,actualCustomerWebsite:false,
  ownerApprovalEstablished:false,releaseAuthorized:false,stage45Go:false
 };
 await fs.mkdir(OUT,{recursive:true});
 await fs.writeFile(path.join(OUT,'browser-report.json'),JSON.stringify(report,null,2)+'\n');
 if(browser)await browser.close();
 server.kill('SIGTERM');
 console.log('E39H_CONTROL_RESULT',JSON.stringify({failed:report.failed,checks:report.checks,scenes:records.length}));
}
