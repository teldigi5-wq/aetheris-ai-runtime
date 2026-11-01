/* Actual Chromium proof for two independently generated static design directions.
 * Scope: native DOM/network/keyboard/route/form observations, not real owner GPU or WCAG certification.
 */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const here=dirname(fileURLToPath(import.meta.url));
const sites=[
  {id:'morrowline',routes:['/','/work/','/contact/'],gallery:'/work/',preset:'mineral-editorial'},
  {id:'arcloom',routes:['/','/studies/','/approach/','/contact/'],gallery:'/studies/',preset:'ink-monograph'}
];
const configurations=[
  {label:'desktop',w:1440,h:900,motion:'no-preference'},
  {label:'mobile',w:390,h:844,motion:'no-preference'},
  {label:'reduced-motion-mobile',w:390,h:844,motion:'reduce'}
];
let browser;
async function checkSite(site,config) {
  const port=site.id==='morrowline'?19107:19108;
  const origin='http://127.0.0.1:'+port;
  const server=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1',
    '--directory',join(here,'target/e6-w2b-'+site.id)],{stdio:'ignore'});
  let ctx;
  try{
    let ready=false;
    for(let i=0;i<60;i++){
      if(server.exitCode!==null)throw Error('LOCAL_SERVER_EXITED');
      try{if((await fetch(origin+'/',{signal:AbortSignal.timeout(1000)})).ok){ready=true;break;}}catch{}
      await delay(120);
    }
    assert(ready,'Local generated-site server not ready');
    ctx=await browser.newContext({viewport:{width:config.w,height:config.h},reducedMotion:config.motion,serviceWorkers:'block'});
    const page=await ctx.newPage(),external=[],errors=[];
    page.on('pageerror',error=>errors.push(String(error)));
    page.on('console',msg=>{if(msg.type()==='error')errors.push(msg.text())});
    await page.route('**/*',route=>{
      if(route.request().url().startsWith(origin+'/'))return route.continue();
      external.push(route.request().url());return route.abort();
    });
    const res=await page.goto(origin+'/',{waitUntil:'load'});
    assert.equal(res?.status(),200);
    assert.equal(await page.locator('html').getAttribute('data-art-direction'),site.preset);
    assert.equal(await page.locator('main').count(),1);
    assert.equal(await page.locator('h1').count(),1);
    assert.equal(await page.locator('nav[aria-label="Primary navigation"] a').count(),site.routes.length);
    const actual=await page.evaluate(()=>({
      background:getComputedStyle(document.body).backgroundColor,
      overflow:document.documentElement.scrollWidth-innerWidth,
      reduced:matchMedia('(prefers-reduced-motion: reduce)').matches,
      canvas:document.querySelectorAll('canvas').length,
      assets:document.querySelectorAll('script[src],link[rel="stylesheet"]').length
    }));
    assert(actual.overflow<=2,'Horizontal overflow');
    assert.equal(actual.canvas,0,'WebGL must remain absent in W2-B');
    assert.equal(actual.assets,2,'Only local CSS and JS assets');
    assert.equal(actual.reduced,config.motion==='reduce');
    for(const pathname of site.routes){
      await page.goto(origin+pathname,{waitUntil:'load'});
      assert.equal(await page.locator('main').count(),1,pathname);
      assert.equal(await page.locator('a[aria-current="page"]').count(),1,pathname);
    }
    await page.goto(origin+site.gallery,{waitUntil:'load'});
    const cards=await page.locator('.filter-grid .project-card').count();
    assert(cards>=3,'Missing real content cards');
    await page.locator('[data-filter="identity"]').focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('[data-filter="identity"]').getAttribute('aria-pressed'),'true');
    const visible=await page.locator('.filter-grid .project-card:visible').count();
    assert(visible>0&&visible<cards,'Keyboard filtering failed');
    assert.match(await page.locator('[data-filter-status]').textContent(),/projects visible/);
    await page.locator('[data-filter="all"]').click();
    assert.equal(await page.locator('.filter-grid .project-card:visible').count(),cards);
    await page.goto(origin+'/contact/',{waitUntil:'load'});
    assert.equal(await page.locator('form[data-email-compose]').count(),1);
    assert.equal(await page.locator('form').evaluate(e=>e.checkValidity()),false);
    await page.locator('[name="name"]').fill('Example Visitor');
    await page.locator('[name="email"]').fill('visitor@example.com');
    await page.locator('[name="message"]').fill('Please prepare an email draft for this preview only.');
    assert.equal(await page.locator('form').evaluate(e=>e.checkValidity()),true);
    assert.match(await page.locator('.form-note').innerText(),/does not submit or store/);
    assert.deepEqual(external,[],'Remote network requests forbidden');
    assert.deepEqual(errors,[],'No browser exceptions accepted');
    console.log('E6_W2B_CHROMIUM_PASS '+JSON.stringify({
      site:site.id,profile:config.label,routes:site.routes.length,preset:site.preset,
      bodyBackground:actual.background,keyboardFiltering:true,formValidation:true,
      external:external.length,errors:errors.length
    }));
  }finally{
    if(ctx)await ctx.close();
    server.kill('SIGTERM');
  }
}
try {
  browser=await chromium.launch({headless:true});
  for(const site of sites)for(const config of configurations)await checkSite(site,config);
}finally{
  if(browser)await browser.close();
}
