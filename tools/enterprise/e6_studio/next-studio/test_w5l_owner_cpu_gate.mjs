import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,mkdir,writeFile,rm,symlink} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {CPU_LABEL,authorizePairedCpuLabelEvent,authorizeLivePairedCpuPr} from './w5l_owner_cpu_gate.mjs';
const SHA='a'.repeat(40),REPO='teldigi5-wq/aetheris-ai-runtime';
const BASE='feat/e6-w5i-local-inference-completion-20261102';
const pr=()=>({number:275,state:'open',draft:true,merged_at:null,
 user:{login:'teldigi5-wq'},
 head:{sha:SHA,repo:{full_name:REPO}},
 base:{ref:BASE,repo:{full_name:REPO}},
 labels:[{name:CPU_LABEL}]});
const event=()=>({action:'labeled',label:{name:CPU_LABEL},pull_request:pr()});
const ctx=()=>({actor:'teldigi5-wq',repository:REPO,expectedPr:275,headSha:SHA});
const reject=fn=>assert.throws(fn,/E6_W5L_/);
test('new owner-only approval event is not W5-J and does not grant release',()=>{
 assert.notEqual(CPU_LABEL,'e6-w5j-three-brief-cpu-approved');
 const accepted=authorizePairedCpuLabelEvent(event(),ctx());
 assert.equal(accepted.allowedCases,6);
 assert.equal(accepted.gpuAuthorized,false);
 assert.equal(accepted.releaseGo,false);
 for(const mutator of [
   x=>{x.action='synchronize';},
   x=>{x.label.name='e6-w5j-three-brief-cpu-approved';},
   x=>{x.pull_request.labels=[];},
   x=>{x.pull_request.labels.push({name:CPU_LABEL});},
   x=>{x.pull_request.head.sha='b'.repeat(40);},
   x=>{x.pull_request.head.repo.full_name='outsider/fork';},
   x=>{x.pull_request.base.ref='main';},
   x=>{x.pull_request.base.repo.full_name='outsider/fork';},
   x=>{x.pull_request.user.login='not-owner';},
   x=>{x.pull_request.merged_at='2026-01-01T00:00:00Z';},
   x=>{x.pull_request.draft=false;},
   x=>{x.pull_request.state='closed';}
 ]){
   const item=event();mutator(item);
   reject(()=>authorizePairedCpuLabelEvent(item,ctx()));
 }
 for(const mod of [
   c=>{c.actor='outsider';},
   c=>{c.repository='outsider/fork';},
   c=>{c.expectedPr=274;},
   c=>{c.headSha='b'.repeat(40);}
 ]){
   const c=ctx();mod(c);reject(()=>authorizePairedCpuLabelEvent(event(),c));
 }
});
test('current PR approval recheck fails on revoked label, head change or merge',()=>{
 const ok=authorizeLivePairedCpuPr(pr(),ctx());
 assert.equal(ok.newLabelStillPresent,true);
 assert.equal(ok.productionAuthorized,false);
 for(const mutator of [
  x=>{x.labels=[];},x=>{x.labels=[{name:'e6-w5j-three-brief-cpu-approved'}];},
  x=>{x.labels.push({name:CPU_LABEL});},
  x=>{x.head.sha='b'.repeat(40);},
  x=>{x.head.repo.full_name='outsider/fork';},
  x=>{x.base.ref='main';},
  x=>{x.draft=false;},
  x=>{x.state='closed';},
  x=>{x.merged_at='2026-11-03T12:00:00Z';}
 ]){
  const value=pr();mutator(value);
  assert.throws(()=>authorizeLivePairedCpuPr(value,ctx()),
    /LIVE_PAIRED_CPU_APPROVAL_REVOKED_OR_HEAD_CHANGED/);
 }
});
test('separate W5-L workflow can only run on explicit new owner label',async()=>{
 const wf=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5l-optin-paired-cpu.yml',import.meta.url),'utf8');
 assert(wf.includes('types:\n      - labeled'));
 assert(wf.includes("github.event.label.name == 'e6-w5l-six-cell-cpu-approved'"));
 assert(wf.includes("github.actor == 'teldigi5-wq'"));
 assert(wf.includes("github.event.pull_request.number == 275"));
 assert(wf.includes('persist-credentials: false'));
 assert(wf.includes('timeout-minutes: 55'));
 assert(wf.includes('OLLAMA_LLM_LIBRARY: cpu'));
 assert(wf.includes("CUDA_VISIBLE_DEVICES: '-1'"));
 assert(wf.includes("HIP_VISIBLE_DEVICES: '-1'"));
 assert(wf.includes('MODEL_TAG: qwen2.5:0.5b'));
 assert(wf.includes('checks: read'));
 assert(!wf.includes('permissions: write'));
 assert(!wf.includes('pull_request_target:'));
 assert(!wf.includes('workflow_dispatch:'));
 assert(!wf.includes('schedule:'));
 assert(!wf.includes('e6-w5j-three-brief-cpu-approved'));
 assert(!wf.includes('E6_W5C_ALLOW_LOCAL_INFERENCE=YES'));
 const steps=[
  'Verify NEW owner label event and exact checked-out SHA BEFORE downloads',
  'Demand successful model-free CI on exactly the labeled commit',
  'Recheck live owner label before downloading CPU runtime or model',
  'Download SHA-pinned CPU-only Ollama v0.40.13 into ephemeral runner',
  'Pull exactly one research model on CPU and verify the published tag prefix',
  'Recheck LIVE owner approval again immediately before paired inference',
  'Attempt exactly three held-out compact-versus-staged pairs on CPU'
 ];
 const positions=steps.map(x=>wf.indexOf(x));
 assert(positions.every(x=>x>=0));
 assert.deepEqual(positions,[...positions].sort((a,b)=>a-b));
 assert.equal((wf.match(/node w5l_owner_cpu_cli\.mjs live-approval/g)||[]).length,2);
 assert.equal((wf.match(/node w5l_owner_cpu_cli\.mjs run/g)||[]).length,1);
 assert(wf.includes('target/e6-w5l-owner-cpu-review/'));
 assert(!wf.includes('target/e6-w5c-local-css-research/'));
 assert(wf.includes('retention-days: 7'));
});
const runner=process.env.RUNNER_TEMP;
test('real CLI accepts synthetic exact-head label and live PR; refuses revoked approval BEFORE run', {
 skip:!(typeof runner==='string'&&runner.startsWith('/home/runner/work/_temp'))
},async()=>{
 const base=await mkdtemp(join(runner,'w5l-gate-test-'));
 try{
  const ep=join(base,'event.json'),lp=join(base,'w5l-live-owner-pr.json');
  await writeFile(ep,JSON.stringify(event()),{mode:0o600});
  await writeFile(lp,JSON.stringify(pr()),{mode:0o600});
  const env={...process.env,GITHUB_EVENT_NAME:'pull_request',
   GITHUB_REPOSITORY:REPO,GITHUB_ACTOR:'teldigi5-wq',
   GITHUB_EVENT_PATH:ep,RUNNER_TEMP:base,
   E6_W5L_ENABLE_OWNER_LABELED_CPU_TRIAL:'YES',
   E6_W5L_APPROVED_PR_NUMBER:'275',
   E6_W5L_CHECKED_OUT_PR_HEAD:SHA};
  const cli=fileURLToPath(new URL('./w5l_owner_cpu_cli.mjs',import.meta.url));
  const invoke=(mode,e=env)=>spawnSync(process.execPath,[cli,mode],{
    env:e,encoding:'utf8',timeout:8000
  });
  assert.equal(invoke('authorize').status,0);
  assert.equal(invoke('live-approval').status,0);
  assert.equal(invoke('authorize',{...env,GITHUB_ACTOR:'outsider'}).status,1);
  assert.equal(invoke('authorize',{...env,E6_W5L_CHECKED_OUT_PR_HEAD:'b'.repeat(40)}).status,1);
  await writeFile(lp,JSON.stringify({...pr(),labels:[]}),{mode:0o600});
  assert.equal(invoke('live-approval').status,1);
  const denied=invoke('run');
  assert.equal(denied.status,1);
  assert(!denied.stdout.includes('PAIRED_CPU_SANITIZED_RESULT'));
  const symlinkParent=join(base,'fake-link');
  await mkdir(join(base,'safe'));
  await symlink(join(base,'safe'),symlinkParent);
  const linkedEvent=join(symlinkParent,'event.json');
  assert.equal(invoke('authorize',{...env,GITHUB_EVENT_PATH:linkedEvent}).status,1);
 }finally{await rm(base,{recursive:true,force:true});}
});
test('CPU CLI error strings, owned artifacts and fixed paths avoid raw provider text',async()=>{
 const code=await readFile(new URL('./w5l_owner_cpu_cli.mjs',import.meta.url),'utf8');
 assert(code.includes('authorizePairedCpuLabelEvent(event,context)'));
 assert(code.includes('authorizeLivePairedCpuPr('));
 assert(code.includes('transport:fetch'));
 assert(code.includes('constants.O_NOFOLLOW'));
 assert(code.includes('flag:\'wx\''));
 assert(code.includes('mode:0o600'));
 assert(code.includes('E6_W5L_ENABLE_OWNER_LABELED_CPU_TRIAL'));
 assert(code.includes("process.env.MODEL_TAG!=='qwen2.5:0.5b'"));
 for(const unsafe of ['console.log(assessed','JSON.stringify(batch)',
 'JSON.stringify(event)','writeFile(join(dest,\'raw',
 'process.env.GITHUB_TOKEN','process.env.GH_READ_TOKEN'])
  assert(!code.includes(unsafe),unsafe);
});
