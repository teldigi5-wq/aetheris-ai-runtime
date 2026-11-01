/* E6 W5-A browser integration using explicitly SYNTHETIC test doubles.
 * Never count this as a real Ollama model run or independent design review.
 */
import assert from 'node:assert/strict';
import {readFile,mkdir,writeFile} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as sleep} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import {compileModelProposedSite} from './w5a_model_design_compiler.mjs';
import {briefs,proposalFor} from './w5a_test_doubles.mjs';
const here=dirname(fileURLToPath(import.meta.url));
const target=join(here,'target');
const css=await readFile(join(here,'w5a_base.css'),'utf8');
const servers=[];
let browser;
async function serve(path,port){
  const p=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1',
    '--directory',path],{stdio:'ignore'});
  servers.push(p);
  const base='http://127.0.0.1:'+port;
  for(let i=0;i<55;i++){
    if(p.exitCode!==null)throw Error('E6_W5A_SIMULATED_LOCAL_SERVER_FAILED');
    try{if((await fetch(base+'/',{signal:AbortSignal.timeout(1000)})).ok)return base;}catch{}
    await sleep(100);
  }
  throw Error('E6_W5A_SIMULATED_LOCAL_SERVER_TIMEOUT');
}
try{
  browser=await chromium.launch({headless:true});
  for(let i=0;i<briefs.length;i++){
    const site=compileModelProposedSite(briefs[i],proposalFor(briefs[i]),css);
    const output=join(target,'e6-w5a-simulated-test-double-'+i);
    await mkdir(output,{recursive:false});
    for(const [path,content]of site.files){
      const {dirname:directory}=await import('node:path');
      const file=join(output,path);
      await mkdir(directory(file),{recursive:true});
      await writeFile(file,content,{flag:'wx'});
    }
    const origin=await serve(output,19191+i);
    for(const [name,width,height]of [['compact',320,700],['mobile',390,844],['desktop',1440,900]]){
      const ctx=await browser.newContext({
        viewport:{width,height},javaScriptEnabled:false,serviceWorkers:'block'
      });
      const page=await ctx.newPage(),external=[],exceptions=[];
      try{
        await page.route('**/*',route=>{
          if(!route.request().url().startsWith(origin+'/')){
            external.push(route.request().url());return route.abort();
          }
          return route.continue();
        });
        page.on('pageerror',error=>exceptions.push(String(error)));
        const res=await page.goto(origin+'/',{waitUntil:'load'});
        assert.equal(res?.status(),200);
        assert.equal(await page.locator('h1').count(),1);
        assert.equal(await page.locator('.section').count(),2);
        assert.equal(await page.locator('.hero__figure').count(),1);
        assert.equal(await page.locator('script').count(),0);
        assert.equal(await page.locator('a[href="/approach/"]').count(),1);
        await page.locator('a[href="/approach/"]').click();
        assert.equal(new URL(page.url()).pathname,'/approach/');
        assert.equal(await page.locator('h1').count(),1);
        assert.equal(await page.locator('a[aria-current="page"]').count(),1);
        const widthOverflow=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
        assert(widthOverflow<=2,'W5-A synthetic overflow '+i+'/'+name+': '+widthOverflow);
        assert.deepEqual(external,[]);
        assert.deepEqual(exceptions,[]);
        console.log('E6_W5A_SIMULATED_BROWSER_PASS '+JSON.stringify({
          site:i,profile:name,modelCalled:false,mockedProvider:true,
          realModelAuthorshipVerified:false,productionReady:false,releaseGo:false
        }));
      }finally{await ctx.close();}
    }
  }
}finally{
  if(browser)await browser.close();
  for(const p of servers){
    if(p.exitCode===null&&p.signalCode===null){
      p.kill('SIGTERM');await once(p,'exit');
    }
  }
}
