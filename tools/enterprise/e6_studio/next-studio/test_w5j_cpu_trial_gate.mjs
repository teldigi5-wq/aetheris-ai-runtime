import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,copyFile,mkdir,writeFile,rm,symlink} from 'node:fs/promises';
import {spawnSync} from 'node:child_process';
import {tmpdir} from 'node:os';
import {join,dirname} from 'node:path';
import {authorizeLabelEvent,summarizeObservedTrial,summarizeMissingCaseReport,summarizeUnverifiableCaseReport,summarizeWithSavedSourceAudit} from './w5j_cpu_trial_gate.mjs';
import {evaluateCodeHeldout} from './w5c_local_code_trial.mjs';
import {fakeCssSourceProvider} from './w5c_test_doubles.mjs';
import {verifyScreenshotEvidence} from './w6_screenshot_integrity_gate.mjs';
import {MODEL} from './w5b_simulated_provider.mjs';
import {requestLocalOllama,buildPrompt} from './w5a_local_ollama.mjs';
import {briefs,proposalFor} from './w5a_test_doubles.mjs';
const batch=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8'));
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const SHA='a'.repeat(64),PR=275,OWNER='teldigi5-wq';
const event=()=>({
 action:'labeled',label:{name:'e6-w5j-three-brief-cpu-approved'},
 pull_request:{number:PR,state:'open',draft:true,user:{login:OWNER},
  base:{ref:'feat/e6-w5i-local-inference-completion-20261102'},
  head:{sha:SHA,repo:{full_name:'teldigi5-wq/aetheris-ai-runtime'}}}
});
const args=()=>({actor:OWNER,repository:'teldigi5-wq/aetheris-ai-runtime',
 expectedPr:PR,headSha:SHA});
test('only the owner-labeled exact same-repo draft PR can authorize model downloads',()=>{
 const x=authorizeLabelEvent(event(),args());
 assert.equal(x.gpuAuthorization,false);
 assert.equal(x.productionAuthorization,false);
 const changes=[
  e=>{e.action='synchronize';},
  e=>{e.label.name='e6-w5d-cpu-smoke-approved';},
  e=>{e.pull_request.number=274;},
  e=>{e.pull_request.state='closed';},
  e=>{e.pull_request.draft=false;},
  e=>{e.pull_request.user.login='untrusted';},
  e=>{e.pull_request.head.repo.full_name='attacker/other';},
  e=>{e.pull_request.base.ref='main';},
  e=>{e.pull_request.head.sha='b'.repeat(64);}
 ];
 for(const edit of changes){
  const v=event();edit(v);
  assert.throws(()=>authorizeLabelEvent(v,args()),/NOT_OWNER_AUTHORIZED_PR_LABEL_EVENT/);
 }
 assert.throws(()=>authorizeLabelEvent(event(),{...args(),actor:'untrusted'}),/NOT_OWNER_AUTHORIZED_PR_LABEL_EVENT/);
 assert.throws(()=>authorizeLabelEvent(event(),{...args(),expectedPr:274}),/NOT_OWNER_AUTHORIZED_PR_LABEL_EVENT/);
});
test('CPU-only fixed local generation request cannot enable GPUs even from model prompts',async()=>{
 const out=[],value=proposalFor(briefs[0]);
 const transport=async(url,init)=>{
  assert.equal(url,'http://127.0.0.1:11434/api/generate');
  const req=JSON.parse(init.body);out.push(req);
  return new Response(JSON.stringify({
   model:MODEL,done:true,done_reason:'stop',eval_count:42,response:JSON.stringify(value)
  }),{status:200});
 };
 const result=await requestLocalOllama(buildPrompt(briefs[0]),MODEL,{transport});
 assert.equal(result.providerStopReasonVerified,true);
 assert.equal(out.length,1);
 assert.equal(out[0].options.num_gpu,0);
 assert.equal(out[0].keep_alive,'0');
 assert.equal(out[0].stream,false);
});
async function report(badIndex=-1){
 const x=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,
  transport:fakeCssSourceProvider({briefs:batch.briefs,badIndex})});
 const v=structuredClone(x.report);
 // This represents ONLY a FAKE test envelope modified to exercise the
 // self-reported case gate. It is not evidence of real provider execution.
 v.providerWasActualLoopbackRequest=true;
 for(const c of v.cases)if(c.accepted){
  c.designStopReasonVerified=true;c.cssStopReasonVerified=true;
  c.designReportedStopReason='stop';c.cssReportedStopReason='stop';
 }
 return v;
}
function summarize(report,briefList=batch.briefs){
 return summarizeObservedTrial(report,briefList,{
  sourceSha:SHA,reportRawBytes:JSON.stringify(report,null,2)+'\n'
 });
}
test('exact three-case synthetic report can be summarized but NEVER attests real weights or release',async()=>{
 const r=summarize(await report());
 assert.equal(r.accepted,3);
 assert.equal(r.rejected,0);
 assert.equal(r.candidateStatus,'ALL_THREE_SOURCE_CANDIDATES_FOR_REVIEW');
 assert.equal(r.modelWeightsCryptographicallyAttested,false);
 assert.equal(r.independentModelAuthorshipVerified,false);
 assert.equal(r.stage45Go,false);
 assert.equal(r.releaseGo,false);
 assert.equal(r.cases.length,3);
 for(const c of r.cases)assert.equal(c.sourceIntegrityProvenBySelfReport,false);
});
test('partial or malicious CSS output stays visible as a REJECTION, not success',async()=>{
 const r=summarize(await report(1));
 assert.equal(r.accepted,2);
 assert.equal(r.rejected,1);
 assert.equal(r.cases[1].accepted,false);
 assert.match(r.cases[1].rejectionCode,/^E6_W5H_/);
 assert.equal(r.candidateStatus,'INCOMPLETE_REJECTED_OR_TRUNCATED');
 assert.equal(r.releaseGo,false);
});
test('fake provider flag without a real localhost request is never accepted as observed trial',async()=>{
 const v=await report();v.providerWasActualLoopbackRequest=false;
 assert.throws(()=>summarize(v),/INVALID_TRIAL_EVIDENCE/);
});
test('never upgrade missing stop reasons, invented model authorship or GPU evidence to acceptance',async()=>{
 for(const change of [
  r=>{r.cases[0].designStopReasonVerified=false;},
  r=>{r.cases[0].cssReportedStopReason='length';},
  r=>{r.cases[0].designTokenBudget=9999;},
  r=>{r.cases[1].cssRequestAttempts=3;},
  r=>{r.modelRuntimeAttested=true;},
  r=>{r.modelAuthorshipIndependentlyAttested=true;},
  r=>{r.physicalGpuMeasured=true;},
  r=>{r.ownerApproved=true;},
  r=>{r.stage45Go=true;},
  r=>{r.cases[1].modelAuthoredCssCandidateSha256='fake';}
 ]){
  const v=await report();change(v);
  assert.throws(()=>summarize(v),/E6_W5J_/);
 }
});
test('refuse incomplete case sets, reused identities and malformed observed origin',async()=>{
 for(const change of [
  r=>{r.cases.pop();},
  r=>{r.cases[1].projectSlug=r.cases[0].projectSlug;},
  r=>{r.accepted=2;r.rejected=0;},
  r=>{r.runtime.endpoint='https://external';},
  r=>{r.runtime.reportedModelDigest='unknown';},
  r=>{r.cases[0].modelReportedName='different';}
 ]){
  const v=await report();change(v);
  assert.throws(()=>summarize(v),/E6_W5J_/);
 }
});

