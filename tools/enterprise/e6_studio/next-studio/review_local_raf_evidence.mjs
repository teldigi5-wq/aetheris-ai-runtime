/**
 * E6 local V2 browser rAF evidence review — fully offline, explicit local files.
 * No upload, no source/build certification, no GPU time, no Stage45 promotion.
 */
import {createHash} from 'node:crypto';
import {constants} from 'node:fs';
import {open,lstat} from 'node:fs/promises';
import {KIND,evaluateLocalBrowserRaf} from './local_browser_raf_policy.mjs';

const MAX_REPORT_BYTES=256*1024;
const MAX_ASSET_BYTES=5*1024*1024;
const MIN_REPORTS=2;
const MAX_REPORTS=8;
const digest=buffer=>createHash('sha256').update(buffer).digest('hex');
const fail=code=>{throw Error(code);};
const plain=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);

/** Bound size and prohibit final-component symlinks, hardlinks and mid-read changes. */
export async function readSingleLocalFile(filename,maxBytes){
 if(typeof filename!=='string'||filename.length===0||filename.length>4096)fail('E6_LOCAL_FILE_PATH_INVALID');
 const before=await lstat(filename);
 if(!before.isFile()||before.isSymbolicLink()||before.nlink!==1||
    before.size<1||before.size>maxBytes)fail('E6_LOCAL_FILE_TYPE_OR_SIZE_INVALID');
 const handle=await open(filename,constants.O_RDONLY|(constants.O_NOFOLLOW||0));
 try{
  const stat=await handle.stat();
  if(!stat.isFile()||stat.nlink!==1||stat.size!==before.size||
    stat.dev!==before.dev||stat.ino!==before.ino)fail('E6_LOCAL_FILE_CHANGED_BEFORE_READ');
  const data=Buffer.alloc(stat.size);
  let pos=0;
  while(pos<data.length){
   const {bytesRead}=await handle.read(data,pos,data.length-pos,pos);
   if(bytesRead===0)fail('E6_LOCAL_FILE_SHORT_READ');
   pos+=bytesRead;
  }
  const extra=Buffer.alloc(1);
  if((await handle.read(extra,0,1,data.length)).bytesRead!==0)fail('E6_LOCAL_FILE_GREW');
  const after=await handle.stat();
  const pathAfter=await lstat(filename);
  if(after.size!==stat.size||after.mtimeMs!==stat.mtimeMs||
     after.ctimeMs!==stat.ctimeMs||after.dev!==stat.dev||after.ino!==stat.ino||
     pathAfter.dev!==stat.dev||pathAfter.ino!==stat.ino||pathAfter.nlink!==1||
     !pathAfter.isFile()||pathAfter.isSymbolicLink())fail('E6_LOCAL_FILE_CHANGED_DURING_READ');
  return {buffer:data,sha256:digest(data)};
 }finally{await handle.close();}
}

const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
const fields=['diagnosticValid','screenBudgetMet','reasons','runs','task7Completed',
 'task8Completed','stage45Go','releaseGo','physicalHardwareCertified',
 'gpuTimeMeasured','gpuVramMeasured','thermalsMeasured','physicalMobileTested',
 'hardwareIdentityVerified','sourceCheckoutIndependentlyVerified'];

