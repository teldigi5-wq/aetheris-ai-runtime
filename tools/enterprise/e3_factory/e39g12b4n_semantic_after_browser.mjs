/** B4N real Chromium semantic verification after strict bounded-Docker patching.
 * Only the already-reviewed B4I static HTML and six exact case identifiers
 * are allowed. Source hash and complete byte delta verified before setContent.
 * JS off, no networks, no model output execution, no production authorization.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';

if(process.argv.length!==2) throw Error('B4N_NO_EXTERNAL_FILE_ARGUMENTS');
const HERE=path.dirname(fileURLToPath(import.meta.url));
const SOURCE=path.join(HERE,'e39g12b4i','fixtures','focus-negative.html');
const OUT=path.resolve(HERE,'../../../target/e3-factory/e39g12b4n');
const EXPECTED_SOURCE_SHA='385d3fac74d775586bb389beb3068e6e34a7fb0e08f8d854c80cb55b433b290b';
const OLD='.skip:focus{top:-120px;}';
const VALUES=Object.freeze([
  {id:'zero',top:0,fullRing:false},
  {id:'six',top:6,fullRing:false},
  {id:'eight',top:8,fullRing:true},
  {id:'sixteen',top:16,fullRing:true},
  {id:'thirtytwo',top:32,fullRing:true},
]);
const VIEWPORTS=Object.freeze([
  {name:'320-mobile',width:320,height:740},
  {name:'390-mobile',width:390,height:844},
  {name:'768-tablet',width:768,height:1024},
  {name:'1280-desktop',width:1280,height:800},
  {name:'1920-desktop',width:1920,height:1080},
]);
const sha=b=>createHash('sha256').update(b).digest('hex');
const report={
  evidenceClass:'E39G12B4N_BOUNDED_STRUCTURAL_CSS_PLUS_REAL_BROWSER_BEHAVIOR',
  historicalB4LScoreChanged:false,
  historicalB4LBrowserVerifiedRepairs:1,
  modelCallsPerformed:0,modelAuthoredSourceRendered:false,
  knownPublicFixtureOnly:true,genuinelyHeldOut:false,
  noGeneralCssEditAuthority:true,ownerApprovalGranted:false,
  stage45Go:false,productionAuthorized:false,
  structurallyEligibleCases:0,semanticPassedCases:[],
  semanticFailedCases:[],observations:[],error:null,
};
let browser;
try{
  const original=await fs.readFile(SOURCE,'utf8');
  assert.equal(sha(original),EXPECTED_SOURCE_SHA,'B4N_TRUSTED_SOURCE_SHA_DRIFT');
  assert.equal(original.split(OLD).length,2);
  const denied=JSON.parse(await fs.readFile(path.join(OUT,'results','malicious','report.json'),'utf8'));
  assert.equal(denied.caseId,'malicious');
  assert.equal(denied.structurallyEligible,false);
  assert.equal(denied.sourceAfterSha256,null);
  assert.equal(denied.outcome,'RULE_FORMAT_REJECTED');
  const deniedHtml=path.join(OUT,'results','malicious','index.html');
  await assert.rejects(fs.stat(deniedHtml),{code:'ENOENT'});
  browser=await chromium.launch({headless:true});
  for(const item of VALUES){
    const folder=path.join(OUT,'results',item.id);
    const policy=JSON.parse(await fs.readFile(path.join(folder,'report.json'),'utf8'));
    const html=await fs.readFile(path.join(folder,'index.html'),'utf8');
    const rule='.skip:focus{top:'+item.top+'px;}';
    const expected=original.replace(OLD,rule);
    assert.equal(html,expected,'B4N_WHOLE_SOURCE_CHANGED');
    assert.equal(policy.sourceBeforeSha256,EXPECTED_SOURCE_SHA);
    assert.equal(policy.sourceAfterSha256,sha(html));
    assert.equal(policy.structurallyEligible,true);
    assert.equal(policy.candidateTopPixels,item.top);
    assert.equal(policy.outcome,'STRUCTURAL_ONLY_BROWSER_REVIEW_REQUIRED');
    assert.equal(policy.semanticBrowserVerified,false);
    assert.equal(policy.patchApprovedForProduction,false);
    assert.equal(policy.modelCallsPerformed,0);
    assert.equal(policy.stage45Go,false);
    report.structurallyEligibleCases++;
    let allPass=true;
    for(const viewport of VIEWPORTS){
      const context=await browser.newContext({viewport:{width:viewport.width,height:viewport.height},
        javaScriptEnabled:false,serviceWorkers:'block',reducedMotion:'reduce'});
      let externalRequests=0;
      await context.route('**/*',route=>{externalRequests++;return route.abort()});
      const page=await context.newPage();
      const pageErrors=[];
      page.on('pageerror',e=>pageErrors.push(String(e.message).slice(0,80)));
      try{
        await page.goto('about:blank');
        await page.setContent(html,{waitUntil:'load',timeout:12000});
        await page.keyboard.press('Tab');
        const measured=await page.evaluate(()=>{
          const el=document.querySelector('.skip');
          const r=el.getBoundingClientRect();
          const s=getComputedStyle(el);
          const outlineWidth=parseFloat(s.outlineWidth);
          const outlineOffset=parseFloat(s.outlineOffset);
          const margin=outlineWidth+outlineOffset;
          const actualFocus=document.activeElement===el;
          const focusVisible=el.matches(':focus-visible');
          const rectangleVisible=actualFocus&&r.top>=0&&r.left>=0&&
            r.bottom<=innerHeight&&r.right<=innerWidth;
          return {
            focused:actualFocus,focusVisible,rectangleVisible,
            fullyVisibleFocusOutline:Boolean(rectangleVisible&&focusVisible&&
              s.outlineStyle==='solid'&&outlineWidth>=3&&outlineOffset>=4&&
              r.top>=margin&&r.left>=margin&&r.right+margin<=innerWidth&&
              r.bottom+margin<=innerHeight),
            ringMarginPixels:margin,
            horizontalOverflow:document.documentElement.scrollWidth>innerWidth+1,
          };
        });
        assert.equal(measured.focused,true,'B4N_KEYBOARD_TAB_FAILED');
        assert.equal(measured.focusVisible,true,'B4N_FOCUS_VISIBLE_PSEUDO_MISSING');
        assert.equal(measured.rectangleVisible,true,'B4N_LINK_HIDDEN');
        assert.equal(measured.fullyVisibleFocusOutline,item.fullRing,
          'B4N_SEMANTIC_RING_ASSERTION '+item.id+' '+viewport.name);
        assert.equal(measured.ringMarginPixels,7);
        assert.equal(measured.horizontalOverflow,false);
        assert.equal(externalRequests,0,'B4N_NETWORK_BLOCKED_BUT_REQUESTED');
        assert.deepEqual(pageErrors,[]);
        const image=await page.screenshot({animations:'disabled'});
        await fs.writeFile(path.join(OUT,item.id+'-'+viewport.name+'.png'),image,{flag:'wx'});
        report.observations.push({
          caseId:item.id,viewport:viewport.name,htmlSha256:sha(html),
          screenshotSha256:sha(image),linkVisible:measured.rectangleVisible,
          fullRingVisible:measured.fullyVisibleFocusOutline,
          networkRequests:externalRequests,modelRepairCredited:false,
        });
        allPass=allPass&&measured.fullyVisibleFocusOutline;
      }finally{await context.close();}
    }
    (allPass?report.semanticPassedCases:report.semanticFailedCases).push(item.id);
  }
  assert.deepEqual(report.semanticFailedCases,['zero','six']);
  assert.deepEqual(report.semanticPassedCases,['eight','sixteen','thirtytwo']);
  assert.equal(report.observations.length,25);
  console.log('B4N_REAL_CHROMIUM_BEHAVIOR_SEPARATE_FROM_TYPED_ADMISSION',
    JSON.stringify({observations:25,structuralCases:5,semanticPass:3,
      semanticFail:2,blockedMalicious:1,modelCalls:0,release:false}));
}catch(e){
  report.error=String(e?.stack||e).slice(0,2400);throw e;
}finally{
  await fs.mkdir(OUT,{recursive:true});
  await fs.writeFile(path.join(OUT,'browser-report.json'),JSON.stringify(report,null,2)+'\n');
  if(browser)await browser.close();
}
