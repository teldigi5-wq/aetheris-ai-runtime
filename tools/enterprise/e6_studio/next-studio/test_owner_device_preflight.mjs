import test from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {evaluateDevicePreflight} from './owner_device_preflight.mjs';

const good=()=>({
 correctRepo:true,cleanCheckout:true,exactHead:true,head:'a'.repeat(40),
 node22:true,blenderFound:true,pythonFound:true,chromeFound:true,
 playwrightFound:true,manifestValid:true,glbDigestMatch:true,staticExportFound:true,
 ci:false,wsl:false,windows:true,platform:'win32'
});
test('complete hypothetical tooling is only ready for ATTEMPT, never certified',()=>{
 const x=evaluateDevicePreflight(good());
 assert.equal(x.readyForLocalBenchmarkAttempt,true);
 assert.deepEqual(x.blockers,[]);
 assert.equal(x.actualGpuBenchmarkPerformed,false);
 assert.equal(x.physicalHardwareCertified,false);
 assert.equal(x.task7Completed,false);
 assert.equal(x.independentReviewCompleted,false);
 assert.equal(x.stage45Go,false);
 assert.equal(x.releaseGo,false);
});
test('every missing prerequisite fails closed and yields explicit blocker',()=>{
 for(const key of ['correctRepo','cleanCheckout','exactHead','node22','blenderFound','pythonFound',
  'chromeFound','playwrightFound','manifestValid','glbDigestMatch','staticExportFound']){
  const d=good();d[key]=false;
  const x=evaluateDevicePreflight(d);
  assert.equal(x.readyForLocalBenchmarkAttempt,false,key);
  assert.equal(x.blockers.length,1,key);
  assert(x.checkedPrerequisites.some(t=>t.name===key&&t.ok===false),key);
 }
});
test('CI is forbidden even when the hypothetical workstation has everything',()=>{
 const d=good();d.ci=true;
 const x=evaluateDevicePreflight(d);
 assert.equal(x.readyForLocalBenchmarkAttempt,false);
 assert(x.blockers.includes('E6_CI_CANNOT_BE_OWNER_HARDWARE'));
 assert.equal(x.actualGpuBenchmarkPerformed,false);
});
test('WSL is surfaced as a warning, never hardware attestation',()=>{
 const d=good();d.wsl=true;d.platform='linux';d.windows=false;
 const x=evaluateDevicePreflight(d);
 assert(x.notes.includes('E6_WSL_GRAPHICS_TRANSLATION_REQUIRES_SEPARATE_PHYSICAL_GPU_VERIFICATION'));
 assert.equal(x.physicalHardwareCertified,false);
});
test('missing model or export identifies rebuild without faking performance',()=>{
 const d=good();d.glbDigestMatch=false;d.staticExportFound=false;
 const x=evaluateDevicePreflight(d);
 assert.equal(x.actions.needsPreviewBuild,true);
 assert.equal(x.readyForLocalBenchmarkAttempt,false);
 assert.equal(x.actualGpuBenchmarkPerformed,false);
});
test('missing browser and playright suggests local browser dependencies',()=>{
 const d=good();d.chromeFound=false;d.playwrightFound=false;
 const x=evaluateDevicePreflight(d);
 assert.equal(x.actions.needsNativeBrowser,true);
 assert.equal(x.readyForLocalBenchmarkAttempt,false);
});
test('invalid source sha never leaks arbitrary head into preflight report',()=>{
 const d=good();d.head='not-a-commit';d.exactHead=false;
 const x=evaluateDevicePreflight(d);
 assert.equal(x.checkedCommit,null);
 assert.equal(x.readyForLocalBenchmarkAttempt,false);
 assert(!JSON.stringify(x).includes('not-a-commit'));
});
test('actual Node preflight in this environment is read-only and cannot certify hardware',()=>{
 const script=fileURLToPath(new URL('./owner_device_preflight.mjs',import.meta.url));
 const proc=spawnSync(process.execPath,[script,'--json'],{
  encoding:'utf8',timeout:15000,env:{...process.env,CI:'true',GITHUB_ACTIONS:'true'}
 });
 assert.equal(proc.status,2,proc.stderr);
 const out=JSON.parse(proc.stdout);
 assert.equal(out.kind,'E6_OWNER_DEVICE_LOCAL_PREFLIGHT_V1');
 assert.equal(out.readyForLocalBenchmarkAttempt,false);
 assert(out.blockers.includes('E6_CI_CANNOT_BE_OWNER_HARDWARE'));
 assert.equal(out.stage45Go,false);
 assert.equal(out.releaseGo,false);
 assert.equal(out.actualGpuBenchmarkPerformed,false);
 assert(!JSON.stringify(out).includes('/home/runner/'));
});