test('raw evidence digest refers to bytes actually saved, not normalized JSON only',async()=>{
 const v=await report();
 const raw=JSON.stringify(v,null,2)+'\n';
 const sum=summarizeObservedTrial(v,batch.briefs,{sourceSha:SHA,reportRawBytes:raw});
 assert.match(sum.reportRawFileSha256,/^[a-f0-9]{64}$/);
 assert.notEqual(sum.reportSha256,sum.reportRawFileSha256);
 assert.throws(()=>summarizeObservedTrial(v,batch.briefs,{
  sourceSha:SHA,reportRawBytes:JSON.stringify({...v,rejected:3},null,2)+'\n'
 }),/REPORT_BYTES_DO_NOT_MATCH_STRUCTURED_EVIDENCE/);
 assert.throws(()=>summarizeObservedTrial(v,batch.briefs,{sourceSha:SHA}),
  /REPORT_RAW_BYTES_REQUIRED/);
});
test('accepted candidate must preserve all exact SHA-256 artifact keys and original brief identity',async()=>{
 for(const damage of [
  c=>{c.outputSha256={};},
  c=>{delete c.outputSha256['assets/site.css'];},
  c=>{c.outputSha256['index.html']='attacker';},
  c=>{c.outputSha256['extra.txt']='a'.repeat(64);},
  c=>{c.designSha256='short';},
  c=>{c.briefSha256='f'.repeat(64);}
 ]){
  const v=await report();
  damage(v.cases[0]);
  assert.throws(()=>summarize(v),/E6_W5J_/);
 }
});

test('opt-in real-model workflow is always owner-labeled and validates all 18 page screenshots',async()=>{
 const source=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 assert.match(source,/pull_request:\s*\n\s*types:\s*\n\s*- labeled/);
 for(const forbidden of ['workflow_dispatch:','push:','pull_request_target:','schedule:'])
  assert(!source.includes(forbidden),'unexpected trigger '+forbidden);
 for(const required of [
  "github.event.label.name == 'e6-w5j-three-brief-cpu-approved'",
  "github.event.pull_request.number == 275",
  "github.actor == 'teldigi5-wq'",
  "github.event.pull_request.head.repo.full_name == github.repository",
  "persist-credentials: false",
  "timeout-minutes: 55",
  "num_gpu",
  "result['fullPageScreenshotCount'] == 18",
  "result['bothRoutesCapturedForAllNineProfiles'] is True",
  "result['releaseGo'] is False",
  "result['bothRoutesAutomatedAccessibilityPassed'] is True",
  "result['automatedAccessibilityRouteScanCount'] == 18",
  "result['textResize200PercentRouteScanCount'] == 18",
  "result['bothRoutesTextResizePassed'] is True",
  "@axe-core/playwright@4.14.0"
 ]){
  // num_gpu is enforced in the adapter, not the workflow itself.
  if(required==='num_gpu')continue;
  assert(source.includes(required),'missing explicit launch/review guard '+required);
 }
 const approved=source.indexOf('node w5j_cpu_trial_cli.mjs authorize');
 const download=source.indexOf('Download verified Ollama');
 const model=source.indexOf('"$RUNNER_TEMP/ollama-bin/bin/ollama" pull');
 assert(approved>=0&&download>approved&&model>download);
});

