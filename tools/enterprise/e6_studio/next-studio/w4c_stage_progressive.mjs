/* E6 W4-C: fixed, fail-closed write-once optimization over W4-B.
 * Source-bound higher-sample Blender still and truly click-only 3D engine.
 * Review-only; no arbitrary paths, CDN, production deploy or overwrite.
 */
import {readFile,readdir,lstat,cp,writeFile,copyFile,unlink} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as esbuild from 'esbuild';
const here=dirname(fileURLToPath(import.meta.url));
const root=join(here,'../../../..');
const base=join(here,'target/e6-w4b-arcloom');
const dest=join(here,'target/e6-w4c-arcloom');
const renderRoot=join(root,'target/e6-studio/w4c-signal-fold');
const sha=v=>createHash('sha256').update(v).digest('hex');
const fail=v=>{throw Error('E6_W4C_'+v)};
async function checkTree(path){
  if((await lstat(path)).isSymbolicLink())fail('SYMLINK_SOURCE');
  const inspect=async dir=>{
    for(const item of await readdir(dir,{withFileTypes:true})){
      const path=join(dir,item.name),st=await lstat(path);
      if(st.isSymbolicLink()||(!st.isDirectory()&&!st.isFile()))fail('UNTRUSTED_SOURCE_ENTRY');
      if(st.isFile()&&st.size>3_000_000)fail('INPUT_BUDGET');
      if(st.isDirectory())await inspect(path);
    }
  };await inspect(path);
}
async function main(){
  if(process.argv.length!==2)fail('NO_USER_SUPPLIED_PATHS');
  if(existsSync(dest))fail('NO_OVERWRITE');
  await checkTree(base);await checkTree(renderRoot);
  const [parentRaw,renderRaw,still,blend,script,html,css,glb,pkg]=await Promise.all([
    readFile(join(base,'assets/w4b-art-direction-evidence.json')),
    readFile(join(renderRoot,'signal-fold-still.provenance.json')),
    readFile(join(renderRoot,'signal-fold-still.png')),
    readFile(join(root,'target/e6-studio/w3b-signal-fold/arcloom-signal-fold.blend')),
    readFile(join(here,'blender/w4c_render_signal_fold_still.py')),
    readFile(join(base,'studies/signal-fold/index.html'),'utf8'),
    readFile(join(base,'assets/signal-fold-scene.css'),'utf8'),
    readFile(join(base,'assets/arcloom-signal-fold.glb')),
    readFile(join(here,'package.json')).then(JSON.parse)
  ]);
  const parent=JSON.parse(parentRaw),render=JSON.parse(renderRaw);
  if(parent.kind!=='E6_W4B_ART_DIRECTION_RESEARCH_V1'||
    parent.stage45Go!==false||parent.releaseGo!==false||
    parent.modelSha256!==sha(glb)||parent.modelBytes!==glb.length||
    render.kind!=='E6_W4C_ORIGINAL_BLENDER_STILL_V1'||
    render.samples!==48||render.resolution?.[0]!==800||render.resolution?.[1]!==900||
    render.independentArtDirectionApproved!==false||
    render.ownerApproved!==false||render.releaseGo!==false||render.stage45Go!==false||
    render.sourceBlenderSha256!==sha(blend)||
    render.renderScriptSha256!==sha(script)||
    render.imageSha256!==sha(still)||render.imageBytes!==still.length||
    still.subarray(0,8).toString('hex')!=='89504e470d0a1a0a'||
    still.readUInt32BE(16)!==800||still.readUInt32BE(20)!==900||
    pkg.dependencies.three!=='0.186.1'||pkg.dependencies.gsap!=='3.15.0'||
    !html.includes('data-w4a-scene')||
    !html.includes('src="/assets/signal-fold-scene.mjs"')||
    !html.includes('noindex,nofollow')||
    !html.includes('connect-src &#39;self&#39;')||
    !css.includes('signal-fold-reference.png'))
    fail('UNVERIFIED_SOURCE_OR_PREVIEW');
  await cp(base,dest,{recursive:true,force:false,errorOnExist:true});
  await copyFile(join(renderRoot,'signal-fold-still.png'),
    join(dest,'assets/signal-fold-refined.png'));
  const cssUpdated=css.replaceAll('signal-fold-reference.png','signal-fold-refined.png');
  await writeFile(join(dest,'assets/signal-fold-scene.css'),cssUpdated);
  const newHtml=html.replace(
    'src="/assets/signal-fold-scene.mjs"',
    'src="/assets/signal-fold-bootstrap.mjs"')
    .replace('Original Blender studio render available. Optional 3D is not yet active.',
       'Refined first-party Blender studio render available. Optional 3D is not yet active.');
  await writeFile(join(dest,'studies/signal-fold/index.html'),newHtml);
  await unlink(join(dest,'assets/signal-fold-scene.mjs'));
  const opts={bundle:true,format:'esm',platform:'browser',target:['es2022'],
    minify:true,sourcemap:false,legalComments:'inline',logLevel:'warning'};
  await esbuild.build({
    ...opts,entryPoints:[join(here,'w4c_demand_viewer.mjs')],
    outfile:join(dest,'assets/signal-fold-engine.mjs'),
    define:{__W4B_VERIFIED_MODEL_SHA__:JSON.stringify(sha(glb))}
  });
  await esbuild.build({
    ...opts,entryPoints:[join(here,'w4c_bootstrap.mjs')],
    outfile:join(dest,'assets/signal-fold-bootstrap.mjs')
  });
  const [entry,engine]=await Promise.all([
    readFile(join(dest,'assets/signal-fold-bootstrap.mjs')),
    readFile(join(dest,'assets/signal-fold-engine.mjs'))
  ]);
  if(entry.length>12_000||engine.length>780_000)fail('SCRIPT_SIZE_BUDGET');
  const result={
    kind:'E6_W4C_PROGRESSIVE_CINEMATIC_RESEARCH_V1',
    brand:'Arcloom Objects',fictionalStudy:'Signal Fold',
    parentW4BSha256:sha(parentRaw),
    modelSha256:sha(glb),modelBytes:glb.length,
    fallbackImageSha256:sha(still),fallbackImageBytes:still.length,
    fallbackSamples:render.samples,fallbackSourceBlenderSha256:sha(blend),
    initialBootstrapSha256:sha(entry),initialBootstrapBytes:entry.length,
    optionalSceneSha256:sha(engine),optionalSceneBytes:engine.length,
    initial3DBundleRequestsExpected:0,
    optionalSceneOnlyAfterDirectActivation:true,
    canvasDprCap:1.25,noRedundantCanvasResize:true,
    nativeScroll:true,modelHashBeforeParse:true,noWebGLFallback:true,
    visualQualityIndependentlyAccepted:false,physicalGpuMeasured:false,
    aiModelAuthored:false,rightsIndependentlyCertified:false,
    ownerApproved:false,releaseGo:false,stage45Go:false
  };
  await writeFile(join(dest,'assets/w4c-performance-evidence.json'),
    JSON.stringify(result,null,2)+'\n',{flag:'wx'});
  console.log('E6_W4C_PROGRESSIVE_SITE_STAGED '+JSON.stringify({
    bootstrapBytes:entry.length,optionalSceneBytes:engine.length,
    renderSamples:48,modelSha256:sha(glb),ownerApproved:false,releaseGo:false
  }));
}
main().catch(e=>{console.error('E6_W4C_STAGE_BLOCKED',e.stack||String(e));process.exitCode=1});
