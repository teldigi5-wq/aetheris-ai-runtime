/* Browser acceptance for actual generated W2-C routes; software Chromium, not hardware certification. */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as sleep} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const here=dirname(fileURLToPath(import.meta.url));
const origin='http://127.0.0.1:19135';
const server=spawn('python3',['-m','http.server','19135','--bind','127.0.0.1',
  '--directory',join(here,'target/e6-w2c-arcloom')],{stdio:'ignore'});
let browser;
async function ready(){
  for(let i=0;i<70;i++){
    if(server.exitCode!==null)throw Error('PREVIEW_SERVER_EXIT');
    try{if((await fetch(origin+'/',{signal:AbortSignal.timeout(1000)})).ok)return;}catch{}
    await sleep(120);
  }
  throw Error('PREVIEW_SERVER_UNAVAILABLE');
}
async function inspect(config) {
  const ctx=await browser.newContext({viewport:{width:config.width,height:config.height},
    reducedMotion:config.motion,javaScriptEnabled:config.js,serviceWorkers:'block'});
  const page=await ctx.newPage(),remote=[],errors=[];
  try{
    page.on('pageerror',e=>errors.push(String(e)));
    page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
    await page.route('**/*',r=>{
      if(r.request().url().startsWith(origin+'/'))return r.continue();
      remote.push(r.request().url());return r.abort();
    });
    let res=await page.goto(origin+'/studies/',{waitUntil:'load'});
    assert.equal(res?.status(),200);
    const cards=page.locator('.project-card');
    assert.equal(await cards.count(),6);
    for(const slug of ['signal-fold','readable-measure','sundial-desk'])
      assert.equal(await page.locator('a[href="/studies/'+slug+'/"]').count(),1);
    const links=['/studies/signal-fold/','/studies/readable-measure/','/studies/sundial-desk/'];
    for(const route of links){
      res=await page.goto(origin+route,{waitUntil:'load'});
      assert.equal(res?.status(),200,route);
      assert.equal(await page.locator('main.story-main').count(),1);
      assert.equal(await page.locator('h1').count(),1);
      assert.equal(await page.locator('.story-toc a').count(),3);
      assert.equal(await page.locator('section.story-chapter').count(),3);
      assert.equal((await page.locator('nav[aria-label="Primary navigation"] a[aria-current="page"]').textContent()).trim(),'Studies');
      assert.equal(await page.locator('canvas').count(),0);
      const layout=await page.evaluate(()=>({
        over:document.documentElement.scrollWidth-innerWidth,
        reduce:matchMedia('(prefers-reduced-motion: reduce)').matches,
        challenge:getComputedStyle(document.querySelector('#context')).display,
        visibility:getComputedStyle(document.querySelector('#context')).visibility
      }));
      assert(layout.over<=2,'Unexpected horizontal overflow at '+route);
      assert.equal(layout.reduce,config.motion==='reduce');
      assert.notEqual(layout.challenge,'none');
      assert.notEqual(layout.visibility,'hidden');
    }
    await page.goto(origin+'/studies/signal-fold/',{waitUntil:'load'});
    await page.locator('.story-toc a[href="#verification"]').focus();
    await page.keyboard.press('Enter');
    assert.equal(new URL(page.url()).hash,'#verification');
    assert.equal(await page.locator('#verification').count(),1);
    if(config.js){
      await page.locator('#verification').scrollIntoViewIfNeeded();
      await page.waitForTimeout(100);
      assert.equal(await page.locator('.story-toc a[aria-current="location"]').count(),1);
    }
    await page.locator('.story-back').click();
    assert.equal(new URL(page.url()).pathname,'/studies/');
    assert.deepEqual(remote,[],'Unexpected external network request');
    assert.deepEqual(errors,[],'Browser JS errors');
    console.log('E6_W2C_CHROMIUM_PASS '+JSON.stringify({
      profile:config.name,routes:3,readableWithoutJs:!config.js,
      chapterNavigation:true,realBrowser:true,external:remote.length,errors:errors.length
    }));
  }finally{await ctx.close();}
}
try{
  await ready();
  browser=await chromium.launch({headless:true});
  for(const config of [
    {name:'desktop',width:1440,height:900,motion:'no-preference',js:true},
    {name:'mobile',width:390,height:844,motion:'no-preference',js:true},
    {name:'small-reduced',width:320,height:700,motion:'reduce',js:true},
    {name:'no-javascript',width:390,height:844,motion:'reduce',js:false}
  ])await inspect(config);
}finally{
  if(browser)await browser.close();
  if(server.exitCode===null&&server.signalCode===null){
    server.kill('SIGTERM');await once(server,'exit');
  }
}
