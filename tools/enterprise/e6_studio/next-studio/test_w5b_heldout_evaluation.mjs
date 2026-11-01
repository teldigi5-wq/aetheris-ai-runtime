import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {verifyHeldoutCollection,evaluateProposal,summarizeBatch}
  from './w5b_evaluation_contract.mjs';
import {probeLocalModel,evaluateHeldout} from './w5b_local_heldout_runner.mjs';
import {MODEL,syntheticDesign,fakeLocalProvider} from './w5b_simulated_provider.mjs';
const here=new URL('.',import.meta.url);
const batch=JSON.parse(await readFile(new URL('w5b_heldout_briefs.json',here)));
const css=await readFile(new URL('w5a_base.css',here),'utf8');
test('three unknown fictional heldout briefs are structurally distinct and bounded',()=>{
  const briefs=verifyHeldoutCollection(batch);
  assert.equal(briefs.length,3);
  assert.equal(new Set(briefs.map(x=>x.projectSlug)).size,3);
  assert(briefs.every(x=>x.fictional===true&&x.constraints.length>=2));
});
test('provider probe checks installed exact model and records only reported digest',async()=>{
  const transport=fakeLocalProvider({briefs:batch.briefs});
  const r=await probeLocalModel(MODEL,{transport});
  assert.equal(r.endpoint,'LOOPBACK_OLLAMA');
  assert.equal(r.reportedModelName,MODEL);
  assert.equal(r.reportedModelDigest,'a'.repeat(64));
  assert.equal(r.modelDigestCryptographicallyAttested,false);
});
test('three simulated provider responses compile independently with no model claims',async()=>{
  const r=await evaluateHeldout({
    batch,model:MODEL,baseCss:css,transport:fakeLocalProvider({briefs:batch.briefs})
  });
  assert.equal(r.report.summary.accepted,3);
  assert.equal(r.report.summary.rejected,0);
  assert.equal(r.report.directLoopbackTransportInvoked,false);
  assert.equal(r.report.actualWeightsExecutionAttested,false);
  assert.equal(r.report.independentModelAuthorshipVerified,false);
  assert.equal(r.report.summary.result,'BLOCKED_PENDING_REAL_MODEL_AND_HUMAN_REVIEW');
  assert.equal(r.acceptedSites.size,3);
  const hashes=new Set();
  for(const [slug,{built,evidence}] of r.acceptedSites){
    assert(built.files.has('index.html'));
    assert(built.files.has('approach/index.html'));
    assert(!built.files.get('index.html').includes('<script'));
    assert.equal(evidence.projectSlug,slug);
    assert.equal(evidence.modelCodeGenerated,false);
    assert.equal(evidence.stage45Go,false);
    hashes.add(evidence.candidateSha256);
  }
  assert.equal(hashes.size,3);
});
test('untrusted model output triggers rejection without HTML generation',async()=>{
  const r=await evaluateHeldout({batch,model:MODEL,baseCss:css,
    transport:fakeLocalProvider({briefs:batch.briefs,rejectIndex:1})});
  assert.equal(r.report.summary.accepted,2);
  assert.equal(r.report.summary.rejected,1);
  assert.equal(r.acceptedSites.has(batch.briefs[1].projectSlug),false);
  assert.equal(r.report.cases[1].status,'REJECTED');
  assert.match(r.report.cases[1].rejectionCode,/^E6_W5A_/);
  assert.equal(r.report.stage45Go,false);
});
test('suspicious repeated text/visual direction creates warning, not false originality certificate',()=>{
  const b=batch.briefs;
  const d=syntheticDesign(b[0],0);
  const first=evaluateProposal(b[0],d,css);
  const copied=structuredClone(d);
  copied.projectSlug=b[1].projectSlug;
  const second=evaluateProposal(b[1],copied,css,[first.comparison]);
  assert(second.evidence.warnings.includes('HIGH_LEXICAL_SIMILARITY_NEEDS_HUMAN_REVIEW'));
  assert(second.evidence.warnings.includes('IDENTICAL_ART_DIRECTION_NEEDS_HUMAN_REVIEW'));
  assert.equal(second.evidence.lexicalNoveltyCertified,false);
  assert.equal(second.evidence.status,'REQUIRES_HUMAN_REVIEW_WITH_WARNINGS');
});
test('reject installed-model digest forgery and URL-shaped model identity',async()=>{
  const original=fakeLocalProvider({briefs:batch.briefs});
  const forged=async(url,options)=>{
    if(url.endsWith('/api/tags'))
      return new Response(JSON.stringify({models:[{name:MODEL,digest:'NOT-A-DIGEST'}]}),{status:200});
    return original(url,options);
  };
  await assert.rejects(probeLocalModel(MODEL,{transport:forged}),/MODEL_NOT_INSTALLED/);
  await assert.rejects(probeLocalModel('https://outside.example/model',{transport:original}),
    /INVALID_MODEL_IDENTIFIER/);
  const missing=async(url,options)=>{
    if(url.endsWith('/api/tags'))
      return new Response(JSON.stringify({models:[]}),{status:200});
    return original(url,options);
  };
  await assert.rejects(probeLocalModel(MODEL,{transport:missing}),/MODEL_NOT_INSTALLED/);
});
test('fail closed on repeated and manipulated heldout set and invalid evidence',()=>{
  const duplicate=structuredClone(batch);
  duplicate.briefs[1].projectSlug=duplicate.briefs[0].projectSlug;
  assert.throws(()=>verifyHeldoutCollection(duplicate),/DUPLICATE_BRIEF/);
  assert.throws(()=>summarizeBatch([]),/INCOMPLETE_HELDOUT_EVAL/);
  const forged=[0,1,2].map(i=>({
    projectSlug:batch.briefs[i].projectSlug,accepted:true,candidateSha256:'a'.repeat(64)
  }));
  assert.throws(()=>summarizeBatch(forged),/DUPLICATE_OR_INVALID_OUTPUT/);
});
test('unsupported achievement claims need human review instead of auto-approval',()=>{
  const b=batch.briefs[0],d=syntheticDesign(b,0);
  d.pages[0].sections[0].body='Our industry-leading imaginary artwork has guaranteed results, although this is only a fictional concept intended for critical review.';
  const result=evaluateProposal(b,d,css);
  assert(result.evidence.warnings.includes('UNVERIFIED_OUTCOME_CLAIM_NEEDS_HUMAN_REVIEW'));
  assert.equal(result.evidence.humanVisualApproved,false);
  assert.equal(result.evidence.releaseGo,false);
});
