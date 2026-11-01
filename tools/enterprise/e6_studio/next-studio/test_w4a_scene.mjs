import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const root=new URL('./target/e6-w4a-arcloom/',import.meta.url);
const load=async name=>readFile(new URL(name,root));
const evidence=JSON.parse(await load('assets/w4a-scene-evidence.json'));
const html=(await load('studies/signal-fold/index.html')).toString('utf8');
const js=await load('assets/signal-fold-scene.mjs');
const css=await load('assets/signal-fold-scene.css');
const model=await load('assets/arcloom-signal-fold.glb');
const hash=x=>createHash('sha256').update(x).digest('hex');

test('real local W3-B model and bundled viewer hashes are bound',()=>{
  assert.equal(evidence.kind,'E6_W4A_SOURCE_BOUND_WEBGL_STORY_V1');
  assert.equal(hash(model),evidence.modelSha256);
  assert.equal(model.length,evidence.modelBytes);
  assert.equal(hash(js),evidence.sceneScriptSha256);
  assert.equal(hash(css),evidence.sceneCssSha256);
  assert.equal(evidence.threeVersion,'0.186.1');
  assert.equal(evidence.gsapVersion,'3.15.0');
  assert.equal(evidence.esbuildVersion,'0.25.12');
});
test('isolated W4-A scene is semantic, user-controlled and has non-WebGL fallback',()=>{
  assert.equal(html.match(/data-w4a-scene/g)?.length,1);
  assert.match(html,/data-w4a-activate hidden/);
  assert.match(html,/data-w4a-fallback role="img"/);
  assert.match(html,/data-w4a-viewport hidden/);
  assert.match(html,/data-w4a-state role="status" aria-live="polite"/);
  assert.match(html,/data-w4a-pause aria-pressed="false" disabled/);
  assert.match(html,/<noscript>/);
  assert.match(html,/script type="module" src="\/assets\/signal-fold-scene.mjs"/);
  assert.match(html,/link rel="stylesheet" href="\/assets\/signal-fold-scene.css"/);
  assert.match(html,/connect-src &#39;self&#39;/);
  assert.match(html,/default-src &#39;none&#39;/);
  assert.match(html,/noindex,nofollow/);
  assert.match(html,/data-w2c-study="signal-fold"/);
  assert.equal((html.match(/section class="story-chapter"/g)||[]).length,3);
  assert.equal((html.match(/<main\b/g)||[]).length,1);
});
test('no arbitrary remote media, dangerous consent or changed release scope',()=>{
  for(const flag of ['actualModelLoadedInBrowser','independentVisualApproval',
    'physicalGpuMeasured','modelGenerated','rightsIndependentlyCertified',
    'ownerApproved','deploymentAuthorized','releaseGo','stage45Go'])
    assert.equal(evidence[flag],false,flag);
  assert.equal(evidence.defaultRendering,'STATIC_UNTIL_USER_ACTIVATES');
  assert.equal(evidence.scrollControl,'NATIVE_GSAP_CAMERA_PROGRESS_NO_PIN_NO_LENIS');
  assert.equal(evidence.noJsStatic,true);
  assert.equal(evidence.reducedMotionNoGpuLoad,true);
  assert(!html.includes('https://evil.example'));
  assert(!html.includes('cdn.jsdelivr.net'));
  assert(!html.includes('unpkg.com'));
});
test('static editorial and chapter access requires no canvas initialization',()=>{
  const text=css.toString('utf8');
  assert.match(text,/\.signal-stage__silhouette/);
  assert.match(text,/prefers-reduced-motion/);
  assert.match(text,/signal-stage__canvas\[hidden\]/);
  assert(!text.includes('scroll-snap-type:mandatory'));
  assert(!text.includes('overflow-x:hidden'));
  assert(js.length>20000);
  assert(js.length<1_000_000);
});
