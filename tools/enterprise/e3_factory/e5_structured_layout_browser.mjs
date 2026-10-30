/**
 * E5 real Chromium: three finite compositions × desktop/mobile/reduced-motion.
 * CI site is a deterministic, human-compiled review; no model is run here.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {chromium} from 'playwright';

const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'../../..');
const e5=path.join(root,'target/e5');
const reference=path.join(root,'tools/enterprise/e3_factory/e39h/reference');
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const expected=[
  {name:'gallery',composition:'gallery',palette:'paper',typography:'editorial-serif',
   background:'rgb(246, 240, 231)'},
  {name:'split',composition:'split',palette:'charcoal',typography:'modern-sans',
   background:'rgb(17, 24, 33)'},
  {name:'centered',composition:'centered',palette:'warm-stone',typography:'editorial-serif',
   background:'rgb(21, 19, 16)'},
];
const mobile=[
  {name:'desktop',width:1280,height:800,motion:'no-preference'},
  {name:'mobile',width:390,height:844,motion:'no-preference'},
  {name:'reduced-motion',width:390,height:844,motion:'reduce'},
];

let browser;
try{
  browser=await chromium.launch({headless:true,args:[
    '--enable-webgl','--enable-unsafe-swiftshader','--use-gl=angle',
    '--use-angle=swiftshader'
  ]});
  for(const variant of expected){
    const site=path.join(e5,'layout-'+variant.name);
    const decision=JSON.parse(await fs.readFile(path.join(site,'layout-proposal.json'),'utf8'));
    const manifest=JSON.parse(await fs.readFile(path.join(site,'preview-manifest.json'),'utf8'));
    assert.equal(manifest.artifactClass,'E5_UNTRUSTED_STRUCTURED_LAYOUT_REVIEW_ONLY');
    assert.equal(manifest.ownerApproved,false);
    assert.equal(manifest.deploymentAuthorized,false);
    assert.equal(manifest.siteCodeModelAuthored,false);
    assert.equal(manifest.independentModelRunVerified,false);
    assert.equal(manifest.productionReady,false);
    assert.equal(manifest.runtimeReleaseStatus,'NO_GO');
    assert.deepEqual(manifest.layoutDecision,decision);
    for(const entry of manifest.files){
      const bytes=await fs.readFile(path.join(site,entry.path));
      assert.equal(bytes.length,entry.bytes);
      assert.equal(hash(bytes),entry.sha256);
    }
    for(const original of ['scene.js','styles.css']){
      assert.equal(hash(await fs.readFile(path.join(site,original))),
                   hash(await fs.readFile(path.join(reference,original))));
    }
    const address=pathToFileURL(path.join(site,'index.html')).href;
    for(const viewport of mobile){
      const context=await browser.newContext({
        viewport:{width:viewport.width,height:viewport.height},
        reducedMotion:viewport.motion,
        serviceWorkers:'block'
      });
      const page=await context.newPage();
      const faults=[],remote=[];
      page.on('pageerror',err=>faults.push(err.message));
      page.on('console',msg=>{if(msg.type()==='error')faults.push(msg.text())});
      page.on('request',req=>{
        if(!req.url().startsWith('file:'))remote.push(req.url());
      });
      await page.route('**/*',route=>route.request().url().startsWith('file:')
        ?route.continue():route.abort());
      const response=await page.goto(address,{waitUntil:'load'});
      assert.equal(response?.status(),200);
      await page.waitForFunction(()=>document.querySelector('#chapter')
        ?.dataset.motionState !== 'not-initialized');
      const observed=await page.evaluate(()=>({
        composition:document.body.dataset.e5Composition,
        palette:document.body.dataset.e5Palette,
        typography:document.body.dataset.e5Typography,
        background:getComputedStyle(document.body).backgroundColor,
        headlineFont:getComputedStyle(document.querySelector('h1')).fontFamily,
        heading:document.querySelector('h1')?.textContent?.trim(),
        nav:document.querySelector('nav[aria-label]')!==null,
        skip:document.querySelector('.skip-link')?.getAttribute('href'),
        main:document.querySelectorAll('main').length,
        motion:document.querySelector('#chapter').dataset.motionState,
        fallback:!document.querySelector('#static-fallback').hidden,
        width:document.documentElement.scrollWidth,
        viewport:window.innerWidth,
        chapterColumns:getComputedStyle(document.querySelector('.chapter-inner')).gridTemplateColumns,
        chapterCopyOrder:getComputedStyle(document.querySelector('.chapter-copy')).order,
        chapterViewOrder:getComputedStyle(document.querySelector('.viewport')).order,
        content:document.querySelector('main').textContent.includes(
          'not an AI-generated luxury customer website'),
        cssLoaded:Array.from(document.styleSheets).some(sheet=>
          sheet.href?.endsWith('/e5-layout.css')),
      }));
      assert.equal(observed.composition,variant.composition);
      assert.equal(observed.palette,variant.palette);
      assert.equal(observed.typography,variant.typography);
      assert.equal(observed.background,variant.background);
      assert.equal(observed.heading,'Quiet materials.Purposeful motion.');
      assert.equal(observed.nav,true);
      assert.equal(observed.skip,'#main');
      assert.equal(observed.main,1);
      assert.equal(observed.content,true);
      assert.equal(observed.cssLoaded,true);
      assert(observed.width<=observed.viewport+2,'Horizontal overflow');
      if(variant.typography==='editorial-serif')
        assert(/Georgia/.test(observed.headlineFont),'Serif choice not rendered');
      else
        assert(/Inter/.test(observed.headlineFont),'Modern sans not rendered');
      if(viewport.name==='desktop'&&variant.composition==='gallery'){
        assert.equal(observed.chapterCopyOrder,'2');
        assert.equal(observed.chapterViewOrder,'1');
      }
      if(viewport.motion==='reduce'){
        assert.equal(observed.motion,'reduced-static');
        assert.equal(observed.fallback,true);
      }else{
        assert(['webgl2-active','no-webgl'].includes(observed.motion));
      }
      assert.deepEqual(faults,[]);
      assert.deepEqual(remote,[]);
      console.log('E5_STRUCTURED_LAYOUT_CHROMIUM_PASS',
        variant.name,viewport.name,observed.motion);
      await context.close();
    }
  }
}finally{
  await browser?.close();
}
