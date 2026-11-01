import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {compileCuratedW2Site} from './w2b_curated_compiler.mjs';
import {compileNarrativeW2Site} from './w2c_narrative_compiler.mjs';
const json=async name=>JSON.parse(await readFile(new URL(name,import.meta.url),'utf8'));
const arcloom=await json('./w2b_arcloom_brief.json');
const spec=await json('./w2c_arcloom_studies.json');
const copy=()=>structuredClone(spec);
const compile=(s=spec)=>compileNarrativeW2Site(arcloom.brief,arcloom.content,arcloom.direction,s);

test('3 authored case studies become actual nested static routes with chapter links',()=>{
  const {files,evidence}=compile();
  assert.equal(files.size,10);
  assert.equal(evidence.caseStudyCount,3);
  assert.deepEqual(evidence.routes,[
    '/','/studies','/approach','/contact',
    '/studies/signal-fold','/studies/readable-measure','/studies/sundial-desk'
  ]);
  const archive=files.get('studies/index.html');
  assert.match(archive,/href="\/studies\/signal-fold\/"/);
  assert.match(archive,/href="\/studies\/readable-measure\/"/);
  const article=files.get('studies/signal-fold/index.html');
  assert.match(article,/<main id="main" class="story-main"/);
  assert.match(article,/<nav class="story-toc" aria-label="Case-study chapters">/);
  for(const id of ['context','response','verification']){
    assert.match(article,new RegExp('href="#'+id+'"'));
    assert.match(article,new RegExp('class="story-chapter" id="'+id+'"'));
  }
  assert.match(article,/Fictional concept/);
  assert.match(article,/noindex,nofollow/);
  assert.match(article,/Content-Security-Policy/);
  assert.match(article,/href="\/studies\/"/);
  assert.match(article,/aria-current="page">Studies/);
});
test('source W2-B compiler stays byte-identical; W2-C only adds fixed assets and routes',()=>{
  const base=compileCuratedW2Site(arcloom.brief,arcloom.content,arcloom.direction);
  const newBuild=compile();
  assert.equal(newBuild.files.get('approach/index.html'),base.files.get('approach/index.html'));
  assert.equal(newBuild.files.get('contact/index.html'),base.files.get('contact/index.html'));
  assert.equal(newBuild.files.get('index.html'),base.files.get('index.html'));
  assert.notEqual(newBuild.files.get('studies/index.html'),base.files.get('studies/index.html'));
  assert.notEqual(newBuild.files.get('assets/studio.js'),base.files.get('assets/studio.js'));
});
test('deterministic digests and exact generated output match',()=>{
  const a=compile(),b=compile();
  assert.deepEqual([...a.files],[...b.files]);
  for(const [p,h] of Object.entries(a.evidence.fileSha256))
    assert.equal(createHash('sha256').update(a.files.get(p)).digest('hex'),h,p);
});
test('rejects duplicate slug, missing source title and preexisting route collision',()=>{
  const d=copy();d.studies[1].slug='signal-fold';
  assert.throws(()=>compile(d),/E6_W2C_INVALID_STUDY_RECORD/);
  const e=copy();e.studies[1].sourceTitle='Unknown Product';
  assert.throws(()=>compile(e),/E6_W2C_INVALID_STUDY_RECORD/);
  const f=copy();f.studies[0].slug='studies';
  const g=copy();g.sourceRoute='/contact';
  assert.throws(()=>compile(g),/E6_W2C_INVALID_SOURCE_SECTION/);
});
test('denies arbitrary routes, claims, extra keys and accessor getters',()=>{
  for(const route of ['//evil','/../private','https://evil.example','/studies/']){
    const s=copy();s.sourceRoute=route;
    assert.throws(()=>compile(s),/E6_W2C_INVALID_CONFIGURATION/);
  }
  const s=copy();s.ownerApproved=true;
  assert.throws(()=>compile(s),/E6_W2C_INVALID_CONFIGURATION/);
  const t=copy();t.studies[0].script='alert(1)';
  assert.throws(()=>compile(t),/E6_W2C_INVALID_STUDY_RECORD/);
  const accessor=copy();
  Object.defineProperty(accessor.studies[0],'challenge',{get(){throw Error('SHOULD_NOT_RUN')}});
  assert.throws(()=>compile(accessor),/E6_W2C_INVALID_STUDY_RECORD/);
});
test('all fictional content escapes HTML and nested links cannot contain arbitrary destinations',()=>{
  const s=copy();
  s.studies[0].challenge='A hypothetical <script>alert(1)</script> case-study concern. People must evaluate this safely.';
  const html=compile(s).files.get('studies/signal-fold/index.html');
  assert(!html.includes('<script>alert(1)</script>'));
  assert(html.includes('&lt;script&gt;'));
  assert(!html.includes('href="https://'));
});
test('progressive enhancement must retain visible chapters without JS',()=>{
  const {files}=compile();
  const css=files.get('assets/studio.css'),js=files.get('assets/studio.js');
  assert.match(css,/\.story-toc/);
  assert.match(css,/prefers-reduced-motion/);
  assert.match(js,/IntersectionObserver/);
  assert.match(js,/pagehide/);
  assert(!css.includes('visibility:hidden'));
  assert(!css.includes('scroll-snap-type:mandatory'));
});
test('every new page and release manifest is explicit about unverified claims',()=>{
  const {files,evidence}=compile();
  for(const flag of ['genuineModelAuthorshipProven','realBrowserVerified','physicalGpuMeasured',
    'humanReviewed','ownerApproved','deploymentAuthorized','stage45Go','releaseGo'])
    assert.equal(evidence[flag],false,flag);
  assert.equal(evidence.generatedConceptDisclosure,true);
  assert.equal(JSON.parse(files.get('build-evidence.json')).stage45Go,false);
});
