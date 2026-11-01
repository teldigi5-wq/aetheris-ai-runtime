/* E6 W4-A: fixed-source local preview compiler. No arbitrary assets or URLs. */
import {readFile,readdir,lstat,mkdir,cp,writeFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as esbuild from 'esbuild';
const here=dirname(fileURLToPath(import.meta.url));
const source=join(here,'target/e6-w3b-arcloom');
const target=join(here,'target/e6-w4a-arcloom');
const story='studies/signal-fold/index.html';
const asset='assets/arcloom-signal-fold.glb';
const sha=x=>createHash('sha256').update(x).digest('hex');
const die=m=>{throw Error('E6_W4A_'+m);};
async function safeTree(root){
  if((await lstat(root)).isSymbolicLink())die('SOURCE_SYMLINK');
  async function inspect(path){
    for(const item of await readdir(path,{withFileTypes:true})){
      const file=join(path,item.name),st=await lstat(file);
      if(st.isSymbolicLink()||(!st.isFile()&&!st.isDirectory()))die('UNTRUSTED_ENTRY');
      if(st.isFile()&&st.size>2_000_000)die('SOURCE_FILE_BUDGET');
      if(st.isDirectory())await inspect(file);
    }
  }
  await inspect(root);
}
const stageMarkup="<section class=\"signal-stage\" data-w4a-scene data-engine=\"static-ready\" aria-labelledby=\"signal-stage-title\">\n<div class=\"signal-stage__intro\"><div><p class=\"signal-stage__eyebrow\">ARCLOOM / ORIGINAL 3D CONCEPT / 01</p><h2 id=\"signal-stage-title\">A study in the fold.</h2></div>\n<p class=\"signal-stage__edition\">MATERIAL / GEOMETRY / MOVEMENT<br>Research edition · not a product</p></div>\n<div class=\"signal-stage__grid\"><div class=\"signal-stage__canvas-shell\">\n<div class=\"signal-stage__silhouette\" data-w4a-fallback role=\"img\" aria-label=\"Abstract static reference representing a folded desktop design concept\"></div>\n<div class=\"signal-stage__canvas\" data-w4a-viewport hidden aria-hidden=\"true\"></div></div>\n<div class=\"signal-stage__details\"><p class=\"eyebrow\">Seven elements. Five materials.</p><h3>Geometry with intent.</h3>\n<p>An original fictional Blender design study. Optional 3D reveals real geometry and finish using a verified local model. The editorial chapters stay readable without JavaScript or graphics hardware.</p>\n<div class=\"signal-stage__actions\"><button type=\"button\" data-w4a-activate hidden>Activate 3D study</button>\n<button type=\"button\" data-w4a-pause aria-pressed=\"false\" disabled>Pause camera motion</button></div>\n<p data-w4a-state role=\"status\" aria-live=\"polite\" class=\"signal-stage__state\">Static concept available. Optional 3D is not yet active.</p>\n<noscript><p>JavaScript is disabled; the complete concept study and chapter navigation remain available.</p></noscript>\n<p class=\"signal-stage__proof\">Local source-verified GLB only · GPU rendering is optional · Camera follows native scrolling · No AI website generation</p></div></div>\n<div class=\"signal-stage__notes\" aria-label=\"Design research chapters\">\n<div><p class=\"signal-stage__eyebrow\">01 / ARRIVAL</p><strong>Read the silhouette.</strong><p>Start from the overall composition.</p></div>\n<div><p class=\"signal-stage__eyebrow\">02 / MATERIAL</p><strong>Discover the finish.</strong><p>A considered light reveals the PBR surfaces.</p></div>\n<div><p class=\"signal-stage__eyebrow\">03 / DETAIL</p><strong>Follow the fold.</strong><p>The camera guides attention without hijacking scrolling.</p></div></div></section>";
async function main(){
  if(process.argv.length!==2)die('NO_ARBITRARY_ARGUMENTS');
  if(existsSync(target))die('NO_OVERWRITE');
  await safeTree(source);
  const [raw,manifestRaw,html,css,packageRaw]=await Promise.all([
    readFile(join(source,asset)),
    readFile(join(source,'assets/w3b-signal-fold-provenance.json')),
    readFile(join(source,story),'utf8'),
    readFile(join(here,'w4a_signal_fold_scene.css'),'utf8'),
    readFile(join(here,'package.json'),'utf8')
  ]);
  const proof=JSON.parse(manifestRaw),pkg=JSON.parse(packageRaw);
  if(proof.kind!=='E6_W3B_SOURCE_VERIFIED_SIGNAL_FOLD_ASSET_V1'||
    proof.glbSha256!==sha(raw)||proof.glbBytes!==raw.length||
    proof.meshCount!==7||proof.materialCount!==5||
    proof.releaseGo!==false||proof.stage45Go!==false||
    proof.ownerApproved!==false||proof.modelGenerated!==false||
    pkg.dependencies.three!=='0.186.1'||pkg.dependencies.gsap!=='3.15.0')
    die('UNVERIFIED_ASSET_OR_LIBRARY');
  if(html.split('</section><div class="story-layout">').length!==2||
    !html.includes('data-w2c-study="signal-fold"')||
    !html.includes("script-src &#39;self&#39;;")||
    !html.includes("default-src &#39;none&#39;")||
    !html.includes('noindex,nofollow'))die('UNEXPECTED_HTML');
  const modified=html.replace('</section><div class="story-layout">',
      '</section>'+stageMarkup+'<div class="story-layout">')
    .replace("script-src &#39;self&#39;;",
      "script-src &#39;self&#39;; connect-src &#39;self&#39;;")
    .replace('</head>', '<link rel="stylesheet" href="/assets/signal-fold-scene.css"></head>')
    .replace('Browser 3D rendering and camera animation belong to W4.',
      'This isolated W4 optional scene is research-only; no production approval is implied.')
    .replace('</body>','<script type="module" src="/assets/signal-fold-scene.mjs"></script></body>');
  await mkdir(target,{recursive:false});
  await cp(source,target,{recursive:true,force:false,errorOnExist:true});
  await writeFile(join(target,story),modified);
  await writeFile(join(target,'assets/signal-fold-scene.css'),css,{flag:'wx'});
  const outfile=join(target,'assets/signal-fold-scene.mjs');
  await esbuild.build({entryPoints:[join(here,'w4a_signal_fold_viewer.mjs')],
    outfile,bundle:true,format:'esm',platform:'browser',target:['es2022'],
    minify:true,sourcemap:false,metafile:true,legalComments:'inline',logLevel:'warning'});
  const bundle=await readFile(outfile);
  if(bundle.length>1_000_000)die('BUNDLE_TOO_LARGE');
  const result={
    kind:'E6_W4A_SOURCE_BOUND_WEBGL_STORY_V1',site:'Arcloom Objects',
    fictionalStudy:'Signal Fold',modelSha256:proof.glbSha256,modelBytes:raw.length,
    sceneScriptSha256:sha(bundle),sceneScriptBytes:bundle.length,
    sceneCssSha256:sha(css),threeVersion:pkg.dependencies.three,
    gsapVersion:pkg.dependencies.gsap,esbuildVersion:'0.25.12',
    defaultRendering:'STATIC_UNTIL_USER_ACTIVATES',
    scrollControl:'NATIVE_GSAP_CAMERA_PROGRESS_NO_PIN_NO_LENIS',
    reducedMotionNoGpuLoad:true,noJsStatic:true,
    sourceValidatedAtBuild:true,actualModelLoadedInBrowser:false,
    independentVisualApproval:false,physicalGpuMeasured:false,modelGenerated:false,
    rightsIndependentlyCertified:false,ownerApproved:false,
    deploymentAuthorized:false,releaseGo:false,stage45Go:false
  };
  await writeFile(join(target,'assets/w4a-scene-evidence.json'),
      JSON.stringify(result,null,2)+'\n',{flag:'wx'});
  console.log('E6_W4A_STAGED_REAL_OPT_IN_WEBGL '+JSON.stringify({
    modelBytes:raw.length,bundleBytes:bundle.length,sourceBound:true,
    sceneBrowserVerified:false,stage45Go:false
  }));
}
main().catch(e=>{console.error('E6_W4A_STAGE_BLOCKED',String(e.stack||e));process.exitCode=1});
