/** Aetheris reference contract for connected flagship experience state.
 * No network, storage, model calls, credentials, tool invocations or booking.
 * Wayfarer integration is NOT claimed by this standalone source module.
 */
export const STAGES=Object.freeze(["discover","plan","arrive","explore"]);
export const SOURCE_STATES=Object.freeze([
  "PROVIDER_RESPONSE_OBSERVED","ESTIMATE","AI_SUGGESTION","UNAVAILABLE"
]);
const FIELDS=Object.freeze(["destination","travelers","currency","budget","startDate","endDate"]);
function rejectUnknown(x,allowed,name) {
  if(!x||typeof x!=="object"||Array.isArray(x)||Object.keys(x).some(key=>!allowed.includes(key)))
    throw new TypeError(name+" contains unknown fields or is not an object");
}
function exactDay(value) {
  if(typeof value!=="string"||! /^\d{4}-\d\d-\d\d$/.test(value))return false;
  const parsed=new Date(value+"T00:00:00.000Z");
  return !Number.isNaN(parsed.getTime())&&parsed.toISOString().slice(0,10)===value;
}
function safeText(value,max=90) {
  return typeof value==="string"&&value.trim().length>=2&&value.trim().length<=max&&
    !/[\u0000-\u001f\u007f<>]/.test(value);
}
function clone(x){return JSON.parse(JSON.stringify(x));}
function freeze(x) {
  if(x&&typeof x==="object") {
    for(const v of Object.values(x))freeze(v);
    Object.freeze(x);
  }
  return x;
}
function validCore(x){
  rejectUnknown(x,FIELDS,"trip brief");
  if(!safeText(x.destination)||!Number.isInteger(x.travelers)||x.travelers<1||x.travelers>12)
    throw new RangeError("invalid destination/traveler count");
  if(typeof x.currency!=="string"||!/^[A-Z]{3}$/.test(x.currency)||
     !Number.isFinite(x.budget)||x.budget<=0||x.budget>10000000)
    throw new RangeError("currency or budget invalid");
  if(!exactDay(x.startDate)||!exactDay(x.endDate)||x.endDate<x.startDate)
    throw new RangeError("invalid travel dates");
  return clone(x);
}
function checkState(s){
  rejectUnknown(s,["schemaVersion","stage","brief","itineraryId","evidence","arrivalChecklist","metadata"],
    "journey state");
  if(s.schemaVersion!==1||!STAGES.includes(s.stage))throw new TypeError("unsupported stage/version");
  validCore(s.brief);
  if(s.itineraryId!==null&&(!safeText(s.itineraryId,64)))throw new TypeError("bad itinerary reference");
  if(!Array.isArray(s.evidence)||s.evidence.length>20)throw new TypeError("bad evidence");
  for(const e of s.evidence)checkEvidence(e);
  if(!Array.isArray(s.arrivalChecklist)||s.arrivalChecklist.length>12||
     s.arrivalChecklist.some(x=>!safeText(x,150)))throw new TypeError("bad arrival checklist");
  if(!s.metadata||s.metadata.executionStatus!=="NOT_WIRED"||
     s.metadata.bookingStatus!=="NOT_AVAILABLE"||
     s.metadata.physicalPcStatus!=="BLOCKED_PENDING_HARDWARE"||
     s.metadata.providerVerification!=="NOT_ASSERTED")throw new TypeError("execution truth boundary");
}
function checkEvidence(e){
  rejectUnknown(e,["type","label","sourceUrl","observedAt"],"evidence");
  if(!SOURCE_STATES.includes(e.type)||!safeText(e.label,120))
    throw new TypeError("bad evidence class or label");
  if(e.type==="PROVIDER_RESPONSE_OBSERVED"){
    if(!safeUrl(e.sourceUrl)||!dateTime(e.observedAt))throw new TypeError("provider response requires source URL and observation time");
  } else if(e.sourceUrl!==null||e.observedAt!==null){
    throw new TypeError("non-provider evidence must not masquerade as sourced result");
  }
}
function safeUrl(url){
  if(typeof url!=="string"||url.length>300)return false;
  try {
    const u=new URL(url);
    return u.protocol==="https:"&&!!u.hostname&&!u.username&&!u.password&&u.port===""&&
      !["localhost","127.0.0.1","0.0.0.0"].includes(u.hostname)&&!u.hostname.endsWith(".local");
  }catch{return false;}
}
function dateTime(value){
  return typeof value==="string"&&/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{3})?Z$/.test(value)&&
    !Number.isNaN(Date.parse(value));
}
export function createDraft(brief) {
  const state={
    schemaVersion:1,stage:"discover",brief:validCore(brief),itineraryId:null,evidence:[],
    arrivalChecklist:[],
    metadata:{
      executionStatus:"NOT_WIRED",bookingStatus:"NOT_AVAILABLE",
      physicalPcStatus:"BLOCKED_PENDING_HARDWARE",providerVerification:"NOT_ASSERTED"
    }
  };
  return freeze(state);
}
export function appendEvidence(state,event){
  checkState(state);checkEvidence(event);
  if(state.evidence.length>=20)throw new RangeError("evidence limit reached");
  const next=clone(state);next.evidence.push(clone(event));
  return freeze(next);
}
export function advance(state,nextStage,approval={}) {
  checkState(state);
  if(!STAGES.includes(nextStage)||STAGES.indexOf(nextStage)!==STAGES.indexOf(state.stage)+1)
    throw new RangeError("only one forward stage transition is allowed");
  rejectUnknown(approval,["approvedByUser","itineraryId","arrivalChecklist"],"approval");
  if(approval.approvedByUser!==true)throw new Error("explicit traveler approval required for handoff");
  const next=clone(state);
  if(nextStage==="arrive"){
    if(!safeText(approval.itineraryId,64))throw new Error("approved itinerary reference required");
    next.itineraryId=approval.itineraryId;
    if(!Array.isArray(approval.arrivalChecklist)||approval.arrivalChecklist.length>12||
       approval.arrivalChecklist.some(x=>!safeText(x,150)))
      throw new Error("arrival checklist must be explicitly supplied");
    next.arrivalChecklist=clone(approval.arrivalChecklist);
  }else if(approval.itineraryId!==undefined||approval.arrivalChecklist!==undefined){
    throw new Error("arrival fields only permitted on plan-to-arrive handoff");
  }
  next.stage=nextStage;
  return freeze(next);
}
