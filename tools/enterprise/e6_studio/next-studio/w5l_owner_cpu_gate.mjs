/** W5-L future owner-approved CPU research: policy only, no side effects.
 * New label MUST NOT reuse W5-J's consumed experiment authorization.
 */
const fail=code=>{throw Error('E6_W5L_'+code);};
export const CPU_LABEL='e6-w5l-six-cell-cpu-approved';
// A GitHub re-run inherits the ORIGINAL actor's credentials even when
// initiated by a different user. One label does not authorize new reruns.
export function authorizePairedCpuRunnerAttempt({actor,triggeringActor,runAttempt}={}){
 if(actor!=='teldigi5-wq'||triggeringActor!=='teldigi5-wq'||runAttempt!=='1')
  fail('FIRST_OWNER_TRIGGERED_CPU_ATTEMPT_ONLY');
 return Object.freeze({kind:'E6_W5L_FIRST_OWNER_RUN_ATTEMPT_V1',
  ownerInitiated:true,firstRunAttempt:true,
  releaseGo:false,stage45Go:false});
}
const OWNER='teldigi5-wq';
const REPO='teldigi5-wq/aetheris-ai-runtime';
const BASE='feat/e6-w5i-local-inference-completion-20261102';
const BASE_SHA='d1abab54b80af1ec63ede7360869c4d9308441e5';
const SHA=s=>typeof s==='string'&&/^[a-f0-9]{40}$/.test(s);
const approvedLabels=labels=>Array.isArray(labels)&&labels.length<=100&&
 labels.filter(x=>x&&typeof x==='object'&&x.name===CPU_LABEL).length===1;
const validContext=({actor,repository,expectedPr,headSha}={})=>
 actor===OWNER&&repository===REPO&&expectedPr===275&&SHA(headSha);
const validPr=(pr,headSha)=>pr&&typeof pr==='object'&&!Array.isArray(pr)&&
 pr.number===275&&pr.state==='open'&&pr.draft===true&&
 pr.merged_at===null&&pr.user?.login===OWNER&&
 pr.head?.sha===headSha&&pr.head?.repo?.full_name===REPO&&
 pr.base?.ref===BASE&&pr.base?.sha===BASE_SHA&&
 pr.base?.repo?.full_name===REPO;
export function authorizePairedCpuLabelEvent(event,context={}){
 if(!validContext(context)||!event||typeof event!=='object'||
    Array.isArray(event)||event.action!=='labeled'||
    event.label?.name!==CPU_LABEL||
    !validPr(event.pull_request,context.headSha)||
    !approvedLabels(event.pull_request?.labels))
   fail('EXACT_OWNER_PAIRED_CPU_LABEL_REQUIRED');
 return Object.freeze({
   kind:'E6_W5L_EXACT_OWNER_LABEL_EVENT_V1',
   approvedPr:275,sourceCommitSha:context.headSha,
   freshLabel:CPU_LABEL,allowedCases:6,
   gpuAuthorized:false,modelWeightsAttested:false,
   ownerVisualApproved:false,stage45Go:false,releaseGo:false
 });
}
export function authorizeLivePairedCpuPr(live,context={}){
 if(!validContext(context)||!validPr(live,context.headSha)||
    !approvedLabels(live.labels))
   fail('LIVE_PAIRED_CPU_APPROVAL_REVOKED_OR_HEAD_CHANGED');
 return Object.freeze({
   kind:'E6_W5L_LIVE_PR_OWNER_RECHECK_V1',
   approvedPr:275,sourceCommitSha:context.headSha,
   newLabelStillPresent:true,gpuAuthorized:false,
   productionAuthorized:false,stage45Go:false,releaseGo:false
 });
}

/** An aborted study provides no independently checked case observations.
 * Never infer all six cases were attempted, nor count missing results as FAIL.
 * This status has no model/runtime attestation, regardless of error source.
 */
export function makeUnverifiablePairedCpuAttempt({sourceCommitSha,reportedGithubRunId}={}){
 if(!SHA(sourceCommitSha)||typeof reportedGithubRunId!=='string'||
    !/^[0-9]{1,24}$/.test(reportedGithubRunId))
   fail('UNVERIFIABLE_ATTEMPT_CONTEXT_INVALID');
 return Object.freeze({
   kind:'E6_W5L_UNVERIFIABLE_CPU_ATTEMPT_V1',
   sourceCommitSha,reportedGithubRunId,
   status:'NO_COMPLETE_VERIFIABLE_SIX_CELL_RECEIPT',
   attemptedModelRequests:null,attemptedCells:null,
   completedCells:null,accepted:null,rejected:null,notRun:null,
   independentlyVerifiedCaseReceipts:0,
   actualModelExecutionAttested:false,
   modelWeightsAttested:false,
   ownerExperimentApproved:false,
   originalDesignQualityVerified:false,
   stage45Go:false,releaseGo:false
 });
}
