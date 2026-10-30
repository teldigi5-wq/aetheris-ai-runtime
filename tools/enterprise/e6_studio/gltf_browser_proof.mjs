/* E6 authored three-shot storyboard, actual WebGL2 Chromium positive control.
 * Performance timings from software emulation are NOT owner GPU certification.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {chromium} from 'playwright';

const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'../../..');
const dir=path.join(here,'gltf_lab');
const origin='http://127.0.0.1:19077/';
const href=origin+'gltf_lab/asset_page.html';
const server=spawn('python3',['-m','http.server','19077','--bind','127.0.0.1','--directory',here],{stdio:'ignore'});
async function serverReady(){
 for(let i=0;i<50;i++){
  if(server.exitCode!==null)throw Error('GLTF_LOCAL_SERVER_EXIT');
  try{const response=await fetch(href,{signal:AbortSignal.timeout(1200)});if(response.status===200)return;}catch{}
  await delay(120);
 }
 throw Error('GLTF_LOCAL_SERVER_NOT_READY');
}
const folder=path.join(root,'target/e6-studio/gltf-proof');
const digest=data=>createHash('sha256').update(data).digest('hex');
const configs=[
  {name:'desktop',width:1440,height:900,isMobile:false,dpr:2},
  {name:'mobile',width:390,height:844,isMobile:true,dpr:3}
];
const frames=[
  {name:'arrival',f:.05},
  {name:'material',f:.50},
  {name:'precision',f:.93},
  {name:'reverse',f:.07}
];
const report={
  kind:'E6_PINNED_LOCAL_GLTF_2_CINEMATIC_WEBGL_RESEARCH_CONTROL',
  stage:'E6_GLTF2_RESEARCH_ONLY',
  checkedGlTF2Bytes:true,
  assetEmbeddedBufferOnly:true,
  blenderExported:false,
  realChromium:true,
  browserWebgl:'SOFTWARE_EMULATED_NOT_OWNER_GPU',
  scenesAreHumanAuthored:true,
  modelCalls:0,
  modelGeneratedGeometry:false,
  assetLicensingCertified:false,
  blenderPipelineCertified:false,
  gsapProductionIntegrationCertified:false,
  trueGpuPerformanceCertified:false,
  genuineModelAuthorshipProven:false,
  ownerHardwareValidated:false,
  ownerApproved:false,
  productionReady:false,
  stage45Go:false,
  cases:[],failures:[]
};
let browser;
async function isolated(context){
  const page=await context.newPage();
  const faults=[],outside=[];
  page.on('pageerror',e=>faults.push(e.message));
  page.on('console',m=>{if(m.type()==='error')faults.push(m.text());});
  await page.route('**/*',route=>{
    const url=route.request().url();
    if(url.startsWith(origin))return route.continue();
    outside.push(url);return route.abort();
  });
  return {page,faults,outside};
}
async function phase(page,fraction){
  await page.evaluate(f=>{
    const story=document.querySelector('#story');
    const range=Math.max(1,story.offsetHeight-innerHeight);
    window.scrollTo({top:story.offsetTop+range*f,behavior:'instant'});
  },fraction);
  await page.waitForTimeout(190);
  const d=await page.evaluate(()=>({
    assetSha256:document.querySelector('#story').dataset.assetSha256,
    assetVertices:Number(document.querySelector('#story').dataset.assetVertices),
    geometryOrigin:document.querySelector('#story').dataset.geometryOrigin,
    shot:document.querySelector('#story').dataset.shot,
    progress:Number(document.querySelector('#story').dataset.progress),
    frames:Number(document.querySelector('#story').dataset.frameCount),
    mode:document.querySelector('#story').dataset.renderState,
    pixels:Number(document.querySelector('#story').dataset.canvasPixels),
    canvasHidden:document.querySelector('#stage-canvas').hidden,
    fallbackHidden:document.querySelector('#static-stage').hidden,
    width:document.documentElement.scrollWidth,
    viewport:innerWidth,
    chapters:document.querySelectorAll('.chapters article').length,
  }));
  assert.equal(d.mode,'webgl2-active');
  assert.equal(d.assetSha256,'ace770e960909c2b788312dfd80e9d788ed89fd1eeb3f8008842b4e246af45a7');
  assert.equal(d.assetVertices,36);
  assert.equal(d.geometryOrigin,'vetted-gltf2-embedded-buffer');
  assert(d.pixels>0&&d.pixels<=1202500,'Exceeded bounded pixel budget');
  assert.equal(d.canvasHidden,false);
  assert.equal(d.fallbackHidden,true);
  assert.equal(d.chapters,3);
  assert(d.width<=d.viewport+2,'Horizontal overflow');
  const image=await page.locator('#stage-canvas').screenshot();
  return {...d,canvasSha256:digest(image)};
}
try{
  await serverReady();
  await fs.mkdir(folder,{recursive:true});
  browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--enable-unsafe-swiftshader',
    '--use-gl=angle','--use-angle=swiftshader'
  ]});
  for(const cfg of configs){
    const context=await browser.newContext({
      viewport:{width:cfg.width,height:cfg.height},
      isMobile:cfg.isMobile,hasTouch:cfg.isMobile,
      deviceScaleFactor:cfg.dpr,reducedMotion:'no-preference',
      serviceWorkers:'block'
    });
    const {page,faults,outside}=await isolated(context);
    const response=await page.goto(href,{waitUntil:'load'});
    assert.equal(response?.status(),200);
    await page.waitForFunction(()=>document.querySelector('#story')
      ?.dataset.renderState==='webgl2-active');
    const observed=[];
    for(const p of frames) {
      const current=await phase(page,p.f);
      assert.equal(current.shot,p.name==='reverse'?'arrival':p.name);
      assert(Math.abs(current.progress-p.f)<.04,'Viewport geometry did not control shot');
      observed.push({...current,phase:p.name});
      await fs.writeFile(path.join(folder,cfg.name+'-'+p.name+'.png'),
        await page.screenshot({fullPage:false}));
    }
    assert(observed[0].frames<observed[1].frames);
    assert(observed[1].frames<observed[2].frames);
    assert(observed[2].frames<observed[3].frames,'Reverse did not redraw');
    assert.notEqual(observed[0].canvasSha256,observed[1].canvasSha256);
    assert.notEqual(observed[1].canvasSha256,observed[2].canvasSha256);
    assert.notEqual(observed[2].canvasSha256,observed[3].canvasSha256);
    const stable=observed[3].frames;
    await page.waitForTimeout(270);
    const idle=await page.locator('#story').getAttribute('data-frame-count');
    assert.equal(Number(idle),stable,'Idle renderer must not animate continuously');

    // Real browser WebGL extension forces actual loss; no ghost frame increments.
    const loss=await page.evaluate(()=>{
      const gl=document.querySelector('#stage-canvas').getContext('webgl2');
      const ext=gl?.getExtension('WEBGL_lose_context');
      if(!ext||gl.isContextLost())return false;
      ext.loseContext();return true;
    });
    assert.equal(loss,true);
    await page.waitForFunction(()=>document.querySelector('#story')
      ?.dataset.renderState==='context-lost');
    const beforeLossFrames=Number(await page.locator('#story').getAttribute('data-frame-count'));
    const fall=await page.evaluate(()=>({
      canvasHidden:document.querySelector('#stage-canvas').hidden,
      staticShown:!document.querySelector('#static-stage').hidden,
      status:document.querySelector('#render-status').textContent,
    }));
    assert.equal(fall.canvasHidden,true);
    assert.equal(fall.staticShown,true);
    assert.match(fall.status,/static study/);
    await page.evaluate(()=>{window.scrollTo(0,0);dispatchEvent(new Event('resize'));});
    await page.waitForTimeout(220);
    assert.equal(Number(await page.locator('#story').getAttribute('data-frame-count')),
                 beforeLossFrames,'Rendering continued after context loss');
    await page.reload({waitUntil:'load'});
    await page.waitForFunction(()=>document.querySelector('#story')
      ?.dataset.renderState==='webgl2-active');
    assert.deepEqual(faults,[]);
    assert.deepEqual(outside,[]);
    report.cases.push({name:cfg.name,
      actualWebGL2:true,forwardThreeShots:true,reverseScroll:true,
      boundedPixels:true,idleFrameLoop:false,contextLossStatic:true,
      contextRecoveryRequiresReload:true,
      renderObservations:observed,
      noExternalRequests:outside.length===0,
      browserErrors:faults.length});
    console.log('E6_GLTF2_REAL_CHROMIUM_3_SHOTS_REVERSE_LOSS_PASS',cfg.name);
    await context.close();
  }

  // Reduced motion must never ask for WebGL2, even on a capable GPU/browser.
  for(const name of ['reduced','no-webgl']){
    const context=await browser.newContext({
      viewport:{width:390,height:844},reducedMotion:name==='reduced'?'reduce':'no-preference',
      serviceWorkers:'block'
    });
    await context.addInitScript(kind=>{
      window.__webglRequests=0;
      const original=HTMLCanvasElement.prototype.getContext;
      HTMLCanvasElement.prototype.getContext=function(type,...args){
        if(type==='webgl2'){
          window.__webglRequests++;
          if(kind==='no-webgl')return null;
        }
        return original.call(this,type,...args);
      };
    },name);
    const {page,faults,outside}=await isolated(context);
    await page.goto(href,{waitUntil:'load'});
    const result=await page.evaluate(()=>({
      tries:window.__webglRequests,
      mode:document.querySelector('#story').dataset.renderState,
      canvasHidden:document.querySelector('#stage-canvas').hidden,
      fallback:!document.querySelector('#static-stage').hidden,
      main:document.querySelectorAll('main').length,
      heading:document.querySelector('h1')?.textContent,
      chapters:document.querySelectorAll('.chapters article').length
    }));
    assert.equal(result.mode,name==='reduced'?'reduced-static':'no-webgl');
    assert.equal(result.tries,name==='reduced'?0:1);
    assert.equal(result.canvasHidden,true);
    assert.equal(result.fallback,true);
    assert.equal(result.main,1);
    assert.equal(result.chapters,3);
    assert(result.heading.length>0);
    assert.deepEqual(faults,[]);
    assert.deepEqual(outside,[]);
    report.cases.push({name,webglAttempts:result.tries,staticFallback:true,
      accessibleContentPresent:true});
    console.log('E6_GLTF2_REAL_CHROMIUM_STATIC_FALLBACK_PASS',name);
    await context.close();
  }
  // A tampered glTF must fail closed to permanent readable static content.
  {
    const context=await browser.newContext({
      viewport:{width:1280,height:800},serviceWorkers:'block'
    });
    const {page,faults,outside}=await isolated(context);
    await page.route('**/reference_mesh.gltf', route=>route.fulfill({
      status:200,contentType:'model/gltf+json',
      body:'{"asset":{"version":"2.0"},"buffers":[{"uri":"https://unsafe.example/data.bin"}]}'
    }));
    await page.goto(href,{waitUntil:'load'});
    await page.waitForFunction(()=>document.querySelector('#story')
      ?.dataset.renderState==='asset-invalid');
    const observed=await page.evaluate(()=>({
      canvasHidden:document.querySelector('#stage-canvas').hidden,
      fallbackShown:!document.querySelector('#static-stage').hidden,
      chapterCount:document.querySelectorAll('.chapters article').length
    }));
    assert.equal(observed.canvasHidden,true);
    assert.equal(observed.fallbackShown,true);
    assert.equal(observed.chapterCount,3);
    assert.deepEqual(faults,[]);
    assert.deepEqual(outside,[]);
    report.cases.push({name:'glTF-tampered',
      assetDenied:true,noExternalRequests:true,staticFallback:true});
    console.log('E6_GLTF2_REAL_CHROMIUM_TAMPER_FAIL_CLOSED_PASS');
    await context.close();
  }
} catch(error) {
  report.failures.push(String(error?.stack||error));
  throw error;
} finally {
  if(browser)await browser.close();
  server.kill('SIGTERM');
  await fs.mkdir(folder,{recursive:true});
  await fs.writeFile(path.join(folder,'report.json'),JSON.stringify(report,null,2)+'\n');
  console.log('E6_GLTF2_CONTROL_RESULT',JSON.stringify({
    cases:report.cases.length,failed:report.failures.length>0,stage45Go:false
  }));
}