test('a labeled CPU run refuses downloads unless the exact PR head has successful normal GitHub CI',async()=>{
 const source=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 const authorize=source.indexOf('node w5j_cpu_trial_cli.mjs authorize');
 const preflight=source.indexOf('Require successful model-free exact-head research CI');
 const checkApi=source.indexOf('/commits/$W5J_APPROVED_HEAD/check-runs?per_page=100');
 const modelDownload=source.indexOf('Download verified Ollama');
 assert(authorize>=0&&preflight>authorize&&checkApi>preflight&&modelDownload>checkApi);
 for(const requirement of [
  "test \"$(git rev-parse HEAD)\" = \"$W5J_APPROVED_HEAD\"",
  "c.get('name')=='bounded-model-proposal-and-simulated-browser-only'",
  "c.get('head_sha')==sha",
  "c.get('status')=='completed' and c.get('conclusion')=='success'",
  "c.get('app',{}).get('slug')=='github-actions'",
  "checks.get('total_count',101)<=100"
 ])assert(source.includes(requirement),'missing exact-head positive CI guard: '+requirement);
});
test('the installed research model must match the published provider tag ID, with no false weight-attestation claim',async()=>{
 const source=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 const pull=source.indexOf('ollama" pull "$MODEL_TAG"');
 const tagged=source.indexOf('w5j-installed-model-tags.json');
 const verify=source.indexOf("model_id.startswith('a8b0c5157701')");
 const inference=source.indexOf('Attempt genuine model authored design');
 assert(pull>0&&tagged>pull&&verify>tagged&&inference>verify);
 assert(source.includes("len(model_id)==64"));
 assert(source.includes("entries[0].get('digest','')"));
 assert(source.includes("E6_W5J_VENDOR_TAG_PREFIX_DRIFT_CHECK_NOT_WEIGHTS_ATTESTATION"));
});

test('W5-J rejected cases preserve phase and provider category without executing a model',async()=>{
 const fake=fakeCssSourceProvider({briefs:batch.briefs});
 const transport=async(url,opts)=>url.endsWith('/api/generate')
   ?new Response('offline',{status:503}):fake(url,opts);
 const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport});
 assert.equal(result.report.accepted,0);
 for(const c of result.report.cases){
  assert.equal(c.failurePhase,'DESIGN');
  assert.equal(c.failureKind,'PROVIDER_IO_OR_LIMIT');
  assert.deepEqual(c.phaseReceipt,{design:'FAILED',css:'NOT_EXECUTED',compile:'NOT_EXECUTED'});
 }
 const simulated=structuredClone(result.report);
 simulated.providerWasActualLoopbackRequest=true; // test fixture ONLY
 const receipt=summarize(simulated);
 assert.equal(receipt.accepted,0);
 assert.equal(receipt.cases[0].failureKind,'PROVIDER_IO_OR_LIMIT');
 assert.equal(receipt.cases[0].failurePhase,'DESIGN');
 assert.equal(receipt.releaseGo,false);
});
test('W5-J CSS provider errors are not misreported as rejected design',async()=>{
 const fake=fakeCssSourceProvider({briefs:batch.briefs});
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate')&&
      JSON.parse(opts.body).options.num_predict===900)
    return new Response('offline',{status:503});
  return fake(url,opts);
 };
 const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport});
 for(const c of result.report.cases){
  assert.equal(c.failurePhase,'CSS');
  assert.equal(c.failureKind,'PROVIDER_IO_OR_LIMIT');
  assert.deepEqual(c.phaseReceipt,{design:'PASSED',css:'FAILED',compile:'NOT_EXECUTED'});
 }
});
test('W5-J rejects forged or contradictory failure phase receipts',async()=>{
 for(const mutate of [
  c=>{delete c.phaseReceipt;},
  c=>{c.phaseReceipt.css='PASSED';},
  c=>{c.phaseReceipt.extra='PASSED';},
  c=>{c.failurePhase='DESIGN';},
  c=>{c.failureKind='SUCCESS';}
 ]){
  const source=await report(1);
  mutate(source.cases[1]);
  assert.throws(()=>summarize(source),/UNSAFE_REJECTED_CASE/);
 }
 for(const mutate of [
  c=>{c.phaseReceipt.compile='FAILED';},
  c=>{c.failurePhase='DESIGN';}
 ]){
  const source=await report();
  mutate(source.cases[0]);
  assert.throws(()=>summarize(source),/UNVERIFIED_ACCEPTED_CANDIDATE/);
 }
});

test('missing W5-C report never fabricates three held-out case attempts',()=>{
 const receipt=summarizeMissingCaseReport({sourceSha:SHA,caseReportAvailable:false});
 assert.equal(receipt.verifiedCaseReceipts,0);
 assert.equal(receipt.threeHeldoutCasesAttempted,null);
 assert.equal(receipt.modelRequestsObserved,null);
 assert.equal(receipt.accepted,null);
 assert.equal(receipt.rejected,null);
 assert.equal(receipt.sourceIntegrityVerified,false);
 assert.equal(receipt.releaseGo,false);
});
test('missing-report receipt refuses readable evidence and invalid source identity',()=>{
 for(const context of [
  {sourceSha:'not-a-commit',caseReportAvailable:false},
  {sourceSha:SHA,caseReportAvailable:true},
  {sourceSha:SHA},
  {}
 ])assert.throws(()=>summarizeMissingCaseReport(context),/MISSING_REPORT_CONTEXT_REJECTED/);
});
test('owner-labeled workflow records absent case report before failing and never treats it as reviewable',async()=>{
 const source=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 const run=source.indexOf('node w5c_local_code_trial.mjs');
 const missing=source.indexOf('node w5j_cpu_trial_cli.mjs missing-report');
 const summary=source.indexOf('node w5j_cpu_trial_cli.mjs summarize');
 assert(run>0&&missing>run&&summary>missing);
 assert(source.includes('if [ ! -s target/e6-w5c-local-css-research/source-code-trial-report.json ]; then'));
 assert(source.includes('E6_W5J_NO_VERIFIABLE_CASE_REPORT_NO_RELEASE'));
 assert(source.includes("exit 1"));
 for(const bad of ['workflow_dispatch:','pull_request_target:','schedule:'])assert(!source.includes(bad));
});

