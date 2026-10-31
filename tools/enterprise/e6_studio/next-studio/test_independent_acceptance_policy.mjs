/* Entirely synthetic offline test files. Passing tests are NOT Task 8 evidence. */
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {DOMAIN_IDS,KIND,evaluateAcceptanceInventory} from './independent_acceptance_policy.mjs';

const hash=x=>createHash('sha256').update(x).digest('hex');
async function fixture(t) {
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'e6-independent-synthetic-'));
 t.after(()=>fs.rm(root,{recursive:true,force:true}));
 const evidence=path.join(root,'evidence');
 await fs.mkdir(evidence);
 const today=new Date().toISOString();
 const records=[];
 for(const [index,id] of DOMAIN_IDS.entries()){
  const name='synthetic-'+index+'.txt';
  const bytes=Buffer.from('SYNTHETIC_TEST_ONLY_'+id+'_'+index);
  await fs.writeFile(path.join(evidence,name),bytes);
  records.push({id,state:'submitted',files:[{
   path:'evidence/'+name,sha256:hash(bytes)
  }],reviewer:{alias:'separate_reviewer',reviewedAt:today,
   decision:'recommend_review'}});
 }
 return {root,manifest:{
  kind:KIND,schemaVersion:1,repository:'teldigi5-wq/aetheris-ai-runtime',
  exactCommit:'a'.repeat(40),glbSha256:'b'.repeat(64),
  assembledAt:today,authorAlias:'code_author',
  stage45Go:false,releaseGo:false,task8Completed:false,domains:records
 }};
}
test('missing manifest remains blocked (the actual current project state)',async t=>{
 const {root}=await fixture(t);
 const out=await evaluateAcceptanceInventory(null,root);
 assert.equal(out.status,'BLOCKED_EVIDENCE_OR_PROVENANCE');
 assert(out.problems.includes('E6_MANIFEST_REQUIRED'));
 assert.equal(out.task8Completed,false);
 assert.equal(out.releaseGo,false);
});
test('synthetic nine-domain inventory only becomes ready FOR HUMAN REVIEW, never GO',async t=>{
 const {root,manifest}=await fixture(t);
 const result=await evaluateAcceptanceInventory(manifest,root);
 assert.equal(result.readyForIndependentHumanReview,true);
 assert.equal(result.domains.length,9);
 assert.equal(result.problems.length,0);
 for(const k of ['reviewersIndependentlyAuthenticated','realGpuOrMobileCertified',
  'manualAccessibilityCertified','licensingApproved','securityAuditApproved',
  'humanDesignApproved','task7Completed','task8Completed','ownerApproved',
  'stage45Go','releaseGo','productionDeployed'])assert.equal(result[k],false,k);
});
test('reject missing, duplicate and unexpected acceptance domains',async t=>{
 const {root,manifest}=await fixture(t);
 manifest.domains.pop();
 assert((await evaluateAcceptanceInventory(manifest,root)).problems.includes('E6_DOMAIN_MATRIX_INCOMPLETE'));
 manifest.domains.push(manifest.domains[0]);
 const res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.startsWith('E6_DUPLICATE_DOMAIN')));
});
test('reject self-review and missing reviewer metadata without treating alias as identity',async t=>{
 const {root,manifest}=await fixture(t);
 manifest.domains[0].reviewer.alias='code_author';
 const res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.startsWith('E6_INDEPENDENT_REVIEW_METADATA_MISSING')));
 assert.equal(res.reviewersIndependentlyAuthenticated,false);
});
test('reject wrong repository, missing SHA and unauthorized release claim',async t=>{
 const {root,manifest}=await fixture(t);
 manifest.repository='teldigi5-wq/aetheris-platform';
 manifest.glbSha256='unverified';
 manifest.stage45Go=true;
 const r=await evaluateAcceptanceInventory(manifest,root);
 for(const code of ['E6_WRONG_REPOSITORY','E6_SOURCE_DIGESTS_REQUIRED','E6_UNAUTHORIZED_RELEASE_ASSERTION'])
  assert(r.problems.includes(code),code);
 assert.equal(r.releaseGo,false);
});
test('reject changed evidence after its hash was recorded',async t=>{
 const {root,manifest}=await fixture(t);
 await fs.writeFile(path.join(root,'evidence/synthetic-0.txt'),'tampered');
 const r=await evaluateAcceptanceInventory(manifest,root);
 assert(r.problems.some(x=>x.includes('E6_EVIDENCE_DIGEST_MISMATCH')));
});
test('reject traversal, absolute path, URL encoding, and outside file',async t=>{
 const {root,manifest}=await fixture(t);
 for(const unsafe of ['../secrets.txt','/etc/passwd','evidence/../secrets.txt',
  'evidence/%2e%2e/secrets.txt','evidence\\bad.txt','evidence/other.exe']){
  manifest.domains[0].files[0].path=unsafe;
  const res=await evaluateAcceptanceInventory(manifest,root);
  assert(res.problems.some(x=>x.includes('E6_UNSAFE_EVIDENCE_REFERENCE')),unsafe);
 }
});
test('reject symlinks inside evidence directory',async t=>{
 const {root,manifest}=await fixture(t);
 const link=path.join(root,'evidence/external.txt');
 const target=path.join(root,'evidence/synthetic-0.txt');
 await fs.symlink(target,link);
 manifest.domains[0].files[0].path='evidence/external.txt';
 const res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.includes('E6_EVIDENCE_SYMLINK_DENIED')));
});
test('reject missing files, duplicated path and incomplete states',async t=>{
 const {root,manifest}=await fixture(t);
 manifest.domains[0].state='missing';
 let res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.startsWith('E6_DOMAIN_NOT_SUBMITTED')));
 manifest.domains[0].state='submitted';
 manifest.domains[0].files[0].path='evidence/not-present.txt';
 res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.includes('ENOENT')));
 manifest.domains[0].files[0]=manifest.domains[1].files[0];
 res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.includes('E6_EVIDENCE_PATH_REUSED')));
});
test('reject stale submitted review and assembled inventory',async t=>{
 const {root,manifest}=await fixture(t);
 const stale='2020-01-01T00:00:00.000Z';
 manifest.assembledAt=stale;
 manifest.domains[0].reviewer.reviewedAt=stale;
 const res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.includes('E6_INVENTORY_DATE_INVALID'));
 assert(res.problems.some(x=>x.startsWith('E6_INDEPENDENT_REVIEW_METADATA_MISSING')));
});
test('reject copied bytes under distinct evidence filenames across domains',async t=>{
 const {root,manifest}=await fixture(t);
 const original=await fs.readFile(path.join(root,'evidence/synthetic-0.txt'));
 await fs.writeFile(path.join(root,'evidence/copied-payload.txt'),original);
 manifest.domains[1].files[0]={
  path:'evidence/copied-payload.txt',sha256:hash(original)
 };
 const result=await evaluateAcceptanceInventory(manifest,root);
 assert.equal(result.readyForIndependentHumanReview,false);
 assert(result.problems.some(x=>x.includes('E6_EVIDENCE_CONTENT_REUSED')));
});
test('reject copied evidence and unbounded file sizes',async t=>{
 const {root,manifest}=await fixture(t);
 manifest.domains[0].files.push({...manifest.domains[0].files[0]});
 let res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.includes('E6_EVIDENCE_PATH_REUSED')));
 manifest.domains[0].files.pop();
 await fs.writeFile(path.join(root,'evidence/synthetic-0.txt'),Buffer.alloc(20*1024*1024+1));
 res=await evaluateAcceptanceInventory(manifest,root);
 assert(res.problems.some(x=>x.includes('E6_EVIDENCE_NOT_BOUNDED_FILE')));
});


