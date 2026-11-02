import test from 'node:test';
import assert from 'node:assert/strict';
import {auditedRuleTags,checkAxeResult,checkPageJourney,checkKeyboardJourney,
 checkReflowProxy,summary,proofSha256} from './w5f_accessibility_policy.mjs';
const axe=()=>({violations:[],passes:[{id:'document-title'}],
 incomplete:[],inapplicable:[],testEngine:{version:'4.14.0'}});
const page=(viewport='mobile',path='/')=>({
 projectSlug:'fictional-test',viewport,path,homeOrApproachStatus:200,
 hasExactlyOneMain:true,hasExactlyOneH1:true,
 hasSkipLink:true,hasPrimaryNavigation:true,hasNoAuthorScripts:true,
 pageIsNoIndex:true,externalRequests:0,browserErrors:0,horizontalOverflowPx:0,
 axe:checkAxeResult(axe())
});
const keyboard=(viewport='mobile',path='/')=>({
 viewport,path,skipLinkFirstFocusable:true,skipLinkVisiblyRevealed:true,
 skipLinkEnteredMainContent:true,skipLinkActualFocusOnMain:true,focusRingVisible:true,
 navigateByKeyboardSucceeded:true,navigationTextIsNonempty:true,
 keyboardTrapObserved:false,screenReaderVerified:false
});
const reflow=(viewport='mobile',path='/')=>({
 viewport,path,cssZoomScale:2,testViewportWidthCssPx:640,horizontalOverflowPx:0,
 contentRemainsVisible:true,browserUiZoomVerified:false,ownerDeviceVerified:false
});
const clone=x=>structuredClone(x);
test('axe policy accepts only structurally complete zero-violation measured scans',()=>{
 const a=checkAxeResult(axe());
 assert.equal(a.violations,0);
 assert.equal(a.passedRules,1);
 assert.equal(a.independentWcagCertified,false);
 assert(auditedRuleTags().includes('wcag22aa'));
 for(const mutate of [
 x=>{x.violations=[{id:'color-contrast',impact:'serious',nodes:[{}],tags:['wcag2aa']}]},
 x=>{delete x.incomplete;},
 x=>{x.passes=null;}
 ]){const v=axe();mutate(v);assert.throws(()=>checkAxeResult(v),/E6_W5F_/);}
});
test('keyboard policy rejects skip-link regressions, invisible focus, failed keyboard navigation and fake screen reader',()=>{
 for(const mutate of [
 x=>{x.skipLinkFirstFocusable=false;},x=>{x.skipLinkVisiblyRevealed=false;},
 x=>{x.skipLinkEnteredMainContent=false;},x=>{x.skipLinkActualFocusOnMain=false;},x=>{x.focusRingVisible=false;},
 x=>{x.navigateByKeyboardSucceeded=false;},x=>{x.keyboardTrapObserved=true;},
 x=>{x.screenReaderVerified=true;}
 ]){const v=keyboard();mutate(v);assert.throws(()=>checkKeyboardJourney(v),/KEYBOARD_A11Y_JOURNEY_BLOCKED/);}
});
test('page evidence rejects release-like missing routes, extra scripts, off-origin fetch and overflow',()=>{
 for(const mutate of [
 x=>{x.hasExactlyOneMain=false;},x=>{x.homeOrApproachStatus=404;},
 x=>{x.externalRequests=1;},x=>{x.browserErrors=1;},
 x=>{x.horizontalOverflowPx=3;},x=>{x.hasNoAuthorScripts=false;},
 x=>{x.pageIsNoIndex=false;},x=>{x.axe.independentWcagCertified=true;}
 ]){const v=page();mutate(v);assert.throws(()=>checkPageJourney(v),/INVALID_PAGE_A11Y_JOURNEY/);}
});
test('2x CSS zoom proxy must not be passed as a real browser UI zoom or owner-device measurement',()=>{
 const v=reflow();assert.equal(checkReflowProxy(v),true);
 for(const mutate of [
 x=>{x.cssZoomScale=1;},x=>{x.testViewportWidthCssPx=320;},x=>{x.horizontalOverflowPx=7;},
 x=>{x.contentRemainsVisible=false;},x=>{x.browserUiZoomVerified=true;},
 x=>{x.ownerDeviceVerified=true;}
 ]){const x=clone(v);mutate(x);assert.throws(()=>checkReflowProxy(x),/CSS_ZOOM_REFLOW_PROXY_BLOCKED/);}
});
test('complete 3×3×2 matrix requires unique case identities and cannot assert release approval',()=>{
 const viewports=['compact','mobile','desktop'],routes=['/','/approach/'];
 const rows=['a','b','c'].flatMap(projectSlug=>viewports.flatMap(viewport=>
 routes.map(path=>({
  projectSlug,page:{...page(viewport,path),projectSlug},
  keyboard:keyboard(viewport,path),reflow:reflow(viewport,path)
 }))));
 const a=summary(rows);
 assert.equal(a.uniqueJourneys,18);
 assert.equal(a.realScreenReaderTested,false);
 assert.equal(a.realModelInferencePerformed,false);
 assert.equal(a.independentWcag22AaCertified,false);
 assert.equal(a.releaseGo,false);
 assert.throws(()=>summary(rows.slice(1)),/INCOMPLETE_BROWSER_A11Y_MATRIX/);
 const duplicate=clone(rows);duplicate[1]=duplicate[0];
 assert.throws(()=>summary(duplicate),/DUPLICATE_BROWSER_JOURNEY/);
 const wrong=clone(rows);wrong[0].keyboard.path='/approach/';
 assert.throws(()=>summary(wrong),/MATRIX_PROVENANCE_MISMATCH/);
});
test('screenshot evidence references must be actual 64-digit hexadecimal digests',()=>{
 assert.equal(proofSha256('a'.repeat(64)),'a'.repeat(64));
 for(const invalid of ['','unknown','a'.repeat(63),'A'.repeat(64)])
  assert.throws(()=>proofSha256(invalid),/INVALID_SCREENSHOT_HASH/);
});