export async function reviewLocalRafEvidence({reportPaths,assetPath}){
 if(!Array.isArray(reportPaths)||reportPaths.length<MIN_REPORTS||reportPaths.length>MAX_REPORTS)
  fail('E6_LOCAL_REQUIRES_2_TO_8_REPORTS');
 const asset=await readSingleLocalFile(assetPath,MAX_ASSET_BYTES);
 if(asset.buffer.length<1024)fail('E6_LOCAL_ASSET_TOO_SMALL');
 const artifacts=[],seenSha=new Set(),seenTime=new Set();
 let cohort=null;
 let totalFrames=0,totalLongTasks=0,maxLongTaskMs=0,maxIntervalMs=0,
     worstP95Ms=0,worstP99Ms=0,over33_3Count=0;
 for(const filename of reportPaths){
  const file=await readSingleLocalFile(filename,MAX_REPORT_BYTES);
  if(seenSha.has(file.sha256))fail('E6_LOCAL_DUPLICATE_FILE_CONTENT');
  seenSha.add(file.sha256);
  let parsed;
  try{parsed=JSON.parse(file.buffer.toString('utf8'));}
  catch{fail('E6_LOCAL_REPORT_JSON_INVALID');}
  if(!plain(parsed)||!plain(parsed.report)||!plain(parsed.assessment))
   fail('E6_LOCAL_REPORT_WRAPPER_INVALID');
  const {report,assessment}=parsed;
  if(report.kind!==KIND||report.schemaVersion!==2)fail('E6_LOCAL_V2_REQUIRED');
  if(report.glbSha256!==asset.sha256)fail('E6_LOCAL_GLB_DIGEST_MISMATCH');
  if(!plain(report.renderer)||
      typeof report.renderer.unmaskedRenderer!=='string'||
      typeof report.renderer.unmaskedVendor!=='string'||
      report.renderer.unmaskedRenderer.length>512||
      report.renderer.unmaskedVendor.length>512)fail('E6_LOCAL_RENDERER_MISSING');
  const software=/swiftshader|llvmpipe|lavapipe|software raster|warp|microsoft basic render|vmware|virtualbox|virgl|angle.*null/i;
  if(software.test(report.renderer.unmaskedRenderer)||software.test(report.renderer.unmaskedVendor))
   fail('E6_LOCAL_SOFTWARE_RENDERER_REJECTED');
  if(!plain(report.metadata)||!/^Chrome\/\d+\.\d+\.\d+\.\d+$/.test(report.metadata.browser||'')||
     report.metadata.sourceCommitAttested!==false||
     report.metadata.actualGpuTimeMeasured!==false||
     report.metadata.externalUpload!==false)fail('E6_LOCAL_META_SCOPE_INVALID');
  const time=Date.parse(report.capturedAt);
  if(!Number.isFinite(time)||seenTime.has(time))fail('E6_LOCAL_DUPLICATE_CAPTURE_TIME');
  seenTime.add(time);
  const shape={viewport:report.viewport,renderer:report.renderer,
   browser:report.metadata.browser,assetSha256:report.glbSha256};
  if(cohort!==null&&!same(cohort,shape))fail('E6_LOCAL_MIXED_DEVICE_OR_VIEWPORT');
  cohort=shape;
  const recomputed=evaluateLocalBrowserRaf(report);
  if(!recomputed.diagnosticValid||!recomputed.screenBudgetMet)
   fail('E6_LOCAL_CAPTURE_POLICY_REJECTED:'+recomputed.reasons.join(','));
  if(!fields.every(field=>same(recomputed[field],assessment[field])))
   fail('E6_LOCAL_EMBEDDED_ASSESSMENT_MISMATCH');
  for(let i=0;i<report.runs.length;i++){
   const raw=report.runs[i];
   const stats=recomputed.runs[i];
   totalFrames+=stats.frames;
   totalLongTasks+=raw.longTaskCount;
   maxLongTaskMs=Math.max(maxLongTaskMs,raw.maxLongTaskMs);
   maxIntervalMs=Math.max(maxIntervalMs,...raw.frameIntervalsMs);
   worstP95Ms=Math.max(worstP95Ms,stats.p95Ms);
   worstP99Ms=Math.max(worstP99Ms,stats.p99Ms);
   over33_3Count+=raw.frameIntervalsMs.filter(x=>x>33.3).length;
  }
  artifacts.push({sha256:file.sha256,bytes:file.buffer.length,capturedAt:report.capturedAt,
   runCount:report.runs.length,frameCount:report.runs.reduce((n,r)=>n+r.frameIntervalsMs.length,0)});
 }
 artifacts.sort((a,b)=>a.capturedAt.localeCompare(b.capturedAt));
 return {
  kind:'E6_LOCAL_RAF_COHORT_REVIEW_V1',
  diagnosticReviewValid:true,preliminaryRafScreenMet:true,
  captureCount:artifacts.length,runCount:artifacts.length*3,totalFrames,
  metrics:{worstP95Ms,worstP99Ms,maxIntervalMs,over33_3Count,
    totalLongTasks,maxLongTaskMs},
  asset:{sha256:asset.sha256,bytes:asset.buffer.length},
  cohort:{sameSelfReportedRendererAndViewport:true,
   independentDeviceIdentityVerified:false,
   exactSourceCommitIndependentlyVerified:false},
  evidence:artifacts,
  measurementScope:'SELF_REPORTED_LOCAL_CHROME_WINDOW_RAF_INTERVALS_NOT_GPU_TIME',
  deviceMatrixComplete:false,physicalGpuCertified:false,realMobileCertified:false,
  task7Completed:false,task8Completed:false,stage45Go:false,releaseGo:false
 };
}

if(process.argv[1]&&import.meta.url===new URL('file://'+process.argv[1]).href){
 const args=process.argv.slice(2);
 let assetPath=null;
 const reportPaths=[];
 for(let i=0;i<args.length;i++){
  if(args[i]==='--asset'&&!assetPath&&args[i+1])assetPath=args[++i];
  else if(args[i]==='--report'&&args[i+1])reportPaths.push(args[++i]);
  else {console.error('Invalid option. Use --asset FILE --report FILE --report FILE.');process.exitCode=2;break;}
 }
 if(!process.exitCode){
  try{const result=await reviewLocalRafEvidence({reportPaths,assetPath});
   process.stdout.write(JSON.stringify(result,null,2)+'\n');
  }catch(error){
   console.error('E6_LOCAL_RAF_REVIEW_BLOCKED:',error instanceof Error?error.message:'unknown error');
   process.exitCode=1;
  }
 }
}
