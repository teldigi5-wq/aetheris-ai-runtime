import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {contrast,validateModelProposal,compileModelProposedSite} from './w5a_model_design_compiler.mjs';
import {briefs,proposalFor} from './w5a_test_doubles.mjs';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
import {EXAMPLE_CODES} from './w5c_test_doubles.mjs';
import {compileAuthoredSite} from './w5c_restricted_css_compiler.mjs';
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const clone=x=>structuredClone(x);
const design=()=>proposalFor(briefs[0]);
test('WCAG contrast formula is symmetric, unrounded and has correct extrema',()=>{
 assert.equal(contrast('#000000','#FFFFFF'),21);
 assert.equal(contrast('#ffffff','#ffffff'),1);
 for(const [a,b] of [['#dddddd','#666666'],['#29464a','#f1bf91'],['#e1d5c8','#85472c']]){
  assert(Math.abs(contrast(a,b)-contrast(b,a))<1e-12);
 }
});
test('reject model palette where accent on paper passes but accent on alternate section fails',()=>{
 const x=design();
 x.art={...x.art,paper:'#ffffff',ink:'#111111',surface:'#dddddd',accent:'#666666'};
 assert(contrast(x.art.paper,x.art.accent)>=4.5);
 assert(contrast(x.art.surface,x.art.ink)>=4.5);
 assert(contrast(x.art.paper,x.art.ink)>=4.5);
 assert(contrast(x.art.surface,x.art.accent)<4.5);
 assert.throws(()=>validateModelProposal(briefs[0],x),/MODEL_SCHEMA_OR_CONTRAST_REJECTED/);
});
test('control case with all four required combinations passes without loosening contrast',()=>{
 const x=design();
 x.art={...x.art,paper:'#ffffff',ink:'#111111',surface:'#dddddd',accent:'#555555'};
 for(const [a,b] of [['paper','ink'],['paper','accent'],['surface','ink'],['surface','accent']])
  assert(contrast(x.art[a],x.art[b])>=4.5);
 assert.doesNotThrow(()=>validateModelProposal(briefs[0],x));
 const site=compileModelProposedSite(briefs[0],x,css);
 assert(site.files.get('assets/site.css').includes('--accent:#555555'));
 assert.equal(site.evidence.stage45Go,false);
});
test('all pre-existing simulated candidate palettes still pass the stronger condition',()=>{
 const cases=[
  [briefs[0],proposalFor(briefs[0])],
  [briefs[1],proposalFor(briefs[1])]
 ];
 for(const [b,d] of cases){
  assert(contrast(d.art.surface,d.art.accent)>=4.5);
  assert.doesNotThrow(()=>validateModelProposal(b,d));
 }
 const heldout=JSON.parse((await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')));
 for(let i=0;i<heldout.briefs.length;i++){
  const b=heldout.briefs[i],d=syntheticDesign(b,i);
  assert(contrast(d.art.surface,d.art.accent)>=4.5);
  assert.doesNotThrow(()=>validateModelProposal(b,d));
  const site=compileAuthoredSite(b,d,EXAMPLE_CODES[i],css);
  assert.equal(site.evidence.ownerApproved,false);
  assert.equal(site.evidence.releaseGo,false);
 }
});
test('contrast guard rejects several distinct near-threshold palettes',()=>{
 for(const [surface,accent] of [
   ['#dddddd','#666666'],
   ['#cccccc','#666666'],
   ['#bbbbbb','#555555']
 ]){
  const p=design();
  p.art={...p.art,paper:'#ffffff',ink:'#111111',surface,accent};
  assert(contrast(p.art.paper,p.art.accent)>=4.5);
  assert(contrast(p.art.surface,p.art.ink)>=4.5);
  assert(contrast(p.art.surface,p.art.accent)<4.5);
  assert.throws(()=>compileModelProposedSite(briefs[0],p,css),/MODEL_SCHEMA_OR_CONTRAST_REJECTED/);
 }
});
test('blocked contrast cannot be reintroduced by valid CSS source in W5-C',()=>{
 const p=design();
 p.art={...p.art,paper:'#ffffff',ink:'#111111',surface:'#dddddd',accent:'#666666'};
 assert.throws(()=>compileAuthoredSite(briefs[0],p,EXAMPLE_CODES[0],css),
  /MODEL_SCHEMA_OR_CONTRAST_REJECTED/);
});
