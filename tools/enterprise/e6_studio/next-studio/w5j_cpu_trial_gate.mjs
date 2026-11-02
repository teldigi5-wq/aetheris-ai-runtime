/**
 * W5-J — a separate, EXPLICITLY OWNER-LABELED CPU research experiment.
 * Summarize a genuine attempted 3-brief run without raw prompts, output,
 * authority, model-weight attestation or false release claims.
 *
 * All normal PR CI tests use human-authored fakes. A self-reported loopback
 * response is NOT a cryptographic proof that the expected model weights ran.
 */
import {createHash} from 'node:crypto';
const fail=x=>{throw Error('E6_W5J_'+x);};
const sha=x=>createHash('sha256').update(x).digest('hex');
const hex=v=>typeof v==='string'&&/^[a-f0-9]{64}$/.test(v);
const OWNER='teldigi5-wq',REPO='teldigi5-wq/aetheris-ai-runtime',
  BASE='feat/e6-w5i-local-inference-completion-20261102',
  LABEL='e6-w5j-three-brief-cpu-approved';
const requiredFalse=[
  'modelRuntimeAttested','modelAuthorshipIndependentlyAttested',
  'modelGeneratedJavaScript','humanVisualApproved','physicalGpuMeasured',
  'rightsIndependentlyCertified','ownerApproved','stage45Go','releaseGo'
];
export function authorizeLabelEvent(event,{actor,repository,expectedPr,headSha}={}){
 if(!event||typeof event!=='object'||Array.isArray(event)||
    !Number.isInteger(expectedPr)||expectedPr<1||
    actor!==OWNER||repository!==REPO||
    event.action!=='labeled'||event.label?.name!==LABEL||
    event.pull_request?.number!==expectedPr||
    event.pull_request?.state!=='open'||event.pull_request?.draft!==true||
    event.pull_request?.user?.login!==OWNER||
    event.pull_request?.head?.repo?.full_name!==REPO||
    event.pull_request?.base?.ref!==BASE||
    !hex(headSha)||event.pull_request?.head?.sha!==headSha)
    fail('NOT_OWNER_AUTHORIZED_PR_LABEL_EVENT');
 return {kind:'E6_W5J_CPU_OWNER_LABEL_AUTHORIZATION_V1',
   expectedPr,reviewedHeadSha:headSha,repository:REPO,
   label:LABEL,actor:OWNER,
   gpuAuthorization:false,modelExecutionOnBorrowedPc:false,
   productionAuthorization:false};
}
const safeRejection=x=>typeof x==='string'&&
  /^E6_W5[ABCH]_[A-Z0-9_]{1,96}$/.test(x);
const outputFiles=Object.freeze(['index.html','approach/index.html','assets/site.css']);
const exactFileHashes=x=>x&&typeof x==='object'&&!Array.isArray(x)&&
  Object.keys(x).length===outputFiles.length&&
  outputFiles.every(k=>hex(x[k]));

const FAILURE_KINDS=new Set([
 'PROVIDER_ABORT_OR_TIMEOUT','PROVIDER_IO_OR_LIMIT','PROVIDER_PROTOCOL',
 'COMPILER_POLICY','MODEL_CONTENT_OR_SCHEMA','UNCLASSIFIED'
]);
const validPhaseReceipt=(c,accepted)=>{
 const p=c.phaseReceipt;
 if(!p||typeof p!=='object'||Array.isArray(p)||
    Object.keys(p).sort().join(',')!=='compile,css,design')return false;
 const phases=['DESIGN','CSS','COMPILE'];
 if(accepted)return phases.every(k=>p[k.toLowerCase()]==='PASSED')&&
    c.failurePhase===undefined&&c.failureKind===undefined;
 const failed=phases.indexOf(c.failurePhase);
 if(failed<0||!FAILURE_KINDS.has(c.failureKind))return false;
 return phases.every((k,i)=>
   p[k.toLowerCase()]===(i<failed?'PASSED':i===failed?'FAILED':'NOT_EXECUTED'));
};


