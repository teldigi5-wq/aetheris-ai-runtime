/** Three explicit human-authored TEST DOUBLE proposals. No model execution. */
import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import {verifyHeldoutCollection,evaluateProposal} from './w5b_evaluation_contract.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const batch=JSON.parse(await readFile(join(here,'w5b_heldout_briefs.json'),'utf8'));
const briefs=verifyHeldoutCollection(batch);
const css=await readFile(join(here,'w5a_base.css'),'utf8');
const base=join(here,'target/e6-w5b-simulated-browser-only');
const server=spawn('python3',['-m','http.server','19198','--bind','127.0.0.1',
 '--directory',base],{stdio:'ignore'});
const origin='http://127.0.0.1:19198';
let browser;
async function ready(){
 for(let i=0;i<75;i++){
   if(server.exitCode!==null)throw Error('E6_W5B_MOCK_BROWSER_SERVER_EXIT');
   try{if((await fetch(origin+'/0/',{signal:AbortSignal.timeout(1000)})).ok)return;}catch{}
   await delay(100);
 }
 throw Error('E6_W5B_MOCK_BROWSER_SERVER_UNAVAILABLE');
}
try{
  await mkdir(base,{recursive:false});
  for(let i=0;i<briefs.length;i++){
    const result=evaluateProposal(briefs[i],syntheticDesign(briefs[i],i),css);
    const root=join(base,String(i));
    await mkdir(root);
    for(const [path,body] of result.built.files){
      const file=join(root,path);
      await mkdir(dirname(file),{recursive:true});
      await writeFile(file,body,{flag:'wx'});
    }
  }
  await ready();
  browser=await chromium.launch({headless:true});
  for(let index=0;index<3;index++){
    for(const [label,width,height] of [
      ['compact',320,700],['mobile',390,844],['desktop',1440,900]]){
      const ctx=await browser.newContext({
        viewport:{width,height},javaScriptEnabled:false,serviceWorkers:'block'});
      const page=await ctx.newPage(),external=[],exceptions=[];
      try{
        page.on('pageerror',e=>exceptions.push(String(e)));
        await page.route('**/*',route=>{
          const url=route.request().url();
          if(!url.startsWith(origin+'/')){external.push(url);return route.abort();}
          return route.continue();
        });
        assert.equal((await page.goto(origin+'/'+index+'/',{waitUntil:'load'}))?.status(),200);
        assert.equal(await page.locator('h1').count(),1);
        assert.equal(await page.locator('main').count(),1);
        assert.equal(await page.locator('section.section').count(),2);
        assert.equal(await page.locator('script').count(),0);
        assert.equal(await page.locator('a[href="/approach/"]').count(),1);
        // Navigate directly because Python's static server is hosted under a test prefix.
        assert.equal((await page.goto(origin+'/'+index+'/approach/',{waitUntil:'load'}))?.status(),200);
        assert.equal(await page.locator('a[aria-current="page"]').count(),1);
        const overflow=await page.evaluate(()=>Math.max(0,document.documentElement.scrollWidth-innerWidth));
        assert(overflow<=2,'Simulated W5-B overflow: '+index+'/'+label+' '+overflow);
        assert.deepEqual(external,[]);
        assert.deepEqual(exceptions,[]);
        console.log('E6_W5B_HUMAN_TEST_DOUBLE_CHROMIUM_PASS '+JSON.stringify({
          brief:index,viewport:label,modelInvoked:false,
          originalModelAuthorshipVerified:false,stage45Go:false
        }));
      }finally{await ctx.close();}
    }
  }
}finally{
  if(browser)await browser.close();
  if(server.exitCode===null&&server.signalCode===null){
    server.kill('SIGTERM');await once(server,'exit');
  }
}
