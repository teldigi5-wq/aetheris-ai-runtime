import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {review} from '../e7_agents/e7_offline_delegation.mjs';
import {build,plan,normalizeReview,summarize,run}
  from './e8_mission_board.mjs';
const FILE=fileURLToPath(new URL('./e8_mission_board.mjs',import.meta.url));
const A='984f254a-b4a7-4c4e-84ba-34056d28bb76';
const B='b74e3515-67aa-4d7b-8981-f4f150d10db0';
const HTML='<!doctype html><html lang="en"><head>'+
 '<link rel="stylesheet" href="./styles.css">'+
 '<script src="./scene.js" defer></script></head><body>'+
 '<a class="skip-link" href="#main">Skip</a>'+
 '<main id="main"><h1>Local site for review</h1></main></body></html>';
const CSS='@media(prefers-reduced-motion:reduce){*{animation:none}}';
const JS="'use strict';document.documentElement.dataset.local='only';";

async function folder(t,contents={}){
  const base=await fs.mkdtemp(path.join(os.tmpdir(),'e8-review-'));
  t.after(()=>fs.rm(base,{recursive:true,force:true}));
  for(const [n,v] of Object.entries({
    'index.html':HTML,'scene.js':JS,'styles.css':CSS,...contents
  }))await fs.writeFile(path.join(base,n),v);
  return base;
}
async function examples(t){
  const clean=await folder(t);
  const malicious=await folder(t,{'scene.js':'fetch("https://external.invalid/secret");'});
  const first=await review({workspace:clean,requestId:A,operatorOptIn:'yes'});
  const second=await review({workspace:malicious,requestId:B,operatorOptIn:'yes'});
  return [first,second];
}
async function sourceDir(t,reviews){
  const root=await fs.mkdtemp(path.join(os.tmpdir(),'e8-traces-'));
  t.after(()=>fs.rm(root,{recursive:true,force:true}));
  for(const evidence of reviews)await fs.writeFile(
    path.join(root,'review-'+evidence.requestId+'.json'),JSON.stringify(evidence));
  return root;
}

test('default CLI is plan-only without data reads or side effects',()=>{
  const value=plan();
  assert.equal(value.status,'PLAN_ONLY_NO_MISSION_DATA_READ');
  assert.equal(value.noNetwork,true);
  assert.equal(value.authenticatedTenantAccess,false);
  const result=spawnSync(process.execPath,[FILE,'--source-dir',
    '/intentionally-missing/private-path'],{encoding:'utf8',timeout:5000});
  assert.equal(result.status,0,result.stderr);
  assert.deepEqual(JSON.parse(result.stdout),value);
});

test('two actual E7 reviews compile into honest E8 case state and findings',async t=>{
  const cases=await examples(t);
  const normalized=summarize(cases);
  assert.deepEqual(normalized.summary,{
    total:2,pending:1,denied:1,failedChecks:1
  });
  assert.equal(normalized.ownerApproved,false);
  assert.equal(normalized.authenticatedTenantAccess,false);
  assert.equal(normalized.liveMissionAccess,false);
  const denied=normalized.cases.find(c=>c.status==='STATIC_DENIAL');
  assert.equal(denied.id,B);
  assert.equal(denied.flags.find(f=>f.name==='noRemoteJs').pass,false);
  assert.deepEqual(denied.audit.map(a=>a.type),[
    'PLAN','INTEGRITY_AGENT','SAFETY_AGENT','REVIEWER'
  ]);
});

test('artifact compiles to stable three-file browser app plus SHA-256 manifest',async t=>{
  const [one,two]=await examples(t);
  const first=await build([two,one]);
  const second=await build([one,two]);
  assert.deepEqual(first.manifest,second.manifest);
  assert.deepEqual(first.files,second.files);
  assert.deepEqual(Object.keys(first.files).sort(),
    ['app.js','index.html','manifest.json','styles.css']);
  assert.equal(first.manifest.artifactClass,'E8_OFFLINE_MISSION_REVIEW_ONLY');
  for(const entry of first.manifest.files){
    const bytes=first.files[entry.name];
    assert.equal(entry.bytes,bytes.length);
    assert.match(entry.sha256,/^[a-f0-9]{64}$/);
  }
  const text=first.files['index.html'].toString();
  assert(text.includes('Content-Security-Policy'));
  assert(text.includes('id="e8-data"'));
  assert(text.includes('noindex,nofollow'));
  assert(!text.includes('https://external.invalid'));
  assert(!text.includes('mailto:'));
  assert(!text.includes('<form'));
  assert(!text.includes('fetch('));
  assert.equal(first.manifest.ownerAuthenticated,false);
  assert.equal(first.manifest.stage45Go,false);
});

