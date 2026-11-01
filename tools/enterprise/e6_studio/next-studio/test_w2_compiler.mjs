import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {compileW2Site} from './w2_compiler.mjs';
const fixture=JSON.parse(await readFile(new URL('./w2_demo_brief.json',import.meta.url),'utf8'));
const sample=()=>structuredClone(fixture);

test('W1 approved fixture compiles real three-page standalone output',()=>{
  const {files,evidence}=compileW2Site(fixture.brief,fixture.content);
  assert.equal(files.size,6);
  assert.deepEqual(evidence.routes,['/','/work','/contact']);
  for(const p of ['index.html','work/index.html','contact/index.html','assets/studio.css','assets/studio.js','build-evidence.json']){
    assert.equal(typeof files.get(p),'string',p);
    const hash=createHash('sha256').update(files.get(p)).digest('hex');
    if(p!=='build-evidence.json') assert.equal(hash,evidence.fileSha256[p]);
  }
  assert.match(files.get('index.html'),/<main id="main">/);
  assert.match(files.get('work/index.html'),/aria-current="page">Work/);
  assert.match(files.get('contact/index.html'),/data-email-compose data-contact-email="studio@example.com"/);
  for(const page of ['index.html','work/index.html','contact/index.html']){
    const html=files.get(page);
    assert.match(html,/<meta name="robots" content="noindex,nofollow">/);
    assert.match(html,/Content-Security-Policy/);
    assert.match(html,/<a class="skip" href="#main">Skip to content/);
    assert.match(html,/href="\/work\/"/);
    assert.match(html,/href="\/contact\/"/);
    assert.match(html,/href="\/assets\/studio.css"/);
    assert.match(html,/src="\/assets\/studio.js"/);
    assert(!html.includes('https://'),'unexpected remote content');
  }
  assert.match(files.get('work/index.html'),/data-filter="identity"/);
  assert.match(files.get('work/index.html'),/data-filter-status/);
  assert.match(files.get('assets/studio.js'),/aria-pressed/);
  assert.match(files.get('assets/studio.css'),/prefers-reduced-motion/);
});
test('identical validated source produces byte-identical files and digests',()=>{
  const one=compileW2Site(fixture.brief,fixture.content);
  const two=compileW2Site(fixture.brief,fixture.content);
  assert.deepEqual([...one.files],[...two.files]);
  assert.deepEqual(one.evidence,two.evidence);
});
test('inert HTML, attributes and entities are escaped in authored demo copy',()=>{
  const x=sample();
  x.content.siteName='<script>alert(1)</script>';
  x.content.pages[0].sections[0].title='Canvas & "Type" <img src=x onerror=alert(1)>';
  const html=compileW2Site(x.brief,x.content).files.get('index.html');
  assert(!html.includes('<script>alert(1)</script>'));
  assert(!html.includes('<img src=x'));
  assert(html.includes('&lt;script&gt;'));
  assert(html.includes('&amp; &quot;Type&quot; &lt;img'));
});
test('W1 route policy rejects traversal and arbitrary URLs before write',()=>{
  for(const route of ['/../private','/open?redirect=evil','https://evil.example','//evil']){
    const x=sample();x.brief.pages[1].path=route;
    assert.throws(()=>compileW2Site(x.brief,x.content),/E6_W2_W1_BRIEF_REJECTED/);
  }
});
test('W2 refuses to hallucinate Blender, media licenses or 3D proof',()=>{
  const x=sample();
  x.brief.pages[0].sections[0]={id:'introduction',kind:'scene3d',motion:'scene',fallback:'static',assetSource:'owner-original'};
  assert.throws(()=>compileW2Site(x.brief,x.content),/E6_W2_SCENE3D_REQUIRES_W3_W4/);
  const y=sample();
  y.brief.pages[0].sections[0].assetSource='unverified';
  assert.throws(()=>compileW2Site(y.brief,y.content),/E6_W2_MEDIA_PROVENANCE_REQUIRES_W3/);
});
test('copy must match exact validated W1 page and section structure',()=>{
  const x=sample();x.content.pages[1].path='/other';
  assert.throws(()=>compileW2Site(x.brief,x.content),/E6_W2_PAGE_OR_SECTION_MISMATCH/);
  const y=sample();y.content.pages[0].sections[1].id='unauthorized';
  assert.throws(()=>compileW2Site(y.brief,y.content),/E6_W2_INVALID_SECTION_CONTENT/);
  const z=sample();z.content.pages[2].sections.pop();
  assert.throws(()=>compileW2Site(z.brief,z.content),/E6_W2_PAGE_OR_SECTION_MISMATCH/);
});
test('CTA destinations must be local and declared W1 routes',()=>{
  for(const route of ['https://evil.example','//evil','/admin','/work?x=1']){
    const x=sample();x.content.pages[0].sections[0].ctaPath=route;
    assert.throws(()=>compileW2Site(x.brief,x.content),/E6_W2_INVALID_SECTION_CONTENT/);
  }
});
test('rejects unverified email, category, missing project cards and extra keys',()=>{
  const x=sample();x.content.contactEmail='user@example.com\r\nBcc:evil@example.com';
  assert.throws(()=>compileW2Site(x.brief,x.content),/E6_W2_INVALID_CONTENT_ROOT/);
  const y=sample();y.content.pages[1].sections[1].items[0].category='admin';
  assert.throws(()=>compileW2Site(y.brief,y.content),/E6_W2_INVALID_ITEM/);
  const z=sample();z.content.pages[1].sections[1].items=[];
  assert.throws(()=>compileW2Site(z.brief,z.content),/E6_W2_ITEMS_REQUIRED/);
  const a=sample();a.content.pages[0].sections[0].script='alert(1)';
  assert.throws(()=>compileW2Site(a.brief,a.content),/E6_W2_INVALID_SECTION_CONTENT/);
});
test('data-only content rejects inherited prototypes and accessors without running getter',()=>{
  const x=sample(), root=Object.create({isAdmin:true});
  Object.assign(root,x.content);x.content=root;
  assert.throws(()=>compileW2Site(x.brief,x.content),/E6_W2_INVALID_CONTENT_ROOT/);
  const y=sample();
  Object.defineProperty(y.content.pages[1].sections[0],'title',{get(){throw Error('SHOULD_NOT_EVALUATE')}});
  assert.throws(()=>compileW2Site(y.brief,y.content),/E6_W2_INVALID_SECTION_CONTENT/);
});
test('release claims remain permanently false in generated evidence',()=>{
  const {evidence,files}=compileW2Site(fixture.brief,fixture.content);
  for(const flag of ['genuineModelAuthorshipProven','realBrowserVerified','ownerApproved','deploymentAuthorized','stage45Go','releaseGo'])
    assert.equal(evidence[flag],false,flag);
  assert.equal(JSON.parse(files.get('build-evidence.json')).stage45Go,false);
});