const offlineCliEnv=()=>({
 ...process.env,
 GITHUB_EVENT_NAME:'pull_request',
 GITHUB_REPOSITORY:'teldigi5-wq/aetheris-ai-runtime',
 E6_W5J_ENABLE_OWNER_LABELED_CPU_TRIAL:'YES',
 E6_W5J_CHECKED_OUT_PR_HEAD:SHA
});
async function isolatedCli(){
 const dir=await mkdtemp(join(tmpdir(),'aetheris-w5j-cli-offline-'));
 await copyFile(new URL('./w5j_cpu_trial_cli.mjs',import.meta.url),join(dir,'w5j_cpu_trial_cli.mjs'));
 await copyFile(new URL('./w5j_cpu_trial_gate.mjs',import.meta.url),join(dir,'w5j_cpu_trial_gate.mjs'));
 return dir;
}
test('missing-report CLI writes parseable sanitized JSON in isolated offline filesystem and refuses overwrite',async()=>{
 const dir=await isolatedCli();
 try{
  const script=join(dir,'w5j_cpu_trial_cli.mjs');
  const first=spawnSync(process.execPath,[script,'missing-report'],{
   env:offlineCliEnv(),encoding:'utf8',timeout:10000
  });
  assert.equal(first.status,0,first.stderr);
  const output=JSON.parse(await readFile(join(dir,'target/e6-w5j-cloud-cpu-review/missing-case-report.json'),'utf8'));
  assert.equal(output.kind,'E6_W5J_MISSING_CASE_REPORT_V1');
  assert.equal(output.threeHeldoutCasesAttempted,null);
  assert.equal(output.accepted,null);
  assert.equal(output.releaseGo,false);
  const second=spawnSync(process.execPath,[script,'missing-report'],{
   env:offlineCliEnv(),encoding:'utf8',timeout:10000
  });
  assert.notEqual(second.status,0);
  assert.match(second.stderr,/E6_W5J_UNVERIFIED_FAILURE|E6_W5J_/);
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('missing-report CLI refuses to replace a nonempty source report or traverse a symlink target',async()=>{
 const dir=await isolatedCli();
 try{
  const script=join(dir,'w5j_cpu_trial_cli.mjs');
  const target=join(dir,'target');
  await mkdir(join(target,'e6-w5c-local-css-research'),{recursive:true});
  await writeFile(join(target,'e6-w5c-local-css-research/source-code-trial-report.json'),'{}');
  const existing=spawnSync(process.execPath,[script,'missing-report'],{
   env:offlineCliEnv(),encoding:'utf8',timeout:10000
  });
  assert.notEqual(existing.status,0);
  await assert.rejects(readFile(join(target,'e6-w5j-cloud-cpu-review/missing-case-report.json')));
 }finally{await rm(dir,{recursive:true,force:true});}
 const linkDir=await isolatedCli(),outside=await mkdtemp(join(tmpdir(),'aetheris-w5j-outside-'));
 try{
  await symlink(outside,join(linkDir,'target'));
  const attempt=spawnSync(process.execPath,[join(linkDir,'w5j_cpu_trial_cli.mjs'),'missing-report'],{
   env:offlineCliEnv(),encoding:'utf8',timeout:10000
  });
  assert.notEqual(attempt.status,0);
  await assert.rejects(readFile(join(outside,'e6-w5j-cloud-cpu-review/missing-case-report.json')));
 }finally{
  await rm(linkDir,{recursive:true,force:true});
  await rm(outside,{recursive:true,force:true});
 }
});

test('metadata probe HTTP refusal never fabricates held-out requests',async()=>{
 const actual=fakeCssSourceProvider({briefs:batch.briefs});
 const calls=[];
 const transport=async(url,opts)=>{
  calls.push(url);
  if(url.endsWith('/api/version'))return new Response('service down',{status:503});
  return actual(url,opts);
 };
 await assert.rejects(
  evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport}),
  /E6_W5B_LOCAL_METADATA_UNAVAILABLE/
 );
 assert.equal(calls.filter(url=>url.endsWith('/api/generate')).length,0);
 const receipt=summarizeMissingCaseReport({sourceSha:SHA,caseReportAvailable:false});
 assert.equal(receipt.threeHeldoutCasesAttempted,null);
 assert.equal(receipt.releaseGo,false);
});
test('malformed provider metadata is infrastructure failure before any site output',async()=>{
 const actual=fakeCssSourceProvider({briefs:batch.briefs});
 let requestedGeneration=false;
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate'))requestedGeneration=true;
  if(url.endsWith('/api/tags'))return new Response('{not valid json',{status:200});
  return actual(url,opts);
 };
 await assert.rejects(
  evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport}),
  /E6_W5B_INVALID_LOCAL_METADATA_JSON/
 );
 assert.equal(requestedGeneration,false);
});
test('installed-model metadata must match exact requested model and valid reported digest',async()=>{
 const actual=fakeCssSourceProvider({briefs:batch.briefs});
 let requestedGeneration=false;
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate'))requestedGeneration=true;
  if(url.endsWith('/api/tags'))
   return new Response(JSON.stringify({models:[{name:MODEL,digest:'forged'}]}),{status:200});
  return actual(url,opts);
 };
 await assert.rejects(
  evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport}),
  /E6_W5B_MODEL_NOT_INSTALLED_OR_DIGEST_MISSING/
 );
 assert.equal(requestedGeneration,false);
});
test('probe transport timeout never becomes three rejected model cases',async()=>{
 const actual=fakeCssSourceProvider({briefs:batch.briefs});
 let requestedGeneration=false;
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate'))requestedGeneration=true;
  if(url.endsWith('/api/version')){
   const error=new Error('private transport detail must not enter receipts');
   error.name='TimeoutError';
   throw error;
  }
  return actual(url,opts);
 };
 await assert.rejects(
  evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport}),
  error=>error.name==='TimeoutError'
 );
 assert.equal(requestedGeneration,false);
 const receipt=summarizeMissingCaseReport({sourceSha:SHA,caseReportAvailable:false});
 assert.equal(JSON.stringify(receipt).includes('private transport detail'),false);
 assert.equal(receipt.modelRequestsObserved,null);
 assert.equal(receipt.accepted,null);
 assert.equal(receipt.rejected,null);
});

