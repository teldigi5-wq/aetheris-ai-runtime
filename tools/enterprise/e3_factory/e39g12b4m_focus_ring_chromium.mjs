/** E3.9G-12B4M: trusted offline Chromium regression for focus-ring clipping.
 * Inspect the real B4L model's exact previously rejected CSS replacement.
 * No fresh model call, no model/curator/customer HTML, no policy changes,
 * no execution of untrusted source, and NO retroactive benchmark credits.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

if (process.argv.length !== 2) throw Error('B4M_NO_ARBITRARY_FILE_ARGS');
const HERE = path.dirname(fileURLToPath(import.meta.url));
const FIX = path.join(HERE, 'e39g12b4i', 'fixtures', 'focus-negative.html');
const OUT = path.resolve(HERE, '../../../target/e3-factory/e39g12b4m-focus-ring');
const OLD = '.skip:focus{top:-120px;}';
const ORIGINAL_SHA = '385d3fac74d775586bb389beb3068e6e34a7fb0e08f8d854c80cb55b433b290b';
const B4L_REAL_MODEL_FOCUS_REPLY_SHA =
  '51d092cbb0181fa010fe656382833ab8762a15d152aec297ac2e8d8fb29645d6';
const VARIANTS = Object.freeze([
  {id:'broken-original',newRule:OLD,expectedLinkVisible:false,expectedRingVisible:false,
    origin:'original-public-fixture',historicallyPolicyApproved:false},
  {id:'model-top-zero',newRule:'.skip:focus{top:0;}',expectedLinkVisible:true,
    expectedRingVisible:false,origin:'archived-b4l-model-suggestion-reconstructed-from-verified-response',
    historicallyPolicyApproved:false},
  {id:'near-miss-top-six',newRule:'.skip:focus{top:6px;}',expectedLinkVisible:true,
    expectedRingVisible:false,origin:'trusted-adversarial-negative-control',
    historicallyPolicyApproved:false},
  {id:'safe-alternate-top-eight',newRule:'.skip:focus{top:8px;}',
    expectedLinkVisible:true,expectedRingVisible:true,
    origin:'trusted-positive-semantic-control-not-approved-by-frozen-gate',
    historicallyPolicyApproved:false},
  {id:'reviewed-top-sixteen',newRule:'.skip:focus{top:16px;}',
    expectedLinkVisible:true,expectedRingVisible:true,
    origin:'previously-reviewed-positive-reference',historicallyPolicyApproved:true},
]);
const VIEWS = Object.freeze([
  {name:'small-mobile',width:320,height:740},
  {name:'mobile',width:390,height:844},
  {name:'tablet',width:768,height:1024},
  {name:'desktop',width:1280,height:800},
  {name:'large-desktop',width:1920,height:1080},
]);
const sha = b => createHash('sha256').update(b).digest('hex');
const report = {
  evidenceClass:'E39G12B4M_FIXED_PUBLIC_FOCUS_RING_SEMANTIC_CONTROLS',
  sourceSha256:ORIGINAL_SHA,
  genuineHistoricalModelReplySha256:B4L_REAL_MODEL_FOCUS_REPLY_SHA,
  reconstructedModelSuggestion:'.skip:focus{top:0;}',
  trustedFixtureOnly:true,
  modelAuthoredHtmlExecuted:false,
  modelCallsPerformed:0,
  historicalB4LPolicyApproved:1,
  historicalB4LBrowserVerifiedRepairs:1,
  revisedHistoricalB4LScore:false,
  independentCuratorVerified:false,
  futureGenericPatchAuthorityGranted:false,
  stage45Go:false,
  productionAuthorized:false,
  screenshots:[],
  assertions:[],
  error:null,
};

let browser;
try {
  const source = await fs.readFile(FIX,'utf8');
  assert.equal(sha(source),ORIGINAL_SHA,'B4M_ORIGINAL_SOURCE_SHA_DRIFT');
  assert.equal(source.split(OLD).length-1,1,'B4M_UNIQUE_FROZEN_RULE_REQUIRED');
  assert.equal(VARIANTS.length,5);
  await fs.mkdir(OUT,{recursive:true});
  browser=await chromium.launch({headless:true});
  for (const viewport of VIEWS) {
    const context=await browser.newContext({
      viewport:{width:viewport.width,height:viewport.height},
      reducedMotion:'reduce',javaScriptEnabled:false,serviceWorkers:'block'
    });
    let networkCount=0;
    await context.route('**/*',route=>{networkCount++;return route.abort();});
    const page=await context.newPage();
    const errors=[];
    page.on('pageerror',e=>errors.push(String(e.message).slice(0,160)));
    try {
      for (const variant of VARIANTS) {
        // The only variation is a pre-reviewed literal CSS rule. Not a generic
        // patch executor or model-output renderer.
        const html=source.replace(OLD,variant.newRule);
        const expected=source.replace(OLD,variant.newRule);
        assert.equal(html,expected);
        await page.goto('about:blank');
        await page.setContent(html,{waitUntil:'load',timeout:12000});
        await page.keyboard.press('Tab');
        const measured=await page.evaluate(()=>{
          const el=document.querySelector('.skip');
          const r=el.getBoundingClientRect();
          const style=getComputedStyle(el);
          const outlineWidth=parseFloat(style.outlineWidth);
          const outlineOffset=parseFloat(style.outlineOffset);
          const focusActive=(document.activeElement===el);
          const keyboardFocusVisible=el.matches(':focus-visible');
          const outlineIsSolid=style.outlineStyle==='solid';
          const halo=outlineWidth+outlineOffset;
          const linkVisible=focusActive&&r.top>=0&&r.left>=0&&
            r.bottom<=innerHeight&&r.right<=innerWidth;
          const ringVisible=linkVisible&&keyboardFocusVisible&&outlineIsSolid&&
            outlineWidth>=3&&outlineOffset>=4&&halo>=7&&
            r.top>=halo&&r.left>=halo&&
            r.right+halo<=innerWidth&&r.bottom+halo<=innerHeight;
          const horizontalOverflow=document.documentElement.scrollWidth>innerWidth+1;
          return {linkVisible,ringVisible,focusActive,keyboardFocusVisible,
            outlineIsSolid,outlineWidth,outlineOffset,
            top:r.top,left:r.left,bottom:r.bottom,right:r.right,
            horizontalOverflow};
        });
        assert.equal(measured.linkVisible,variant.expectedLinkVisible,
          'B4M_LINK_VISIBILITY_CONTROL '+variant.id+' '+viewport.name);
        assert.equal(measured.ringVisible,variant.expectedRingVisible,
          'B4M_OUTLINE_CLIPPING_CONTROL '+variant.id+' '+viewport.name);
        assert.equal(measured.horizontalOverflow,false,'B4M_HORIZONTAL_OVERFLOW');
        assert.equal(networkCount,0,'B4M_NETWORK_REQUEST_FORBIDDEN');
        assert.deepEqual(errors,[],'B4M_UNEXPECTED_PAGE_ERRORS');
        assert.equal(measured.outlineWidth,3);
        assert.equal(measured.outlineOffset,4);
        const png=await page.screenshot({animations:'disabled'});
        const file=variant.id+'-'+viewport.name+'.png';
        await fs.writeFile(path.join(OUT,file),png,{flag:'wx'});
        report.screenshots.push({
          variant:variant.id,viewport:viewport.name,width:viewport.width,height:viewport.height,
          sourceSha256:sha(html),screenshotSha256:sha(png),
          linkVisible:measured.linkVisible,
          fullKeyboardFocusRingVisible:measured.ringVisible,
          focusTopPixels:measured.top,
          expectedRingClearancePixels:measured.outlineWidth+measured.outlineOffset,
          browserExternalRequests:networkCount,
          historicallyPolicyApproved:variant.historicallyPolicyApproved,
          modelRepairNewlyCredited:false,
        });
      }
    } finally {await context.close();}
  }
  assert.equal(report.screenshots.length,25);
  report.assertions.push('5 exact trusted controls x 5 viewports, link and outline separately measured');
  report.assertions.push('B4L real model top:0 makes link visible but clips 3px outline + 4px offset');
  report.assertions.push('Frozen exact B4J gate and B4L result unchanged');
  console.log('B4M_REAL_CHROMIUM_FOCUS_RING_SEMANTIC_NEGATIVE_CONTROL',
    JSON.stringify({frames:25,topZeroRingPassed:false,topSixRingPassed:false,
      topEightRingPassed:true,topSixteenRingPassed:true,
      modelRepairsNewlyCredited:0,stage45Go:false}));
} catch (e) {
  report.error=String(e?.stack||e).slice(0,3000);
  throw e;
} finally {
  await fs.mkdir(OUT,{recursive:true});
  await fs.writeFile(path.join(OUT,'report.json'),JSON.stringify(report,null,2)+'\n');
  if(browser) await browser.close();
}
