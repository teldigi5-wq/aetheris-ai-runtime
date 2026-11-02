/** E6 W5-F. Audit-result policy is independent of the page runner.
 * axe/browser automation can detect a subset of WCAG failures; a pass
 * NEVER certifies WCAG 2.2 AA, screen reader quality, genuine AI output,
 * real device zoom or production readiness.
 */
const fail=code=>{throw Error('E6_W5F_'+code);};
const TAGS=Object.freeze(['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa']);
const views=new Set(['compact','mobile','desktop']);
const routes=new Set(['/','/approach/']);
const hex=/^[a-f0-9]{64}$/;
export function auditedRuleTags(){return [...TAGS];}
export function checkAxeResult(result){
 if(!result||typeof result!=='object'||
   !Array.isArray(result.violations)||!Array.isArray(result.passes)||
   !Array.isArray(result.incomplete)||!Array.isArray(result.inapplicable))
   fail('INCOMPLETE_AXE_RESULT');
 const violations=result.violations.map(v=>({
   id:v.id,impact:v.impact||'unknown',
   affectedNodes:v.nodes?.length||0,
   wcagTags:Array.isArray(v.tags)?v.tags.filter(x=>TAGS.includes(x)):[]
 }));
 if(violations.length)fail('WCAG_RULE_VIOLATIONS_'+violations.map(x=>x.id).join('_'));
 return {violations:0,passedRules:result.passes.length,
   needsManualReview:result.incomplete.length,rawAxeVersion:String(result.testEngine?.version||'unknown'),
   automatedWcagTagScanCompleted:true,independentWcagCertified:false};
}
export function checkPageJourney(x){
 if(!x||!views.has(x.viewport)||!routes.has(x.path)||
   typeof x.projectSlug!=='string'||!x.projectSlug||
   x.homeOrApproachStatus!==200||
   x.hasExactlyOneMain!==true||x.hasExactlyOneH1!==true||
   x.hasSkipLink!==true||x.hasPrimaryNavigation!==true||
   x.hasNoAuthorScripts!==true||x.pageIsNoIndex!==true||
   x.externalRequests!==0||x.browserErrors!==0||
   !Number.isFinite(x.horizontalOverflowPx)||x.horizontalOverflowPx>2||
   x.horizontalOverflowPx<0||
   !x.axe||x.axe.automatedWcagTagScanCompleted!==true||
   x.axe.violations!==0||x.axe.independentWcagCertified!==false)
   fail('INVALID_PAGE_A11Y_JOURNEY');
 return true;
}
export function checkKeyboardJourney(x){
 if(!x||!views.has(x.viewport)||!routes.has(x.path)||
    x.skipLinkFirstFocusable!==true||
    x.skipLinkVisiblyRevealed!==true||
    x.skipLinkEnteredMainContent!==true||
    // A fragment URL or scroll alone is not evidence of keyboard focus.
    x.skipLinkActualFocusOnMain!==true||
    x.focusRingVisible!==true||
    x.navigateByKeyboardSucceeded!==true||
    x.navigationTextIsNonempty!==true||
    x.keyboardTrapObserved!==false||
    x.screenReaderVerified!==false)
   fail('KEYBOARD_A11Y_JOURNEY_BLOCKED');
 return true;
}
export function checkReflowProxy(x){
 if(!x||!views.has(x.viewport)||!routes.has(x.path)||
    x.cssZoomScale!==2||
    !Number.isInteger(x.testViewportWidthCssPx)||
    x.testViewportWidthCssPx<640||
    !Number.isFinite(x.horizontalOverflowPx)||
    x.horizontalOverflowPx<0||x.horizontalOverflowPx>2||
    x.contentRemainsVisible!==true||
    x.browserUiZoomVerified!==false||
    x.ownerDeviceVerified!==false)
    fail('CSS_ZOOM_REFLOW_PROXY_BLOCKED');
 return true;
}
export function summary(rows,expectedCases=3){
 if(!Array.isArray(rows)||rows.length!==expectedCases*3*2)
   fail('INCOMPLETE_BROWSER_A11Y_MATRIX');
 const keys=new Set();
 for(const r of rows){
   checkPageJourney(r.page);
   checkKeyboardJourney(r.keyboard);
   checkReflowProxy(r.reflow);
   if(r.page.projectSlug!==r.projectSlug||
      r.keyboard.viewport!==r.page.viewport||
      r.keyboard.path!==r.page.path||
      r.reflow.viewport!==r.page.viewport||
      r.reflow.path!==r.page.path||
      !views.has(r.page.viewport)||!routes.has(r.page.path))
     fail('MATRIX_PROVENANCE_MISMATCH');
   const key=[r.projectSlug,r.page.viewport,r.page.path].join(':');
   if(keys.has(key))fail('DUPLICATE_BROWSER_JOURNEY');
   keys.add(key);
 }
 return {kind:'E6_W5F_MOCK_STATIC_ACCESSIBILITY_EVIDENCE_V1',
   uniqueJourneys:keys.size,
   manualReviewItems:rows.reduce((n,r)=>n+r.page.axe.needsManualReview,0),
   inputMode:'HUMAN_AUTHORED_TEST_DOUBLE',
   automationTags:auditedRuleTags(),
   automatedAxeChecksPassed:true,keyboardChecksPassed:true,
   cssZoomReflowProxyPassed:true,
   trueBrowser200PercentZoomMeasured:false,
   realScreenReaderTested:false,independentWcag22AaCertified:false,
   realModelInferencePerformed:false,realModelAuthorshipVerified:false,
   ownerHardwareVerified:false,humanArtReviewApproved:false,
   ownerApproved:false,stage45Go:false,releaseGo:false,
   status:'AUTOMATED_A11Y_REGRESSION_ONLY'};
}
export function proofSha256(x){
 if(typeof x!=='string'||!hex.test(x))fail('INVALID_SCREENSHOT_HASH');
 return x;
}