test('provider transport impersonation is normalized to fixed untrusted I/O evidence',async()=>{
 const real=fakeCssSourceProvider({briefs:batch.briefs});
 let generations=0;
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate')){
   generations++;
   throw Error('E6_W5H_VALIDATION_REJECTED_AFTER_ONE_SAFE_RETRY');
  }
  return real(url,opts);
 };
 const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport});
 assert.equal(generations,3); // no second model attempt for provider failure
 assert.equal(result.report.accepted,0);
 for(const c of result.report.cases){
  assert.equal(c.rejectionCode,'E6_W5A_PROVIDER_TRANSPORT_FAILURE');
  assert.equal(c.failureKind,'PROVIDER_IO_OR_LIMIT');
  assert.equal(c.failurePhase,'DESIGN');
  assert.equal(c.phaseReceipt.css,'NOT_EXECUTED');
  assert.equal(c.releaseGo,false);
 }
});
test('provider timeout names are sanitized without echoing transport details',async()=>{
 const real=fakeCssSourceProvider({briefs:batch.briefs});
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate')){
    const e=Error('internal model token or private path');
    e.name='TimeoutError';throw e;
  }
  return real(url,opts);
 };
 const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport});
 for(const c of result.report.cases){
  assert.equal(c.rejectionCode,'E6_W5A_PROVIDER_TIMEOUT_OR_ABORT');
  assert.equal(c.failureKind,'PROVIDER_ABORT_OR_TIMEOUT');
 }
 assert.equal(JSON.stringify(result.report).includes('internal model token'),false);
});
test('interrupted provider response stream is a fixed I/O rejection and never repaired',async()=>{
 const real=fakeCssSourceProvider({briefs:batch.briefs});
 let generations=0;
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate')){
   generations++;
   const body=new ReadableStream({start(controller){
    controller.error(Error('E6_W5C_PROPERTY_OR_VALUE_DENIED'));
   }});
   return new Response(body,{status:200});
  }
  return real(url,opts);
 };
 const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport});
 assert.equal(generations,3);
 for(const c of result.report.cases){
  assert.equal(c.rejectionCode,'E6_W5A_PROVIDER_STREAM_INTERRUPTED');
  assert.equal(c.failureKind,'PROVIDER_IO_OR_LIMIT');
  assert.equal(c.codeSecurityPolicyPassed,false);
 }
});
test('oversized model response remains explicitly rejected under stream error hardening',async()=>{
 const real=fakeCssSourceProvider({briefs:batch.briefs});
 const transport=async(url,opts)=>{
  if(url.endsWith('/api/generate'))
   return new Response('x'.repeat(49_000),{status:200});
  return real(url,opts);
 };
 const result=await evaluateCodeHeldout({batch,model:MODEL,baseCss:css,transport});
 for(const c of result.report.cases){
  assert.equal(c.accepted,false);
  assert.equal(c.rejectionCode,'E6_W5A_MODEL_RESPONSE_BUDGET');
  assert.equal(c.failureKind,'PROVIDER_IO_OR_LIMIT');
 }
});

