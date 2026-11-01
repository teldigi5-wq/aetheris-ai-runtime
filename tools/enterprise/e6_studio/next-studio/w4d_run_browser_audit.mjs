/**
 * W4-D: repeatable *software-browser* evidence from the exact W4-C generated
 * preview. This is NOT hardware FPS/GPU acceptance, production CrUX/RUM,
 * cross-browser testing, actual user input INP, or artistic approval.
 *
 * No external URLs, arbitrary filesystem paths, paid services, deploy, secrets,
 * or changes to previously generated website sources.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {setTimeout as sleep} from 'node:timers/promises';
import {chromium} from 'playwright';
import {EXPECTED_PROFILES,validateReport} from './w4d_evidence_contract.mjs';

const here=dirname(fileURLToPath(import.meta.url));
const repository=join(here,'../../../..');
const site=join(here,'target/e6-w4c-arcloom');
const output=join(repository,'target/e6-studio/w4d-browser-evidence');
const hostname='http://127.0.0.1:19187';
const route='/studies/signal-fold/';
const sha=raw=>createHash('sha256').update(raw).digest('hex');
const read=path=>readFile(join(site,path));
if(process.argv.length!==2)throw Error('E6_W4D_FIXED_LOCAL_COMMAND_ONLY');
const [manifestRaw,bootstrap,engine,glb,still]=await Promise.all([
  read('assets/w4c-performance-evidence.json'),
  read('assets/signal-fold-bootstrap.mjs'),
  read('assets/signal-fold-engine.mjs'),
  read('assets/arcloom-signal-fold.glb'),
  read('assets/signal-fold-refined.png')
]);
const base=JSON.parse(manifestRaw);
if(base.kind!=='E6_W4C_PROGRESSIVE_CINEMATIC_RESEARCH_V1'||
   base.releaseGo!==false||base.stage45Go!==false||
   base.initialBootstrapSha256!==sha(bootstrap)||
   base.optionalSceneSha256!==sha(engine)||
   base.modelSha256!==sha(glb)||
   base.fallbackImageSha256!==sha(still))
  throw Error('E6_W4D_UNTRUSTED_PARENT_HASH_OR_AUTHORITY');
const server=spawn('python3',['-m','http.server','19187','--bind','127.0.0.1',
  '--directory',site],{stdio:'ignore'});
let browser;
async function ready(){
  for(let i=0;i<75;i++){
    if(server.exitCode!==null)throw Error('E6_W4D_LOCAL_SERVER_EXIT');
    try{
      const response=await fetch(hostname+route,{signal:AbortSignal.timeout(1000)});
      if(response.status===200)return;
    }catch{}
    await sleep(120);
  }
  throw Error('E6_W4D_LOCAL_SERVER_UNAVAILABLE');
}
async function profileAudit(profile){
  const settings={
    'desktop-software':{w:1440,h:900,js:true,motion:'no-preference'},
    'mobile-software':{w:390,h:844,js:true,motion:'no-preference'},
    'compact-software':{w:320,h:700,js:true,motion:'no-preference'},
    'reduced-motion-static':{w:390,h:844,js:true,motion:'reduce'},
    'javascript-disabled-static':{w:390,h:844,js:false,motion:'no-preference'}
  }[profile];
  const ctx=await browser.newContext({
    viewport:{width:settings.w,height:settings.h},javaScriptEnabled:settings.js,
    reducedMotion:settings.motion,deviceScaleFactor:1,serviceWorkers:'block'
  });
  const page=await ctx.newPage(),remote=[],exceptions=[],requests=[];
  try{
    if(settings.js)await page.addInitScript(()=>{
      window.__w4dVitals={cls:0,lcp:0};
      if('PerformanceObserver' in window){
        try{new PerformanceObserver(list=>{
          for(const entry of list.getEntries())
            if(!entry.hadRecentInput)window.__w4dVitals.cls+=entry.value;
        }).observe({type:'layout-shift',buffered:true});}catch{}
        try{new PerformanceObserver(list=>{
          for(const entry of list.getEntries())
            window.__w4dVitals.lcp=Math.max(window.__w4dVitals.lcp,entry.startTime);
        }).observe({type:'largest-contentful-paint',buffered:true});}catch{}
      }
    });
    page.on('pageerror',error=>exceptions.push(String(error)));
    await page.route('**/*',route=>{
      const link=route.request().url();
      if(!link.startsWith(hostname+'/')){remote.push(link);return route.abort();}
      requests.push(new URL(link).pathname);
      return route.continue();
    });
    const result=await page.goto(hostname+route+'?w4b-visual-proof=1',{waitUntil:'load'});
    const pageStatus=result?.status()??0;
    const load=await page.evaluate(()=>({
      duration:performance.getEntriesByType('navigation')[0]?.loadEventEnd??0,
      fcp:performance.getEntriesByType('paint').find(e=>e.name==='first-contentful-paint')?.startTime??0
    }));
    await page.waitForTimeout(120);
    const initial3DEngineRequests=requests.filter(x=>x==='/assets/signal-fold-engine.mjs').length;
    const initialGlbRequests=requests.filter(x=>x==='/assets/arcloom-signal-fold.glb').length;
    assert.equal(initial3DEngineRequests,0,'Heavy engine unexpectedly eager');
    assert.equal(initialGlbRequests,0,'GLB unexpectedly eager');
    const stillLocator=page.locator('[data-w4a-fallback]');
    assert.equal(await stillLocator.isVisible(),true);
    const stillAsset=await page.request.get(hostname+'/assets/signal-fold-refined.png');
    assert.equal(sha(await stillAsset.body()),sha(still));
    // Capture a real viewport-independent static reference for human review.
    const stillShot=await stillLocator.screenshot({animations:'disabled'});
    const staticShot=profile+'-static.png';
    await writeFile(join(output,staticShot),stillShot,{flag:'wx'});
    assert.equal(await page.locator('section.story-chapter').count(),3);
    const entry={
      profile,viewport:[settings.w,settings.h],pageStatus,
      documentLoadMs:Math.max(0,load.duration),fcpCandidateMs:Math.max(0,load.fcp),
      lcpCandidateMs:0,clsObserved:0,
      externalRequests:0,uncaughtBrowserExceptions:0,horizontalOverflowPx:0,
      contentRoutesAccessible:true,staticBlenderStillVisible:true,
      staticScreenshotSha256:sha(stillShot),staticScreenshotFile:staticShot,
      initial3DEngineRequests,initialGlbRequests,
      activationAttempted:settings.js&&settings.motion!=='reduce',
      engineRequestsAfterActivation:0,glbRequestsAfterActivation:0,
      renderedWebglCanvas:false,sourceVerifiedModel:false,
      cameraPositionsObserved:0,nonIdenticalCameraFrames:false,scrollToDrawMs:[],
      coreWebVitalsCertified:false,physicalGpuFrameTimeMeasured:false
    };
    if(entry.activationAttempted){
      await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='static-ready');
      const activate=page.locator('[data-w4a-activate]');
      if(profile==='compact-software'){
        await activate.focus();await page.keyboard.press('Enter');
      }else await activate.click();
      await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='webgl-loaded',
        null,{timeout:30_000});
      entry.engineRequestsAfterActivation=requests.filter(x=>x==='/assets/signal-fold-engine.mjs').length;
      entry.glbRequestsAfterActivation=requests.filter(x=>x==='/assets/arcloom-signal-fold.glb').length;
      entry.renderedWebglCanvas=await page.locator('[data-w4a-viewport] canvas').count()===1;
      entry.sourceVerifiedModel=(await page.locator('[data-w4a-scene]').getAttribute('data-w4b-model-hash'))===base.modelSha256;
      const frames=[];
      for(const step of [0.12,0.54,0.90]){
        const before=Number(await page.locator('[data-w4a-scene]').getAttribute('data-w4b-rendered-frames')||0);
        const start=Date.now();
        await page.evaluate(fraction=>{
          const node=document.querySelector('[data-w4a-scene]');
          const top=scrollY+node.getBoundingClientRect().top;
          const height=node.getBoundingClientRect().height;
          scrollTo({top:Math.max(0,top-innerHeight+fraction*(height+innerHeight)),behavior:'instant'});
        },step);
        await page.waitForFunction(n=>
          Number(document.querySelector('[data-w4a-scene]')?.dataset.w4bRenderedFrames||0)>n,
          before,{timeout:10_000});
        entry.scrollToDrawMs.push(Date.now()-start);
        const encoded=await page.evaluate(()=>window.__w4bVisualProof||'');
        assert(encoded.startsWith('data:image/png;base64,'),'Missing true post-render framebuffer');
        const buffer=Buffer.from(encoded.slice('data:image/png;base64,'.length),'base64');
        assert(buffer.length>2_000,'Blank framebuffer');
        const name=profile+'-camera-'+frames.length+'.png';
        await writeFile(join(output,name),buffer,{flag:'wx'});
        frames.push({sha:sha(buffer),name});
      }
      entry.cameraPositionsObserved=frames.length;
      entry.nonIdenticalCameraFrames=(new Set(frames.map(x=>x.sha))).size===3;
      entry.cameraFrameHashes=frames.map(x=>x.sha);
      assert(entry.nonIdenticalCameraFrames,'No visible camera-frame changes');
    }else{
      if(settings.js)await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='reduced-static');
      assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),0);
    }
    const runtime=await page.evaluate(()=>({
      overflow:Math.max(0,document.documentElement.scrollWidth-innerWidth),
      cls:window.__w4dVitals?.cls??0,lcp:window.__w4dVitals?.lcp??0
    }));
    entry.horizontalOverflowPx=runtime.overflow;
    entry.clsObserved=runtime.cls;
    entry.lcpCandidateMs=runtime.lcp;
    entry.externalRequests=remote.length;
    entry.uncaughtBrowserExceptions=exceptions.length;
    assert.equal(remote.length,0);
    assert.equal(exceptions.length,0,exceptions.join('\n'));
    console.log('E6_W4D_SW_BROWSER_PROFILE '+JSON.stringify({
      profile,initialHeavyRequests:entry.initial3DEngineRequests,
      engineRequestsAfterActivation:entry.engineRequestsAfterActivation,
      modelRequestsAfterActivation:entry.glbRequestsAfterActivation,
      documentLoadMs:Math.round(entry.documentLoadMs),
      lcpCandidateMs:Math.round(entry.lcpCandidateMs),
      clsObserved:entry.clsObserved,scrollToDrawMs:entry.scrollToDrawMs
    }));
    return entry;
  }finally{await ctx.close();}
}
try{
  await mkdir(output,{recursive:false});
  await ready();
  browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle','--use-angle=swiftshader'
  ]});
  const profiles=[];
  for(const name of EXPECTED_PROFILES)profiles.push(await profileAudit(name));
  const report={
    kind:'E6_W4D_SW_BROWSER_EVIDENCE_V1',
    executionEnvironment:'GITHUB_ACTIONS_UBUNTU_CHROMIUM_SWIFTSHADER',
    parentW4CManifestSha256:sha(manifestRaw),
    parentModelSha256:sha(glb),refinedStillSha256:sha(still),
    initialBootstrapSha256:sha(bootstrap),optionalSceneSha256:sha(engine),
    initialBootstrapBytes:bootstrap.length,optionalSceneBytes:engine.length,
    modelBytes:glb.length,
    physicalGpuMeasured:false,ownerDeviceVerified:false,
    independentVisualApproval:false,wcagCertified:false,
    aiModelAuthored:false,productionReady:false,releaseGo:false,stage45Go:false,
    measurementCaveat:'Software Chromium CI / synthetic page visits, not field performance or physical GPU timing; LCP candidate only, CLS observed, INP unmeasured.',
    profiles
  };
  assert.equal(validateReport(report),true);
  await writeFile(join(output,'w4d-software-browser-evidence.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx'});
  console.log('E6_W4D_MEASURED_SOFTWARE_BROWSER_EVIDENCE_PASS '+
    JSON.stringify({profiles:profiles.length,bootstrapBytes:bootstrap.length,
      optionalSceneBytes:engine.length,physicalGpuMeasured:false,releaseGo:false}));
}finally{
  if(browser)await browser.close();
  if(server.exitCode===null&&server.signalCode===null){
    server.kill('SIGTERM');await once(server,'exit');
  }
}
