import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,mkdir,writeFile,rm,symlink,readdir,copyFile,stat} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {CPU_LABEL,authorizePairedCpuLabelEvent,authorizeLivePairedCpuPr,makeUnverifiablePairedCpuAttempt,authorizePairedCpuRunnerAttempt} from './w5l_owner_cpu_gate.mjs';
import {inspectSanitizedCpuArtifact} from './w5l_sanitized_metadata_gate.mjs';
const SHA='a'.repeat(40),REPO='teldigi5-wq/aetheris-ai-runtime';
const BASE='feat/e6-w5i-local-inference-completion-20261102';
const BASE_SHA='d1abab54b80af1ec63ede7360869c4d9308441e5';
const pr=()=>({number:275,state:'open',draft:true,merged_at:null,
 user:{login:'teldigi5-wq'},
 head:{sha:SHA,repo:{full_name:REPO}},
 base:{ref:BASE,sha:BASE_SHA,repo:{full_name:REPO}},
 labels:[{name:CPU_LABEL}]});
const event=()=>({action:'labeled',label:{name:CPU_LABEL},pull_request:pr()});
const ctx=()=>({actor:'teldigi5-wq',repository:REPO,expectedPr:275,headSha:SHA});
const reject=fn=>assert.throws(fn,/E6_W5L_/);
test('one owner-label event only authorizes the first owner-triggered Actions attempt',()=>{
 const valid={actor:'teldigi5-wq',triggeringActor:'teldigi5-wq',runAttempt:'1'};
 const result=authorizePairedCpuRunnerAttempt(valid);
 assert.equal(result.firstRunAttempt,true);
 assert.equal(result.ownerInitiated,true);
 assert.equal(result.releaseGo,false);
 for(const modify of [
  x=>{x.runAttempt='2';},x=>{x.runAttempt='3';},
  x=>{x.runAttempt='0';},x=>{x.runAttempt='01';},
  x=>{delete x.runAttempt;},x=>{x.triggeringActor='collaborator';},
  x=>{delete x.triggeringActor;},x=>{x.actor='collaborator';}
 ]){
  const bad={...valid};modify(bad);
  assert.throws(()=>authorizePairedCpuRunnerAttempt(bad),
   /FIRST_OWNER_TRIGGERED_CPU_ATTEMPT_ONLY/);
 }
 assert.throws(()=>authorizePairedCpuRunnerAttempt(),
  /FIRST_OWNER_TRIGGERED_CPU_ATTEMPT_ONLY/);
});
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
   x=>{x.pull_request.base.sha='b'.repeat(40);},
   x=>{delete x.pull_request.base.sha;},
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
  x=>{x.base.sha='b'.repeat(40);},
  x=>{delete x.base.sha;},
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
 assert(wf.includes("github.triggering_actor == 'teldigi5-wq'"));
 assert(wf.includes("github.run_attempt == '1'"));
 assert(wf.includes("test \"$GITHUB_RUN_ATTEMPT\" = '1'"));
 assert(wf.includes("test \"$GITHUB_TRIGGERING_ACTOR\" = 'teldigi5-wq'"));
 assert(wf.includes("github.event.pull_request.number == 275"));
 assert(wf.includes("github.event.pull_request.base.sha == 'd1abab54b80af1ec63ede7360869c4d9308441e5'"));
 assert(wf.includes('persist-credentials: false'));
 assert(wf.includes('timeout-minutes: 55'));
 assert(wf.includes('OLLAMA_LLM_LIBRARY: cpu'));
 assert(wf.includes("CUDA_VISIBLE_DEVICES: '-1'"));
 assert(wf.includes("HIP_VISIBLE_DEVICES: '-1'"));
 assert(wf.includes('MODEL_TAG: qwen2.5:0.5b'));
 assert(wf.includes('checks: read'));
 assert(wf.includes('GH_READ_TOKEN: ${{ github.token }}'));
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
 assert.equal((wf.match(/node w5l_owner_cpu_cli\.mjs live-approval/g)||[]).length,3);
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
   GITHUB_TRIGGERING_ACTOR:'teldigi5-wq',GITHUB_RUN_ATTEMPT:'1',
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
   // A re-run keeps the original owner actor, but may be started by a different user.
   assert.equal(invoke('authorize',{...env,GITHUB_RUN_ATTEMPT:'2'}).status,1);
   assert.equal(invoke('authorize',{...env,GITHUB_TRIGGERING_ACTOR:'collaborator'}).status,1);
   assert.equal(invoke('authorize',{...env,GITHUB_TRIGGERING_ACTOR:''}).status,1);
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
 assert(code.includes('beforeRequest'));
 assert(code.includes('requireCurrentOwnerApprovalForRequest'));
 assert(code.includes('authorizePairedCpuRunnerAttempt'));
 assert(code.includes('PER_REQUEST_GITHUB_APPROVAL_TOKEN_REQUIRED'));
 assert(code.includes('constants.O_NOFOLLOW'));
 assert(code.includes('flag:\'wx\''));
 assert(code.includes('mode:0o600'));
 assert(code.includes('E6_W5L_ENABLE_OWNER_LABELED_CPU_TRIAL'));
 assert(code.includes("process.env.MODEL_TAG!=='qwen2.5:0.5b'"));
 for(const unsafe of ['console.log(assessed','JSON.stringify(batch)',
 'JSON.stringify(event)','writeFile(join(dest,\'raw',
 'process.env.GITHUB_TOKEN'])
  assert(!code.includes(unsafe),unsafe);
});