async function savedMockTrial({badIndex=-1}={}){
 const root=await mkdtemp(join(tmpdir(),'aetheris-w5j-source-audit-'));
 try{
  const outcome=await evaluateCodeHeldout({
   batch,model:MODEL,baseCss:css,
   transport:fakeCssSourceProvider({briefs:batch.briefs,badIndex})
  });
  outcome.report.providerWasActualLoopbackRequest=true; // offline TEST DOUBLE
  for(const c of outcome.report.cases)if(c.accepted){
   c.designStopReasonVerified=true;
   c.cssStopReasonVerified=true;
   c.designReportedStopReason='stop';
   c.cssReportedStopReason='stop';
  }
  for(const [slug,site] of outcome.sites){
   for(const [path,content] of site.files){
    const file=join(root,slug,path);
    await mkdir(dirname(file),{recursive:true});
    await writeFile(file,content);
   }
  }
  const reportRawBytes=JSON.stringify(outcome.report,null,2)+'\n';
  await writeFile(join(root,'source-code-trial-report.json'),reportRawBytes);
  return {root,report:outcome.report,reportRawBytes};
 }catch(e){await rm(root,{recursive:true,force:true});throw e;}
}
test('W5-J independently verifies all three saved candidates before marking source files reviewable',async()=>{
 const v=await savedMockTrial();
 try{
  const result=await summarizeWithSavedSourceAudit(v.report,batch,{
   sourceSha:SHA,reportRawBytes:v.reportRawBytes,trialRoot:v.root
  });
  assert.equal(result.accepted,3);
  assert.equal(result.savedCandidateFilesHashVerified,true);
  assert.match(result.savedSourceAuditSha256,/^[a-f0-9]{64}$/);
  assert.equal(result.independentModelAuthorshipVerified,false);
  assert.equal(result.humanDesignApproved,false);
  assert.equal(result.stage45Go,false);
  assert.equal(result.releaseGo,false);
 }finally{await rm(v.root,{recursive:true,force:true});}
});
test('W5-J rejects post-report stylesheet tampering before writing a reviewable summary',async()=>{
 const v=await savedMockTrial();
 try{
  const file=join(v.root,batch.briefs[0].projectSlug,'assets/site.css');
  await writeFile(file,(await readFile(file,'utf8'))+'\nbody{display:none}');
  await assert.rejects(
   summarizeWithSavedSourceAudit(v.report,batch,{
    sourceSha:SHA,reportRawBytes:v.reportRawBytes,trialRoot:v.root
   }),/E6_W5D_GENERATED_FILE_HASH_MISMATCH/
  );
 }finally{await rm(v.root,{recursive:true,force:true});}
});
test('W5-J refuses missing or symlinked source artifacts even with valid self-reported hashes',async()=>{
 const v=await savedMockTrial();
 try{
  const file=join(v.root,batch.briefs[1].projectSlug,'assets/site.css');
  await rm(file);
  await symlink(join(v.root,batch.briefs[1].projectSlug,'index.html'),file);
  await assert.rejects(
   summarizeWithSavedSourceAudit(v.report,batch,{
    sourceSha:SHA,reportRawBytes:v.reportRawBytes,trialRoot:v.root
   }),/E6_W5D_UNSAFE_FILE_OR_SIZE/
  );
 }finally{await rm(v.root,{recursive:true,force:true});}
});
test('W5-J incomplete case summaries never claim all saved source files were checked',async()=>{
 const v=await savedMockTrial({badIndex:1});
 try{
  const result=await summarizeWithSavedSourceAudit(v.report,batch,{
   sourceSha:SHA,reportRawBytes:v.reportRawBytes,trialRoot:v.root
  });
  assert.equal(result.accepted,2);
  assert.equal(result.savedCandidateFilesHashVerified,false);
  assert.equal(result.savedSourceAuditSha256,null);
  assert.equal(result.candidateStatus,'INCOMPLETE_REJECTED_OR_TRUNCATED');
  assert.equal(result.releaseGo,false);
 }finally{await rm(v.root,{recursive:true,force:true});}
});
test('W5-J real CPU summary path is wired to the actual W5-D disk audit',async()=>{
 const source=await readFile(new URL('./w5j_cpu_trial_cli.mjs',import.meta.url),'utf8');
 const gateSource=await readFile(new URL('./w5j_cpu_trial_gate.mjs',import.meta.url),'utf8');
 assert(source.includes('await summarizeWithSavedSourceAudit('));
 assert(gateSource.includes("await import('./w5d_trial_integrity.mjs')"));
 assert(source.includes("trialRoot:join(here,'target/e6-w5c-local-css-research')"));
});

test('report envelope rejects injected authority, raw prompts, and undocumented fields',async()=>{
 for(const mutate of [
  r=>{r.internalToken='example-private-token';},
  r=>{r.rawModelReply='should never be archived';},
  r=>{r.runtime.reportedModelWeightsExecuted=true;},
  r=>{r.runtime.privateEndpoint='http://outside.invalid';},
  r=>{r.runtime.modelDigestCryptographicallyAttested=true;},
  r=>{r.runtime.modelDigestCryptographicallyAttested='unknown';},
  r=>{delete r.runtime.reportedVersion;},
  r=>{r.runtime.reportedVersion='unsafe\nvalue';}
 ]){
  const v=await report();mutate(v);
  assert.throws(()=>summarize(v),/INVALID_TRIAL_EVIDENCE/);
 }
});
test('accepted case envelope rejects extra source, secrets, or authority fields',async()=>{
 for(const mutate of [
  c=>{c.rawModelReply='private generated model text';},
  c=>{c.ownerApproved=true;},
  c=>{c.accessToken='fixture-secret';},
  c=>{c.extraEvidence=true;},
  c=>{delete c.initialDesignPromptSha256;},
  c=>{delete c.deterministicRepairBudgetPerPhase;}
 ]){
  const v=await report();mutate(v.cases[0]);
  assert.throws(()=>summarize(v),/UNVERIFIED_ACCEPTED_CANDIDATE/);
 }
});
test('rejected case envelope rejects raw source and invented proof fields',async()=>{
 for(const mutate of [
  c=>{c.rawRejectedSource='untrusted CSS';},
  c=>{c.errorStack='filesystem path';},
  c=>{c.modelWeightsAttested=true;},
  c=>{delete c.phaseReceipt;},
  c=>{c.metadataURL='https://example.invalid';}
 ]){
  const v=await report(1);mutate(v.cases[1]);
  assert.throws(()=>summarize(v),/UNSAFE_REJECTED_CASE|INCONSISTENT_CASE_PROVENANCE/);
 }
});

