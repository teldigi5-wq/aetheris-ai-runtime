/* Isolated E6 conceptual Blender preview: actual Next.js and SwiftShader WebGL.
 * No real customer environment, GPU benchmark, product certification or deployment.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../../../');
const app=path.resolve(root,'tools/enterprise/e6_studio/next-studio');
const proof=JSON.parse(await fs.readFile(path.join(root,'target/e6-studio/blender-preview-stage.json'),'utf8'));
const binary=await fs.readFile(path.join(app,'out/e6-blender-preview.glb'));
const hash=x=>createHash('sha256').update(x).digest('hex');
assert.equal(proof.kind,'E6_BLENDER_OPT_IN_RESEARCH_PREVIEW_ONLY');
assert.equal(hash(binary),proof.previewGlbSha256);
assert(proof.previewGlbBytes>10000);
assert.equal(proof.stage45Go,false);
assert.equal(proof.physicalGpuVerified,false);

const origin='http://127.0.0.1:19127';
const evidence=path.join(root,'target/e6-studio/blender-visual-preview');
const server=spawn('python3',['-m','http.server','19127','--bind','127.0.0.1','--directory',path.join(app,'out')],{stdio:'ignore'});
const report={
 kind:'E6_BLENDER_CONCEPT_CINEMATIC_BROWSER_RESEARCH',
 sourceSha256:proof.sourceBlenderSha256,
 glbSha256:proof.previewGlbSha256,
 glbBytes:proof.previewGlbBytes,
 originalFixtureStillUsedAtRoot:true,
 noUntrustedAssetRequests:true,
 browserRenderer:'CHROMIUM_SWIFTSHADER_SOFTWARE_NOT_OWNER_GPU',
 visualApproval:false,measuredFps:false,deviceGpuVerified:false,
 assetLicensedForDistribution:false,productionDeployed:false,
 ownerApproved:false,stage45Go:false,cases:[],failures:[],
};
let browser;
async function ready(){
 for(let i=0;i<90;i++){
  if(server.exitCode!==null)throw Error('E6_PREVIEW_SERVER_EXIT');
  try{const a=await fetch(origin+'/blender-preview/',{signal:AbortSignal.timeout(1000)});if(a.ok)return;}catch{}
  await delay(150);
 }
 throw Error('E6_PREVIEW_SERVER_NOT_READY');
}
async function snapshot(page){
 return page.evaluate(()=>{
  const el=document.querySelector('#studio-story');
  return {
   engine:el?.dataset.engine,assetVariant:el?.dataset.assetVariant,
   act:el?.dataset.shot,progress:Number(el?.dataset.progress),
   webglCanvas:document.querySelectorAll('canvas').length,
   main:document.querySelectorAll('main').length,
   title:document.querySelector('h1')?.textContent?.trim(),
   width:document.documentElement.scrollWidth,viewport:innerWidth,
   chapters:document.querySelectorAll('.chapters article').length,
   smooth:document.querySelector('button[aria-pressed]')?.getAttribute('aria-pressed'),
   scrim:getComputedStyle(el,'::before').backgroundImage.includes('linear-gradient'),
  };
 });
}
async function move(page,t){
 const start=await page.evaluate(()=>{const el=document.querySelector('#studio-story');return scrollY+el.getBoundingClientRect().top;});
 await page.evaluate(({start,t})=>scrollTo({top:start+innerHeight*2.2*t,behavior:'instant'}),{start,t});
 await page.waitForTimeout(620);
 return snapshot(page);
}
try{
 await fs.mkdir(evidence,{recursive:true});
 await ready();
 browser=await chromium.launch({headless:true,args:['--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle','--use-angle=swiftshader']});
 for(const cfg of [
  {name:'desktop',width:1440,height:900,mobile:false,motion:'no-preference'},
  {name:'mobile',width:390,height:844,mobile:true,motion:'no-preference'},
  {name:'reduced',width:390,height:844,mobile:true,motion:'reduce'},
 ]){
  const ctx=await browser.newContext({
   viewport:{width:cfg.width,height:cfg.height},isMobile:cfg.mobile,hasTouch:cfg.mobile,
   deviceScaleFactor:1,reducedMotion:cfg.motion,serviceWorkers:'block',
  });
  const page=await ctx.newPage(),errors=[],outside=[],loadedAssets=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.route('**/*',route=>{
   const url=route.request().url();
   if(!url.startsWith(origin+'/')){outside.push(url);return route.abort();}
   const pathname=new URL(url).pathname;
   if(pathname.endsWith('.glb'))loadedAssets.push(pathname);
   return route.continue();
  });
  const res=await page.goto(origin+'/blender-preview/',{waitUntil:'load'});
  assert.equal(res?.status(),200);
  assert.match(await page.locator('h1').innerText(),/trust tangible/i);
  if(cfg.motion==='reduce'){
   await page.waitForFunction(()=>document.querySelector('#studio-story')?.dataset.engine==='reduced-static');
   const state=await snapshot(page);
   assert.equal(state.webglCanvas,0);
   assert.equal(state.assetVariant,'blender');
   assert.equal(state.smooth,'false');
   assert(state.scrim);
   assert.equal(loadedAssets.length,0,'Reduced-motion must not fetch GLB or allocate GPU canvas');
   assert.equal(await page.locator('button[aria-pressed]').isDisabled(),true);
   await page.screenshot({path:path.join(evidence,cfg.name+'-static.png'),fullPage:false});
   report.cases.push({name:cfg.name,staticOnly:true,noGLBRequest:true,noWebglCanvas:true});
  }else{
   await page.waitForFunction(()=>document.querySelector('#studio-story')?.dataset.engine==='r3f-loaded',null,{timeout:25000});
   const initial=await snapshot(page);
   assert.equal(initial.assetVariant,'blender');
   assert.equal(initial.webglCanvas,1);
   assert.equal(initial.scrim,true);
   assert.equal(initial.smooth,'false');
   assert.equal(initial.chapters,3);
   assert.equal(initial.main,1);
   assert(initial.width<=initial.viewport+2);
   assert.deepEqual(loadedAssets,['/e6-blender-preview.glb'],'Only validated owned Blender GLB may be fetched');
   // The actual evidence response must match the staged GLB, not just its filename.
   const response=await page.request.get(origin+'/e6-blender-preview.glb');
   assert.equal(response.status(),200);
   assert.equal(hash(await response.body()),proof.previewGlbSha256);
   const frames=[];
   for(const [act,t] of [['arrival',0.10],['material',0.44],['precision',0.95],['reverse',0.10]]){
    const s=await move(page,t);
    assert.equal(s.assetVariant,'blender');
    assert.equal(s.act,act==='reverse'?'arrival':act);
    assert(s.progress>=0&&s.progress<=1);
    assert(s.webglCanvas>=1);
    assert(s.width<=s.viewport+2);
    const bytes=await page.screenshot({fullPage:false,animations:'disabled'});
    assert(bytes.length>3000);
    const filename=cfg.name+'-'+act+'.png';
    await fs.writeFile(path.join(evidence,filename),bytes);
    frames.push({act,progress:s.progress,filename,sha256:hash(bytes),size:bytes.length});
   }
   assert.notEqual(frames[1].sha256,frames[2].sha256);
   report.cases.push({name:cfg.name,actualBlenderGLBLoaded:true,
     expectedSha256:proof.previewGlbSha256,acts:frames,reverseWorks:true,
     noOutsideRequests:true,viewportNoOverflow:true});
  }
  assert.deepEqual(errors,[],cfg.name+' browser exceptions');
  assert.deepEqual(outside,[],cfg.name+' outside URLs denied');
  console.log('E6_OPT_IN_BLENDER_SWIFTSHADER_PREVIEW_BROWSER_PASS',cfg.name);
  await ctx.close();
 }
 // Root remains tied to the original fixture even in a preview-flagged build.
 const ctx=await browser.newContext({viewport:{width:1280,height:800},reducedMotion:'no-preference',serviceWorkers:'block'});
 const page=await ctx.newPage(),rootGLBs=[],unexpected=[];
 await page.route('**/*',route=>{
  const u=route.request().url();
  if(!u.startsWith(origin+'/')){unexpected.push(u);return route.abort();}
  if(u.endsWith('.glb'))rootGLBs.push(new URL(u).pathname);
  return route.continue();
 });
 const rootResponse=await page.goto(origin+'/',{waitUntil:'load'});
 assert.equal(rootResponse?.status(),200);
 await page.waitForFunction(()=>document.querySelector('#studio-story')?.dataset.engine==='r3f-loaded',null,{timeout:25000});
 assert.equal((await snapshot(page)).assetVariant,'fixture');
 assert.deepEqual(rootGLBs,['/research-panel.glb']);
 assert.deepEqual(unexpected,[]);
 report.cases.push({name:'default-root-unchanged',originalFixtureLoaded:true});
 await ctx.close();
 assert.equal(report.cases.length,4);
} catch(e){report.failures.push(String(e?.stack||e));throw e;}
finally{
 if(browser)await browser.close();
 server.kill('SIGTERM');
 await fs.mkdir(evidence,{recursive:true});
 await fs.writeFile(path.join(evidence,'report.json'),JSON.stringify(report,null,2)+'\n');
 console.log('E6_BLENDER_CONCEPT_PREVIEW_PROOF',JSON.stringify({cases:report.cases.length,failures:report.failures.length,stage45Go:false}));
}