const exactKeys=(v,allowed)=>v&&typeof v==='object'&&!Array.isArray(v)&&
  Object.keys(v).length===allowed.length&&
  allowed.every(k=>Object.hasOwn(v,k));
// These source-owned receipts are archived. Unexpected keys can carry
// raw provider data, secrets, or invented authority, so reject them.
const reportKeys=[
 'kind','runtime','briefCount','providerWasActualLoopbackRequest',
 'modelRuntimeAttested','modelAuthorshipIndependentlyAttested',
 'modelGeneratedJavaScript','modelAuthoredRestrictedCssCandidate',
 'sourcePolicyEnforced','accepted','rejected','cases',
 'humanVisualApproved','physicalGpuMeasured','rightsIndependentlyCertified',
 'ownerApproved','stage45Go','releaseGo'
];
const runtimeKeys=[
 'endpoint','reportedVersion','reportedModelName','reportedModelDigest',
 'modelDigestCryptographicallyAttested'
];
const caseCommon=[
 'projectSlug','briefSha256','modelReportedName','phaseReceipt',
 'accepted','status','modelRuntimeAttested','codeSecurityPolicyPassed',
 'humanVisualAccepted','releaseGo'
];
const acceptedKeys=[...caseCommon,
 'designPromptSha256','cssPromptSha256','initialDesignPromptSha256',
 'initialCssPromptSha256','designRequestAttempts','cssRequestAttempts',
 'designTokenBudget','cssTokenBudget','designReportedStopReason',
 'cssReportedStopReason','designStopReasonVerified','cssStopReasonVerified',
 'designReportedOutputTokens','cssReportedOutputTokens',
 'designFirstRejectionCode','cssFirstRejectionCode',
 'rawRejectedModelOutputSaved','deterministicRepairBudgetPerPhase',
 'designRawReplySha256','cssRawReplySha256','designSha256',
 'modelAuthoredCssCandidateSha256','outputSha256',
 'modelAuthorshipIndependentlyAttested'
];
const rejectedKeys=[...caseCommon,'rejectionCode','failurePhase','failureKind'];
const expectedVersion=v=>typeof v==='string'&&v.length>0&&v.length<=48&&
 /^[0-9A-Za-z.+-]+$/.test(v);

const sameReportBytes=(report,bytes)=>{
  if(!(typeof bytes==='string'||Buffer.isBuffer(bytes))||
     Buffer.byteLength(bytes)<50||Buffer.byteLength(bytes)>120_000)
    fail('REPORT_RAW_BYTES_REQUIRED');
  let parsed;
  try{parsed=JSON.parse(bytes.toString());}catch{fail('REPORT_RAW_JSON_REJECTED');}
  if(JSON.stringify(parsed)!==JSON.stringify(report))
    fail('REPORT_BYTES_DO_NOT_MATCH_STRUCTURED_EVIDENCE');
  return sha(bytes);
};
// A failed trial with no saved case report is NOT three rejected model cases.
// The stage that failed and whether any provider requests ran remain unknown.
export function summarizeMissingCaseReport({sourceSha,caseReportAvailable}={}){
 if(!hex(sourceSha)||caseReportAvailable!==false)
   fail('MISSING_REPORT_CONTEXT_REJECTED');
 return {
   kind:'E6_W5J_MISSING_CASE_REPORT_V1',
   sourceCommitSha:sourceSha,
   status:'NO_VERIFIABLE_CASE_REPORT',
   observedCondition:'W5C_SOURCE_CASE_REPORT_ABSENT_OR_EMPTY',
   verifiedCaseReceipts:0,
   threeHeldoutCasesAttempted:null,
   modelRequestsObserved:null,
   accepted:null,rejected:null,
   modelRuntimeAttested:false,modelWeightsCryptographicallyAttested:false,
   sourceIntegrityVerified:false,browserReviewVerified:false,
   humanVisualApproved:false,stage45Go:false,releaseGo:false
 };
}

 // There is a nonempty report file, but the summary attempt failed. This
 // receipt does NOT diagnose its content or assert that any model ran.
