import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {evaluateOriginalExperienceContract as evaluate, EXPERIENCE_CONTRACT_KIND} from './experience_contract.mjs';

const section=(id,kind='editorial',motion='none',fallback='none',assetSource='none') => ({id,kind,motion,fallback,assetSource});
const basic=()=>({version:1,projectSlug:'original-studio',siteProfile:'creative-studio',pages:[
  {path:'/',sections:[section('hero','hero','micro','static','owner-original'),section('work','gallery','scroll','text','owner-original'),section('contact','contact')]},
  {path:'/work',sections:[section('portfolio','case-studies','none','none','owner-original')]}
]});

test('creative studio supports multi-page original editorial content without unnecessary 3D',()=>{
 const p=evaluate(basic());
 assert.equal(p.kind,EXPERIENCE_CONTRACT_KIND);
 assert.equal(p.valid,true);
 assert.equal(p.plan.pageCount,2);
 assert.equal(p.plan.sectionCount,4);
 assert.equal(p.plan.sceneCount,0);
 assert.deepEqual(p.plan.suggestedStack,['css','css-waapi','gsap-scrolltrigger-conditional','semantic-html']);
 assert.equal(p.plan.stage45Go,false);
 assert.equal(p.plan.runnableWebsiteBuilt,false);
});
test('premium immersive site plans opt-in R3F and independently useful static fallback',()=>{
 const d=basic();
 d.pages[0].sections.push(section('product-showcase','scene3d','scene','static','owner-original'));
 const p=evaluate(d);
 assert.equal(p.valid,true);
 assert.equal(p.plan.sceneCount,1);
 assert(p.plan.suggestedStack.includes('three-r3f-opt-in'));
 assert.equal(p.plan.constraints.maximumSimultaneousWebglContexts,1);
 assert(p.plan.requiredEvidence.includes('PHYSICAL_GPU_AND_WEBGL_FALLBACK_UNVERIFIED'));
});
test('never treat a valid plan as a generated, measured, approved or deployed website',()=>{
 const d=basic();
 Object.assign(d,{stage45Go:true,deploymentAuthorized:true});
 assert.equal(evaluate(d).valid,false);
 const result=evaluate(basic());
 for(const field of ['genuineModelAuthorshipProven','runnableWebsiteBuilt','browserTested',
  'physicalGpuMeasured','humanReviewed','ownerApproved','deploymentAuthorized','stage45Go']){
  assert.equal(result.plan[field],false,field);
 }
 assert.equal(result.releaseGo,false);
});
test('denies remote scripts, custom commands, URL-bearing fields and arbitrary content',()=>{
 for(const key of ['scripts','url','renderer','shell','customerData']){
  const d=basic();d.pages[0].sections[0][key]='https://evil.example';
  assert.equal(evaluate(d).valid,false,key);
 }
});
test('requires meaningful fallbacks for every moving element',()=>{
 for(const [kind,motion,fallback] of [
  ['hero','micro','none'],['gallery','scroll','none'],['scene3d','scene','text'],
  ['editorial','none','static'],['hero','scene','static']
 ]){
  const d=basic();d.pages[0].sections[0]=section('hero',kind,motion,fallback,'owner-original');
  assert.equal(evaluate(d).valid,false,kind+motion+fallback);
 }
});
test('scene3d cannot silently run without local or licensed asset provenance',()=>{
 const d=basic();d.pages[0].sections[0]=section('scene','scene3d','scene','static','none');
 assert.equal(evaluate(d).valid,false);
 d.pages[0].sections[0].assetSource='unverified';
 const p=evaluate(d);
 assert.equal(p.valid,true);
 assert(p.plan.requiredEvidence.includes('ASSET_RIGHTS_REVIEW_REQUIRED'));
 assert.equal(p.plan.ownerApproved,false);
});
test('path traversal, query and URLs do not become new application routes',()=>{
 for(const v of ['https://example.test','/../admin','/blog?x=1','//host','/A','/blog/','/foo#bar']){
  const d=basic();d.pages[1].path=v;
  assert.equal(evaluate(d).valid,false,v);
 }
});
test('duplicate pages and duplicate section IDs fail closed',()=>{
 const d=basic();d.pages[1].path='/';assert.equal(evaluate(d).valid,false);
 const e=basic();e.pages[0].sections[1].id='hero';assert.equal(evaluate(e).valid,false);
});
test('rejects proto pollution and accessor side effects at every level',()=>{
 const d=basic();const x=Object.create({admin:true});Object.assign(x,d);
 assert.equal(evaluate(x).valid,false);
 const g=basic();Object.defineProperty(g.pages[0].sections[0],'assetSource',{get(){throw Error('should not be called')}});
 assert.equal(evaluate(g).valid,false);
});
test('validates bounded pages, sections and scene complexity',()=>{
 const d=basic();d.pages=Array.from({length:13},(_,i)=>({path:i===0?'/':'/p'+i,sections:[section('x')]}));
 assert.equal(evaluate(d).valid,false);
 const e=basic();e.pages[0].sections=Array.from({length:17},(_,i)=>section('s'+i));
 assert.equal(evaluate(e).valid,false);
 const f=basic();f.pages[0].sections=Array.from({length:5},(_,i)=>section('scene'+i,'scene3d','scene','static','owner-original'));
 assert.equal(evaluate(f).valid,false);
});
test('original brand identity is separate from unverified clients and case-study facts',()=>{
 const p=evaluate(basic());
 assert.equal(p.valid,true);
 assert(p.plan.requiredEvidence.includes('CLIENT_AND_PERSON_CLAIMS_REQUIRE_SOURCE_REVIEW'));
 assert(p.plan.requiredEvidence.includes('ORIGINAL_CREATIVE_AND_CONTENT_REVIEW_REQUIRED'));
});
test('source policy has no network, credentials, execution or output side effects',async()=>{
 const src=await readFile(new URL('./experience_contract.mjs',import.meta.url),'utf8');
 for(const banned of ['fetch(', 'sendBeacon(', 'exec(', 'spawn(', 'writeFile(', 'process.env', 'localStorage', 'new WebSocket('])
  assert(!src.includes(banned),banned);
 assert.equal(evaluate(null).stage45Go,false);
 assert.equal(evaluate({}).releaseGo,false);
});
