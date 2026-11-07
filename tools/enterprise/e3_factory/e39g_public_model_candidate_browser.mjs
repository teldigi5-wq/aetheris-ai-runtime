/** Real Chromium review of two SHA-pinned PUBLIC Qwen7B candidate repairs.
 * Four known model answers from Actions #42329086279; this is not held-out
 * inference, independent certification or general website-edit authority.
 */
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
import {verifyFrozenPublicModelReceipt} from './e39g_public_candidate_receipt.mjs';

const here=path.dirname(fileURLToPath(import.meta.url));
const sha=v=>createHash('sha256').update(v).digest('hex');
const candidates=[
 {id:'cityline-public',source:'fixtures/e39g12b/cityline/index.html',
  digest:'40353c2d23a2bde49fddafbf0fdacb075bd5e16d9bf5d0f9df8a7d62f6064e50',
  old:"output.innerHTML = 'Station: '+name;",next:"output.textContent = 'Station: '+name;"},
 {id:'studynest-public',source:'fixtures/e39g12b/studynest/index.html',
  digest:'114705feff3479eabafeeccdc57e3eedfa60a5f03d48f352ea6d9a644f762cf5',
  old:'min-width:960px;',next:'min-width:100%;'}
];
const declined=[
 {id:'focus-public',why:'Proposed top:0 clips focus ring; do not weaken B4M/B4N semantic gate'},
 {id:'motion-public',why:'B4H rejected multi-line literal and incorrect target; no patch applied'}
];
const report={evidenceClass:'E39G_PUBLIC_QWEN7B_TYPED_AND_REAL_CHROMIUM_TWO_REPAIRS',
 modelRun:42329086279,modelCallsPerformed:0,sourceOnlyKnownPublic:true,
 independentCuratorVerified:false,heldOut:false,ownerApproval:false,
 productionAuthorized:false,considered:4,declined,admitted:[],verified:[],observations:[]};
assert.equal(candidates.length+declined.length,4);
// Verify all four original public model attempts and exact untrusted receipt bytes
// before loading Chromium or applying either narrowly pre-reviewed literal edit.
const receiptEvidence=verifyFrozenPublicModelReceipt(
 await fs.readFile(path.join(here,'fixtures/e39g_public_model_receipt_42329086279.json')),
 candidates);
assert.equal(receiptEvidence.modelRunId,report.modelRun);
report.publicModelReceiptLink=receiptEvidence; // still NOT held-out or signed model provenance
const browser=await chromium.launch({headless:true});
try{
 for(const c of candidates){
  const original=(await fs.readFile(path.join(here,c.source),'utf8')).replace(/\r\n/g,'\n');
  assert.equal(sha(original),c.digest,c.id+' SHA256 source mismatch');
  assert.equal(original.split(c.old).length,2,c.id+' unique edit target');
  assert.equal(c.id==='cityline-public'
    ? c.next==="output.textContent = 'Station: '+name;"
    : c.next==='min-width:100%;',true);
  const fixed=original.replace(c.old,c.next);
  assert.equal(fixed.replace(c.next,c.old),original,'exact single change');
  report.admitted.push(c.id);
  for(const width of [390,1280]){
   const context=await browser.newContext({viewport:{width,height:850},
    javaScriptEnabled:c.id==='cityline-public',serviceWorkers:'block',reducedMotion:'reduce'});
   let outgoing=0;
   await context.route('**/*',route=>{outgoing++;return route.abort()});
   const page=await context.newPage();const errors=[];
   page.on('pageerror',e=>errors.push(e.message));
   try{
    await page.goto('about:blank');
    let beforeDefectObserved = null;
    if(c.id==='cityline-public'){
     // Self-contained script-injection proof: SVG onload needs no network.
     const attack='<svg onload="window.__injected=true"></svg>';
     const enter=value=>{const input=document.querySelector('#station');
      input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));};
     // First prove the pinned BROKEN page executes untrusted markup.
     await page.setContent(original,{waitUntil:'load'});
     await page.evaluate(enter,attack);
     await page.waitForFunction(()=>window.__injected===true,undefined,{timeout:3000});
     const before=await page.evaluate(()=>({
      svg:document.querySelector('#query-output svg')!==null,
      injected:window.__injected===true}));
     assert.deepEqual(before,{svg:true,injected:true},
      'known-public CityLine injection defect must be present before repair');
     beforeDefectObserved=true;
     // Reload the patched source; reset the probe because setContent may
     // retain window properties across document writes.
     await page.setContent(fixed,{waitUntil:'load'});
     await page.evaluate(value=>{window.__injected=false;
      const input=document.querySelector('#station');
      input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));},attack);
     const observed=await page.evaluate(()=>({
      output:document.querySelector('#query-output').textContent,
      children:document.querySelector('#query-output').children.length,
      injected:window.__injected===true}));
     assert.equal(observed.output,'Station: '+attack);
     assert.equal(observed.children,0);
     assert.equal(observed.injected,false);
    }else{
     await page.setContent(original,{waitUntil:'load'});
     const previous=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1);
     await page.setContent(fixed,{waitUntil:'load'});
     const current=await page.evaluate(()=>({
      overflow:document.documentElement.scrollWidth>innerWidth+1,
      columns:getComputedStyle(document.querySelector('.schedule-grid')).gridTemplateColumns.split(' ').length,
      cards:document.querySelectorAll('.schedule-grid article').length}));
     if(width===390){
      assert.equal(previous,true,'public source must reproduce mobile overflow');
      beforeDefectObserved=true;
     }
     assert.equal(current.overflow,false);
     assert.equal(current.columns,width===390?1:3);
     assert.equal(current.cards,3);
    }
    assert.equal(outgoing,0);assert.deepEqual(errors,[]);
    report.observations.push({caseId:c.id,width,originalSha256:c.digest,
     afterSha256:sha(fixed),beforeDefectObserved,networkRequests:0,passed:true});
   }finally{await context.close()}
  }
  report.verified.push(c.id);
 }
 assert.equal(report.observations.length,4);
 console.log('E39G_TWO_REAL_QWEN_PUBLIC_CANDIDATES_CHROMIUM_PASS',JSON.stringify(report));
}finally{await browser.close()}