test('aborted CPU experiment produces a strictly non-claiming immutable status',()=>{
 const report=makeUnverifiablePairedCpuAttempt({
  sourceCommitSha:SHA,reportedGithubRunId:'41629454997'
 });
 assert(Object.isFrozen(report));
 assert.equal(report.kind,'E6_W5L_UNVERIFIABLE_CPU_ATTEMPT_V1');
 assert.equal(report.status,'NO_COMPLETE_VERIFIABLE_SIX_CELL_RECEIPT');
 assert.equal(report.independentlyVerifiedCaseReceipts,0);
 for(const name of ['attemptedModelRequests','attemptedCells','completedCells',
  'accepted','rejected','notRun'])assert.equal(report[name],null);
 for(const name of ['actualModelExecutionAttested','modelWeightsAttested',
  'ownerExperimentApproved','originalDesignQualityVerified','stage45Go','releaseGo'])
   assert.equal(report[name],false);
 assert(!JSON.stringify(report).includes('error'));
 assert.throws(()=>{report.accepted=6;},TypeError);
 assert.throws(()=>makeUnverifiablePairedCpuAttempt({
  sourceCommitSha:'bad',reportedGithubRunId:'41629454997'
 }),/UNVERIFIABLE_ATTEMPT_CONTEXT_INVALID/);
 assert.throws(()=>makeUnverifiablePairedCpuAttempt({
  sourceCommitSha:SHA,reportedGithubRunId:'run; unsafe'
 }),/UNVERIFIABLE_ATTEMPT_CONTEXT_INVALID/);
});

