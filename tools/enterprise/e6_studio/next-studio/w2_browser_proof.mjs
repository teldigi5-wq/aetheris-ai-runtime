/* W2 CI browser proof for real generated pages. No inference, GPU, WCAG or owner-device claims. */
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {setTimeout as delay} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';
import {chromium} from 'playwright';

const here=dirname(fileURLToPath(import.meta.url));
const origin='http://127.0.0.1:19094';
const out=join(here,'target/e6-w2-multipage-site');
const server=spawn('python3',['-m','http.server','19094','--bind','127.0.0.1','--directory',out],{stdio:'ignore'});
let browser;
async function ready(){
  for(let i=0;i<70;i++){
    if(server.exitCode!==null)throw Error('W2_SERVER_EXITED');
    try{if((await fetch(origin+'/',{signal:AbortSignal.timeout(1000)})).ok)return;}catch{}
    await delay(120);
  }
  throw Error('W2_SERVER_START_FAILURE');
}
async function runCase(label,width,height,motion){
  const ctx=await browser.newContext({viewport:{width,height},reducedMotion:motion,serviceWorkers:'block'});
  const page=await ctx.newPage(),external=[],errors=[];
  page.on('pageerror',e=>errors.push(String(e)));
  page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
  await page.route('**/*',route=>{
    if(route.request().url().startsWith(origin+'/'))return route.continue();
    external.push(route.request().url());return route.abort();
  });
  try{
    let res=await page.goto(origin+'/',{waitUntil:'load'});
    assert.equal(res?.status(),200);
    assert.equal(await page.locator('main').count(),1);
    assert.equal(await page.locator('h1').count(),1);
    assert.equal(await page.locator('nav[aria-label="Primary navigation"] a').count(),3);
    assert.equal(await page.locator('a.skip').getAttribute('href'),'#main');
    await page.locator('nav a[href="/work/"]').click();
    assert.equal(new URL(page.url()).pathname,'/work/');
    assert.equal(await page.locator('.filter-grid .project-card:visible').count(),6);
    await page.locator('[data-filter="spatial"]').click();
    assert.equal(await page.locator('.filter-grid .project-card:visible').count(),2);
    assert.equal(await page.locator('[data-filter="spatial"]').getAttribute('aria-pressed'),'true');
    await page.locator('[data-filter="all"]').click();
    assert.equal(await page.locator('.filter-grid .project-card:visible').count(),6);
    await page.locator('nav a[href="/contact/"]').click();
    assert.equal(new URL(page.url()).pathname,'/contact/');
    const valid=await page.evaluate(()=>document.querySelector('form[data-email-compose]').checkValidity());
    assert.equal(valid,false,'Empty contact form must be invalid');
    await page.locator('[name="name"]').fill('Demo Visitor');
    await page.locator('[name="email"]').fill('visitor@example.com');
    await page.locator('[name="message"]').fill('This is an intentional demo contact request.');
    assert.equal(await page.evaluate(()=>document.querySelector('form').checkValidity()),true);
    assert.match(await page.locator('.form-note').innerText(),/does not submit or store/);
    const layout=await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,innerWidth,canvas:document.querySelectorAll('canvas').length,summary:document.querySelectorAll('h1').length}));
    assert(layout.scrollWidth<=layout.innerWidth+2,'Unexpected horizontal overflow');
    assert.equal(layout.canvas,0,'W2 plain static pages must not allocate WebGL');
    assert.deepEqual(external,[],'External requests are forbidden in offline demonstration');
    assert.deepEqual(errors,[],'Browser errors are not acceptable');
    console.log('E6_W2_ACTUAL_CHROMIUM_PASS '+JSON.stringify({label,viewport:[width,height],motion,routes:3,filtering:true,formValidation:true,external:external.length,errors:errors.length}));
  }finally{await ctx.close();}
}
try{
  await ready();
  browser=await chromium.launch({headless:true});
  for(const run of [
    ['desktop',1440,900,'no-preference'],
    ['mobile',390,844,'no-preference'],
    ['reduced-mobile',390,844,'reduce']
  ]) await runCase(...run);
}finally{
  if(browser)await browser.close();
  server.kill('SIGTERM');
}
