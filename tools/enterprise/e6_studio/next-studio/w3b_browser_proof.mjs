/* W3-B real Chromium check: new original Signal Fold GLB can be fetched
 * only as a user-invoked research download. No WebGL or AI generation claimed.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {setTimeout as delay} from 'node:timers/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

const here=dirname(fileURLToPath(import.meta.url));
const origin='http://127.0.0.1:19158';
const server=spawn('python3',['-m','http.server','19158','--bind','127.0.0.1',
  '--directory',join(here,'target/e6-w3b-arcloom')],{stdio:'ignore'});
let browser;
try {
  let online=false;
  for(let i=0;i<65;i++){
    if(server.exitCode!==null)throw Error('W3B_SERVER_FAILED');
    try{if((await fetch(origin+'/studies/signal-fold/',{signal:AbortSignal.timeout(1000)})).ok){online=true;break;}}catch{}
    await delay(120);
  }
  assert(online,'Brand-specific local preview unavailable');
  browser=await chromium.launch({headless:true});
  for(const setup of [{name:'desktop',width:1440,height:900},{name:'mobile',width:390,height:844}]){
    const ctx=await browser.newContext({viewport:{width:setup.width,height:setup.height},
      javaScriptEnabled:false,serviceWorkers:'block'});
    const page=await ctx.newPage(),remote=[],errors=[];
    try{
      page.on('pageerror',e=>errors.push(String(e)));
      await page.route('**/*',route=>{
        if(route.request().url().startsWith(origin+'/'))return route.continue();
        remote.push(route.request().url());return route.abort();
      });
      const response=await page.goto(origin+'/studies/signal-fold/',{waitUntil:'load'});
      assert.equal(response?.status(),200);
      assert.equal(await page.locator('main[data-w2c-study="signal-fold"]').count(),1);
      assert.equal(await page.locator('section.story-chapter').count(),3);
      assert.equal(await page.locator('a[href="/assets/arcloom-signal-fold.glb"]').count(),1);
      assert.equal(await page.locator('a[href="/assets/w3b-signal-fold-provenance.json"]').count(),1);
      assert.match(await page.locator('.signal-fold-proof').innerText(),/not a manufactured/);
      assert.equal(await page.locator('canvas').count(),0);
      const proofRes=await fetch(origin+'/assets/w3b-signal-fold-provenance.json');
      assert.equal(proofRes.status,200);
      const proof=await proofRes.json();
      assert.equal(proof.kind,'E6_W3B_SOURCE_VERIFIED_SIGNAL_FOLD_ASSET_V1');
      assert.equal(proof.meshCount,7);
      assert.equal(proof.materialCount,5);
      assert.equal(proof.modelGenerated,false);
      assert.equal(proof.releaseGo,false);
      const assetRes=await fetch(origin+'/assets/arcloom-signal-fold.glb');
      assert.equal(assetRes.status,200);
      const bytes=Buffer.from(await assetRes.arrayBuffer());
      assert.equal(bytes.length,proof.glbBytes);
      assert.equal(createHash('sha256').update(bytes).digest('hex'),proof.glbSha256);
      const over=await page.evaluate(()=>document.documentElement.scrollWidth-innerWidth);
      assert(over<=2,'Horizontal overflow on Signal Fold page');
      assert.deepEqual(remote,[]);
      assert.deepEqual(errors,[]);
      console.log('E6_W3B_BRAND_GLTF_CHROMIUM_PASS '+JSON.stringify({
        profile:setup.name,bytes:bytes.length,sha256:proof.glbSha256,
        sourceBound:true,rendered3D:false,noJS:true,external:0,errors:0
      }));
    }finally{await ctx.close();}
  }
}finally{
  if(browser)await browser.close();
  if(server.exitCode===null&&server.signalCode===null){
    server.kill('SIGTERM');await once(server,'exit');
  }
}
