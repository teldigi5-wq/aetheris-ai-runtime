/** E6 W5-L: bounded OFFLINE research comparison, not an inference runner.
 * No imports from model transport, no CLI, network, persistent writes,
 * owner label, workflow execution, model provenance, or release authority.
 * Results are researcher-supplied descriptive observations, NOT verified
 * model outputs. A three-brief synthetic fixture is never a real win.
 */
import {createHash} from 'node:crypto';
import {validateOwnerBrief} from './w5a_model_design_compiler.mjs';
const fail=c=>{throw Error('E6_W5L_'+c);};
const sha=s=>createHash('sha256').update(s,'utf8').digest('hex');
const commit=s=>typeof s==='string'&&/^[0-9a-f]{40}$/.test(s);
const keys=(x,k)=>!!x&&typeof x==='object'&&!Array.isArray(x)&&
 [Object.prototype,null].includes(Object.getPrototypeOf(x))&&
 Reflect.ownKeys(x).length===k.length&&
 k.every(n=>Object.hasOwn(x,n)&&Object.hasOwn(Object.getOwnPropertyDescriptor(x,n)||{},'value'))&&
 Reflect.ownKeys(x).every(n=>typeof n==='string'&&k.includes(n));
const modes=Object.freeze([
 Object.freeze({id:'single_compact',designCallsMax:2,designTokenCeilingPerCall:3000}),
 Object.freeze({id:'three_stage',designCallsMax:3,designTokenCeilingPerCall:3000})
]);
const slugs=[
 'quiet-heat-ceramic-library',
 'afterlight-mapping-atelier',
 'paper-migration-journal'
];
const failures=new Set([
 'DESIGN_ABNORMAL_FINISH','DESIGN_SCHEMA_REJECTED',
 'DESIGN_RESPONSE_BUDGET','DESIGN_TIMEOUT_OR_TRANSPORT',
 'DESIGN_MODEL_UNAVAILABLE','DESIGN_OTHER_BOUNDED',
 'CSS_REJECTED','COMPILE_REJECTED','BROWSER_REJECTED'
]);
function pairs(briefs){
 if(!Array.isArray(briefs)||Object.getPrototypeOf(briefs)!==Array.prototype||
    briefs.length!==3||Reflect.ownKeys(briefs).length!==4)
   fail('BRIEF_MATRIX_INVALID');
 for(let i=0;i<3;i++){
   if(!Object.hasOwn(briefs,i))fail('BRIEF_MATRIX_INVALID');
   validateOwnerBrief(briefs[i]);
   if(briefs[i].projectSlug!==slugs[i])fail('BRIEF_MATRIX_CHANGED');
 }
 return slugs.flatMap(projectSlug=>modes.map(mode=>Object.freeze({
   projectSlug,mode:mode.id,
   designCallsMax:mode.designCallsMax,
   outputTokenCeilingPerCall:mode.designTokenCeilingPerCall
 })));
}
export function buildPairedEvaluationProtocol({briefs,sourceCommitSha}={}){
 if(!commit(sourceCommitSha))fail('SOURCE_SHA_NOT_GIT_COMMIT');
 const planned=pairs(briefs);
 const briefSetSha256=sha(JSON.stringify(briefs));
 const protocol={
   kind:'E6_W5L_OFFLINE_PAIRED_PROTOCOL_V1',
   sourceCommitSha,briefSetSha256,
   modelTag:'qwen2.5:0.5b',
   runtimeVersion:'0.40.13',
   executionMode:'CPU_ONLY_FUTURE_OWNER_APPROVAL_REQUIRED',
   jobWallClockCeilingMinutes:55,
   executionOrder:'SERIAL_FIXED_BRIEF_THEN_METHOD',
   methods:modes,
   planned,
   measurementsVerified:false,
   modelInvocationAuthorized:false,
   modelRequestsMade:0,
   modelWeightsCryptographicallyAttested:false,
   stage45Go:false,releaseGo:false
 };
 return Object.freeze({...protocol,
   protocolSha256:sha(JSON.stringify(protocol))
 });
}
function checkFixedProtocol(p){
 const expected=['kind','sourceCommitSha','briefSetSha256','modelTag',
   'runtimeVersion','executionMode','jobWallClockCeilingMinutes',
   'executionOrder','methods','planned','measurementsVerified',
   'modelInvocationAuthorized','modelRequestsMade',
   'modelWeightsCryptographicallyAttested','stage45Go','releaseGo',
   'protocolSha256'];
 if(!keys(p,expected)||p.kind!=='E6_W5L_OFFLINE_PAIRED_PROTOCOL_V1'||
    !commit(p.sourceCommitSha)||!/^[a-f0-9]{64}$/.test(p.briefSetSha256)||
    p.modelTag!=='qwen2.5:0.5b'||p.runtimeVersion!=='0.40.13'||
    p.executionMode!=='CPU_ONLY_FUTURE_OWNER_APPROVAL_REQUIRED'||
    p.executionOrder!=='SERIAL_FIXED_BRIEF_THEN_METHOD'||
    p.jobWallClockCeilingMinutes!==55||p.measurementsVerified!==false||
    p.modelInvocationAuthorized!==false||p.modelRequestsMade!==0||
    p.modelWeightsCryptographicallyAttested!==false||
    p.releaseGo!==false||p.stage45Go!==false||
    !/^[a-f0-9]{64}$/.test(p.protocolSha256))
   fail('PROTOCOL_NOT_ACCEPTED');
 if(!Array.isArray(p.methods)||p.methods.length!==2||
    !Array.isArray(p.planned)||p.planned.length!==6)
   fail('PROTOCOL_MATRIX_CHANGED');
 for(let i=0;i<2;i++){
   const actual=p.methods[i],original=modes[i];
   if(!keys(actual,['id','designCallsMax','designTokenCeilingPerCall'])||
      actual.id!==original.id||
      actual.designCallsMax!==original.designCallsMax||
      actual.designTokenCeilingPerCall!==original.designTokenCeilingPerCall)
      fail('PROTOCOL_MATRIX_CHANGED');
 }
 for(let i=0;i<6;i++){
   const actual=p.planned[i],method=modes[i%2];
   if(!keys(actual,['projectSlug','mode','designCallsMax','outputTokenCeilingPerCall'])||
      actual.projectSlug!==slugs[Math.floor(i/2)]||
      actual.mode!==method.id||
      actual.designCallsMax!==method.designCallsMax||
      actual.outputTokenCeilingPerCall!==method.designTokenCeilingPerCall)
      fail('PROTOCOL_MATRIX_CHANGED');
 }
}
export function summarizePairedResearch(protocol,observations){
 checkFixedProtocol(protocol);
 const {protocolSha256,...payload}=protocol;
 if(sha(JSON.stringify(payload))!==protocolSha256)
   fail('PROTOCOL_DIGEST_MISMATCH');
 if(!Array.isArray(observations)||Object.getPrototypeOf(observations)!==Array.prototype||
    observations.length!==6||Reflect.ownKeys(observations).length!==7)
   fail('INCOMPLETE_PAIRED_MATRIX');
 const totals=modes.map(m=>({method:m.id,accepted:0,rejected:0,notRun:0,
   reportedDesignCalls:0,reportedOutputTokens:0,reportedElapsedMs:0}));
 const rows=[];
 for(let i=0;i<6;i++){
   if(!Object.hasOwn(observations,i))fail('INCOMPLETE_PAIRED_MATRIX');
   const x=observations[i],expected=protocol.planned[i];
   if(!keys(x,['projectSlug','mode','outcome','failureCode','reportedDesignCalls','reportedOutputTokens','reportedElapsedMs'])||
      x.projectSlug!==expected.projectSlug||x.mode!==expected.mode||
      !['ACCEPTED','REJECTED','NOT_RUN'].includes(x.outcome))
     fail('UNTRUSTED_RESEARCH_OBSERVATION');
   const totalsRow=totals.find(t=>t.method===x.mode);
   if(x.outcome==='NOT_RUN'){
     if(x.failureCode!==null||x.reportedDesignCalls!==null||
        x.reportedOutputTokens!==null||x.reportedElapsedMs!==null)
       fail('FABRICATED_NOT_RUN_METRICS');
     totalsRow.notRun++;
   }else{
     const cap=expected.designCallsMax*expected.outputTokenCeilingPerCall;
     if(!Number.isInteger(x.reportedDesignCalls)||x.reportedDesignCalls<0||
        x.reportedDesignCalls>expected.designCallsMax||
        !Number.isInteger(x.reportedOutputTokens)||x.reportedOutputTokens<0||
        x.reportedOutputTokens>cap||
        !Number.isInteger(x.reportedElapsedMs)||x.reportedElapsedMs<0||
        x.reportedElapsedMs>protocol.jobWallClockCeilingMinutes*60*1000||
        (x.reportedDesignCalls===0&&x.reportedOutputTokens!==0)||
        (x.outcome==='ACCEPTED'&&
          (x.failureCode!==null||x.reportedDesignCalls<1||
           x.reportedOutputTokens<1))||
        (x.outcome==='REJECTED'&&!failures.has(x.failureCode)))
       fail('INVALID_BOUNDED_OBSERVATION');
     totalsRow.reportedDesignCalls+=x.reportedDesignCalls;
     totalsRow.reportedOutputTokens+=x.reportedOutputTokens;
     totalsRow.reportedElapsedMs+=x.reportedElapsedMs;
     totalsRow[x.outcome==='ACCEPTED'?'accepted':'rejected']++;
   }
   rows.push({projectSlug:x.projectSlug,mode:x.mode,outcome:x.outcome,
     failureCode:x.failureCode});
 }
 // The approved 55-minute ceiling is for ONE serial job, not six jobs.
 // Per-cell checks alone could previously admit six hours of observations.
 const cumulativeReportedElapsedMs=totals.reduce((n,t)=>n+t.reportedElapsedMs,0);
 if(cumulativeReportedElapsedMs>protocol.jobWallClockCeilingMinutes*60*1000)
   fail('AGGREGATE_JOB_TIME_BUDGET_EXCEEDED');
 const complete=totals.every(t=>t.notRun===0);
 return Object.freeze({
   kind:'E6_W5L_UNATTESTED_RESEARCH_COMPARISON_V1',
   sourceCommitSha:protocol.sourceCommitSha,
   protocolSha256,
   plannedPairs:3,
   executionOrder:protocol.executionOrder,
   cumulativeReportedElapsedMs,
   methods:totals,
   observations:rows,
   allSixCellsReported:complete,
   comparableBudget:false,
   // Accepted observations do NOT prove model inference or human approval.
   observedAcceptanceDifference:complete?
     totals[1].accepted-totals[0].accepted:null,
   assessment:'SELF_REPORTED_ONLY_NOT_A_REAL_MODEL_VERDICT',
   realProviderEvidenceVerified:false,
   originalDesignQualityVerified:false,
   ownerExperimentApproved:false,
   statisticallyPowered:false,
   stage45Go:false,releaseGo:false
 });
}
