/** B4K: independently verify ONLY a B4J-allowlisted source after model proposal.
 * Called only when the separate network-none typed worker accepted a proposal.
 * Never uses model HTML directly or a user-provided file path.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const sha=b=>createHash('sha256').update(b).digest('hex');
const HERE=path.dirname(fileURLToPath(import.meta.url));
const FIX=path.join(HERE,'e39g12b4i','fixtures');
const ROOT=path.resolve(HERE,'../../../target/e3-factory/e39g12b4k');
const caseId=process.argv[2];
if(process.argv.length!==3||!['focus','motion'].includes(caseId))
  throw Error('B4K_ONLY_TWO_FIXED_REVIEWED_CASES');
const folder=path.join(ROOT,'repairs',caseId);
const source=await fs.readFile(path.join(folder,'index.html'),'utf8');
const reference=await fs.readFile(path.join(FIX,caseId+'-positive.html'),'utf8');
const negative=await fs.readFile(path.join(FIX,caseId+'-negative.html'),'utf8');
const policy=JSON.parse(await fs.readFile(path.join(folder,'policy-report.json'),'utf8'));
const validated=reference.replace('B4I — Trusted '+caseId+' positive control',
                                  'B4I — Trusted '+caseId+' negative control');
assert.notEqual(validated,reference,'B4K_UNREVIEWED_REFERENCE_TITLE');
assert.equal(source,validated,'B4K_EXACT_REVIEWED_HTML_ONLY');
assert.notEqual(source,negative,'B4K_NO_BROKEN_CONTROL_CREDIT');
assert.equal(policy.policyOutcome,'POLICY_APPLIED');
assert.equal(policy.sourceBeforeSha256,sha(negative));
assert.equal(policy.sourceAfterSha256,sha(source));
assert.equal(policy.modelTextExecutedAsScript,false);
assert.equal(policy.ownerApprovalGranted,false);
assert.equal(policy.stage45Go,false);
const out={
  evidenceClass:'E39G12B4K_MODEL_PROPOSAL_FIXED_SITE_REAL_BROWSER',
  case:caseId,
  checkedReviewedHtmlSha256:sha(source),
  modelProvenanceCryptographicallyAttested:false,
  noArbitraryModelHtmlRendered:true,
  independentHeldOut:false,
  productionAuthorized:false,
  stage45Go:false,verifiedViewports:0,frames:[],error:null,
};
let browser;
try{
  browser=await chromium.launch({headless:true});
  for(const v of [{name:'mobile',width:390,height:844},{name:'desktop',width:1280,height:800}]){
    const context=await browser.newContext({viewport:{width:v.width,height:v.height},
      reducedMotion:'reduce',javaScriptEnabled:false,serviceWorkers:'block'});
    let requests=0;
    await context.route('**/*',route=>{requests++;return route.abort()});
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(String(e.message).slice(0,120)));
    try{
      await page.goto('about:blank');
      await page.setContent(source,{waitUntil:'load',timeout:12000});
      if(caseId==='focus')await page.keyboard.press('Tab');
      const state=await page.evaluate(kind=>{
        const overflow=document.documentElement.scrollWidth>innerWidth+1;
        if(kind==='focus'){
          const el=document.activeElement;
          const rect=el?.getBoundingClientRect();
          return {passed:Boolean(el?.classList.contains('skip')&&rect&&rect.top>=0&&
             rect.bottom<=innerHeight&&rect.left>=0&&rect.right<=innerWidth),overflow};
        }
        const ticker=document.querySelector('#sample-ticker');
        return {passed:Boolean(ticker&&
          matchMedia('(prefers-reduced-motion:reduce)').matches&&
          getComputedStyle(ticker).animationName==='none'),overflow};
      },caseId);
      assert.equal(state.passed,true,'B4K_BROWSER_AFTER_STATE_FAILED');
      assert.equal(state.overflow,false,'B4K_BROWSER_HORIZONTAL_OVERFLOW');
      assert.equal(requests,0,'B4K_EXTERNAL_NETWORK_REQUEST');
      assert.deepEqual(errors,[],'B4K_UNEXPECTED_SCRIPT_ERROR');
      const screenshot=await page.screenshot({animations:'disabled'});
      await fs.writeFile(path.join(folder,v.name+'-browser.png'),screenshot,{flag:'wx'});
      out.frames.push({viewport:v.name,width:v.width,height:v.height,
        sourceSha256:sha(source),screenshotSha256:sha(screenshot),
        realChromePassed:true,externalRequests:requests});
      out.verifiedViewports++;
    }finally{await context.close();}
  }
  assert.equal(out.verifiedViewports,2);
  console.log('B4K_MODEL_PROPOSAL_REAL_BROWSER_AFTER_STATE',caseId,'2_OF_2');
}catch(e){
  out.error=String(e?.stack||e).slice(0,2000);
  throw e;
}finally{
  await fs.writeFile(path.join(folder,'browser-report.json'),JSON.stringify(out,null,2)+'\n');
  if(browser)await browser.close();
}
