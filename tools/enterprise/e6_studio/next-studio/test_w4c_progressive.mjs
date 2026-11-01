import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const source=new URL('./target/e6-w4c-arcloom/',import.meta.url);
const prev=new URL('./target/e6-w4b-arcloom/',import.meta.url);
const read=(root,path)=>readFile(new URL(path,root));
const sha=x=>createHash('sha256').update(x).digest('hex');
const p=JSON.parse(await read(source,'assets/w4c-performance-evidence.json'));
const entry=await read(source,'assets/signal-fold-bootstrap.mjs');
const engine=await read(source,'assets/signal-fold-engine.mjs');
const png=await read(source,'assets/signal-fold-refined.png');
const glb=await read(source,'assets/arcloom-signal-fold.glb');
const html=(await read(source,'studies/signal-fold/index.html')).toString('utf8');
test('new Blender still is source-addressed and really higher sample count',()=>{
 assert.equal(p.kind,'E6_W4C_PROGRESSIVE_CINEMATIC_RESEARCH_V1');
 assert.equal(p.fallbackSamples,48);
 assert.equal(p.fallbackImageSha256,sha(png));
 assert.equal(p.fallbackImageBytes,png.length);
 assert.equal(png.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
 assert.equal(png.readUInt32BE(16),800);
 assert.equal(png.readUInt32BE(20),900);
 assert.equal(p.modelSha256,sha(glb));
});
test('initial controller is tiny, full scene is separately opt-in and source bound',()=>{
 assert.equal(p.initialBootstrapBytes,entry.length);
 assert.equal(p.initialBootstrapSha256,sha(entry));
 assert.equal(p.optionalSceneBytes,engine.length);
 assert.equal(p.optionalSceneSha256,sha(engine));
 assert(entry.length<12_000);
 assert(engine.length>20000&&engine.length<780_000);
 assert.equal(p.initial3DBundleRequestsExpected,0);
 assert.equal(p.optionalSceneOnlyAfterDirectActivation,true);
 assert(html.includes('src="/assets/signal-fold-bootstrap.mjs"'));
 assert(!html.includes('src="/assets/signal-fold-scene.mjs"'));
 assert(!html.includes('src="/assets/signal-fold-engine.mjs"'));
 assert.equal((html.match(/section class="story-chapter"/g)||[]).length,3);
});
test('old W4-B preview remains immutable and there is no remote runtime code',async()=>{
 for(const name of ['assets/arcloom-signal-fold.glb',
   'assets/w4b-art-direction-evidence.json','approach/index.html',
   'studies/readable-measure/index.html','studies/sundial-desk/index.html']){
   assert.equal(sha(await read(source,name)),sha(await read(prev,name)),name);
 }
 assert(!html.includes('cdn.jsdelivr.net'));
 assert(!html.includes('unpkg.com'));
 assert(html.includes('noindex,nofollow'));
 assert(html.includes('connect-src &#39;self&#39;'));
});
test('no release, third-party-rights, AI-authorship or real GPU claims',()=>{
 for(const key of ['visualQualityIndependentlyAccepted','physicalGpuMeasured',
   'aiModelAuthored','rightsIndependentlyCertified','ownerApproved','releaseGo','stage45Go'])
   assert.equal(p[key],false,key);
 assert.equal(p.canvasDprCap,1.25);
 assert.equal(p.noRedundantCanvasResize,true);
 assert.equal(p.nativeScroll,true);
 assert.equal(p.modelHashBeforeParse,true);
});
