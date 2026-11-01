/* W5-C: REAL Chromium verifies three HUMAN-AUTHORED CSS test doubles.
 * The compiler proves bounded source execution, not AI originality or quality.
 */
import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import {compileAuthoredSite} from './w5c_restricted_css_compiler.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const batch=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
const css=await readFile(join(here,'w5a_base.css'),'utf8');
const folder=join(here,'target/e6-w5c-human-css-test-doubles');
const sha=x=>createHash('sha256').update(x).digest('hex');
const servers=[];let browser;
async function ready(proc,origin){
  for(let i=0;i<80;i++){
    if(proc.exitCode!==null)throw Error('W5C_TEST_SERVER_FAILED');
    try{if((await fetch(origin+'/',{signal:AbortSignal.timeout(1000)})).ok)return;}catch{}
    await delay(100);
  }
  throw Error('W5C_TEST_SERVER_TIMEOUT');
}
try{
  await mkdir(folder,{recursive:false});
  for(let i=0;i<3;i++){
    const b=batch.briefs[i];
    const site=compileAuthoredSite(b,syntheticDesign(b,i),EXAMPLE_CODES[i],css);
    const root=join(folder,String(i));await mkdir(root);
    for(const [path,body] of site.files){
      const file=join(root,path);
      await mkdir(dirname(file),{recursive:true});
      await writeFile(file,body,{flag:'wx'});
    }
  }
  browser=await chromium.launch({headless:true});
  const hashes=[];
  for(let i=0;i<3;i++){
    const port=19211+i,origin='http://127.0.0.1:'+port;
    const proc=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1',
      '--directory',join(folder,String(i))],{stdio:'ignore'});
    servers.push(proc);await ready(proc,origin);
    for(const [label,w,h]of [
      ['compact',320,700],['mobile',390,844],['desktop',1440,900]]){
      const ctx=await browser.newContext({viewport:{width:w,height:h},
        javaScriptEnabled:false,reducedMotion:'reduce',serviceWorkers:'block'});
      const page=await ctx.newPage(),remote=[],exceptions=[];
      try{
        await page.route('**/*',route=>{
          const u=route.request().url();
          if(!u.startsWith(origin+'/')){remote.push(u);return route.abort();}
          return route.continue();
        });
        page.on('pageerror',e=>exceptions.push(String(e)));
        assert.equal((await page.goto(origin+'/',{waitUntil:'load'}))?.status(),200);
        const cs=await page.request.get(origin+'/assets/site.css');
        assert.equal(cs.status(),200);
        const compiled=await cs.text();
        assert(compiled.includes('validated model-source CSS'));
        assert(compiled.includes('max-width')||compiled.includes('transform'));
        assert.equal(await page.locator('h1').count(),1);
        assert.equal(await page.locator('script').count(),0);
        assert.equal(await page.locator('section.section').count(),2);
        assert.equal(await page.locator('a[href="/approach/"]').count(),1);
        assert.equal(await page.evaluate(()=>getComputedStyle(document.body).margin),'0px');
        if(i===0)assert.equal(await page.locator('.hero__figure').evaluate(
          el=>getComputedStyle(el).borderTopLeftRadius),'18px');
        if(i===1)assert.equal(await page.locator('.eyebrow').first().evaluate(
          el=>getComputedStyle(el).fontWeight),'800');
        if(i===2)assert.equal(await page.locator('.hero__figure').evaluate(
          el=>getComputedStyle(el).borderTopLeftRadius),'4px');
        const overflow=await page.evaluate(()=>
          Math.max(0,document.documentElement.scrollWidth-innerWidth));
        assert(overflow<=2,'W5C responsive overflow '+i+'/'+label+' '+overflow);
        if(label==='desktop'){
          const shot=await page.screenshot({animations:'disabled',fullPage:false});
          assert(shot.length>3000);
          hashes.push(sha(shot));
        }
        assert.equal((await page.goto(origin+'/approach/',{waitUntil:'load'}))?.status(),200);
        assert.equal(await page.locator('a[aria-current="page"]').count(),1);
        assert.deepEqual(remote,[]);
        assert.deepEqual(exceptions,[]);
        console.log('E6_W5C_CSS_SOURCE_CHROMIUM_PASS '+JSON.stringify({
          brief:i,viewport:label,actualModelInvoked:false,
          restrictedCssExecuted:true,scriptRequests:0,releaseGo:false
        }));
      }finally{await ctx.close();}
    }
  }
  assert.equal(new Set(hashes).size,3,'Visual differences expected from mock design and code choices');
  console.log('E6_W5C_THREE_DIFFERENT_HUMAN_CODE_SCREENSHOTS_PASS');
}finally{
  if(browser)await browser.close();
  for(const proc of servers)if(proc.exitCode===null&&proc.signalCode===null){
    proc.kill('SIGTERM');await once(proc,'exit');
  }
}
