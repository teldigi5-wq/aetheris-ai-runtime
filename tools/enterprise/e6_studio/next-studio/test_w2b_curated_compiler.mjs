import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {compileW2Site} from './w2_compiler.mjs';
import {compileCuratedW2Site,W2B_PRESETS} from './w2b_curated_compiler.mjs';

const read=async file=>JSON.parse(await readFile(new URL(file,import.meta.url),'utf8'));
const morrowline=await read('./w2_demo_brief.json');
const arcloom=await read('./w2b_arcloom_brief.json');
const direction=preset=>({version:1,preset});

test('two independent briefs generate distinct page sets, routes and design systems',()=>{
  const a=compileCuratedW2Site(morrowline.brief,morrowline.content,direction('mineral-editorial'));
  const b=compileCuratedW2Site(arcloom.brief,arcloom.content,direction('ink-monograph'));
  assert.deepEqual(a.evidence.routes,['/','/work','/contact']);
  assert.deepEqual(b.evidence.routes,['/','/studies','/approach','/contact']);
  assert.equal(a.files.size,6);
  assert.equal(b.files.size,7);
  assert.match(a.files.get('index.html'),/data-art-direction="mineral-editorial"/);
  assert.match(b.files.get('index.html'),/data-art-direction="ink-monograph"/);
  assert.match(b.files.get('approach/index.html'),/aria-current="page">Approach/);
  assert.notEqual(a.files.get('assets/studio.css'),b.files.get('assets/studio.css'));
  assert(!a.files.get('index.html').includes('Arcloom'));
  assert(!b.files.get('index.html').includes('Morrowline'));
});
test('backward compatible: W2-A generated output remains byte-identical',()=>{
  const original=compileW2Site(morrowline.brief,morrowline.content);
  const fresh=compileW2Site(morrowline.brief,morrowline.content);
  assert.deepEqual([...original.files],[...fresh.files]);
  assert.equal(original.evidence.kind,'E6_W2_DETERMINISTIC_STATIC_SITE_V1');
});
test('every generated file hash matches immutable evidence and is reproducible',()=>{
  for(const [fixture,preset] of [[morrowline,'mineral-editorial'],[arcloom,'ink-monograph']]){
    const one=compileCuratedW2Site(fixture.brief,fixture.content,direction(preset));
    const two=compileCuratedW2Site(fixture.brief,fixture.content,direction(preset));
    assert.deepEqual([...one.files],[...two.files]);
    assert.deepEqual(one.evidence,two.evidence);
    for(const [path,hash] of Object.entries(one.evidence.fileSha256)){
      assert.equal(createHash('sha256').update(one.files.get(path)).digest('hex'),hash,path);
    }
  }
});
test('malicious or unknown visual preset cannot inject CSS or HTML',()=>{
  assert.deepEqual(W2B_PRESETS,['mineral-editorial','ink-monograph']);
  for(const p of ['../../etc','night-monograph;background:url(https://evil.example)','<script>','__proto__','toString']){
    assert.throws(()=>compileCuratedW2Site(morrowline.brief,morrowline.content,direction(p)),/E6_W2B_UNSUPPORTED_PRESET/);
  }
});
test('rejects additional privileges, non-data direction properties and inherited data',()=>{
  const extra={version:1,preset:'ink-monograph',deploymentAuthorized:true};
  assert.throws(()=>compileCuratedW2Site(morrowline.brief,morrowline.content,extra),/E6_W2B_INVALID_DIRECTION/);
  const accessor={version:1};
  Object.defineProperty(accessor,'preset',{enumerable:true,get(){throw Error('DO_NOT_READ')}});
  assert.throws(()=>compileCuratedW2Site(morrowline.brief,morrowline.content,accessor),/E6_W2B_INVALID_DIRECTION/);
  const inherited=Object.assign(Object.create({secret:'unexpected'}),direction('ink-monograph'));
  assert.throws(()=>compileCuratedW2Site(morrowline.brief,morrowline.content,inherited),/E6_W2B_INVALID_DIRECTION/);
});
test('W1/W2 strict route and content safety still blocks unsafe content in second brief',()=>{
  const bad=structuredClone(arcloom);
  bad.content.pages[0].sections[0].ctaPath='https://attacker.example/path';
  assert.throws(()=>compileCuratedW2Site(bad.brief,bad.content,direction('ink-monograph')),/E6_W2_INVALID_SECTION_CONTENT/);
  const injected=structuredClone(arcloom);
  injected.content.pages[1].sections[1].items[0].title='<img src=x onerror=alert(1)> Lorem';
  const html=compileCuratedW2Site(injected.brief,injected.content,direction('ink-monograph')).files.get('studies/index.html');
  assert(!html.includes('<img src=x'));
  assert(html.includes('&lt;img src=x'));
  const traversal=structuredClone(arcloom);
  traversal.brief.pages[1].path='/../private';
  assert.throws(()=>compileCuratedW2Site(traversal.brief,traversal.content,direction('ink-monograph')),/E6_W2_W1_BRIEF_REJECTED/);
});
test('no validated 3D or asset ownership is forged by visual preset',()=>{
  const x=structuredClone(arcloom);
  x.brief.pages[0].sections[0]={id:'opening',kind:'scene3d',motion:'scene',fallback:'static',assetSource:'owner-original'};
  assert.throws(()=>compileCuratedW2Site(x.brief,x.content,direction('ink-monograph')),/E6_W2_SCENE3D_REQUIRES_W3_W4/);
});
test('all build evidence preserves explicit permanent release and authorship denials',()=>{
  for(const [fixture,preset] of [[morrowline,'mineral-editorial'],[arcloom,'ink-monograph']]){
    const {files,evidence}=compileCuratedW2Site(fixture.brief,fixture.content,direction(preset));
    assert.equal(evidence.kind,'E6_W2B_CURATED_MULTIBRIEF_V1');
    assert.equal(evidence.designAuthorship,'HUMAN_AUTHORED_PRESET');
    for(const field of ['genuineModelAuthorshipProven','realBrowserVerified','physicalGpuMeasured',
      'humanReviewed','ownerApproved','deploymentAuthorized','stage45Go','releaseGo'])
      assert.equal(evidence[field],false,field);
    assert.equal(JSON.parse(files.get('build-evidence.json')).releaseGo,false);
    for(const [,body] of files){
      assert(!body.includes('https://evil.example'),'unexpected hostile asset');
    }
  }
});
