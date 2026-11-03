/** E6 W5-L: detached-digest, source-pinned research metadata receipt.
 * Checks byte-level consistency with an independently retained digest;
 * DOES NOT attest model authorship, provider behavior, actual calls or design quality.
 * No provider/network/filesystem write or authorization capability.
 */
import {createHash,timingSafeEqual} from 'node:crypto';
import {summarizePairedResearch} from './w5l_paired_evaluation_protocol.mjs';

const fail=x=>{throw Error('E6_W5L_'+x);};
const sha=value=>createHash('sha256').update(JSON.stringify(value),'utf8').digest('hex');
const isHex=x=>typeof x==='string'&&/^[a-f0-9]{64}$/.test(x);
const isCommit=x=>typeof x==='string'&&/^[a-f0-9]{40}$/.test(x);
const exact=(x,keys)=>!!x&&typeof x==='object'&&!Array.isArray(x)&&
 [Object.prototype,null].includes(Object.getPrototypeOf(x))&&
 Reflect.ownKeys(x).length===keys.length&&
 keys.every(k=>Object.hasOwn(x,k)&&
  Object.hasOwn(Object.getOwnPropertyDescriptor(x,k)||{},'value'))&&
 Reflect.ownKeys(x).every(k=>typeof k==='string'&&keys.includes(k));
const observationKeys=['projectSlug','mode','outcome','failureCode',
 'reportedDesignCalls','reportedOutputTokens','reportedElapsedMs'];
const receiptKeys=['kind','protocolSha256','sourceCommitSha','briefSetSha256',
 'classification','acceptanceScope','observations',
 'realProviderEvidenceVerified','ownerExperimentApproved',
 'comparisonWinnerEstablished','releaseGo','stage45Go','receiptSha256'];
const copyRow=x=>Object.freeze({
 projectSlug:x.projectSlug,mode:x.mode,outcome:x.outcome,
 failureCode:x.failureCode,reportedDesignCalls:x.reportedDesignCalls,
 reportedOutputTokens:x.reportedOutputTokens,reportedElapsedMs:x.reportedElapsedMs
});
function payload(protocol,observations){
 // The protocol validator separately checks the complete canonical brief SHA,
 // full six-cell matrix, model tag, deadline and its SHA-256.
 const summary=summarizePairedResearch(protocol,observations);
 const rows=Object.freeze(observations.map(copyRow));
 return Object.freeze({
  kind:'E6_W5L_UNATTESTED_DESIGN_OBSERVATION_RECEIPT_V1',
  protocolSha256:summary.protocolSha256,
  sourceCommitSha:summary.sourceCommitSha,
  briefSetSha256:protocol.briefSetSha256,
  classification:'RESEARCHER_SUPPLIED_UNATTESTED_METADATA',
  acceptanceScope:'DESIGN_VALIDATION_ONLY',
  observations:rows,
  realProviderEvidenceVerified:false,
  ownerExperimentApproved:false,
  comparisonWinnerEstablished:false,
  releaseGo:false,stage45Go:false
 });
}
export function createPairedResearchReceipt(protocol,observations){
 const content=payload(protocol,observations);
 return Object.freeze({...content,receiptSha256:sha(content)});
}
export function verifyPairedResearchReceipt({
 receipt,protocol,expectedReceiptSha256,expectedSourceCommitSha
}={}){
 if(!isHex(expectedReceiptSha256)||!isCommit(expectedSourceCommitSha))
   fail('INDEPENDENT_DIGEST_AND_SOURCE_REQUIRED');
 if(!exact(receipt,receiptKeys)||!isHex(receipt.receiptSha256)||
    receipt.kind!=='E6_W5L_UNATTESTED_DESIGN_OBSERVATION_RECEIPT_V1'||
    receipt.classification!=='RESEARCHER_SUPPLIED_UNATTESTED_METADATA'||
    receipt.acceptanceScope!=='DESIGN_VALIDATION_ONLY'||
    receipt.realProviderEvidenceVerified!==false||
    receipt.ownerExperimentApproved!==false||
    receipt.comparisonWinnerEstablished!==false||
    receipt.stage45Go!==false||receipt.releaseGo!==false||
    !Array.isArray(receipt.observations)||
    Object.getPrototypeOf(receipt.observations)!==Array.prototype||
    receipt.observations.length!==6||
    Reflect.ownKeys(receipt.observations).length!==7)
   fail('UNTRUSTED_RECEIPT_STRUCTURE');
 for(let i=0;i<6;i++){
   if(!Object.hasOwn(receipt.observations,i)||
      !exact(receipt.observations[i],observationKeys))
     fail('UNTRUSTED_RECEIPT_STRUCTURE');
 }
 if(receipt.sourceCommitSha!==expectedSourceCommitSha||
    protocol?.sourceCommitSha!==expectedSourceCommitSha)
   fail('SOURCE_COMMIT_NOT_PINNED');
 const canonical=payload(protocol,receipt.observations);
 const computed=sha(canonical);
 if(receipt.protocolSha256!==canonical.protocolSha256||
    receipt.briefSetSha256!==canonical.briefSetSha256||
    receipt.receiptSha256!==computed||
    !timingSafeEqual(Buffer.from(computed,'hex'),
      Buffer.from(expectedReceiptSha256,'hex')))
   fail('RECEIPT_DIGEST_OR_PAYLOAD_MISMATCH');
 // Return a fresh constrained comparison, NEVER an "approved" model verdict.
 return Object.freeze({
  kind:'E6_W5L_DETACHED_DIGEST_METADATA_CHECK_V1',
  receiptSha256:computed,
  sourceCommitSha:expectedSourceCommitSha,
  detachedDigestMatched:true,
  comparison:summarizePairedResearch(protocol,receipt.observations),
  authenticModelProvenanceEstablished:false,
  independentlyVerifiedModelRequests:0,
  releaseGo:false,stage45Go:false
 });
}
