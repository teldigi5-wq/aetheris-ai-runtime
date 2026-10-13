import test from "node:test";
import assert from "node:assert/strict";
import {createDraft,appendEvidence,advance,STAGES,SOURCE_STATES} from "../experience-design/connected-journey.mjs";
const brief={destination:"Kyoto, Japan",travelers:2,currency:"USD",budget:1200,startDate:"2026-12-01",endDate:"2026-12-05"};
test("journey is immutable and begins unverified in Discover",()=>{
  const s=createDraft(brief);
  assert.equal(s.stage,"discover");
  assert.equal(s.metadata.executionStatus,"NOT_WIRED");
  assert.equal(s.metadata.providerVerification,"NOT_ASSERTED");
  assert.equal(s.metadata.bookingStatus,"NOT_AVAILABLE");
  assert.equal(s.metadata.physicalPcStatus,"BLOCKED_PENDING_HARDWARE");
  assert.equal(s.evidence.length,0);assert.ok(Object.isFrozen(s.brief));
  assert.deepEqual(STAGES,["discover","plan","arrive","explore"]);
  assert.deepEqual(SOURCE_STATES,["PROVIDER_RESPONSE_OBSERVED","ESTIMATE","AI_SUGGESTION","UNAVAILABLE"]);
});
test("requires traveler consent and sequential transitions to arrival",()=>{
  const a=createDraft(brief);
  assert.throws(()=>advance(a,"arrive",{approvedByUser:true}),/stage/i);
  assert.throws(()=>advance(a,"plan",{}),/approval/i);
  const b=advance(a,"plan",{approvedByUser:true});
  assert.equal(b.stage,"plan");assert.equal(a.stage,"discover");
  assert.throws(()=>advance(b,"arrive",{approvedByUser:true}),/itinerary/i);
  const c=advance(b,"arrive",{approvedByUser:true,itineraryId:"user-approved-itinerary-1",
    arrivalChecklist:["Save hotel address","Check verified airport transfer"]});
  assert.equal(c.itineraryId,"user-approved-itinerary-1");
  assert.equal(c.arrivalChecklist.length,2);
  assert.equal(b.itineraryId,null);
  const d=advance(c,"explore",{approvedByUser:true});
  assert.equal(d.stage,"explore");
  assert.throws(()=>advance(d,"plan",{approvedByUser:true}),/stage/i);
});
test("source timestamps and provider URLs never imply independently verified pricing",()=>{
  const state=createDraft(brief);
  assert.throws(()=>appendEvidence(state,{type:"PROVIDER_RESPONSE_OBSERVED",label:"Price $100",
    sourceUrl:null,observedAt:null}),/source URL/);
  const event={type:"PROVIDER_RESPONSE_OBSERVED",label:"Provider reported rate, check directly",
    sourceUrl:"https://example.com/provider",observedAt:"2026-10-13T01:00:00Z"};
  const next=appendEvidence(state,event);
  assert.equal(next.evidence.length,1);assert.equal(state.evidence.length,0);
  assert.equal(next.metadata.providerVerification,"NOT_ASSERTED");
  assert.equal(next.evidence[0].type,"PROVIDER_RESPONSE_OBSERVED");
  assert.throws(()=>appendEvidence(state,{...event,sourceUrl:"http://localhost/admin"}),/source URL/);
  assert.throws(()=>appendEvidence(state,{...event,sourceUrl:"https://evil@example.com/x"}),/source URL/);
});
test("unknown keys, private credentials and nonsensical dates fail closed",()=>{
  assert.throws(()=>createDraft({...brief,apiKey:"do-not-store"}),/unknown/i);
  assert.throws(()=>createDraft({...brief,startDate:"2026-02-30"}),/date/i);
  assert.throws(()=>createDraft({...brief,budget:-1}),/budget/i);
  assert.throws(()=>createDraft({...brief,destination:"<script>"}),/destination/i);
  assert.throws(()=>createDraft({...brief,travelers:100}),/traveler/i);
  assert.throws(()=>createDraft({...brief,currency:"usd"}),/currency/i);
  assert.throws(()=>advance(createDraft(brief),"plan",{approvedByUser:true,secret:"x"}),/unknown/i);
});
test("no fake provider status, unlimited evidence or missing report provenance",()=>{
  const s=createDraft(brief);
  assert.throws(()=>appendEvidence(s,{type:"VERIFIED",label:"Hotel price",sourceUrl:null,observedAt:null}),/evidence/i);
  assert.throws(()=>appendEvidence(s,{type:"ESTIMATE",label:"Guess",sourceUrl:"https://example.com",observedAt:null}),/masquerade/i);
  assert.throws(()=>appendEvidence(s,{type:"AI_SUGGESTION",label:"Idea",sourceUrl:null,observedAt:"2026-10-13T01:00:00Z"}),/masquerade/i);
  let x=s;
  for(let i=0;i<20;i++)x=appendEvidence(x,{type:"ESTIMATE",label:"Estimate #"+i,sourceUrl:null,observedAt:null});
  assert.throws(()=>appendEvidence(x,{type:"ESTIMATE",label:"One more",sourceUrl:null,observedAt:null}),/limit/i);
});
