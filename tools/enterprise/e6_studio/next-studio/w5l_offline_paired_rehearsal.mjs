/** W5-L: deterministic offline rehearsal only, never a real-model comparison.
 * Six human-authored test-double cells exercise compact and three-stage
 * design validators. No model transports, external requests, artifact writes,
 * resource measurements or release authority exist in this module.
 */
import {buildPairedEvaluationProtocol} from './w5l_paired_evaluation_protocol.mjs';
import {parseProviderEnvelope} from './w5a_local_ollama.mjs';
import {validateModelProposal} from './w5a_model_design_compiler.mjs';
import {evaluateStagedDesignOffline} from './w5k_staged_design_research.mjs';

const MODEL='test-research:1';
const fail=code=>{throw Error('E6_W5L_'+code);};
const exact=(x,fields)=>x&&typeof x==='object'&&!Array.isArray(x)&&
  [Object.prototype,null].includes(Object.getPrototypeOf(x))&&
  Reflect.ownKeys(x).length===fields.length&&
  fields.every(k=>Object.hasOwn(x,k)&&Object.hasOwn(Object.getOwnPropertyDescriptor(x,k)||{},'value'))&&
  Reflect.ownKeys(x).every(k=>typeof k==='string'&&fields.includes(k));
const envelopeFields=['model','done','done_reason','response','eval_count'];
function checkFixtureEnvelope(x){
  if(!exact(x,envelopeFields)||x.model!==MODEL||x.done!==true||
     typeof x.done_reason!=='string'||x.done_reason.length>40||
     typeof x.response!=='string'||x.response.length>36_000||
     !Number.isInteger(x.eval_count)||x.eval_count<1||x.eval_count>3001)
    fail('INVALID_OFFLINE_FIXTURE_ENVELOPE');
  if(Buffer.byteLength(JSON.stringify(x),'utf8')>48_000)
    fail('INVALID_OFFLINE_FIXTURE_ENVELOPE');
}
function rejectionCode(error){
  const message=error?.message;
  return typeof message==='string'&&
    /^E6_W5(?:A|B|K)_[A-Z0-9_]{1,96}$/.test(message)
     ?message:'E6_W5L_SYNTHETIC_VALIDATION_REJECTED';
}
/** No W5-L V2 observations are produced: simulated acceptance is not data
 * about real model performance and must not enter the comparison summary.
 */
export function rehearsePairedDesignOffline(options){
  if(!exact(options,['briefs','sourceCommitSha','fixturePairs']))
    fail('OFFLINE_REHEARSAL_CONTEXT_REQUIRED');
  const {briefs,sourceCommitSha,fixturePairs}=options;
  const protocol=buildPairedEvaluationProtocol({briefs,sourceCommitSha});
  if(!Array.isArray(fixturePairs)||
     Object.getPrototypeOf(fixturePairs)!==Array.prototype||
     fixturePairs.length!==6||Reflect.ownKeys(fixturePairs).length!==7)
    fail('OFFLINE_REHEARSAL_MATRIX_INVALID');
  const rows=[];
  for(let i=0;i<6;i++){
    if(!Object.hasOwn(fixturePairs,i))fail('OFFLINE_REHEARSAL_MATRIX_INVALID');
    const cell=fixturePairs[i],plan=protocol.planned[i];
    if(!exact(cell,['projectSlug','mode','envelopes'])||
       cell.projectSlug!==plan.projectSlug||cell.mode!==plan.mode||
       !Array.isArray(cell.envelopes)||
       Object.getPrototypeOf(cell.envelopes)!==Array.prototype||
       cell.envelopes.length!==(plan.mode==='single_compact'?1:3)||
       Reflect.ownKeys(cell.envelopes).length!==cell.envelopes.length+1)
      fail('OFFLINE_REHEARSAL_MATRIX_INVALID');
    for(let j=0;j<cell.envelopes.length;j++){
      if(!Object.hasOwn(cell.envelopes,j))fail('OFFLINE_REHEARSAL_MATRIX_INVALID');
      checkFixtureEnvelope(cell.envelopes[j]);
    }
    let status='SYNTHETIC_VALIDATED',errorCode=null;
    try{
      if(plan.mode==='single_compact'){
        const parsed=parseProviderEnvelope(cell.envelopes[0],MODEL,{phase:'design'});
        if(parsed.providerStopReasonVerified!==true)
          fail('SYNTHETIC_STOP_UNVERIFIED');
        validateModelProposal(briefs[Math.floor(i/2)],parsed.design);
      }else{
        const result=evaluateStagedDesignOffline({
          brief:briefs[Math.floor(i/2)],model:MODEL,envelopes:cell.envelopes
        });
        if(result.receipt.modelRequestsMade!==0||
           result.receipt.networkTransportSupported!==false)
          fail('OFFLINE_REHEARSAL_AUTHORITY_VIOLATION');
      }
    }catch(error){
      status='SYNTHETIC_REJECTED';
      errorCode=rejectionCode(error);
    }
    rows.push(Object.freeze({
      projectSlug:plan.projectSlug,method:plan.mode,
      plannedDesignCallCeiling:plan.designCallsMax,
      syntheticEnvelopesProvided:cell.envelopes.length,
      simulationStatus:status,rejectionCode:errorCode
    }));
  }
  return Object.freeze({
    kind:'E6_W5L_HUMAN_AUTHORED_OFFLINE_MATRIX_REHEARSAL_V1',
    sourceCommitSha:protocol.sourceCommitSha,
    protocolSha256:protocol.protocolSha256,
    acceptanceScope:'DESIGN_VALIDATION_ONLY',
    fixtureModel:MODEL,
    caseCount:6,caseOutcomes:Object.freeze(rows),
    syntheticValidatedCount:rows.filter(r=>r.simulationStatus==='SYNTHETIC_VALIDATED').length,
    syntheticRejectedCount:rows.filter(r=>r.simulationStatus==='SYNTHETIC_REJECTED').length,
    simulatedProviderEnvelopesProvided:rows.reduce((n,r)=>n+r.syntheticEnvelopesProvided,0),
    actualModelRequestsMade:0,actualDesignCallsObserved:0,
    genuineAcceptanceObservationsReported:0,
    wallClockMeasurementsMade:false,modelWeightsAttested:false,
    comparisonWinnerEstablished:false,
    realProviderEvidenceVerified:false,ownerExperimentApproved:false,
    humanVisualApproved:false,stage45Go:false,releaseGo:false
  });
}
