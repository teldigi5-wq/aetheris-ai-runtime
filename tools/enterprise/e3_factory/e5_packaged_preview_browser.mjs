/** E5 true Chromium test of the ZIP-EXTRACTED preview. No model calls.
 * Browser proof is about a HUMAN-CODED reference, not AI website generation.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {chromium} from 'playwright';

const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'../../..');
const directory=path.join(root,'target/e5/site');
const manifest=JSON.parse(await fs.readFile(
  path.join(directory,'preview-manifest.json'),'utf8'));
assert.equal(manifest.productionReady,false);
assert.equal(manifest.modelAuthored,false);
assert.equal(manifest.runtimeReleaseStatus,'NO_GO');
const uri=pathToFileURL(path.join(directory,'index.html')).href;
let browser;
try {
  browser=await chromium.launch({
    headless:true,
    args:['--enable-webgl','--enable-unsafe-swiftshader',
          '--use-gl=angle','--use-angle=swiftshader']
  });
  for(const viewport of [
    {name:'desktop',width:1280,height:800},
    {name:'mobile',width:390,height:844}
  ]) {
    const context=await browser.newContext({
      viewport:{width:viewport.width,height:viewport.height},
      reducedMotion:'no-preference',serviceWorkers:'block'
    });
    const page=await context.newPage();
    const errors=[],requests=[];
    page.on('pageerror',e=>errors.push(e.message));
    page.on('request',r=>{
      if(!r.url().startsWith('file:'))requests.push(r.url());
    });
    page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
    await page.route('**/*',route=>{
      if(route.request().url().startsWith('file:'))return route.continue();
      return route.abort();
    });
    const loaded=await page.goto(uri,{waitUntil:'load'});
    assert.equal(loaded?.status(),200);
    await page.waitForFunction(()=>
      document.querySelector('#chapter')?.dataset.motionState!=='not-initialized');
    const state=await page.evaluate(()=>({
      title:document.querySelector('h1')?.textContent.trim(),
      main:document.querySelectorAll('main').length,
      canvas:!!document.querySelector('#study-canvas'),
      mode:document.querySelector('#chapter')?.dataset.motionState,
      width:document.documentElement.scrollWidth,
      viewport:innerWidth
    }));
    assert.equal(state.main,1);
    assert(state.canvas);
    assert(state.title.length>0);
    assert(state.width<=state.viewport+2,'Unexpected horizontal overflow');
    assert(['webgl2-active','no-webgl'].includes(state.mode));
    assert.deepEqual(errors,[]);
    assert.deepEqual(requests,[]);
    console.log('E5_PACKAGED_PREVIEW_BROWSER_PASS',viewport.name,state.mode);
    await context.close();
  }
  const context=await browser.newContext({
    viewport:{width:390,height:844},reducedMotion:'reduce'
  });
  const page=await context.newPage();
  await page.goto(uri,{waitUntil:'load'});
  const reduced=await page.evaluate(()=>({
    mode:document.querySelector('#chapter').dataset.motionState,
    fallback:!document.querySelector('#static-fallback').hidden,
    copy:document.querySelector('main')?.textContent.length
  }));
  assert.equal(reduced.mode,'reduced-static');
  assert.equal(reduced.fallback,true);
  assert(reduced.copy>200);
  await context.close();
  console.log('E5_PACKAGED_PREVIEW_REDUCED_MOTION_PASS');
} finally {
  await browser?.close();
}
