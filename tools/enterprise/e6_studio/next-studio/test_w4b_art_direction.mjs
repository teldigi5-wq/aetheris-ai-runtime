import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const source=new URL('./target/e6-w4b-arcloom/',import.meta.url);
const original=new URL('./target/e6-w4a-arcloom/',import.meta.url);
const read=(root,name)=>readFile(new URL(name,root));
const sha=v=>createHash('sha256').update(v).digest('hex');
const evidence=JSON.parse(await read(source,'assets/w4b-art-direction-evidence.json'));
const fallback=await read(source,'assets/signal-fold-reference.png');
const js=await read(source,'assets/signal-fold-scene.mjs');
const css=await read(source,'assets/signal-fold-scene.css');
const html=(await read(source,'studies/signal-fold/index.html')).toString('utf8');

test('real studio image is bounded 800×900 valid PNG with content-linked SHA',()=>{
  assert.equal(evidence.kind,'E6_W4B_ART_DIRECTION_RESEARCH_V1');
  assert.equal(fallback.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
  assert.equal(fallback.readUInt32BE(16),800);
  assert.equal(fallback.readUInt32BE(20),900);
  assert(fallback.length>15000&&fallback.length<3_000_000);
  assert.equal(sha(fallback),evidence.renderedFallbackSha256);
  assert.equal(fallback.length,evidence.renderedFallbackBytes);
  assert.equal(evidence.staticImageIsActualBlenderRender,true);
  assert.equal(evidence.renderEngine,'BLENDER_CYCLES_CPU');
});
test('new JS and CSS are fixed verified bundles with real source image',()=>{
  assert.equal(sha(js),evidence.scriptSha256);
  assert.equal(sha(css),evidence.cssSha256);
  assert(js.length>15000&&js.length<1_000_000);
  assert.equal(evidence.sceneCameraKeyframes,3);
  assert.equal(evidence.runtimeSha256CheckBeforeParse,true);
  assert(css.toString().includes('signal-fold-reference.png'));
  assert(html.includes('data-w4b-shot'));
  assert(html.includes('signal-fold-scene.mjs'));
  assert(html.includes('connect-src &#39;self&#39;'));
  assert(html.includes('noindex,nofollow'));
});
test('W4-A source assets and original site's studied narrative remain unchanged',async()=>{
  for(const name of ['assets/arcloom-signal-fold.glb',
    'assets/w4a-scene-evidence.json','studies/readable-measure/index.html',
    'studies/sundial-desk/index.html','approach/index.html']){
    assert.equal(sha(await read(source,name)),sha(await read(original,name)),name);
  }
  assert.equal((html.match(/section class="story-chapter"/g)||[]).length,3);
});
test('owner, rights, hardware and release assertions remain FALSE',()=>{
  for(const key of ['browserAcceptance','physicalGpuMeasured','humanVisualApproved',
    'productPhotograph','modelGenerated','ownerApproved',
    'deploymentAuthorized','stage45Go','releaseGo'])
    assert.equal(evidence[key],false,key);
  assert.equal(evidence.nativeScrolling,true);
  assert.equal(evidence.noPinning,true);
  assert.equal(evidence.noLenis,true);
});
