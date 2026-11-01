/* Exact local website + real GLB browser proof, software Chromium only.
 * Distinguish code/browser success from physical GPU and artistic acceptance.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const here=dirname(fileURLToPath(import.meta.url));
const origin='http://127.0.0.1:19169';
const asset='/assets/arcloom-signal-fold.glb';
const manifest=JSON.parse(await readFile(join(here,'target/e6-w4a-arcloom/assets/w4a-scene-evidence.json'),'utf8'));
const sha=x=>createHash('sha256').update(x).digest('hex');
const server=spawn('python3',['-m','http.server','19169','--bind','127.0.0.1',
  '--directory',join(here,'target/e6-w4a-arcloom')],{stdio:'ignore'});
let browser;
async function readiness(){
  for(let i=0;i<80;i++){
    if(server.exitCode!==null)throw Error('W4A_LOCAL_SERVER_EXITED');
    try{if((await fetch(origin+'/studies/signal-fold/',{signal:AbortSignal.timeout(1000)})).ok)return;}catch{}
    await delay(120);
  }
  throw Error('W4A_LOCAL_SERVER_NOT_READY');
}
async function visit(config){
  const ctx=await browser.newContext({
    viewport:{width:config.width,height:config.height},
    reducedMotion:config.motion,javaScriptEnabled:config.js,
    serviceWorkers:'block',deviceScaleFactor:1
  });
  const page=await ctx.newPage(),outside=[],errors=[],glbs=[];
  try{
    if(config.noWebgl){
      await page.addInitScript(()=>{
        const native=HTMLCanvasElement.prototype.getContext;
        HTMLCanvasElement.prototype.getContext=function(kind,...args){
          if(kind==='webgl'||kind==='webgl2'||kind==='experimental-webgl')return null;
          return native.call(this,kind,...args);
        };
      });
    }
    page.on('pageerror',e=>errors.push(String(e)));
    await page.route('**/*',route=>{
      const url=route.request().url();
      if(!url.startsWith(origin+'/')){outside.push(url);return route.abort();}
      if(new URL(url).pathname===asset)glbs.push(url);
      return route.continue();
    });
    const response=await page.goto(origin+'/studies/signal-fold/',{waitUntil:'load'});
    assert.equal(response?.status(),200);
    assert.equal(await page.locator('main[data-w2c-study="signal-fold"]').count(),1);
    assert.equal(await page.locator('section.story-chapter').count(),3);
    assert.equal(await page.locator('[data-w4a-scene]').count(),1);
    assert.equal(await page.locator('[data-w4a-fallback]').count(),1);
    assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),0);
    assert.deepEqual(glbs,[],'Must not preload GPU model');
    const stage=page.locator('[data-w4a-scene]');
    if(!config.js){
      assert.equal(await page.locator('noscript').count(),1);
    }else if(config.motion==='reduce'){
      await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='reduced-static');
      assert.equal(await page.locator('[data-w4a-activate]').isHidden(),true);
      assert.deepEqual(glbs,[]);
    }else{
      await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='static-ready');
      await page.locator('[data-w4a-activate]').click();
      if(config.noWebgl){
        await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='static-fallback');
        assert.equal(await page.locator('[data-w4a-fallback]').isVisible(),true);
        assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),0);
        assert.deepEqual(glbs,[]);
      }else{
        await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='webgl-loaded',null,{timeout:30000});
        assert.equal(await page.locator('[data-w4a-viewport] canvas').count(),1);
        assert.equal(glbs.length,1);
        const raw=await page.request.get(origin+asset);
        assert.equal(raw.status(),200);
        const bytes=await raw.body();
        assert.equal(bytes.length,manifest.modelBytes);
        assert.equal(sha(bytes),manifest.modelSha256);
        await page.locator('[data-w4a-pause]').click();
        assert.equal(await page.locator('[data-w4a-pause]').getAttribute('aria-pressed'),'true');
        await page.locator('[data-w4a-pause]').click();
        assert.equal(await page.locator('[data-w4a-pause]').getAttribute('aria-pressed'),'false');
        const scroll=await stage.evaluate(el=>({
          top:el.getBoundingClientRect().top+scrollY,
          height:el.getBoundingClientRect().height
        }));
        await page.evaluate(({top,height})=>scrollTo(0,Math.max(0,top+height-innerHeight*.25)),scroll);
        await page.waitForTimeout(250);
        const progress=Number(await stage.getAttribute('data-progress'));
        assert(progress>.35&&progress<=1,'GSAP camera progress did not follow native scroll: '+progress);
        assert(['material','detail'].includes(await stage.getAttribute('data-camera-phase')));
        // Explicitly simulate GPU context loss: no unbounded automatic retry.
        await page.locator('[data-w4a-viewport] canvas').evaluate(c=>
          c.dispatchEvent(new Event('webglcontextlost',{bubbles:false,cancelable:true})));
        await page.waitForFunction(()=>document.querySelector('[data-w4a-scene]')?.dataset.engine==='static-fallback');
        assert.equal(await page.locator('[data-w4a-fallback]').isVisible(),true);
      }
    }
    const width=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
    assert(width<=2,'Horizontal overflow on W4-A '+config.name+': '+width);
    assert.deepEqual(outside,[],'External request detected');
    assert.deepEqual(errors,[],'Uncaught browser exception');
    console.log('E6_W4A_CHROMIUM_PASS '+JSON.stringify({
      profile:config.name,enabledScene:!config.noWebgl&&config.js&&config.motion!=='reduce',
      fetchedGlbCount:glbs.length,normalScroll:true,noHorizontalOverflow:true,
      contextLossFallback:!config.noWebgl&&config.js&&config.motion!=='reduce',
      realGpuMeasured:false,modelGenerated:false,releaseGo:false
    }));
  }finally{await ctx.close();}
}
try{
  await readiness();
  browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle','--use-angle=swiftshader'
  ]});
  for(const config of [
    {name:'desktop-webgl',width:1440,height:900,js:true,motion:'no-preference',noWebgl:false},
    {name:'mobile-webgl',width:390,height:844,js:true,motion:'no-preference',noWebgl:false},
    {name:'reduced-static',width:390,height:844,js:true,motion:'reduce',noWebgl:false},
    {name:'no-javascript',width:390,height:844,js:false,motion:'no-preference',noWebgl:false},
    {name:'webgl-unavailable',width:390,height:844,js:true,motion:'no-preference',noWebgl:true}
  ])await visit(config);
}finally{
  if(browser)await browser.close();
  if(server.exitCode===null&&server.signalCode===null){server.kill('SIGTERM');await once(server,'exit');}
}
