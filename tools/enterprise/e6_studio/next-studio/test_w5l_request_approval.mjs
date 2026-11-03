import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {requireCurrentOwnerApprovalForRequest} from './w5l_request_approval.mjs';
import {CPU_LABEL} from './w5l_owner_cpu_gate.mjs';
const SHA='a'.repeat(40),repo='teldigi5-wq/aetheris-ai-runtime';
const context={actor:'teldigi5-wq',repository:repo,expectedPr:275,headSha:SHA};
const token='mock-test-only-token-not-authentication';
const body=()=>({number:275,state:'open',draft:true,merged_at:null,
 user:{login:'teldigi5-wq'},head:{sha:SHA,repo:{full_name:repo}},
 base:{ref:'feat/e6-w5i-local-inference-completion-20261102',repo:{full_name:repo}},
 labels:[{name:CPU_LABEL}]});
const reply=(data,status=200)=>new Response(JSON.stringify(data),{status});
const go=(transport,other={})=>requireCurrentOwnerApprovalForRequest({
 context,token,transport,...other
});
test('fresh no-cache GitHub read exactly matches pinned PR and token scope',async()=>{
 let calls=0;
 assert.equal(await go(async(url,opts)=>{
  calls++;
  assert.equal(url,'https://api.github.com/repos/'+repo+'/pulls/275');
  assert.equal(opts.method,'GET');
  assert.equal(opts.redirect,'error');
  assert.equal(opts.cache,'no-store');
  assert.equal(opts.headers.Authorization,'Bearer '+token);
  assert.equal(opts.headers.Accept,'application/vnd.github+json');
  assert(opts.signal);
  return reply(body());
 }),true);
 assert.equal(calls,1);
});
test('label removal, reused W5-J label, head drift and closed draft fail closed',async()=>{
 for(const edit of [
  p=>{p.labels=[];},
  p=>{p.labels=[{name:'e6-w5j-three-brief-cpu-approved'}];},
  p=>{p.labels.push({name:CPU_LABEL});},
  p=>{p.head.sha='b'.repeat(40);},
  p=>{p.draft=false;},p=>{p.state='closed';}
 ]){
  const pr=body();edit(pr);
  await assert.rejects(go(async()=>reply(pr)),/LIVE_PAIRED_CPU_APPROVAL_REVOKED_OR_HEAD_CHANGED/);
 }
});
test('HTTP, transport, malformed, excessive and missing-token states deny',async()=>{
 for(const transport of [
  async()=>{throw Error('untrusted private message');},
  async()=>reply({message:'not authorized'},403),
  async()=>new Response('not JSON',{status:200}),
  async()=>new Response('x'.repeat(200001),{status:200}),
  async()=>new Response('',{status:200})
 ])
  await assert.rejects(go(transport),/E6_W5L_/);
 await assert.rejects(go(async()=>reply(body()),{token:''}),/PER_REQUEST_APPROVAL_TRANSPORT_OR_TOKEN_REQUIRED/);
 await assert.rejects(go(null),/PER_REQUEST_APPROVAL_TRANSPORT_OR_TOKEN_REQUIRED/);
});
test('no model transport or artifact writes in read-only approval module',async()=>{
 const source=await readFile(new URL('./w5l_request_approval.mjs',import.meta.url),'utf8');
 for(const forbidden of ['/api/generate','writeFile(','createWriteStream(',
   'child_process','workflow_dispatch','console.log','process.env'])
  assert(!source.includes(forbidden),forbidden);
 assert(source.includes('authorizeLivePairedCpuPr(current,context)'));
});