test('owner CPU CLI writes a redacted failure receipt only after authorized run starts',async()=>{
 const source=await readFile(new URL('./w5l_owner_cpu_cli.mjs',import.meta.url),'utf8');
 const approval=source.indexOf("context=await verifyApproval(mode!=='authorize')");
 const run=source.indexOf('const assessed=await collectBoundedPairedDesign(');
 const captured=source.indexOf('assessed=await collectBoundedPairedDesign(');
 const redacted=source.indexOf('makeUnverifiablePairedCpuAttempt({');
 assert(approval>=0);
 assert.equal(run,-1);
 assert(captured>approval);
 assert(redacted>captured);
 assert(source.includes('fail(\'NO_COMPLETE_VERIFIABLE_SIX_CELL_RECEIPT\')'));
 assert(source.includes('attemptedModelRequests:null')===false);
 assert(source.includes('await saveSanitized(makeUnverifiablePairedCpuAttempt({'));
 assert(!source.includes('JSON.stringify(error)'));
});
test('actual W5-L CLI rehearses revoked, partially failed and completed six-cell paths offline', {
 skip:!(typeof runner==='string'&&runner.startsWith('/home/runner/work/_temp'))
},async()=>{
 const base=await mkdtemp(join(runner,'w5l-cli-isolated-rehearsal-'));
 const sourceDir=fileURLToPath(new URL('.',import.meta.url));
 const canonicalBriefs=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')).briefs;
 const canary='MODEL_PRIVATE_CANARY_MUST_NEVER_APPEAR';
 const fakePreload=String.raw`
import {readFile} from 'node:fs/promises';
import {syntheticDesign} from './w5b_simulated_provider.mjs';
const briefs=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')).briefs;
let requestCount=0;
globalThis.fetch=async(url,options)=>{
 if(url==='https://api.github.com/repos/teldigi5-wq/aetheris-ai-runtime/pulls/275'){
  if(options.method!=='GET'||options.cache!=='no-store')
   throw Error('INVALID_GITHUB_APPROVAL_LOOKUP');
  const revoked=process.env.E6_W5L_TEST_SCENARIO==='revoke-after-one'&&requestCount>=1;
  return new Response(JSON.stringify({
   number:275,state:'open',draft:true,merged_at:null,
   user:{login:'teldigi5-wq'},
   head:{sha:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    repo:{full_name:'teldigi5-wq/aetheris-ai-runtime'}},
   base:{ref:'feat/e6-w5i-local-inference-completion-20261102',
    sha:'d1abab54b80af1ec63ede7360869c4d9308441e5',
    repo:{full_name:'teldigi5-wq/aetheris-ai-runtime'}},
   labels:revoked?[]:[{name:'e6-w5l-six-cell-cpu-approved'}]
  }),{status:200});
 }
 if(url!=='http://127.0.0.1:11434/api/generate'||options.method!=='POST')
  throw Error('FORBIDDEN_NON_LOOPBACK_TRANSPORT');
 const request=JSON.parse(options.body);
 if(request.model!=='qwen2.5:0.5b'||request.options.num_gpu!==0||
    request.stream!==false||request.format!=='json')
  throw Error('UNSAFE_MODEL_REQUEST');
 const briefIndex=briefs.findIndex(b=>request.prompt.includes(JSON.stringify(b)));
 if(briefIndex<0)throw Error('UNRECOGNIZED_PINNED_BRIEF');
 requestCount++;
 if(process.env.E6_W5L_TEST_SCENARIO==='partial'&&requestCount===2)
  throw Error('MODEL_PRIVATE_CANARY_MUST_NEVER_APPEAR');
 const design=syntheticDesign(briefs[briefIndex],briefIndex);
 const {pages,...identity}=design;
 let proposal=design;
 if(request.prompt.includes('Return ONLY brand identity:'))proposal=identity;
 else if(request.prompt.includes('page.path="/approach"'))
  proposal={version:1,projectSlug:briefs[briefIndex].projectSlug,page:pages[1]};
 else if(request.prompt.includes('page.path="/"'))
  proposal={version:1,projectSlug:briefs[briefIndex].projectSlug,page:pages[0]};
 return new Response(JSON.stringify({
  model:'qwen2.5:0.5b',done:true,done_reason:'stop',
  eval_count:150,response:JSON.stringify(proposal)
 }),{status:200});
};
`;
 try{
  const eventPath=join(base,'event.json');
  const livePath=join(base,'w5l-live-owner-pr.json');
  await writeFile(eventPath,JSON.stringify(event()),{mode:0o600});
  await writeFile(livePath,JSON.stringify({...pr(),labels:[]}),{mode:0o600});
  const baseEnv={
   PATH:process.env.PATH||'',HOME:process.env.HOME||'',
   GITHUB_EVENT_NAME:'pull_request',
   GITHUB_REPOSITORY:REPO,GITHUB_ACTOR:'teldigi5-wq',
   GITHUB_TRIGGERING_ACTOR:'teldigi5-wq',GITHUB_RUN_ATTEMPT:'1',
   GITHUB_EVENT_PATH:eventPath,RUNNER_TEMP:base,
   E6_W5L_ENABLE_OWNER_LABELED_CPU_TRIAL:'YES',
   E6_W5L_APPROVED_PR_NUMBER:'275',
   E6_W5L_CHECKED_OUT_PR_HEAD:SHA,
   GITHUB_RUN_ID:'41630752655',
   OLLAMA_HOST:'127.0.0.1:11434',
   OLLAMA_LLM_LIBRARY:'cpu',OLLAMA_NUM_PARALLEL:'1',
   OLLAMA_MAX_LOADED_MODELS:'1',
   CUDA_VISIBLE_DEVICES:'-1',HIP_VISIBLE_DEVICES:'-1',
   MODEL_TAG:'qwen2.5:0.5b',
   GH_READ_TOKEN:'mock-test-only-github-read-token'
  };
  // Copy source into disposable, source-pinned fixtures so the real CLI
  // writes no files under the checkout and cannot overwrite user data.
  const available=await readdir(sourceDir);
  const sourceFiles=available.filter(x=>x.endsWith('.mjs')||
    x==='w5b_heldout_briefs.json');
  assert(sourceFiles.includes('w5l_owner_cpu_cli.mjs'));
  assert(sourceFiles.includes('w5b_heldout_briefs.json'));
  const clone=async name=>{
   const snapshot=join(base,name);
   await mkdir(snapshot,{mode:0o700});
   for(const f of sourceFiles)
    await copyFile(join(sourceDir,f),join(snapshot,f));
   const preload=join(snapshot,'offline-only-preload.mjs');
   await writeFile(preload,fakePreload,{mode:0o600});
   const cli=join(snapshot,'w5l_owner_cpu_cli.mjs');
   const resultFile=join(snapshot,'target','e6-w5l-owner-cpu-review','paired-design-metadata.json');
   return {preload,cli,resultFile};
  };
  const invoke=(snapshot,scenario)=>spawnSync(process.execPath,[
    '--import',snapshot.preload,snapshot.cli,'run'
  ],{env:{...baseEnv,E6_W5L_TEST_SCENARIO:scenario},
      encoding:'utf8',timeout:12000,maxBuffer:128*1024});
  const deniedSnapshot=await clone('revoked');
  const denied=invoke(deniedSnapshot,'success');
  assert.equal(denied.status,1,denied.stderr);
  assert.match(denied.stderr,/LIVE_PAIRED_CPU_APPROVAL_REVOKED_OR_HEAD_CHANGED/);
  await assert.rejects(readFile(deniedSnapshot.resultFile),{code:'ENOENT'});
  await assert.rejects(inspectSanitizedCpuArtifact({
   directory:dirname(deniedSnapshot.resultFile),sourceCommitSha:SHA,
   githubRunId:'41630752655',briefs:canonicalBriefs
  }));
  await writeFile(livePath,JSON.stringify(pr()),{mode:0o600});
  const failedSnapshot=await clone('partial');
  const failed=invoke(failedSnapshot,'partial');
  assert.equal(failed.status,1,failed.stderr);
  assert.match(failed.stderr,/NO_COMPLETE_VERIFIABLE_SIX_CELL_RECEIPT/);
  assert(!failed.stderr.includes(canary));
  assert(!failed.stdout.includes(canary));
  const fallbackRaw=await readFile(failedSnapshot.resultFile,'utf8');
  assert(!fallbackRaw.includes(canary));
  const fallback=JSON.parse(fallbackRaw);
  assert.equal(fallback.kind,'E6_W5L_UNVERIFIABLE_CPU_ATTEMPT_V1');
  assert.equal(fallback.attemptedModelRequests,null);
  assert.equal(fallback.attemptedCells,null);
  assert.equal(fallback.completedCells,null);
  assert.equal(fallback.accepted,null);
  assert.equal(fallback.rejected,null);
  assert.equal(fallback.notRun,null);
  assert.equal(fallback.releaseGo,false);
  assert.equal((await stat(failedSnapshot.resultFile)).mode&0o777,0o600);
  const checkedFailure=await inspectSanitizedCpuArtifact({
   directory:dirname(failedSnapshot.resultFile),sourceCommitSha:SHA,
   githubRunId:'41630752655',briefs:canonicalBriefs
  });
  assert.equal(checkedFailure.completeSixCells,false);
  const revokedMidSnapshot=await clone('revoked-mid-run');
  const revokedMid=invoke(revokedMidSnapshot,'revoke-after-one');
  assert.equal(revokedMid.status,1,revokedMid.stderr);
  assert.match(revokedMid.stderr,/NO_COMPLETE_VERIFIABLE_SIX_CELL_RECEIPT/);
  const revokedRaw=await readFile(revokedMidSnapshot.resultFile,'utf8');
  const revokedReceipt=JSON.parse(revokedRaw);
  assert.equal(revokedReceipt.kind,'E6_W5L_UNVERIFIABLE_CPU_ATTEMPT_V1');
  assert.equal(revokedReceipt.attemptedModelRequests,null);
  assert.equal(revokedReceipt.completedCells,null);
  assert.equal(revokedReceipt.releaseGo,false);
  const successSnapshot=await clone('complete');
  const succeeded=invoke(successSnapshot,'success');
  assert.equal(succeeded.status,0,succeeded.stderr);
  assert.match(succeeded.stdout,/E6_W5L_PAIRED_CPU_SANITIZED_RESULT_ONLY/);
  assert(!succeeded.stdout.includes(canary));
  const fullRaw=await readFile(successSnapshot.resultFile,'utf8');
  assert(!fullRaw.includes('The warmth of an imagined reading object'));
  const full=JSON.parse(fullRaw);
  assert.equal(full.kind,'E6_W5L_OWNER_CPU_UNATTESTED_METADATA_V1');
  assert.equal(full.sourceCommitSha,SHA);
  assert.equal(full.captureCount,12);
  assert.equal(full.receipt.observations.length,6);
  assert.deepEqual(full.comparison.methods.map(m=>m.accepted),[3,3]);
  assert.equal(full.providerResponsesIndependentlyAuthenticated,false);
  assert.equal(full.modelWeightsAttested,false);
  assert.equal(full.stage45Go,false);
  assert.equal(full.releaseGo,false);
  assert.equal((await stat(successSnapshot.resultFile)).mode&0o777,0o600);
  const checkedComplete=await inspectSanitizedCpuArtifact({
   directory:dirname(successSnapshot.resultFile),sourceCommitSha:SHA,
   githubRunId:'41630752655',briefs:canonicalBriefs
  });
  assert.equal(checkedComplete.completeSixCells,true);
  assert.equal(checkedComplete.schemaOnlyNotAuthenticity,true);
 }finally{await rm(base,{recursive:true,force:true});}
});

