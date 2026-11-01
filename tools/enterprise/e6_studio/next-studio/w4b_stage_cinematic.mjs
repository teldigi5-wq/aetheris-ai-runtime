/* W4-B: strict isolated preview wrapper over CI-proven W4-A. Fixed paths. */
import {readFile,readdir,lstat,cp,writeFile,copyFile} from 'node:fs/promises';
import {existsSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {join,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as esbuild from 'esbuild';
const here=dirname(fileURLToPath(import.meta.url));
const root=join(here,'../../../..');
const base=join(here,'target/e6-w4a-arcloom');
const dest=join(here,'target/e6-w4b-arcloom');
const stillRoot=join(root,'target/e6-studio/w4b-signal-fold');
const sha=x=>createHash('sha256').update(x).digest('hex');
const fail=x=>{throw Error('E6_W4B_'+x)};
async function inspect(rootPath){
  if((await lstat(rootPath)).isSymbolicLink())fail('SOURCE_SYMLINK');
  const walk=async d=>{
    for(const entry of await readdir(d,{withFileTypes:true})){
      const path=join(d,entry.name),s=await lstat(path);
      if(s.isSymbolicLink()||(!s.isFile()&&!s.isDirectory()))fail('UNTRUSTED_FILE');
      if(s.isFile()&&s.size>3_000_000)fail('SIZE_BUDGET');
      if(s.isDirectory())await walk(path);
    }
  };
  await walk(rootPath);
}
async function main(){
  if(process.argv.length!==2)fail('NO_USER_INPUT');
  if(existsSync(dest))fail('NO_OVERWRITE');
  await inspect(base);await inspect(stillRoot);
  const [v4,model,html,css,still,stillProof,blend,renderScript,pkg]=await Promise.all([
    readFile(join(base,'assets/w4a-scene-evidence.json')).then(JSON.parse),
    readFile(join(base,'assets/arcloom-signal-fold.glb')),
    readFile(join(base,'studies/signal-fold/index.html'),'utf8'),
    readFile(join(base,'assets/signal-fold-scene.css'),'utf8'),
    readFile(join(stillRoot,'signal-fold-still.png')),
    readFile(join(stillRoot,'signal-fold-still.provenance.json')).then(JSON.parse),
    readFile(join(root,'target/e6-studio/w3b-signal-fold/arcloom-signal-fold.blend')),
    readFile(join(here,'blender/w4b_render_signal_fold_still.py')),
    readFile(join(here,'package.json')).then(JSON.parse)
  ]);
  const pngHeader=still.subarray(0,8).equals(Buffer.from('89504e470d0a1a0a','hex'));
  if(v4.kind!=='E6_W4A_SOURCE_BOUND_WEBGL_STORY_V1'||
    v4.modelSha256!==sha(model)||v4.modelBytes!==model.length||
    v4.stage45Go!==false||v4.releaseGo!==false||
    stillProof.kind!=='E6_W4B_ORIGINAL_BLENDER_STILL_V1'||
    stillProof.imageSha256!==sha(still)||
    stillProof.imageBytes!==still.length||!pngHeader||
    still.readUInt32BE(16)!==800||still.readUInt32BE(20)!==900||
    stillProof.sourceBlenderSha256!==sha(blend)||
    stillProof.renderScriptSha256!==sha(renderScript)||
    stillProof.releaseGo!==false||stillProof.stage45Go!==false||
    stillProof.independentArtDirectionApproved!==false||
    pkg.dependencies.three!=='0.186.1'||pkg.dependencies.gsap!=='3.15.0')
    fail('INTEGRITY_OR_AUTHORITY_MISMATCH');
  if(html.split('data-w4a-scene').length!==2||
     !html.includes('script-src &#39;self&#39;')||
     !html.includes('connect-src &#39;self&#39;')||
     !html.includes('noindex,nofollow')||
     !html.includes('data-w2c-study="signal-fold"'))
    fail('ORIGINAL_HTML_CONTRACT_CHANGED');
  await cp(base,dest,{recursive:true,force:false,errorOnExist:true});
  await copyFile(join(stillRoot,'signal-fold-still.png'),
    join(dest,'assets/signal-fold-reference.png'));
  const extension=[
    '/* W4-B: real Blender Cycles CPU fallback still, not a CSS imitation. */',
    '.signal-stage{background:#292825;color:#f5f0e9}',
    '.signal-stage__canvas-shell{background:#352f2a;border-color:#91857a}',
    '.signal-stage__silhouette{background:#bbb1a3 url("/assets/signal-fold-reference.png") center/cover no-repeat}',
    '.signal-stage__silhouette:before,.signal-stage__silhouette:after{display:none!important}',
    '.signal-stage__canvas{background:#292825}',
    '.signal-stage__eyebrow{color:#f1ad99}',
    '.signal-stage__details{max-width:40ch}',
    '@media(max-width:700px){.signal-stage__canvas-shell{aspect-ratio:.88}}'
  ].join('\n')+'\n';
  await writeFile(join(dest,'assets/signal-fold-scene.css'),css+'\n'+extension);
  const nextHtml=html.replace('<p class="signal-stage__proof">',
    '<p data-w4b-shot class="signal-stage__eyebrow" aria-hidden="true">01 / Silhouette</p><p class="signal-stage__proof">')
    .replace('Static concept available. Optional 3D is not yet active.',
      'Original Blender studio render available. Optional 3D is not yet active.');
  if(nextHtml===html)fail('DID_NOT_UPDATE');
  await writeFile(join(dest,'studies/signal-fold/index.html'),nextHtml);
  const outfile=join(dest,'assets/signal-fold-scene.mjs');
  await esbuild.build({
    entryPoints:[join(here,'w4b_cinematic_viewer.mjs')],
    outfile,bundle:true,format:'esm',platform:'browser',target:['es2022'],
    minify:true,sourcemap:false,legalComments:'inline',logLevel:'warning',
    define:{__W4B_VERIFIED_MODEL_SHA__:JSON.stringify(v4.modelSha256)}
  });
  const bundle=await readFile(outfile);
  if(bundle.length>1_000_000)fail('SCRIPT_BUDGET');
  const proof={
    kind:'E6_W4B_ART_DIRECTION_RESEARCH_V1',
    parentW4A:sha(await readFile(join(base,'assets/w4a-scene-evidence.json'))),
    brand:'Arcloom Objects',fictionalStudy:'Signal Fold',
    modelSha256:sha(model),modelBytes:model.length,
    renderedFallbackSha256:sha(still),renderedFallbackBytes:still.length,
    renderedFallbackWidth:800,renderedFallbackHeight:900,
    renderEngine:'BLENDER_CYCLES_CPU',
    scriptSha256:sha(bundle),scriptBytes:bundle.length,
    cssSha256:sha(css+'\n'+extension),staticImageIsActualBlenderRender:true,
    runtimeSha256CheckBeforeParse:true,sceneCameraKeyframes:3,
    nativeScrolling:true,noPinning:true,noLenis:true,
    browserAcceptance:false,physicalGpuMeasured:false,
    humanVisualApproved:false,productPhotograph:false,
    modelGenerated:false,ownerApproved:false,
    deploymentAuthorized:false,stage45Go:false,releaseGo:false
  };
  await writeFile(join(dest,'assets/w4b-art-direction-evidence.json'),
    JSON.stringify(proof,null,2)+'\n',{flag:'wx'});
  console.log('E6_W4B_ISOLATED_CINEMATIC_STAGE_CREATED '+JSON.stringify({
    staticImageBytes:still.length,scriptBytes:bundle.length,
    actualBlenderFallback:true,browserAcceptance:false,releaseGo:false
  }));
}
main().catch(e=>{console.error('E6_W4B_BLOCKED',e.stack||String(e));process.exitCode=1;});
