/** E3.9G-12B4C NEGATIVE CONTROL: inspect existing disposable public websites.
 * Real Chromium observations do not show 3D/scroll-driven visual engineering.
 * This is NOT a production test or a certification of model capabilities.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as pause} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {chromium} from 'playwright';

const HERE=path.dirname(fileURLToPath(import.meta.url));
const ROOT=path.resolve(HERE,'../../../target/e3-factory/e39g12b');
const SITE_ROOT=path.join(ROOT,'sites');
const REPORT=path.resolve(HERE,'../../../target/e3-factory/e39g12b4c');
const PORT=18802;
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const server=spawn('python3',['-m','http.server',String(PORT),'--bind','127.0.0.1','--directory',SITE_ROOT],
  {stdio:['ignore','pipe','pipe']});
let browser;
const rows=[];
async function ready(){
 for(let n=0;n<60;n++){
  if(server.exitCode!==null)throw Error('E39G12B4C_SERVER_EXITED');
  try{
   const r=await fetch('http://127.0.0.1:'+PORT+'/studynest/reference/index.html',
                       {signal:AbortSignal.timeout(1200)});
   if(r.ok)return;
  }catch{}
  await pause(130);
 }
 throw Error('E39G12B4C_SERVER_UNAVAILABLE');
}
try{
 await ready();
 browser=await chromium.launch({headless:true});
 await fs.mkdir(REPORT,{recursive:true});
 for(const site of ['studynest','cityline']){
  for(const [viewport,width,height] of [['mobile',390,844],['desktop',1280,800]]){
   const context=await browser.newContext({viewport:{width,height},reducedMotion:'reduce'});
   const page=await context.newPage();
   const errors=[];
   page.on('pageerror',e=>errors.push(String(e.message)));
   try{
    const url='http://127.0.0.1:'+PORT+'/'+site+'/reference/index.html';
    const response=await page.goto(url,{waitUntil:'load',timeout:15000});
    assert.equal(response?.status(),200);
    const before=await page.evaluate(()=>{
     const scene=[...document.querySelectorAll('canvas,[data-scroll-scene],[data-3d-scene]')];
     return {
       webglCanvasCount:[...document.querySelectorAll('canvas')].filter(c=>
         c.getContext('webgl')||c.getContext('webgl2')).length,
       explicitlyTaggedSceneCount:scene.length,
       scrollY:scrollY,
       sceneTransform:scene.map(el=>getComputedStyle(el).transform),
       reducedMotion:matchMedia('(prefers-reduced-motion: reduce)').matches,
       horizontalOverflow:document.documentElement.scrollWidth>innerWidth+1
     };
    });
    await page.evaluate(()=>scrollTo(0,document.documentElement.scrollHeight));
    await pause(75);
    const after=await page.evaluate(()=>({
      scrollY,
      sceneTransform:[...document.querySelectorAll('canvas,[data-scroll-scene],[data-3d-scene]')]
        .map(el=>getComputedStyle(el).transform)
    }));
    const screenshot=await page.screenshot({fullPage:false});
    const rec={
      evidenceClass:'E39G12B4C_REAL_CHROMIUM_NONCINEMATIC_BASELINE',
      site,viewport,width,height,
      sourceSha256:sha(await fs.readFile(path.join(SITE_ROOT,site,'reference/index.html'))),
      screenshotSha256:sha(screenshot),
      webglCanvasCount:before.webglCanvasCount,
      explicitlyTaggedSceneCount:before.explicitlyTaggedSceneCount,
      scrollSceneTransformChanged:before.sceneTransform.length>0 &&
        JSON.stringify(before.sceneTransform)!==JSON.stringify(after.sceneTransform),
      scrollStartedAt:before.scrollY,scrollEndedAt:after.scrollY,
      reducedMotionEmulated:before.reducedMotion,
      javascriptErrors:errors,
      demonstratesPremiumCinematic3D:false,
      modelGeneratedScene:false,
      productionDeployed:false,ownerApproved:false
    };
    assert.equal(rec.webglCanvasCount,0);
    assert.equal(rec.explicitlyTaggedSceneCount,0);
    assert.equal(rec.scrollSceneTransformChanged,false);
    assert.equal(rec.reducedMotionEmulated,true);
    assert.deepEqual(errors,[]);
    rows.push(rec);
    await fs.writeFile(path.join(REPORT,site+'-'+viewport+'.json'),
      JSON.stringify(rec,null,2)+'\n',{flag:'wx'});
    await fs.writeFile(path.join(REPORT,site+'-'+viewport+'.png'),screenshot,{flag:'wx'});
    console.log('E39G12B4C_REAL_CHROMIUM_CINEMATIC_NEGATIVE_CONTROL',site,viewport,
      JSON.stringify({scenes:rec.explicitlyTaggedSceneCount,webgl:rec.webglCanvasCount}));
   }finally{await context.close();}
  }
 }
 assert.equal(rows.length,4);
 const summary={
  evidenceClass:'E39G12B4C_BASELINE_BROWSER_4_NEGATIVE_CONTROLS',
  realChromiumObservations:4,actualWebglCanvasCount:rows.reduce((n,x)=>n+x.webglCanvasCount,0),
  actualScrollSceneTransitions:rows.filter(x=>x.scrollSceneTransformChanged).length,
  independentlyVerifiedFull3dProductionSite:false,
  autonomousCodingModelDemonstrated:false,
  noModelCalls:true,
  siteCount:2,
  note:'These are public author-created non-3D fixtures; this does NOT exclude future generated 3D sites.',
  rows:rows.map(x=>({site:x.site,viewport:x.viewport,screenshotSha256:x.screenshotSha256,
   sourceSha256:x.sourceSha256,webglCanvasCount:x.webglCanvasCount}))
 };
 await fs.writeFile(path.join(REPORT,'browser-summary.json'),
   JSON.stringify(summary,null,2)+'\n',{flag:'wx'});
 console.log('E39G12B4C_REAL_BROWSER_4_CINEMATIC_NEGATIVE_CONTROLS',JSON.stringify({
    browserRuns:4,realWebglScenes:0,scrollSceneTransitions:0,modelCalls:0}));
}finally{
 if(browser)await browser.close();
 server.kill('SIGTERM');
}
