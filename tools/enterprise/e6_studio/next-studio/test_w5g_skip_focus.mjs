import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {compileModelProposedSite} from './w5a_model_design_compiler.mjs';
import {compileAuthoredSite} from './w5c_restricted_css_compiler.mjs';
import {briefs,proposalFor} from './w5a_test_doubles.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
import {checkSkipTargetMarkup,checkNoJsFocusProof} from './w5g_skip_focus_policy.mjs';
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const heldout=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8'));
const original=compileModelProposedSite(briefs[0],proposalFor(briefs[0]),css).files.get('index.html');
const evidence={
 browserJavaScriptEnabled:false,hasExactlyOneMain:true,hasExactlyOneSkipLink:true,
 skipWasFirstTabStop:true,skipFocusIndicatorVisible:true,
 fragmentNavigatedToMain:true,activeElementIsMain:true,
 mainHasTabindexMinusOne:true,nextTabDidNotReturnToSkip:true,
 horizontalOverflowPx:0,outsideRequests:0,browserErrors:0,
 ownerDeviceMeasured:false,realModelUsed:false,wcagCertified:false,releaseGo:false
};
test('both real generated static routes have exactly one focusable, non-tab-stop main',()=>{
 for(const b of briefs){
  const files=compileModelProposedSite(b,proposalFor(b),css).files;
  for(const p of ['index.html','approach/index.html']){
   const a=checkSkipTargetMarkup(files.get(p));
   assert.equal(a.mainTabindexMinusOne,true);
   assert.equal(a.extraSequentialTabstopAdded,false);
   assert.equal(a.realBrowserFocusVerified,false);
  }
 }
});
test('all three held-out bounded CSS code proposals retain the semantic skip target',()=>{
 heldout.briefs.forEach((b,i)=>{
  const files=compileAuthoredSite(b,syntheticDesign(b,i),EXAMPLE_CODES[i],css).files;
  for(const p of ['index.html','approach/index.html'])
   assert.equal(checkSkipTargetMarkup(files.get(p)).mainTabindexMinusOne,true);
 });
});
test('negative mutation: a URL fragment, missing or duplicate focus target cannot pass',()=>{
 for(const mutation of [
  s=>s.replace('tabindex="-1"',''),
  s=>s.replace('tabindex="-1"','tabindex="0"'),
  s=>s.replace('id="main"','id="not-main"'),
  s=>s.replace('href="#main"','href="#unknown"'),
  s=>s.replace('<main','<main hidden'),
  s=>s.replace('<main','<main style="display:none"'),
  s=>s.replace('</main>','</main><main id="main"></main>'),
  s=>s.replace('</main>','</main><div id="main"></div>'),
  s=>s.replace('</main>','</main><script>alert(1)</script>'),
  s=>s.replace('class="skip"','class="not-skip"')
 ]){
  assert.throws(()=>checkSkipTargetMarkup(mutation(original)),/E6_W5G_/);
 }
});
test('only actual no-author-JS focus proof passes; hash or scroll is insufficient',()=>{
 assert.equal(checkNoJsFocusProof(evidence),true);
 for(const change of [
  x=>{x.activeElementIsMain=false;},
  x=>{x.mainHasTabindexMinusOne=false;},
  x=>{x.browserJavaScriptEnabled=true;},
  x=>{x.nextTabDidNotReturnToSkip=false;},
  x=>{x.fragmentNavigatedToMain=false;},
  x=>{x.horizontalOverflowPx=3;},
  x=>{x.outsideRequests=1;},
  x=>{x.browserErrors=1;},
  x=>{x.ownerDeviceMeasured=true;},
  x=>{x.wcagCertified=true;},
  x=>{x.realModelUsed=true;},
  x=>{x.releaseGo=true;}
 ]){
  const x=structuredClone(evidence);change(x);
  assert.throws(()=>checkNoJsFocusProof(x),/E6_W5G_/);
 }
});