test('tampered E7 traces and contradictory verdicts are rejected',async t=>{
  const [accepted]=await examples(t);
  for(const mutant of [
    {...accepted,rootSha256:'0'.repeat(64)},
    {...accepted,ownerApproved:true},
    {...accepted,status:'STATIC_REVIEW_DENIED'},
    {...accepted,unknown:'GRANTED'},
    {...accepted,inputSha256:'0'.repeat(64)},
    {...accepted,workerProcessCount:3}
  ]) assert.throws(()=>normalizeReview(mutant),/E8_/);
  const chain=structuredClone(accepted);
  chain.trace[2].payload.evidence.checks.noRemoteJs=false;
  assert.throws(()=>normalizeReview(chain));
});

test('duplicate IDs, empty and oversized collections denied without output',async t=>{
  const [one,two]=await examples(t);
  assert.throws(()=>summarize([]),/E8_CASE_LIMIT/);
  assert.throws(()=>summarize(Array(13).fill(one)),/E8_CASE_LIMIT/);
  assert.throws(()=>summarize([one,structuredClone(one)]),/E8_DUPLICATE_REVIEW_ID/);
  assert.equal(summarize([two]).summary.denied,1);
});

test('CLI requires opt-in, absolute paths and nonexisting output directory',async t=>{
  const reviews=await examples(t);
  const source=await sourceDir(t,reviews);
  const parent=await fs.mkdtemp(path.join(os.tmpdir(),'e8-output-'));
  t.after(()=>fs.rm(parent,{recursive:true,force:true}));
  const target=path.join(parent,'board');
  let result=await run(['--run-local','--source-dir',source,'--output-dir',target],
     {AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'no'});
  assert.equal(result.exit,2);
  await assert.rejects(fs.stat(target),{code:'ENOENT'});
  result=await run(['--run-local','--source-dir',source,'--output-dir',target],
    {AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'yes'});
  assert.equal(result.exit,0,result.result.status);
  assert.equal(result.result.caseCount,2);
  assert.equal(result.result.ownerAuthenticated,false);
  for(const name of ['index.html','styles.css','app.js','manifest.json']){
    assert((await fs.stat(path.join(target,name))).size>0);
  }
  const again=await run(['--run-local','--source-dir',source,'--output-dir',target],
    {AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'yes'});
  assert.equal(again.exit,2);
  const manifest=JSON.parse(await fs.readFile(path.join(target,'manifest.json'),'utf8'));
  assert.equal(manifest.inputCaseCount,2);
  assert.equal(manifest.deploymentAuthorized,false);
  const relative=await run(['--run-local','--source-dir','../relative',
    '--output-dir',path.join(parent,'bad')],
    {AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'yes'});
  assert.equal(relative.exit,2);
});

test('extra source file, symlink and malicious JSON fail closed',async t=>{
  const reviews=await examples(t);
  const source=await sourceDir(t,reviews);
  const dest=path.join(os.tmpdir(),'e8-never-written-'+process.pid);
  await fs.writeFile(path.join(source,'credentials.txt'),'synthetic');
  let res=await run(['--run-local','--source-dir',source,'--output-dir',dest],
    {AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'yes'});
  assert.equal(res.exit,2);
  await fs.rm(path.join(source,'credentials.txt'));
  const firstPath=path.join(source,'review-'+A+'.json');
  await fs.writeFile(firstPath,'{"schema":1,"ownerApproved":true}');
  res=await run(['--run-local','--source-dir',source,'--output-dir',dest],
    {AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'yes'});
  assert.equal(res.exit,2);
  await fs.rm(firstPath);
  try{await fs.symlink(path.join(source,'review-'+B+'.json'),firstPath);}
  catch{t.skip('OS symlink creation unavailable');return;}
  res=await run(['--run-local','--source-dir',source,'--output-dir',dest],
    {AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'yes'});
  assert.equal(res.exit,2);
});

test('source IDs must equal file basename; stale/forged labels fail closed',async t=>{
  const reviews=await examples(t);
  const source=await sourceDir(t,reviews);
  await fs.rename(path.join(source,'review-'+A+'.json'),
    path.join(source,'review-4dc668c6-d01c-4f06-98bb-d979285a3179.json'));
  const parent=await fs.mkdtemp(path.join(os.tmpdir(),'e8-out-'));
  t.after(()=>fs.rm(parent,{recursive:true,force:true}));
  const res=await run(['--run-local','--source-dir',source,
    '--output-dir',path.join(parent,'out')],{
    AETHERIS_E8_OFFLINE_DASHBOARD_OPT_IN:'yes'
  });
  assert.equal(res.exit,2);
});

test('review-only output contains no source files, web requests or fake live metrics',async t=>{
  const [one,two]=await examples(t);
  const product=await build([one,two]);
  assert(!Object.keys(product.files).includes('scene.js'));
  assert(!Object.keys(product.files).includes('credentials.json'));
  const css=product.files['styles.css'].toString();
  const script=product.files['app.js'].toString();
  assert(css.includes('prefers-reduced-motion:reduce'));
  assert(!script.includes('fetch('));
  assert(!script.includes('XMLHttpRequest'));
  assert(!script.includes('localStorage'));
  assert(!script.includes('innerHTML'));
  assert(script.includes('textContent'));
  assert(product.files['index.html'].toString().includes('Stage 45: NO_GO'));
});