test('hardlinked evidence bytes are not accepted as independently sourced files',async t=>{
 const {root,manifest}=await fixture(t);
 const source=path.join(root,'evidence/synthetic-0.txt');
 const hardlink=path.join(root,'evidence/alternate.txt');
 await fs.link(source,hardlink);
 manifest.domains[0].files[0].path='evidence/alternate.txt';
 const result=await evaluateAcceptanceInventory(manifest,root);
 assert.equal(result.readyForIndependentHumanReview,false);
 assert(result.problems.some(x=>x.includes('E6_EVIDENCE_NOT_BOUNDED_FILE')));
});
test('Windows device names and unsafe characters cannot pass evidence path checks',async t=>{
 const {root,manifest}=await fixture(t);
 for(const name of ['evidence/CON.txt','evidence/NUL.json','evidence/COM1.csv',
  'evidence/LPT9.png','evidence/name:stream.txt','evidence/space file.txt',
  'evidence/control\tfile.txt']){
  manifest.domains[0].files[0].path=name;
  const result=await evaluateAcceptanceInventory(manifest,root);
  assert(result.problems.some(x=>x.includes('E6_UNSAFE_EVIDENCE_REFERENCE')),name);
 }
});
test('CLI refuses symlinked manifest, without processing its outside target',async t=>{
 const {root,manifest}=await fixture(t);
 const out=path.join(root,'elsewhere.json');
 await fs.writeFile(out,JSON.stringify(manifest));
 await fs.symlink(out,path.join(root,'manifest.json'));
 const cli=fileURLToPath(new URL('./independent_acceptance_cli.mjs',import.meta.url));
 const r=spawnSync(process.execPath,[cli,'--case-dir',root],{encoding:'utf8',timeout:10000});
 assert.equal(r.status,2);
 const result=JSON.parse(r.stdout);
 assert.equal(result.readyForIndependentHumanReview,false);
 assert(result.problems.includes('E6_LOCAL_MANIFEST_READ_DENIED'));
 assert.equal(result.stage45Go,false);
});
test('CLI caps manifest file BEFORE allocating full input and blocks malformed JSON',async t=>{
 const {root}=await fixture(t);
 const cli=fileURLToPath(new URL('./independent_acceptance_cli.mjs',import.meta.url));
 await fs.writeFile(path.join(root,'manifest.json'),Buffer.alloc(256*1024+1,'x'));
 let run=spawnSync(process.execPath,[cli,'--case-dir',root],{encoding:'utf8',timeout:10000});
 assert.equal(run.status,2);
 assert(JSON.parse(run.stdout).problems.includes('E6_LOCAL_MANIFEST_READ_DENIED'));
 await fs.writeFile(path.join(root,'manifest.json'),'{invalid-json');
 run=spawnSync(process.execPath,[cli,'--case-dir',root],{encoding:'utf8',timeout:10000});
 assert.equal(run.status,2);
 assert(JSON.parse(run.stdout).problems.includes('E6_LOCAL_MANIFEST_READ_DENIED'));
});
test('CLI no-manifest scenario remains a predictable blocked status',async t=>{
 const {root}=await fixture(t);
 const cli=fileURLToPath(new URL('./independent_acceptance_cli.mjs',import.meta.url));
 const r=spawnSync(process.execPath,[cli,'--case-dir',root],{encoding:'utf8',timeout:10000});
 assert.equal(r.status,2);
 const result=JSON.parse(r.stdout);
 assert(result.problems.includes('E6_LOCAL_MANIFEST_NOT_PROVIDED'));
 assert.equal(result.task8Completed,false);
});
