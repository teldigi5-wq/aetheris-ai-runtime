/**
 * CI real Chromium check of a ZIP-extracted untrusted-copy *review* artifact.
 * The WebGL/animations remain exactly the human-written reference code.
 * No model inference, publication, external URL or owner approval.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import path from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {chromium} from 'playwright';

const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'../../..');
const source=path.join(root,'tools/enterprise/e3_factory/e39h/reference');
const dir=path.join(root,'target/e5/untrusted-copy-site');
const manifest=JSON.parse(await fs.readFile(path.join(dir,'preview-manifest.json'),'utf8'));
const proposal=JSON.parse(await fs.readFile(path.join(dir,'copy-proposal.json'),'utf8'));
assert.equal(manifest.artifactClass,'E5_UNTRUSTED_COPY_REVIEW_ONLY');
assert.equal(manifest.modelAuthored,null);
assert.equal(manifest.ownerApproved,false);
assert.equal(manifest.deploymentAuthorized,false);
assert.equal(manifest.productionReady,false);
assert.equal(manifest.runtimeReleaseStatus,'NO_GO');
assert.equal(manifest.files.length,4);
const hash=data=>crypto.createHash('sha256').update(data).digest('hex');
for(const item of manifest.files){
  const bytes=await fs.readFile(path.join(dir,item.path));
  assert.equal(bytes.length,item.bytes);
  assert.equal(hash(bytes),item.sha256);
}
for(const unchanged of ['scene.js','styles.css']){
  assert.equal(hash(await fs.readFile(path.join(dir,unchanged))),
               hash(await fs.readFile(path.join(source,unchanged))));
}
const pageUrl=pathToFileURL(path.join(dir,'index.html')).href;
let browser;
try {
  browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle',
    '--use-angle=swiftshader'
  ]});
  for(const viewport of [
    {name:'desktop',width:1280,height:800,reduce:'no-preference'},
    {name:'mobile',width:390,height:844,reduce:'no-preference'},
    {name:'reduced-motion',width:390,height:844,reduce:'reduce'}
  ]){
    const context=await browser.newContext({
      viewport:{width:viewport.width,height:viewport.height},
      reducedMotion:viewport.reduce,serviceWorkers:'block'
    });
    const page=await context.newPage();
    const faults=[],nonLocal=[];
    page.on('pageerror',e=>faults.push(e.message));
    page.on('console',e=>{if(e.type()==='error')faults.push(e.text())});
    page.on('request',req=>{if(!req.url().startsWith('file:'))nonLocal.push(req.url())});
    await page.route('**/*',route=>route.request().url().startsWith('file:')
      ?route.continue():route.abort());
    const response=await page.goto(pageUrl,{waitUntil:'load'});
    assert.equal(response?.status(),200);
    await page.waitForFunction(()=>
      document.querySelector('#chapter')?.dataset.motionState!=='not-initialized');
    const state=await page.evaluate(()=>({
      brand:document.querySelector('.brand')?.textContent?.trim(),
      heading:document.querySelector('h1')?.textContent?.trim(),
      main:document.querySelectorAll('main').length,
      motion:document.querySelector('#chapter')?.dataset.motionState,
      fallback:!document.querySelector('#static-fallback')?.hidden,
      width:document.documentElement.scrollWidth,
      viewport:window.innerWidth,
      disclaimer:document.querySelector('main')?.textContent?.includes(
        'not an AI-generated luxury customer website')
    }));
    assert.equal(state.brand,proposal.brandLabel+' / UNAPPROVED DRAFT');
    assert.equal(state.heading,proposal.headlineLines.join(''));
    assert.equal(state.main,1);
    assert.equal(state.disclaimer,true);
    assert(state.width<=state.viewport+2,'Unexpected horizontal overflow');
    if(viewport.reduce==='reduce'){
      assert.equal(state.motion,'reduced-static');
      assert.equal(state.fallback,true);
    } else {
      assert(['webgl2-active','no-webgl'].includes(state.motion));
    }
    assert.deepEqual(nonLocal,[]);
    assert.deepEqual(faults,[]);
    console.log('E5_UNTRUSTED_COPY_EXTRACTED_CHROMIUM_PASS',
      viewport.name,state.motion);
    await context.close();
  }
} finally {
  await browser?.close();
}
