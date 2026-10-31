import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolveLocalPinExperiment} from './cls_pin_experiment.mjs';

test('default behavior is always unchanged unless locally opted into comparison',()=>{
 for(const assetVariant of ['fixture','blender']){
  for(const host of ['localhost','127.0.0.1','[::1]']){
   for(const q of ['', '?utm_source=chatgpt.com','?e6_cls_pin=default','?e6_cls_pin=fix','?e6_cls_pin=offx']){
    assert.equal(resolveLocalPinExperiment(host,assetVariant,q),'default');
   }
  }
 }
});
test('local unmerged Blender preview permits exactly two diagnostic comparison modes',()=>{
 for(const host of ['localhost','127.0.0.1','[::1]']){
  for(const mode of ['off','transform']){
   assert.equal(resolveLocalPinExperiment(host,'blender','?utm_source=chatgpt.com&e6_cls_pin='+mode),mode);
  }
 }
});
test('production, external hosts and unrelated research pages never accept variants',()=>{
 for(const host of ['github.com','example.org','localhost.evil.test','127.0.0.2','']){
  for(const mode of ['off','transform'])
   assert.equal(resolveLocalPinExperiment(host,'blender','?e6_cls_pin='+mode),'default');
 }
 for(const mode of ['off','transform'])
  assert.equal(resolveLocalPinExperiment('localhost','fixture','?e6_cls_pin='+mode),'default');
});
test('large query and ambiguous unsupported settings fail closed',()=>{
 assert.equal(resolveLocalPinExperiment('localhost','blender','?e6_cls_pin='+ 'a'.repeat(5000)),'default');
 assert.equal(resolveLocalPinExperiment('localhost','blender','?e6_cls_pin=OFF'),'default');
});
test('explicitly chosen no-pin/transform never silently passes timing benchmark',async()=>{
 const src=await readFile(new URL('./components/LocalBrowserRafCapture.tsx',import.meta.url),'utf8');
 assert.match(src,/story\.dataset\.e6ClsPin!=="default"/);
 assert.match(src,/performance capture is disabled/);
 assert(src.indexOf('story.dataset.e6ClsPin!=="default"')<src.indexOf('const activeTrigger=ScrollTrigger.getAll()'));
});
test('the existing non-research route, release gates and default pin remain unchanged',async()=>{
 const here=new URL('./',import.meta.url);
 const src=await readFile(new URL('./components/CinematicExperience.tsx',here),'utf8');
 const resolver=await readFile(new URL('./cls_pin_experiment.mjs',here),'utf8');
 const ui=await readFile(new URL('./app/blender-preview/page.tsx',here),'utf8');
 assert.match(src,/pin:pinMode!=="off"/);
 assert.match(src,/pinType:"transform" as const/);
 assert.match(src,/data-e6-cls-pin/);
 assert.match(src,/pinModeResolved/);
 assert.match(resolver,/assetVariant!=='blender'/);
 assert.match(ui,/NEXT_PUBLIC_E6_BLENDER_PREVIEW/);
 assert.match(ui,/LocalBrowserRafCapture/);
 assert(!resolver.includes('fetch('));
 assert(!resolver.includes('sendBeacon('));
});
