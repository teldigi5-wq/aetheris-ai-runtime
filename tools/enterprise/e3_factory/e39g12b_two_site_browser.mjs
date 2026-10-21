/** E3.9G-12B1 independent real Chromium on TWO different public site fixtures.
 * Runs broken-vs-trusted-reference, 390x844 and 1280x800. No AI patches,
 * golden fix text in browser, remote sites, or model tools. Only observation.
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import {spawn} from 'node:child_process';
import {setTimeout as pause} from 'node:timers/promises';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {chromium} from 'playwright';

const scriptDir=path.dirname(fileURLToPath(import.meta.url));
const repo=path.resolve(scriptDir,'../../..');
const root=path.join(repo,'target/e3-factory/e39g12b');
const location=path.join(root,'sites');
const manifest=JSON.parse(await fs.readFile(path.join(root,'corpus-manifest.json'),'utf8'));
assert.equal(manifest.evidenceClass,'E39G12B_TWO_DISTINCT_PUBLIC_SYNTHETIC_WEBSITES');
assert.equal(manifest.noModelPromptsOrCalls,true);
assert.equal(manifest.externallyCurated,false);
assert.deepEqual(Object.keys(manifest.sites).sort(),['cityline','studynest']);
const port=18797;
const base='http://127.0.0.1:'+port;
const server=spawn('python3',['-m','http.server',String(port),'--bind','127.0.0.1','--directory',location],
 {stdio:['ignore','pipe','pipe']});
function sha(data){return createHash('sha256').update(data).digest('hex');}
async function ready(){
  for(let i=0;i<45;i++){
    if(server.exitCode!==null)throw Error('E39G12B_TEST_SERVER_EARLY_EXIT');
    try{
      const r=await fetch(base+'/studynest/broken/index.html',{signal:AbortSignal.timeout(2000)});
      if(r.ok)return;
    }catch{}
    await pause(170);
  }
  throw Error('E39G12B_TEST_SERVER_NOT_READY');
}
let browser;
const trials=[];
try{
  await ready();
  browser=await chromium.launch({headless:true});
  for(const site of ['studynest','cityline']){
    for(const variant of ['broken','reference']){
      for(const [viewport,width,height] of [['mobile',390,844],['desktop',1280,800]]){
        const dir=path.join(root,'evidence',site,variant);
        await fs.mkdir(dir,{recursive:true});
        const source=await fs.readFile(path.join(location,site,variant,'index.html'));
        assert.equal(sha(source),manifest.sites[site].variants[variant].sourceSha256);
        const context=await browser.newContext({viewport:{width,height},reducedMotion:'reduce'});
        const page=await context.newPage();
        const errors=[];
        page.on('pageerror',e=>errors.push(String(e.message).slice(0,170)));
        const payload='<b data-e39g12b-literal="true">USER_INPUT</b>';
        const url=base+'/'+site+'/'+variant+'/index.html'+
          (site==='cityline'?'?q='+encodeURIComponent(payload):'');
        try{
          const response=await page.goto(url,{waitUntil:'load',timeout:15000});
          assert.equal(response?.status(),200);
          const all=site==='studynest'
            ? await evaluateStudy(page,viewport)
            : await evaluateCity(page,payload);
          const img=await page.screenshot({path:path.join(dir,viewport+'.png'),fullPage:false,animations:'disabled'});
          const checks=Object.values(all);
          assert.ok(checks.length===2 && checks.every(x=>typeof x==='boolean'),
            'E39G12B_EXPECT_TWO_TYPED_CONTRACTS');
          const item={
            evidenceClass:'E39G12B_INDEPENDENT_REAL_CHROMIUM_TWO_SITE_CONTRACT',
            site,variant,viewport,width,height,
            contracts:all,allPassed:checks.every(Boolean),pageJavaScriptErrors:errors,
            sourceSha256:sha(source),screenshotSha256:sha(img),
            websiteIndependentFromOtherFixture:true,
            referenceContainsGoldenBytesButNotModelInput:variant==='reference',
            realBrowserRuns:1,modelCalls:0,modelPatchApplied:false,
            modelBrowserRepairCredited:false,externallyCurated:false,
            ownerPcUsed:false,ownerApprovalGranted:false,productionPublished:false
          };
          await fs.writeFile(path.join(dir,viewport+'.json'),JSON.stringify(item,null,2)+'\n',{flag:'wx'});
          trials.push(item);
          console.log('E39G12B_REAL_CHROMIUM_SITE_CHECK',site,variant,viewport,JSON.stringify(all));
          // Before faults MUST fail, positive control MUST pass all.
          const expected=variant==='reference';
          if(item.allPassed!==expected){
            throw Error('E39G12B_WRONG_BROWSER_CONTROL_VERDICT_'+site+'_'+variant+'_'+viewport);
          }
          if(errors.length)throw Error('E39G12B_PAGE_SCRIPT_ERROR_'+site+'_'+variant);
        }finally{await context.close();}
      }
    }
  }
  if(trials.length!==8)throw Error('E39G12B_MISSING_BROWSER_TRIALS');
  // Study desktop has no overflow even when its keyboard defect is broken.
  assert.equal(trials.find(x=>x.site==='studynest'&&x.variant==='broken'&&x.viewport==='desktop')
    .contracts.mobileLayoutWithinViewport,true);
  for(const site of ['studynest','cityline']){
    const samples=trials.filter(x=>x.site===site);
    assert.equal(samples.length,4);
    assert.equal(samples.filter(x=>x.variant==='reference'&&x.allPassed).length,2);
    assert.equal(samples.filter(x=>x.variant==='broken'&&!x.allPassed).length,2);
  }
  const summary={
    evidenceClass:'E39G12B_TWO_PUBLIC_DIFFERENT_WEBSITES_BROKEN_VS_REFERENCE_BROWSER',
    sites:2,distinctFaultCategories:4,realChromiumObservations:trials.length,
    brokenVariantsThatFailedExpectedChecks:4,
    referenceVariantsThatPassedExpectedChecks:4,
    modelCalls:0,modelRepairsCredited:0,
    independentExternalCuration:false,
    futureModelEvaluationReadyOnlyAfterFurtherIsolationReview:true,
    stage45Go:false,ownerApprovalGranted:false,productionPublished:false,
    entries:trials.map(x=>({site:x.site,variant:x.variant,viewport:x.viewport,
        contracts:x.contracts,sourceSha256:x.sourceSha256,screenshotSha256:x.screenshotSha256}))
  };
  await fs.writeFile(path.join(root,'two-site-browser-summary.json'),
    JSON.stringify(summary,null,2)+'\n',{flag:'wx'});
  console.log('E39G12B_INDEPENDENT_REAL_CHROMIUM_CORPUS_VERIFIED',JSON.stringify({
    sites:2,brokenObserved:4,controlsPassed:4,modelCalls:0,modelRepairs:0}));
}finally{
  if(browser)await browser.close();
  server.kill('SIGTERM');
}

async function evaluateStudy(page,viewport){
  await page.keyboard.press('Tab');
  return await page.evaluate(viewport=>{
    const focus=document.activeElement;
    const r=focus?.getBoundingClientRect();
    return {
      keyboardSkipFocusVisible:Boolean(focus?.matches('.skip')&&r&&
        r.top>=0&&r.bottom<=innerHeight&&r.left>=0&&r.right<=innerWidth),
      mobileLayoutWithinViewport:document.documentElement.scrollWidth<=innerWidth+1
    };
  },viewport);
}
async function evaluateCity(page,payload){
  return await page.evaluate(payload=>{
    const el=document.querySelector('#service-ticker');
    const computed=getComputedStyle(el);
    const query=document.querySelector('#query-output');
    const found=query?.querySelector('[data-e39g12b-literal]');
    return {
      reducedMotionStopsTicker:matchMedia('(prefers-reduced-motion:reduce)').matches&&
        computed.animationName==='none',
      untrustedStationQueryIsLiteralText:!found&&query?.textContent?.includes(payload)===true
    };
  },payload);
}
