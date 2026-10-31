/* E6 Task 6 — actual Chromium keyboard, WCAG rules and WebGL context loss.
 * This is browser-level accessibility evidence, NOT independent WCAG certification,
 * device GPU profiling or a Stage45 release approval.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';

const app=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(app,'../../../../');
const proof=JSON.parse(await fs.readFile(path.join(root,'target/e6-studio/blender-preview-stage.json'),'utf8'));
assert.equal(proof.kind,'E6_BLENDER_OPT_IN_RESEARCH_PREVIEW_ONLY');
assert.equal(proof.stage45Go,false);
const axe=await fs.readFile(path.join(app,'../node_modules/axe-core/axe.min.js'),'utf8');
const base='http://127.0.0.1:19133';
const evidence=path.join(root,'target/e6-studio/resilience-accessibility');
const report={
 kind:'E6_RESILIENCE_ACCESSIBILITY_BROWSER_RESEARCH',
 engine:'CHROMIUM_SWIFTSHADER_SOFTWARE',
 automatedAxeRulesNotWCAGCertification:true,
 realDeviceGpuMeasured:false,physicalMobileMeasured:false,
 independentAccessibilityApproved:false,
 sourceGlbSha256:proof.previewGlbSha256,
 productionDeployed:false,protectedMainMerged:false,
 ownerApproved:false,stage45Go:false,
 cases:[],failures:[],
};
const server=spawn('python3',['-m','http.server','19133','--bind','127.0.0.1','--directory',path.join(app,'out')],{stdio:'ignore'});
let browser;
async function ready(){
 for(let i=0;i<90;i++){
  if(server.exitCode!==null)throw Error('E6_RESILIENCE_SERVER_EXITED');
  try{const res=await fetch(base+'/blender-preview/',{signal:AbortSignal.timeout(1000)});if(res.ok)return;}catch{}
  await delay(150);
 }
 throw Error('E6_RESILIENCE_SERVER_NOT_READY');
}
async function scan(page,scope){
 await page.addScriptTag({content:axe});
 const result=await page.evaluate(async()=>{
  return globalThis.axe.run(document,{runOnly:{
   type:'tag',values:['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa']
  }});
 });
 const violations=result.violations.map(v=>({id:v.id,impact:v.impact,nodes:v.nodes.map(n=>n.target).slice(0,5)}));
 assert.deepEqual(violations,[],scope+' must have no axe WCAG-tagged violations');
 return {rulesPassed:result.passes.length,violations:0,incomplete:result.incomplete.length};
}
async function facts(page){
 return page.evaluate(()=>{
  const story=document.querySelector('#studio-story');
  return {
   engine:story?.getAttribute('data-engine'),
   recovery:story?.getAttribute('data-recovery'),
   focus:document.activeElement===story,
   canvas:document.querySelectorAll('canvas').length,
   status:document.querySelector('.tech-state')?.textContent,
   main:document.querySelectorAll('main').length,
   headings:document.querySelectorAll('h1').length,
   overflow:document.documentElement.scrollWidth>innerWidth+2,
   chapters:document.querySelectorAll('.chapters article').length,
   asset:story?.getAttribute('data-asset-variant'),
  };
 });
}
try{
 await fs.mkdir(evidence,{recursive:true});
 await ready();
 browser=await chromium.launch({headless:true,args:[
  '--enable-webgl','--enable-unsafe-swiftshader',
  '--use-gl=angle','--use-angle=swiftshader',
 ]});
 for(const cfg of [
  {name:'wide',w:1440,h:900,mobile:false,reduced:false},
  {name:'tablet',w:820,h:1180,mobile:false,reduced:false},
  {name:'small-mobile',w:320,h:700,mobile:true,reduced:false},
  {name:'mobile',w:390,h:844,mobile:true,reduced:false},
  {name:'reduced',w:390,h:844,mobile:true,reduced:true},
 ]){
  const ctx=await browser.newContext({
   viewport:{width:cfg.w,height:cfg.h},
   hasTouch:cfg.mobile,isMobile:cfg.mobile,deviceScaleFactor:1,
   reducedMotion:cfg.reduced?'reduce':'no-preference',serviceWorkers:'block',
  });
  const page=await ctx.newPage(),errors=[],outside=[],glbs=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.route('**/*',route=>{
   const url=route.request().url();
   if(!url.startsWith(base+'/')){outside.push(url);return route.abort();}
   if(new URL(url).pathname.endsWith('.glb'))glbs.push(new URL(url).pathname);
   return route.continue();
  });
  const response=await page.goto(base+'/blender-preview/?asset=https://untrusted.example/model.glb',{waitUntil:'load'});
  assert.equal(response?.status(),200);
  await page.waitForFunction(()=>document.querySelector('#studio-story')?.dataset.engine===
   (matchMedia('(prefers-reduced-motion: reduce)').matches?'reduced-static':'r3f-loaded'),null,{timeout:25000});
  const initial=await facts(page);
  assert.equal(initial.asset,'blender');
  assert.equal(initial.main,1);
  assert.equal(initial.headings,1);
  assert.equal(initial.chapters,3);
  assert.equal(initial.overflow,false);
  assert.equal(initial.recovery,'none');
  // Skip must transfer keyboard focus, not just scroll to an inert section.
  await page.locator('a.skip-link').focus();
  await page.keyboard.press('Enter');
  await page.waitForFunction(()=>document.activeElement===document.getElementById('studio-story'),null,{timeout:5000});
  assert.equal((await facts(page)).focus,true);
  const audit=await scan(page,cfg.name+' initial');
  if(cfg.reduced){
   assert.equal(initial.canvas,0);
   assert.deepEqual(glbs,[]);
   assert.equal(await page.locator('button[aria-pressed]').isDisabled(),true);
   report.cases.push({name:cfg.name,reducedMotion:true,noCanvas:true,noAssetFetch:true,
    skipFocus:true,...audit});
  }else{
   assert.equal(initial.engine,'r3f-loaded');
   assert.equal(initial.canvas,1);
   assert.deepEqual(glbs,['/e6-blender-preview.glb']);
   // A genuine browser WebGL context-loss event must promptly release the
   // canvas, preserve content and expose a single manual retry control.
   const prevented=await page.evaluate(()=>{
    const canvas=document.querySelector('canvas');
    const event=new Event('webglcontextlost',{cancelable:true});
    return canvas.dispatchEvent(event)===false;
   });
   assert.equal(prevented,true,'WebGL context loss must be explicitly handled');
   await page.waitForFunction(()=>{
    const story=document.querySelector('#studio-story');
    return story?.dataset.engine==='webgl-unavailable'
     && story.dataset.recovery==='needed'
     && document.querySelectorAll('canvas').length===0;
   },null,{timeout:10000});
   const fallback=await facts(page);
   assert.match(fallback.status||'',/WebGL unavailable/);
   const button=page.getByRole('button',{name:'Retry 3D view'});
   assert.equal(await button.count(),1);
   const buttonHeight=await button.evaluate(el=>el.getBoundingClientRect().height);
   assert(buttonHeight>=44,'Retry must provide a 44px touch/keyboard target');
   await button.focus();
   assert.equal(await button.evaluate(el=>document.activeElement===el),true);
   const failScan=await scan(page,cfg.name+' fallback');
   await page.screenshot({path:path.join(evidence,cfg.name+'-fallback.png'),fullPage:false});
   await button.click();
   await page.waitForFunction(()=>{
    const story=document.querySelector('#studio-story');
    return story?.dataset.engine==='r3f-loaded'
      && story.dataset.recovery==='none'
      && document.querySelectorAll('canvas').length===1;
   },null,{timeout:20000});
   assert.equal(await page.getByRole('button',{name:'Retry 3D view'}).count(),0);
   const restored=await facts(page);
   assert.equal(restored.overflow,false);
   await page.screenshot({path:path.join(evidence,cfg.name+'-restored.png'),fullPage:false});
   report.cases.push({name:cfg.name,contextLossPrevented:true,canvasReleased:true,
    manualRetryRecovered:true,accessibleRetry:true,noOverflow:true,skipFocus:true,
    ...audit,fallbackRulesPassed:failScan.rulesPassed});
  }
  assert.deepEqual(outside,[],cfg.name+' unauthorized external requests');
  assert.deepEqual(errors,[],cfg.name+' JavaScript errors');
  console.log('E6_ACCESSIBILITY_CONTEXT_RESILIENCE_PASS',cfg.name);
  await ctx.close();
 }
 assert.equal(report.cases.length,5);
} catch(error){
 report.failures.push(String(error?.stack||error));
 throw error;
} finally {
 if(browser)await browser.close();
 server.kill('SIGTERM');
 await fs.mkdir(evidence,{recursive:true});
 await fs.writeFile(path.join(evidence,'report.json'),JSON.stringify(report,null,2)+'\n');
 console.log('E6_RESILIENCE_A11Y_RESULT',JSON.stringify({cases:report.cases.length,failures:report.failures.length,stage45Go:false}));
}
