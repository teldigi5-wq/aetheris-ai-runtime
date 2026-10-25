/** B4J trusted-only policy-applied control, real browser after-state.
 * Reads two FIXED output paths and compares their entire bytes with the
 * independent reviewed positive controls BEFORE rendering. No arbitrary
 * curator/model/customer HTML, JavaScript, network, source mutation or tools.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

if(process.argv.length!==2) throw Error('B4J_NO_PATH_OR_MODEL_ARGS');
const HERE=path.dirname(fileURLToPath(import.meta.url));
const FIX=path.join(HERE,'e39g12b4i','fixtures');
const OUT=path.resolve(HERE,'../../../target/e3-factory/e39g12b4j');
const CASES=['focus','motion'];
const VIEWS=[{name:'mobile',width:390,height:844},{name:'desktop',width:1280,height:800}];
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const report={
  evidenceClass:'E39G12B4J_REVIEWED_LITERAL_NETWORK_NONE_WORKER_AND_REAL_CHROMIUM_CONTROL',
  fixedTrustedSitesOnly:true,
  actualModelCalls:0,
  modelAuthoredPatch:false,
  genuineHeldoutCorpus:false,
  untrustedModelHtmlRendered:false,
  independentCuratorVerified:false,
  browserVerifiedModelRepairs:0,
  productionAuthorized:false,
  stage45Go:false,
  cases:[],
  error:null,
};
let browser;
try {
  browser=await chromium.launch({headless:true});
  for(const caseId of CASES){
    const negative=await fs.readFile(path.join(FIX,caseId+'-negative.html'),'utf8');
    const reference=await fs.readFile(path.join(FIX,caseId+'-positive.html'),'utf8');
    const candidate=await fs.readFile(path.join(OUT,'repairs',caseId,'index.html'),'utf8');
    const worker=JSON.parse(await fs.readFile(path.join(OUT,'repairs',caseId,'report.json'),'utf8'));
    // Normalize ONLY the fixture title variation. Complete bytewise equality
    // guarantees browser sees exactly the code-reviewed positive fixture.
    const reviewed=reference.replace(
      'B4I — Trusted '+caseId+' positive control',
      'B4I — Trusted '+caseId+' negative control');
    assert.notEqual(reference,reviewed,'B4J_REFERENCE_TITLE_NOT_REVIEWED');
    assert.equal(candidate,reviewed,'B4J_UNAPPROVED_BROWSER_BYTES');
    assert.notEqual(candidate,negative,'B4J_PATCH_DID_NOT_CHANGE_SOURCE');
    assert.equal(worker.sourceBeforeSha256,sha(negative));
    assert.equal(worker.sourceAfterSha256,sha(candidate));
    assert.equal(worker.case,caseId);
    assert.equal(worker.reviewedReferenceMatches,true);
    assert.equal(worker.ownerApprovalGranted,false);
    assert.equal(worker.stage45Go,false);
    assert.equal(worker.modelCallPerformed,false);
    for(const vp of VIEWS){
      const ctx=await browser.newContext({
        viewport:{width:vp.width,height:vp.height},reducedMotion:'reduce',
        javaScriptEnabled:false,serviceWorkers:'block',
      });
      let requests=0;
      await ctx.route('**/*',route=>{requests++;return route.abort()});
      const page=await ctx.newPage();
      const errors=[];
      page.on('pageerror',e=>errors.push(e.message.slice(0,120)));
      try{
        await page.goto('about:blank');
        await page.setContent(candidate,{waitUntil:'load',timeout:15000});
        if(caseId==='focus') await page.keyboard.press('Tab');
        const browserState=await page.evaluate(kind=>{
          const overflow=document.documentElement.scrollWidth>innerWidth+1;
          if(kind==='focus'){
            const el=document.activeElement;
            const r=el?.getBoundingClientRect();
            return {passed:Boolean(el?.classList.contains('skip')&&r&&r.top>=0&&
              r.bottom<=innerHeight&&r.left>=0&&r.right<=innerWidth),overflow};
          }
          const ticker=document.querySelector('#sample-ticker');
          return {passed:Boolean(
            matchMedia('(prefers-reduced-motion:reduce)').matches&&
            ticker&&getComputedStyle(ticker).animationName==='none'),overflow};
        },caseId);
        assert.equal(browserState.passed,true,'B4J_REVIEWED_PATCH_FAILED_BROWSER');
        assert.equal(browserState.overflow,false,'B4J_BROWSER_HORIZONTAL_OVERFLOW');
        assert.equal(requests,0,'B4J_NETWORK_REQUEST_BLOCKED_BUT_FORBIDDEN');
        assert.deepEqual(errors,[],'B4J_BROWSER_JS_ERROR');
        const shot=await page.screenshot({animations:'disabled'});
        const png=caseId+'-'+vp.name+'-reviewed-control.png';
        await fs.writeFile(path.join(OUT,png),shot,{flag:'wx'});
        report.cases.push({
          case:caseId,viewport:vp.name,
          workerAfterSourceSha256:sha(candidate),
          screenshotSha256:sha(shot),trustedControlAfterPassed:true,
          externalRequestCount:requests,modelPatchCredited:false,
        });
      } finally {await ctx.close();}
    }
  }
  assert.equal(report.cases.length,4);
  console.log('B4J_NETWORK_NONE_PATCH_THEN_TRUSTED_CHROMIUM',
    JSON.stringify({browserControls:4,modelRepairs:0,releaseAuthorized:false}));
} catch(e){
  report.error=String(e?.stack||e).slice(0,2500);
  throw e;
} finally {
  await fs.mkdir(OUT,{recursive:true});
  await fs.writeFile(path.join(OUT,'browser-report.json'),JSON.stringify(report,null,2)+'\n');
  if(browser)await browser.close();
}