test('consumed W5-J label can never start the historical weaker CPU runner',async()=>{
 const old=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 const next=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5l-optin-paired-cpu.yml',import.meta.url),'utf8');
 const index=old.indexOf('\njobs:\n');
 assert(index>0);
 const body=old.slice(index+7);
 const job='  owner-approved-three-brief-cloud-cpu:\n';
 assert(body.startsWith(job));
 const guard=body.slice(0,body.indexOf('\n    runs-on:'));
 assert(guard.includes("    if: >-\n      false &&\n      github.event.label.name == 'e6-w5j-three-brief-cpu-approved' &&"));
 assert.equal((body.match(/^  [a-zA-Z][a-zA-Z0-9-]*:\s*$/gm)||[]).length,1);
 for(const unwanted of ['workflow_dispatch:','pull_request_target:','schedule:'])
  assert(!old.includes(unwanted),unwanted);
 assert(!next.includes('      false &&\n'));
 assert(next.includes("github.event.label.name == 'e6-w5l-six-cell-cpu-approved'"));
});

test('every label-triggered CPU workflow for PR275 is inventoried and legacy disabled',async()=>{
 const folder=new URL('../../../../.github/workflows/',import.meta.url);
 const filenames=(await readdir(folder)).filter(x=>/\.ya?ml$/.test(x)).sort();
 assert(filenames.length>=75,'workflow discovery unexpectedly incomplete');
 const eligible=[];
 let otherExperiment=null;
 for(const filename of filenames){
  const raw=await readFile(new URL(filename,folder),'utf8');
  // A labeled PR event is necessary, but never sufficient for CPU approval.
  if(!/^on:\s*\n  pull_request:\s*\n    types:\s*\n      - labeled/m.test(raw))
   continue;
  const src=raw.replace(/^\s*#.*$/gm,'');
  if(filename==='runtime-e6-w5d-cpu-optin.yml')
   otherExperiment=src;
  if(!/github\.event\.pull_request\.number\s*==\s*275/.test(src))
   continue;
  const usesCpu=/OLLAMA_HOST:|ollama.*pull|E6_W5[DJL]_.*CPU/.test(src);
  if(usesCpu)eligible.push({filename,src});
 }
 assert.deepEqual(eligible.map(x=>x.filename),[
  'runtime-e6-w5j-optin-heldout-cpu.yml',
  'runtime-e6-w5l-optin-paired-cpu.yml'
 ]);
 const old=eligible[0].src,next=eligible[1].src;
 assert(old.includes("    if: >-\n      false &&\n      github.event.label.name == 'e6-w5j-three-brief-cpu-approved'"));
 assert(next.includes("    if: >-\n      github.event.label.name == 'e6-w5l-six-cell-cpu-approved'"));
 assert(next.includes("github.actor == 'teldigi5-wq'"));
 assert(next.includes("github.event.pull_request.head.repo.full_name == github.repository"));
 assert(next.includes("checks: read"));
 assert(next.includes("pull-requests: read"));
 assert(next.includes("persist-credentials: false"));
 assert(!next.includes("      false &&"));
 assert(otherExperiment);
 assert(otherExperiment.includes("github.event.pull_request.number == 269"));
 assert(!otherExperiment.includes("github.event.pull_request.number == 275"));
});
test('model-weight download rechecks live owner approval after CPU runtime setup',async()=>{
 const wf=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5l-optin-paired-cpu.yml',import.meta.url),'utf8');
 const first=wf.indexOf('      - name: Pull exactly one research model on CPU and verify the published tag prefix');
 const next=wf.indexOf('      - name: Recheck LIVE owner approval again immediately before paired inference');
 assert(first>=0&&next>first);
 const step=wf.slice(first,next);
 assert(step.includes('GH_READ_TOKEN: ' + '$' + '{{ github.token }}'));
 const daemon=step.indexOf('env -u GH_READ_TOKEN "$RUNNER_TEMP/w5l-ollama-bin/bin/ollama" serve');
 const ready=step.indexOf("curl --fail --silent --max-time 5 'http://127.0.0.1:11434/api/version'");
 const live=step.indexOf('aetheris-w5l-before-weights');
 const authorize=step.indexOf('node w5l_owner_cpu_cli.mjs live-approval');
 const pull=step.indexOf('env -u GH_READ_TOKEN timeout --signal=TERM --kill-after=10s 600s');
 assert(daemon>=0&&ready>daemon&&live>ready&&authorize>live&&pull>authorize);
 assert(step.includes('test "$(git rev-parse HEAD)" = "$E6_W5L_CHECKED_OUT_PR_HEAD"'));
 assert(step.includes('https://api.github.com/repos/teldigi5-wq/aetheris-ai-runtime/pulls/275'));
 assert(step.includes('-o "$RUNNER_TEMP/w5l-live-owner-pr.json"'));
 assert(step.includes('( cd tools/enterprise/e6_studio/next-studio && node w5l_owner_cpu_cli.mjs live-approval )'));
 assert.equal((step.match(/node w5l_owner_cpu_cli\.mjs live-approval/g)||[]).length,1);
 assert.equal((step.match(/ollama" pull "\$MODEL_TAG"/g)||[]).length,1);
 assert(!step.includes('          "$RUNNER_TEMP/w5l-ollama-bin/bin/ollama" serve'));
 assert(!step.includes('          timeout --signal=TERM --kill-after=10s 600s'));
});
