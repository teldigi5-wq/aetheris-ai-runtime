/* Actual local Chromium verification of provenance lab and GLB download; no WebGL rendering claimed. */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const here=dirname(fileURLToPath(import.meta.url));
const origin='http://127.0.0.1:19146';
const server=spawn('python3',['-m','http.server','19146','--bind','127.0.0.1',
  '--directory',join(here,'target/e6-w3a-arcloom')],{stdio:'ignore'});
let browser;
try{
  let ready=false;
  for(let i=0;i<60;i++){
    if(server.exitCode!==null)throw Error('PROVENANCE_PREVIEW_SERVER_STOPPED');
    try{if((await fetch(origin+'/asset-lab/',{signal:AbortSignal.timeout(1000)})).ok){ready=true;break;}}catch{}
    await delay(100);
  }
  assert(ready,'Provenance preview server unavailable');
  browser=await chromium.launch({headless:true});
  for(const config of [{name:'desktop',width:1440,height:900},{name:'mobile',width:390,height:844}]){
    const ctx=await browser.newContext({viewport:{width:config.width,height:config.height},
      javaScriptEnabled:false,serviceWorkers:'block'});
    const page=await ctx.newPage(),external=[],errors=[];
    try{
      page.on('pageerror',e=>errors.push(String(e)));
      await page.route('**/*',r=>{
        if(r.request().url().startsWith(origin+'/'))return r.continue();
        external.push(r.request().url());return r.abort();
      });
      const archive=await page.goto(origin+'/studies/',{waitUntil:'load'});
      assert.equal(archive?.status(),200);
      assert.equal(await page.locator('a[href="/asset-lab/"]').count(),1);
      await page.locator('a[href="/asset-lab/"]').click();
      assert.equal(new URL(page.url()).pathname,'/asset-lab/');
      assert.match(await page.locator('h1').innerText(),/Provenance before presentation/);
      assert.match(await page.locator('main').innerText(),/No GPU scene is/);
      assert.equal(await page.locator('canvas').count(),0);
      const manifestRes=await fetch(origin+'/assets/w3a-provenance.json');
      assert.equal(manifestRes.status,200);
      const manifest=await manifestRes.json();
      assert.equal(manifest.releaseGo,false);
      assert.equal(manifest.stage45Go,false);
      assert.equal(manifest.renderedInW2Pages,false);
      const glbRes=await fetch(origin+manifest.asset);
      assert.equal(glbRes.status,200);
      const bytes=Buffer.from(await glbRes.arrayBuffer());
      assert.equal(createHash('sha256').update(bytes).digest('hex'),manifest.stagedGlbSha256);
      assert.equal(bytes.length,manifest.stagedBytes);
      const over=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
      assert(over<=2,'Horizontal overflow in provenance lab');
      assert.deepEqual(external,[]);
      assert.deepEqual(errors,[]);
      console.log('E6_W3A_CHROMIUM_PROVENANCE_PASS '+JSON.stringify({
        profile:config.name,assetBytes:bytes.length,glbSha256:manifest.stagedGlbSha256,
        provenanceValid:true,rendered3D:false,releaseGo:false,external:0,errors:0
      }));
    }finally{await ctx.close();}
  }
}finally{
  if(browser)await browser.close();
  if(server.exitCode===null&&server.signalCode===null){
    server.kill('SIGTERM');await once(server,'exit');
  }
}
