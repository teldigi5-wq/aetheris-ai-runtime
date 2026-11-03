import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {buildPairedEvaluationProtocol} from './w5l_paired_evaluation_protocol.mjs';
import {createPairedResearchReceipt,verifyPairedResearchReceipt} from './w5l_research_receipt.mjs';

const briefs=JSON.parse(await readFile(
 new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')).briefs;
const HEAD='a'.repeat(40),OTHER_HEAD='b'.repeat(40);
const protocol=()=>buildPairedEvaluationProtocol({briefs,sourceCommitSha:HEAD});
const empty=p=>p.planned.map(row=>({
 projectSlug:row.projectSlug,mode:row.mode,outcome:'NOT_RUN',
 failureCode:null,reportedDesignCalls:null,reportedOutputTokens:null,
 reportedElapsedMs:null
}));
const make=(rows=empty(protocol()))=>createPairedResearchReceipt(protocol(),rows);
const verify=(receipt,expected=receipt.receiptSha256,source=HEAD)=>
 verifyPairedResearchReceipt({
  receipt,protocol:protocol(),expectedReceiptSha256:expected,
  expectedSourceCommitSha:source
 });

test('all NOT_RUN cases remain unverified and source-bound in deterministic receipt',()=>{
 const p=protocol(),obs=empty(p);
 const first=createPairedResearchReceipt(p,obs);
 assert.deepEqual(first,createPairedResearchReceipt(p,obs));
 assert.match(first.receiptSha256,/^[a-f0-9]{64}$/);
 assert.equal(first.observations.length,6);
 assert.equal(first.observations.every(r=>r.outcome==='NOT_RUN'),true);
 assert(Object.isFrozen(first));
 assert(Object.isFrozen(first.observations));
 assert(first.observations.every(Object.isFrozen));
 const checked=verify(first);
 assert.equal(checked.detachedDigestMatched,true);
 assert.equal(checked.comparison.allSixCellsReported,false);
 assert.equal(checked.comparison.observedAcceptanceDifference,null);
 assert.equal(checked.comparison.methods[0].notRun,3);
 assert.equal(checked.comparison.methods[1].notRun,3);
 assert.equal(checked.authenticModelProvenanceEstablished,false);
 assert.equal(checked.independentlyVerifiedModelRequests,0);
 assert.equal(checked.releaseGo,false);
 assert.equal(checked.stage45Go,false);
});

test('self-rehashed modified outcomes cannot override independently retained receipt digest',()=>{
 const p=protocol(),obs=empty(p);
 obs[0]={...obs[0],outcome:'REJECTED',failureCode:'DESIGN_SCHEMA_REJECTED',
   reportedDesignCalls:1,reportedOutputTokens:50,reportedElapsedMs:200};
 const saved=createPairedResearchReceipt(p,obs);
 const independentlyPinnedDigest=saved.receiptSha256;
 const altered=structuredClone(saved);
 altered.observations[0].outcome='ACCEPTED';
 altered.observations[0].failureCode=null;
 altered.receiptSha256=createHash('sha256').update(JSON.stringify(
  Object.fromEntries(Object.entries(altered).filter(([k])=>k!=='receiptSha256'))
 )).digest('hex');
 assert.notEqual(altered.receiptSha256,independentlyPinnedDigest);
 assert.throws(()=>verify(altered,independentlyPinnedDigest),
   /RECEIPT_DIGEST_OR_PAYLOAD_MISMATCH/);
 assert.equal(verify(saved,independentlyPinnedDigest).comparison.methods[0].rejected,1);
});

test('untrusted receipt does not become authoritative from internal digest alone',()=>{
 const r=make();
 assert.throws(()=>verifyPairedResearchReceipt({receipt:r,protocol:protocol()}),
   /INDEPENDENT_DIGEST_AND_SOURCE_REQUIRED/);
 assert.throws(()=>verify(r,r.receiptSha256,OTHER_HEAD),/SOURCE_COMMIT_NOT_PINNED/);
 assert.throws(()=>verify(r,'f'.repeat(64)),/RECEIPT_DIGEST_OR_PAYLOAD_MISMATCH/);
 const changed=structuredClone(r);changed.ownerApproved=true;
 assert.throws(()=>verify(changed),/UNTRUSTED_RECEIPT_STRUCTURE/);
 const forged=structuredClone(r);forged.realProviderEvidenceVerified=true;
 assert.throws(()=>verify(forged),/UNTRUSTED_RECEIPT_STRUCTURE/);
 const injected=structuredClone(r);
 injected.observations[0].rawModelOutput='untrusted private content';
 assert.throws(()=>verify(injected),/UNTRUSTED_RECEIPT_STRUCTURE/);
});

test('invalid evidence budgets, changed canonical briefs and fabricated cells fail closed',()=>{
 const p=protocol(),obs=empty(p);
 obs[0].reportedElapsedMs=1000;
 assert.throws(()=>createPairedResearchReceipt(p,obs),/FABRICATED_NOT_RUN_METRICS/);
 obs[0].reportedElapsedMs=null;
 obs[0].outcome='ACCEPTED';
 obs[0].reportedDesignCalls=0;obs[0].reportedOutputTokens=0;
 assert.throws(()=>createPairedResearchReceipt(p,obs),/INVALID_BOUNDED_OBSERVATION/);
 const modifiedBriefs=structuredClone(briefs);
 modifiedBriefs[0].purpose+=' Unapproved change.';
 assert.throws(()=>buildPairedEvaluationProtocol({
  briefs:modifiedBriefs,sourceCommitSha:HEAD}),/BRIEF_CONTENT_CHANGED/);
});

test('receipts copy metadata and cannot mutate when original input changes',()=>{
 const obs=empty(protocol());
 const receipt=make(obs),digest=receipt.receiptSha256;
 obs[0].projectSlug='forged';
 assert.equal(receipt.observations[0].projectSlug,briefs[0].projectSlug);
 assert.equal(receipt.receiptSha256,digest);
 assert.throws(()=>{receipt.observations[0].outcome='ACCEPTED';},TypeError);
 assert.throws(()=>receipt.observations.reverse(),TypeError);
 assert.equal(verify(receipt,digest).comparison.methods[1].notRun,3);
});

test('detached integrity checker has no transport, write or model execution authority',async()=>{
 const src=await readFile(new URL('./w5l_research_receipt.mjs',import.meta.url),'utf8');
 for(const forbidden of ['fetch(', 'requestLocalOllama', 'writeFile(', 'createWriteStream',
  'process.env','child_process','https://','workflow_dispatch']){
  assert(!src.includes(forbidden),forbidden);
 }
 assert(src.includes('RESEARCHER_SUPPLIED_UNATTESTED_METADATA'));
 assert(src.includes('authenticModelProvenanceEstablished:false'));
 assert(src.includes('releaseGo:false'));
});
