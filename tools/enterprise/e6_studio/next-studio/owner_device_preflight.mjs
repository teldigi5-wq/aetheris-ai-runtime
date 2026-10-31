#!/usr/bin/env node
/** E6 Task 7 owner hardware attempt preflight — read-only/no GPU claims.
 * Built-ins only. No browser launch, Blender run, network, telemetry or uploads.
 * Usage: node tools/enterprise/e6_studio/next-studio/owner_device_preflight.mjs
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const file=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(file,'../../../../');
const repo='teldigi5-wq/aetheris-ai-runtime';
const HEX40=/^[a-f0-9]{40}$/;
const HEX64=/^[a-f0-9]{64}$/;
const isTrue=x=>x===true;

export function evaluateDevicePreflight(data){
 const checks=[
  ['correctRepo','E6_WRONG_REPOSITORY_OR_ORIGIN'],
  ['cleanCheckout','E6_TRACKED_SOURCE_DIRTY'],
  ['exactHead','E6_GIT_HEAD_UNVERIFIED'],
  ['node22','E6_NODE22_REQUIRED'],
  ['blenderFound','E6_BLENDER_NOT_FOUND'],
  ['pythonFound','E6_PYTHON_NOT_FOUND'],
  ['chromeFound','E6_LOCAL_CHROME_NOT_FOUND'],
  ['playwrightFound','E6_PLAYWRIGHT_NOT_INSTALLED'],
  ['manifestValid','E6_STAGED_BLENDER_PROVENANCE_MISSING'],
  ['glbDigestMatch','E6_PREVIEW_GLB_UNBUILT_OR_STALE'],
  ['staticExportFound','E6_NEXT_PREVIEW_STATIC_EXPORT_MISSING'],
 ];
 const blockers=checks.filter(([name])=>!isTrue(data?.[name])).map(([,reason])=>reason);
 if(data?.ci===true)blockers.push('E6_CI_CANNOT_BE_OWNER_HARDWARE');
 const notes=[];
 if(data?.wsl===true)notes.push('E6_WSL_GRAPHICS_TRANSLATION_REQUIRES_SEPARATE_PHYSICAL_GPU_VERIFICATION');
 if(data?.windows===true)notes.push('E6_WINDOWS_USE_NATIVE_CHROME_AND_BROWSER_GPU_DETAILS');
 notes.push('E6_REAL_GPU_FRAME_TIMES_VRAM_THERMALS_AND_MOBILE_NOT_MEASURED');
 return {
  kind:'E6_OWNER_DEVICE_LOCAL_PREFLIGHT_V1',
  repository:repo,
  checkedCommit:HEX40.test(data?.head||'')?data.head:null,
  environment:{platform:data?.platform||'unknown',wsl:isTrue(data?.wsl),ci:isTrue(data?.ci)},
  checkedPrerequisites:checks.map(([name,reason])=>({name,ok:isTrue(data?.[name]),reasonIfMissing:isTrue(data?.[name])?null:reason})),
  readyForLocalBenchmarkAttempt:blockers.length===0,
  blockers,notes,
  actions:{
   missingToolchain:!data?.blenderFound||!data?.pythonFound||!data?.node22,
   needsPreviewBuild:!data?.manifestValid||!data?.glbDigestMatch||!data?.staticExportFound,
   needsNativeBrowser:!data?.chromeFound||!data?.playwrightFound
  },
  actualGpuBenchmarkPerformed:false,
  physicalHardwareCertified:false,task7Completed:false,
  independentReviewCompleted:false,stage45Go:false,releaseGo:false
 };
}

function git(args){
 try{return execFileSync('git',args,{cwd:root,encoding:'utf8',timeout:5000,stdio:['ignore','pipe','ignore']}).trim();}
 catch{return null;}
}
async function exists(name){
 try{return (await fs.stat(name)).isFile();}catch{return false;}
}
async function availableExecutable(names){
 const envPaths=(process.env.PATH||'').split(path.delimiter).filter(Boolean);
 const suffixes=process.platform==='win32'?['.exe','.cmd','.bat','']:[''];
 for(const loc of envPaths){
  for(const name of names){
   for(const ext of suffixes){
    if(await exists(path.join(loc,name+ext)))return true;
   }
  }
 }
 return false;
}
async function systemChromeFound(){
 if(await availableExecutable(['google-chrome','google-chrome-stable','chrome']))return true;
 if(process.platform==='win32'){
  const dirs=[process.env.PROGRAMFILES,process.env['PROGRAMFILES(X86)'],process.env.LOCALAPPDATA].filter(Boolean);
  for(const dir of dirs)if(await exists(path.join(dir,'Google','Chrome','Application','chrome.exe')))return true;
 }
 if(process.platform==='darwin')return exists('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome');
 return false;
}

export async function collectDevicePreflight(){
 const origin=git(['remote','get-url','origin'])||'';
 const head=git(['rev-parse','HEAD'])||'';
 const clean=git(['status','--porcelain','--untracked-files=no']);
 const node22=Number(process.versions.node.split('.')[0])===22;
 const pythonFound=await availableExecutable(['python3','python','py']);
 const blenderFound=await availableExecutable(['blender']);
 const chromeFound=await systemChromeFound();
 const require=createRequire(import.meta.url);
 let playwrightFound=false;
 try{require.resolve('playwright');playwrightFound=true;}catch{}
 const manifestPath=path.join(root,'target/e6-studio/blender-preview-stage.json');
 const glbPath=path.join(file,'out/e6-blender-preview.glb');
 let manifestValid=false,glbDigestMatch=false;
 try{
  const stat=await fs.stat(manifestPath);
  if(stat.isFile()&&stat.size<256*1024&&stat.size>0){
   const proof=JSON.parse(await fs.readFile(manifestPath,'utf8'));
   manifestValid=proof.kind==='E6_BLENDER_OPT_IN_RESEARCH_PREVIEW_ONLY'&&
     proof.stage45Go===false&&HEX64.test(proof.previewGlbSha256||'');
   if(manifestValid){
    const glbInfo=await fs.stat(glbPath);
    // A preflight must not allocate an unbounded user-supplied GLB.
    if(glbInfo.isFile()&&glbInfo.size>10000&&glbInfo.size<=5*1024*1024){
     const binary=await fs.readFile(glbPath);
     glbDigestMatch=binary.length===glbInfo.size&&
       createHash('sha256').update(binary).digest('hex')===proof.previewGlbSha256;
    }
   }
  }
 }catch{}
 let wsl=false;
 if(process.platform==='linux'){
  try{const info=await fs.readFile('/proc/version','utf8');wsl=/microsoft|wsl/i.test(info);}
  catch{}
 }
 return evaluateDevicePreflight({
  correctRepo:/github\.com[:/]teldigi5-wq\/aetheris-ai-runtime(?:\.git)?$/.test(origin),
  cleanCheckout:clean==='',exactHead:HEX40.test(head),head,
  node22,pythonFound,blenderFound,chromeFound,playwrightFound,
  manifestValid,glbDigestMatch,
  staticExportFound:await exists(path.join(file,'out/blender-preview/index.html')),
  ci:Boolean(process.env.CI||process.env.GITHUB_ACTIONS),
  wsl,windows:process.platform==='win32',platform:process.platform
 });
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 if(process.argv.length!==2&&!(process.argv.length===3&&process.argv[2]==='--json')){
  throw Error('E6_USAGE: node owner_device_preflight.mjs [--json]');
 }
 const result=await collectDevicePreflight();
 console.log(JSON.stringify(result,null,2));
 if(!result.readyForLocalBenchmarkAttempt)process.exitCode=2;
}