export function summarizeUnverifiableCaseReport({sourceSha,caseReportPresent}={}){
 if(!hex(sourceSha)||caseReportPresent!==true)
   fail('UNVERIFIABLE_REPORT_CONTEXT_REJECTED');
 return {
   kind:'E6_W5J_UNVERIFIABLE_CASE_REPORT_V1',
   sourceCommitSha:sourceSha,
   status:'NO_VERIFIABLE_CASE_SUMMARY',
   observedCondition:'W5C_NONEMPTY_REPORT_SUMMARY_NOT_VERIFIED',
   verifiedCaseReceipts:0,
   threeHeldoutCasesAttempted:null,
   modelRequestsObserved:null,
   accepted:null,rejected:null,
   modelRuntimeAttested:false,modelWeightsCryptographicallyAttested:false,
   sourceIntegrityVerified:false,browserReviewVerified:false,
   humanVisualApproved:false,stage45Go:false,releaseGo:false
 };
}

export function summarizeObservedTrial(report,briefs,{sourceSha,reportRawBytes}={}){
 const reportRawFileSha256=sameReportBytes(report,reportRawBytes);
 if(!hex(sourceSha)||!Array.isArray(briefs)||briefs.length!==3||
    briefs.some(x=>!x||typeof x.projectSlug!=='string')||
    new Set(briefs.map(x=>x.projectSlug)).size!==3)
   fail('INVALID_FIXED_RESEARCH_CONTEXT');
 if(!exactKeys(report,reportKeys)||
   !exactKeys(report.runtime,runtimeKeys)||
   !expectedVersion(report.runtime.reportedVersion)||
   report.runtime.modelDigestCryptographicallyAttested!==false||
   report.kind!=='E6_W5C_LOCAL_SOURCE_CODE_TRIAL_V1'||
   report.briefCount!==3||report.providerWasActualLoopbackRequest!==true||
   report.sourcePolicyEnforced!==true||
   report.modelAuthoredRestrictedCssCandidate!==true||
   !report.runtime||report.runtime.endpoint!=='LOOPBACK_OLLAMA'||
   typeof report.runtime.reportedModelName!=='string'||
   !/^(?:sha256:)?[a-f0-9]{64}$/i.test(report.runtime.reportedModelDigest||'')||
   !Array.isArray(report.cases)||report.cases.length!==3||
   !Number.isInteger(report.accepted)||!Number.isInteger(report.rejected)||
   report.accepted<0||report.rejected<0||
   report.accepted+report.rejected!==3||
   requiredFalse.some(k=>report[k]!==false))
   fail('INVALID_TRIAL_EVIDENCE');
 let counted=0;
 const cases=report.cases.map((c,i)=>{
   if(!c||c.projectSlug!==briefs[i].projectSlug||
      c.briefSha256!==sha(JSON.stringify(briefs[i]))||
      c.modelReportedName!==report.runtime.reportedModelName||
      c.modelRuntimeAttested!==false||c.releaseGo!==false)
     fail('INCONSISTENT_CASE_PROVENANCE');
   if(c.accepted===true){
     if(!exactKeys(c,acceptedKeys))fail('UNVERIFIED_ACCEPTED_CANDIDATE');
     counted++;
     if(c.status!=='HUMAN_REVIEW_REQUIRED'||
       c.codeSecurityPolicyPassed!==true||
       c.humanVisualAccepted!==false||
       c.modelAuthorshipIndependentlyAttested!==false||
       c.designStopReasonVerified!==true||
       c.cssStopReasonVerified!==true||
       c.designReportedStopReason!=='stop'||
       c.cssReportedStopReason!=='stop'||
       c.designTokenBudget!==3000||c.cssTokenBudget!==900||
       !Number.isInteger(c.designRequestAttempts)||c.designRequestAttempts<1||
       c.designRequestAttempts>2||
       !Number.isInteger(c.cssRequestAttempts)||c.cssRequestAttempts<1||
       c.cssRequestAttempts>2||
       c.rawRejectedModelOutputSaved!==false||
       !hex(c.designRawReplySha256)||!hex(c.cssRawReplySha256)||
       !hex(c.designPromptSha256)||!hex(c.cssPromptSha256)||
       !hex(c.modelAuthoredCssCandidateSha256)||
       !hex(c.designSha256)||
       !exactFileHashes(c.outputSha256)||
       !validPhaseReceipt(c,true))
       fail('UNVERIFIED_ACCEPTED_CANDIDATE');
     return {projectSlug:c.projectSlug,accepted:true,
       phaseReceipt:c.phaseReceipt,
       reviewStatus:'STATIC_SOURCE_AND_BROWSER_REVIEW_REQUIRED',
       designRequests:c.designRequestAttempts,
       cssRequests:c.cssRequestAttempts,
       modelOutputSha256:c.modelAuthoredCssCandidateSha256,
       sourceIntegrityProvenBySelfReport:false};
   }
   if(!exactKeys(c,rejectedKeys)||
      c.accepted!==false||c.status!=='REJECTED'||
      c.codeSecurityPolicyPassed!==false||
      c.humanVisualAccepted!==false||
      !safeRejection(c.rejectionCode)||
      !validPhaseReceipt(c,false))
     fail('UNSAFE_REJECTED_CASE');
   return {projectSlug:c.projectSlug,accepted:false,
     rejectionCode:c.rejectionCode,failurePhase:c.failurePhase,
     failureKind:c.failureKind,phaseReceipt:c.phaseReceipt,
     reviewStatus:'REJECTED'};
 });
 if(counted!==report.accepted)fail('CASE_COUNT_MISMATCH');
 return {kind:'E6_W5J_GENUINE_PROVIDER_RESPONSE_SUMMARY_V1',
   sourceCommitSha:sourceSha,
   modelReportedName:report.runtime.reportedModelName,
   modelDigestReportedByProvider:report.runtime.reportedModelDigest,
   threeHeldoutCasesAttempted:true,accepted:counted,rejected:3-counted,
   cases,
   reportSha256:sha(JSON.stringify(report)),
   reportRawFileSha256,
   candidateStatus:counted===3?'ALL_THREE_SOURCE_CANDIDATES_FOR_REVIEW':'INCOMPLETE_REJECTED_OR_TRUNCATED',
   providerSelfReportedLoopback:true,modelWeightsCryptographicallyAttested:false,
   independentModelAuthorshipVerified:false,independentRightsAudited:false,
   humanDesignApproved:false,physicalGpuTested:false,
   ownerHardwareBenchmarked:false,independentWcagCertified:false,
   stage45Go:false,releaseGo:false};
}