test('nonempty unverified report receipt cannot invent case attempts or model requests',()=>{
 const receipt=summarizeUnverifiableCaseReport({sourceSha:SHA,caseReportPresent:true});
 assert.equal(receipt.kind,'E6_W5J_UNVERIFIABLE_CASE_REPORT_V1');
 assert.equal(receipt.verifiedCaseReceipts,0);
 for(const field of ['threeHeldoutCasesAttempted','modelRequestsObserved','accepted','rejected'])
  assert.equal(receipt[field],null);
 for(const field of ['sourceIntegrityVerified','browserReviewVerified','stage45Go','releaseGo'])
  assert.equal(receipt[field],false);
 assert.equal(JSON.stringify(receipt).includes('modelOutput'),false);
 for(const context of [
  {sourceSha:SHA,caseReportPresent:false},
  {sourceSha:'wrong',caseReportPresent:true},{}
 ])assert.throws(()=>summarizeUnverifiableCaseReport(context),
   /UNVERIFIABLE_REPORT_CONTEXT_REJECTED/);
});
test('CLI writes write-once sanitized nonempty-report failure without parsing raw model evidence',async()=>{
 const dir=await isolatedCli();
 try{
  const target=join(dir,'target'),source=join(target,'e6-w5c-local-css-research');
  await mkdir(source,{recursive:true});
  const secret='untrusted private model response MUST NOT LEAK';
  await writeFile(join(source,'source-code-trial-report.json'),'{'+secret);
  const script=join(dir,'w5j_cpu_trial_cli.mjs');
  const first=spawnSync(process.execPath,[script,'unverified-report'],{
   env:offlineCliEnv(),encoding:'utf8',timeout:10000
  });
  assert.equal(first.status,0,first.stderr);
  const receipt=JSON.parse(await readFile(join(
   target,'e6-w5j-cloud-cpu-review/unverifiable-case-report.json'),'utf8'));
  assert.equal(receipt.status,'NO_VERIFIABLE_CASE_SUMMARY');
  assert.equal(receipt.modelRequestsObserved,null);
  assert.equal(JSON.stringify(receipt).includes(secret),false);
  assert.equal(first.stdout.includes(secret),false);
  const second=spawnSync(process.execPath,[script,'unverified-report'],{
   env:offlineCliEnv(),encoding:'utf8',timeout:10000
  });
  assert.notEqual(second.status,0);
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('unverified-report CLI refuses missing source file and a symlink source directory',async()=>{
 const dir=await isolatedCli(),outside=await mkdtemp(join(tmpdir(),'w5j-unverified-outside-'));
 try{
  const script=join(dir,'w5j_cpu_trial_cli.mjs');
  const target=join(dir,'target'),source=join(target,'e6-w5c-local-css-research');
  await mkdir(source,{recursive:true});
  const env=offlineCliEnv();
  const missing=spawnSync(process.execPath,[script,'unverified-report'],{
   env,encoding:'utf8',timeout:10000
  });
  assert.notEqual(missing.status,0);
  await rm(source,{recursive:true});
  await writeFile(join(outside,'source-code-trial-report.json'),'{}');
  await symlink(outside,source);
  const linked=spawnSync(process.execPath,[script,'unverified-report'],{
   env,encoding:'utf8',timeout:10000
  });
  assert.notEqual(linked.status,0);
  await assert.rejects(readFile(join(target,
   'e6-w5j-cloud-cpu-review/unverifiable-case-report.json')));
 }finally{
  await rm(dir,{recursive:true,force:true});
  await rm(outside,{recursive:true,force:true});
 }
});
test('workflow archives only sanitized receipts after failed or unverified source summaries',async()=>{
 const wf=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 const summary=wf.indexOf('if ! node w5j_cpu_trial_cli.mjs summarize; then');
 const fallback=wf.indexOf('node w5j_cpu_trial_cli.mjs unverified-report');
 const failure=wf.indexOf('E6_W5J_NONEMPTY_REPORT_SUMMARY_REJECTED_NO_RELEASE');
 assert(summary>0&&fallback>summary&&failure>fallback);
 const archive=wf.slice(wf.indexOf('- name: Seven-day sanitized summary'));
 const always=archive.split('- name: Seven-day checked candidate')[0];
 const verified=archive.slice(archive.indexOf('- name: Seven-day checked candidate'));
 assert(always.includes('if: always()'));
 assert(always.includes('e6-w5j-cloud-cpu-review/'));
 assert(!always.includes('e6-w5c-local-css-research/'));
 assert(!always.includes('e6-w5d-local-owner-browser-evidence/'));
 assert(verified.includes('if: success()'));
 assert(verified.includes('e6-w5c-local-css-research/'));
 assert(verified.includes('e6-w5d-local-owner-browser-evidence/'));
});

test('W5-J browser quality runner must measure real Chromium root text resize on both routes',async()=>{
 const file=new URL('./w5d_owner_browser_review.mjs',import.meta.url);
 const content=await readFile(file,'utf8');
 assert(content.includes("await page.route(cssUrl,async route=>{"));
 assert(content.includes("route.fetch({maxRedirects:0,timeout:10000})"));
 assert(content.includes("String.fromCharCode(10)+'html {font-size:200% !important}'"));
 assert(content.includes("await page.unroute(cssUrl)"));
 assert(!content.includes('page.addStyleTag('));
 for(const failure of [
   'TEXT_RESIZE_SCALE_FAILED',
   'TEXT_RESIZE_HORIZONTAL_OVERFLOW',
   'TEXT_RESIZE_CONTENT_MISSING'
 ])assert(content.includes("refuse('"+failure+"')"));
 assert(content.includes('/^E6_W5D_[A-Z0-9_]+$/'));
 assert(content.includes('textResize200PercentRouteScanCount:views.length*2'));
 assert(content.includes('bothRoutesTextResizePassed:views.length===9'));
 const mock=await readFile(new URL('./w5j_mock_full_pipeline_browser.mjs',import.meta.url),'utf8');
 assert(mock.includes('assert.equal(review.textResize200PercentRouteScanCount,18)'));
 assert(mock.includes('assert.equal(review.bothRoutesTextResizePassed,true)'));
 const workflow=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 assert(workflow.includes("resized['browserUiZoomVerified'] is False"));
 assert(workflow.includes("resized['ownerDeviceVerified'] is False"));
 assert(workflow.includes("resized['independentWcagCertified'] is False"));
});

test('W6 200-percent reflow gate keeps fail-closed geometry diagnostics and responsive source policy',async()=>{
 const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
 const runner=await readFile(new URL('./w5d_owner_browser_review.mjs',import.meta.url),'utf8');
 assert(css.includes('.brand{font-weight:800;letter-spacing:-.04em;text-decoration:none;min-width:0;max-width:100%;overflow-wrap:anywhere}'));
 assert(css.includes('.nav a{text-underline-offset:.35em;overflow-wrap:anywhere}'));
 assert(css.includes('grid-template-columns:minmax(0,1fr)'));
 assert(css.includes('.footer>*{min-width:0;max-width:100%;overflow-wrap:anywhere}'));
 assert(runner.includes('E6_W5D_TEXT_RESIZE_OVERFLOW_METRIC'));
 assert(runner.includes('structuralSelectors:resized.overflowSelectors'));
 assert(runner.includes("refuse('TEXT_RESIZE_HORIZONTAL_OVERFLOW')"));
 assert(!runner.includes('document.documentElement.style.overflow'));
});

test('W6 screenshot verifier rejects missing review files and malformed matrix without invoking a model',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'aetheris-w6-absence-'));
 try{
   await assert.rejects(verifyScreenshotEvidence(dir,batch),
     /MISSING_OR_UNSAFE_EVIDENCE_FILE/);
   await writeFile(join(dir,'review-report.json'),JSON.stringify({
     kind:'E6_W5D_LOCAL_BROWSER_REVIEW_V1',profiles:[],
     fullPageScreenshotCount:18
   }));
   await assert.rejects(verifyScreenshotEvidence(dir,batch),
     /UNVERIFIED_BROWSER_REVIEW/);
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('W6 rejects fabricated held-out identities before accessing candidate evidence',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'aetheris-w6-identities-'));
 try{
   for(const bad of [
     {briefs:[{projectSlug:'a'}]},
     {briefs:[{projectSlug:'a'},{projectSlug:'a'},{projectSlug:'c'}]},
     {briefs:[{projectSlug:'../bad'},{projectSlug:'b'},{projectSlug:'c'}]}
   ])await assert.rejects(verifyScreenshotEvidence(dir,bad),
     /INVALID_FIXED_REVIEW_CONTEXT/);
 }finally{await rm(dir,{recursive:true,force:true});}
});
test('W6 screenshot archive verification is required by owner CPU workflow',async()=>{
 const workflow=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 const browser=workflow.indexOf('node w5d_owner_browser_review.mjs');
 const gate=workflow.indexOf('node w6_screenshot_integrity_gate.mjs');
 const upload=workflow.indexOf('- name: Seven-day checked candidate');
 assert(browser>0&&gate>browser&&upload>gate);
 const mock=await readFile(new URL('./w5j_mock_full_pipeline_browser.mjs',import.meta.url),'utf8');
 assert(mock.includes('verifyScreenshotEvidence(evidence,batch)'));
 assert(mock.includes('screenshotTamperingRejected:true'));
});

test('W6 local performance lab requires actual Chromium timings and same-origin bounded assets',async()=>{
 const reviewer=await readFile(new URL('./w5d_owner_browser_review.mjs',import.meta.url),'utf8');
 for(const required of [
  "response.url()!==origin+route",
  "maxRedirects:0,timeout:10000",
  "stylesheetBytes>25_000",
  "performance.getEntriesByType('navigation')[0]",
  "refuse('LAB_NAVIGATION_TIMING_UNVERIFIED')",
  "refuse('LAB_STATIC_TRANSFER_BUDGET_EXCEEDED')",
  "fieldCoreWebVitalsMeasured:false",
  "physicalDevicePerformanceMeasured:false",
  "localNavigationLabRouteCount:views.length*2"
 ])assert(reviewer.includes(required),'missing W6 lab constraint '+required);
 const workflow=await readFile(new URL('../../../../.github/workflows/runtime-e6-w5j-optin-heldout-cpu.yml',import.meta.url),'utf8');
 assert(workflow.includes("result['localNavigationLabRouteCount'] == 18"));
 assert(workflow.includes("result['localNavigationLabBudgetsPassed'] is True"));
 assert(workflow.includes("nav['fieldCoreWebVitalsMeasured'] is False"));
});
