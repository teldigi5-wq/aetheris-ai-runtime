import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,mkdtemp,mkdir,writeFile,unlink,symlink,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join,dirname} from 'node:path';
import {inspectTrial} from './w5d_trial_integrity.mjs';
import {evaluateCodeHeldout} from './w5c_local_code_trial.mjs';
import {fakeCssSourceProvider} from './w5c_test_doubles.mjs';
import {MODEL} from './w5b_simulated_provider.mjs';
const batch=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8'));
const css=await readFile(new URL('./w5a_base.css',import.meta.url),'utf8');
const project=batch.briefs[0].projectSlug;
async function fixture(){
  const root=await mkdtemp(join(tmpdir(),'e6-w5d-assertions-'));
  const result=await evaluateCodeHeldout({
    batch,model:MODEL,baseCss:css,
    transport:fakeCssSourceProvider({briefs:batch.briefs})
  });
  assert.equal(result.report.accepted,3);
  // Test-only simulation of a self-reported local provider receipt.
  // Setting this boolean is NOT proof of actual model execution.
  result.report.providerWasActualLoopbackRequest=true;
  for(const [slug,site]of result.sites){
    for(const [path,content]of site.files){
      const file=join(root,slug,path);
      await mkdir(dirname(file),{recursive:true});
      await writeFile(file,content);
    }
  }
  await writeFile(join(root,'source-code-trial-report.json'),
    JSON.stringify(result.report,null,2)+'\n');
  return {root,report:result.report,sites:result.sites};
}
async function updateReport(root,mutate){
  const file=join(root,'source-code-trial-report.json');
  const report=JSON.parse(await readFile(file,'utf8'));
  mutate(report);
  await writeFile(file,JSON.stringify(report,null,2)+'\n');
}
async function withFixture(run){
  const v=await fixture();
  try{await run(v);}finally{await rm(v.root,{recursive:true,force:true});}
}
test('three complete hash-verified mock sites are reviewable but NEVER certified model-authored',async()=>{
  await withFixture(async({root})=>{
    const r=(await inspectTrial(root,batch)).audit;
    assert.equal(r.acceptedSiteCount,3);
    assert.equal(r.allCandidateFilesHashVerified,true);
    assert.equal(r.providerSelfReportedLoopbackOnly,true);
    assert.equal(r.actualWeightsExecutionAttested,false);
    assert.equal(r.independentModelAuthorshipVerified,false);
    assert.equal(r.originalityVerified,false);
    assert.equal(r.releaseGo,false);
    assert.equal(r.decision,'REVIEWABLE_LOCAL_CANDIDATES_ONLY');
  });
});
test('reject actual fake provider receipts when no loopback request was made',async()=>{
  await withFixture(async({root})=>{
    await updateReport(root,x=>{x.providerWasActualLoopbackRequest=false;});
    await assert.rejects(inspectTrial(root,batch),/MISSING_COMPLETE_LOOPBACK_TRIAL_RECEIPT/);
  });
});
test('never accept fabricated model attestation, human approval or production GO',async()=>{
  for(const key of ['modelRuntimeAttested','modelAuthorshipIndependentlyAttested',
    'humanVisualApproved','physicalGpuMeasured','rightsIndependentlyCertified',
    'ownerApproved','stage45Go','releaseGo']){
    await withFixture(async({root})=>{
      await updateReport(root,x=>{x[key]=true;});
      await assert.rejects(inspectTrial(root,batch),/UNSUPPORTED_AUTHORITY_CLAIM/);
    });
  }
});
test('tampering with compiled stylesheet fails even when model receipt remains unchanged',async()=>{
  await withFixture(async({root})=>{
    const file=join(root,project,'assets/site.css');
    await writeFile(file,(await readFile(file,'utf8'))+'\n.hero { display:none }\n');
    await assert.rejects(inspectTrial(root,batch),/GENERATED_FILE_HASH_MISMATCH/);
  });
});
test('altering compiled HTML or the case source hashes breaks the integrity chain',async()=>{
  await withFixture(async({root})=>{
    const file=join(root,project,'index.html');
    await writeFile(file,(await readFile(file,'utf8')).replace('Fictional research','Real commercial'));
    await assert.rejects(inspectTrial(root,batch),/GENERATED_FILE_HASH_MISMATCH/);
  });
  await withFixture(async({root})=>{
    await updateReport(root,x=>{x.cases[0].modelAuthoredCssCandidateSha256='f'.repeat(64);});
    await assert.rejects(inspectTrial(root,batch),/INVALID_COMPILED_SOURCE_MANIFEST/);
  });
});
test('deny incomplete results and unapproved extra site files',async()=>{
  await withFixture(async({root})=>{
    await updateReport(root,x=>{x.rejected=1;x.accepted=2;});
    await assert.rejects(inspectTrial(root,batch),/MISSING_COMPLETE_LOOPBACK_TRIAL_RECEIPT/);
  });
  await withFixture(async({root})=>{
    await writeFile(join(root,project,'unexpected.js'),'console.log(1)');
    await assert.rejects(inspectTrial(root,batch),/UNEXPECTED_OUTPUT_ENTRY/);
  });
});
test('deny symlinked source files and symlinked output directories',async()=>{
  await withFixture(async({root})=>{
    const file=join(root,project,'assets/site.css');
    const original=await readFile(file);
    await unlink(file);await symlink(join(root,project,'index.html'),file);
    await assert.rejects(inspectTrial(root,batch),/UNSAFE_FILE_OR_SIZE/);
    assert(original.length>1000);
  });
  await withFixture(async({root})=>{
    const file=join(root,project,'assets');
    const rootCopy=await mkdtemp(join(tmpdir(),'e6-w5d-symlink-'));
    try{
      // Repointing the entire test root through a symbolic link must not be trusted.
      const indirect=join(rootCopy,'link');
      await symlink(root,indirect);
      await assert.rejects(inspectTrial(indirect,batch),/UNTRUSTED_DIRECTORY/);
    }finally{await rm(rootCopy,{recursive:true,force:true});}
  });
});
test('reject forged installed-model digest, unexpected runtime or duplicate output slug',async()=>{
  await withFixture(async({root})=>{
    await updateReport(root,x=>{x.runtime.reportedModelDigest='unverified';});
    await assert.rejects(inspectTrial(root,batch),/MISSING_COMPLETE_LOOPBACK_TRIAL_RECEIPT/);
  });
  await withFixture(async({root})=>{
    await updateReport(root,x=>{x.cases[1].projectSlug=project;});
    await assert.rejects(inspectTrial(root,batch),/INVALID_CASE_RECEIPT/);
  });
});