// This additional disk-backed check is *only* for the 3/3 accepted case.
// A self-reported hash is not independent evidence that a file exists.
export async function summarizeWithSavedSourceAudit(report,heldoutSet,{
 sourceSha,reportRawBytes,trialRoot
}={}){
 const summary=summarizeObservedTrial(report,heldoutSet?.briefs,{
  sourceSha,reportRawBytes
 });
 if(summary.accepted!==3)return {
  ...summary,savedCandidateFilesHashVerified:false,
  savedSourceAuditSha256:null
 };
 if(typeof trialRoot!=='string'||!trialRoot)
   fail('MISSING_SOURCE_AUDIT_ROOT');
 // Load the disk auditor only for 3/3 accepted cases. Authorization and
 // missing-report paths must remain independent of candidate audit imports.
 const {inspectTrial}=await import('./w5d_trial_integrity.mjs');
 const {audit}=await inspectTrial(trialRoot,heldoutSet);
 if(audit.inputReportSha256!==summary.reportRawFileSha256||
    audit.acceptedSiteCount!==3||
    audit.allCandidateFilesHashVerified!==true||
    audit.releaseGo!==false||
    audit.actualWeightsExecutionAttested!==false)
   fail('SAVED_SOURCE_AUDIT_MISMATCH');
 return {...summary,
  savedCandidateFilesHashVerified:true,
  savedSourceAuditSha256:sha(JSON.stringify(audit)),
  independentModelAuthorshipVerified:false,
  humanDesignApproved:false,
  releaseGo:false
 };
}
