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
export function summarizeObservedTrial(report,briefs,{sourceSha}={}){
 if(!hex(sourceSha)||!Array.isArray(briefs)||briefs.length!==3||
    briefs.some(x=>!x||typeof x.projectSlug!=='string')||
    new Set(briefs.map(x=>x.projectSlug)).size!==3)
   fail('INVALID_FIXED_RESEARCH_CONTEXT');
 if(!report||report.kind!=='E6_W5C_LOCAL_SOURCE_CODE_TRIAL_V1'||
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
      c.modelReportedName!==report.runtime.reportedModelName||
      c.modelRuntimeAttested!==false||c.releaseGo!==false)
     fail('INCONSISTENT_CASE_PROVENANCE');
   if(c.accepted===true){
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
       typeof c.outputSha256!=='object')
       fail('UNVERIFIED_ACCEPTED_CANDIDATE');
     return {projectSlug:c.projectSlug,accepted:true,
       reviewStatus:'STATIC_SOURCE_AND_BROWSER_REVIEW_REQUIRED',
       designRequests:c.designRequestAttempts,
       cssRequests:c.cssRequestAttempts,
       modelOutputSha256:c.modelAuthoredCssCandidateSha256,
       sourceIntegrityProvenBySelfReport:false};
   }
   if(c.accepted!==false||c.status!=='REJECTED'||
      c.codeSecurityPolicyPassed!==false||
      c.humanVisualAccepted!==false||
      !safeRejection(c.rejectionCode))
     fail('UNSAFE_REJECTED_CASE');
   return {projectSlug:c.projectSlug,accepted:false,
     rejectionCode:c.rejectionCode,reviewStatus:'REJECTED'};
 });
 if(counted!==report.accepted)fail('CASE_COUNT_MISMATCH');
 return {kind:'E6_W5J_GENUINE_PROVIDER_RESPONSE_SUMMARY_V1',
   sourceCommitSha:sourceSha,
   modelReportedName:report.runtime.reportedModelName,
   modelDigestReportedByProvider:report.runtime.reportedModelDigest,
   threeHeldoutCasesAttempted:true,accepted:counted,rejected:3-counted,
   cases,reportSha256:sha(JSON.stringify(report)),
   candidateStatus:counted===3?'ALL_THREE_SOURCE_CANDIDATES_FOR_REVIEW':'INCOMPLETE_REJECTED_OR_TRUNCATED',
   providerSelfReportedLoopback:true,modelWeightsCryptographicallyAttested:false,
   independentModelAuthorshipVerified:false,independentRightsAudited:false,
   humanDesignApproved:false,physicalGpuTested:false,
   ownerHardwareBenchmarked:false,independentWcagCertified:false,
   stage45Go:false,releaseGo:false};
}
