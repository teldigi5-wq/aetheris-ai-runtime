import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {rehearsePairedDesignOffline} from './w5l_offline_paired_rehearsal.mjs';
import {syntheticDesign,MODEL as SIMULATED_MODEL} from './w5b_simulated_provider.mjs';

const briefs=JSON.parse(await readFile(new URL('./w5b_heldout_briefs.json',import.meta.url),'utf8')).briefs;
const SOURCE='a'.repeat(40);
const envelope=value=>({
  model:'test-research:1',done:true,done_reason:'stop',
  response:JSON.stringify(value),eval_count:150
});
const cells=()=>briefs.flatMap((brief,i)=>{
  const proposal=syntheticDesign(brief,i);
  const {pages,...identity}=proposal;
  return [
    {projectSlug:brief.projectSlug,mode:'single_compact',envelopes:[envelope(proposal)]},
    {projectSlug:brief.projectSlug,mode:'three_stage',envelopes:[
      envelope(identity),
      envelope({version:1,projectSlug:brief.projectSlug,page:pages[0]}),
      envelope({version:1,projectSlug:brief.projectSlug,page:pages[1]})
    ]}
  ];
});
const rehearsal=fixturePairs=>rehearsePairedDesignOffline({
  briefs,sourceCommitSha:SOURCE,fixturePairs
});

test('all six human-authored simulated design paths pass without genuine inference',()=>{
  const a=rehearsal(cells()),b=rehearsal(cells());
  assert.deepEqual(a,b);
  assert.equal(a.caseCount,6);
  assert.equal(a.caseOutcomes.length,6);
  assert.equal(a.syntheticValidatedCount,6);
  assert.equal(a.syntheticRejectedCount,0);
  assert.equal(a.simulatedProviderEnvelopesProvided,12);
  assert.deepEqual(a.caseOutcomes.map(x=>x.method),[
    'single_compact','three_stage','single_compact','three_stage',
    'single_compact','three_stage'
  ]);
  assert.equal(a.fixtureModel,SIMULATED_MODEL);
  assert.equal(a.actualModelRequestsMade,0);
  assert.equal(a.actualDesignCallsObserved,0);
  assert.equal(a.genuineAcceptanceObservationsReported,0);
  assert.equal(a.comparisonWinnerEstablished,false);
  assert.equal(a.realProviderEvidenceVerified,false);
  assert.equal(a.ownerExperimentApproved,false);
  assert.equal(a.releaseGo,false);
});

test('malformed and truncated simulated outputs remain synthetic rejections',()=>{
  for(const [index,change,expected] of [
    [0,c=>{c.envelopes[0].done_reason='length';},'E6_W5A_MODEL_GENERATION_NOT_NORMALLY_FINISHED'],
    [1,c=>{c.envelopes[0].response='{bad json';},'E6_W5A_MODEL_JSON_PARSE_REJECTED'],
    [2,c=>{const p=JSON.parse(c.envelopes[0].response);p.art.accent=p.art.surface;
      c.envelopes[0].response=JSON.stringify(p);},'E6_W5A_MODEL_SCHEMA_OR_CONTRAST_REJECTED'],
    [3,c=>{const p=JSON.parse(c.envelopes[1].response);p.page.sections=[];
      c.envelopes[1].response=JSON.stringify(p);},'E6_W5A_INVALID_PAGE_ARCHITECTURE']
  ]){
    const x=cells();change(x[index]);const a=rehearsal(x);
    assert.equal(a.syntheticRejectedCount,1);
    assert.equal(a.syntheticValidatedCount,5);
    assert.equal(a.caseOutcomes[index].simulationStatus,'SYNTHETIC_REJECTED');
    assert.equal(a.caseOutcomes[index].rejectionCode,expected);
    assert.equal(a.genuineAcceptanceObservationsReported,0);
  }
});

test('wrong methods, slugs, holes, extra authority and altered briefs fail closed',()=>{
  const reject=(f,pattern)=>assert.throws(()=>f(),pattern);
  reject(()=>rehearsal(cells().slice(0,5)),/OFFLINE_REHEARSAL_MATRIX_INVALID/);
  const swapped=cells();[swapped[0],swapped[1]]=[swapped[1],swapped[0]];
  reject(()=>rehearsal(swapped),/OFFLINE_REHEARSAL_MATRIX_INVALID/);
  const wrongSlug=cells();wrongSlug[0].projectSlug='wrong-project';
  reject(()=>rehearsal(wrongSlug),/OFFLINE_REHEARSAL_MATRIX_INVALID/);
  const extra=cells();extra[0].ownerApproved=true;
  reject(()=>rehearsal(extra),/OFFLINE_REHEARSAL_MATRIX_INVALID/);
  const dangerous=cells();dangerous[0].envelopes[0].model='qwen2.5:0.5b';
  reject(()=>rehearsal(dangerous),/INVALID_OFFLINE_FIXTURE_ENVELOPE/);
  const oversized=cells();oversized[0].envelopes[0].eval_count=3002;
  reject(()=>rehearsal(oversized),/INVALID_OFFLINE_FIXTURE_ENVELOPE/);
  const withHole=cells();delete withHole[1];
  reject(()=>rehearsal(withHole),/OFFLINE_REHEARSAL_MATRIX_INVALID/);
  reject(()=>rehearsePairedDesignOffline({
    briefs,sourceCommitSha:SOURCE,fixturePairs:cells(),transport:fetch
  }),/OFFLINE_REHEARSAL_CONTEXT_REQUIRED/);
  const changed=structuredClone(briefs);changed[0].audience+=' Modified.';
  reject(()=>rehearsePairedDesignOffline({briefs:changed,sourceCommitSha:SOURCE,
    fixturePairs:cells()}),/BRIEF_CONTENT_CHANGED/);
});

test('invalid synthetic page content cannot be counted as a verified design',()=>{
  const x=cells();const stage=JSON.parse(x[5].envelopes[2].response);
  stage.page.sections[0].body='<script>alert(1)</script>';
  x[5].envelopes[2].response=JSON.stringify(stage);
  const result=rehearsal(x);
  assert.equal(result.syntheticRejectedCount,1);
  assert.equal(result.caseOutcomes[5].simulationStatus,'SYNTHETIC_REJECTED');
  assert.equal(result.caseOutcomes[5].rejectionCode,'E6_W5A_UNTRUSTED_PAGE_CONTENT');
  assert.equal(result.actualModelRequestsMade,0);
  assert.equal(result.stage45Go,false);
});

test('offline rehearsal contains no transport, writes, approval or real-comparison bridge',async()=>{
  const src=await readFile(new URL('./w5l_offline_paired_rehearsal.mjs',import.meta.url),'utf8');
  for(const forbidden of ['fetch(', 'requestLocalOllama', 'writeFile(', 'child_process',
    'process.env','workflow_dispatch','summarizePairedResearch',
    'https://','github.token','setTimeout('])
    assert(!src.includes(forbidden),forbidden);
  assert(src.includes("fixtureModel:MODEL"));
  assert(src.includes('genuineAcceptanceObservationsReported:0'));
  assert(src.includes('actualModelRequestsMade:0'));
});
