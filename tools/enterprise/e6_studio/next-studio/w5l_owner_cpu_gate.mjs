/** W5-L future owner-approved CPU research: policy only, no side effects.
 * New label MUST NOT reuse W5-J's consumed experiment authorization.
 */
const fail=code=>{throw Error('E6_W5L_'+code);};
export const CPU_LABEL='e6-w5l-six-cell-cpu-approved';
const OWNER='teldigi5-wq';
const REPO='teldigi5-wq/aetheris-ai-runtime';
const BASE='feat/e6-w5i-local-inference-completion-20261102';
const SHA=s=>typeof s==='string'&&/^[a-f0-9]{40}$/.test(s);
const approvedLabels=labels=>Array.isArray(labels)&&labels.length<=100&&
 labels.filter(x=>x&&typeof x==='object'&&x.name===CPU_LABEL).length===1;
const validContext=({actor,repository,expectedPr,headSha}={})=>
 actor===OWNER&&repository===REPO&&expectedPr===275&&SHA(headSha);
const validPr=(pr,headSha)=>pr&&typeof pr==='object'&&!Array.isArray(pr)&&
 pr.number===275&&pr.state==='open'&&pr.draft===true&&
 pr.merged_at===null&&pr.user?.login===OWNER&&
 pr.head?.sha===headSha&&pr.head?.repo?.full_name===REPO&&
 pr.base?.ref===BASE&&pr.base?.repo?.full_name===REPO;
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
