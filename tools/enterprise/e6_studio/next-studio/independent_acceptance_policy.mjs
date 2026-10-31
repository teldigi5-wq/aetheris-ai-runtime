/** E6 Task 8: an offline evidence-INVENTORY gate, never an independent sign-off.
 * Browser/CI inputs cannot certify human identity, device provenance or Stage45.
 * The evaluator does no network I/O, uploads nothing and mutates no inputs.
 */
import {readFile, lstat, realpath} from 'node:fs/promises';
import path from 'node:path';
import {createHash} from 'node:crypto';

export const KIND='E6_INDEPENDENT_ACCEPTANCE_INVENTORY_V1';
export const DOMAIN_IDS=Object.freeze([
 'source_and_build_provenance',
 'dependency_and_licensing_review',
 'physical_desktop_gpu_frame_time',
 'physical_mobile_device_profile',
 'sustained_memory_thermals_power',
 'manual_accessibility_and_assistive_tech',
 'real_driver_context_loss_and_browser_matrix',
 'human_visual_art_direction',
 'independent_security_threat_review'
]);
const HEX40=/^[a-f0-9]{40}$/;
const HEX64=/^[a-f0-9]{64}$/;
const MAX_BYTES=20*1024*1024;
const EXTENSIONS=new Set(['.json','.txt','.md','.csv','.png','.jpg','.jpeg','.webp','.pdf']);
const isObject=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const safeAlias=s=>typeof s==='string'&&/^[a-zA-Z0-9][a-zA-Z0-9_-]{2,47}$/.test(s);
const safePath=s=>typeof s==='string'&&s.length<240&&!s.includes('%')&&
 !s.includes('\\')&&!s.includes('\0')&&s.startsWith('evidence/')&&
 !s.startsWith('/')&&s.split('/').every(x=>x!=='.'&&x!=='..'&&x.length>0)&&
 EXTENSIONS.has(path.posix.extname(s).toLowerCase());

async function verifyLocalFile(base,filename,expectedSha) {
 if(!safePath(filename)||!HEX64.test(expectedSha))throw Error('E6_UNSAFE_EVIDENCE_REFERENCE');
 const baseReal=await realpath(base);
 // Check every path component: a symlink must not redirect verification outside
 // this case folder, even where final file resolves inside by coincidence.
 const parts=filename.split('/');
 let cursor=baseReal;
 for(const part of parts){
  cursor=path.join(cursor,part);
  const stat=await lstat(cursor);
  if(stat.isSymbolicLink())throw Error('E6_EVIDENCE_SYMLINK_DENIED');
 }
 const stat=await lstat(cursor);
 if(!stat.isFile()||stat.size===0||stat.size>MAX_BYTES)throw Error('E6_EVIDENCE_NOT_BOUNDED_FILE');
 const bytes=await readFile(cursor);
 if(createHash('sha256').update(bytes).digest('hex')!==expectedSha)throw Error('E6_EVIDENCE_DIGEST_MISMATCH');
 return {path:filename,sha256:expectedSha,bytes:bytes.length};
}
export async function evaluateAcceptanceInventory(manifest,caseDir,{now=Date.now()}={}){
 const problems=[],details=[],seenPaths=new Set(),seenDigests=new Set();
 const result=()=>{
  const ready=problems.length===0;
  return {
   kind:'E6_INDEPENDENT_EVIDENCE_INVENTORY_ASSESSMENT',
   readyForIndependentHumanReview:ready,
   status:ready?'EVIDENCE_INVENTORY_READY_FOR_HUMAN_REVIEW':'BLOCKED_EVIDENCE_OR_PROVENANCE',
   problems,domains:details,requiredDomains:DOMAIN_IDS.length,
   // Self-declared reviewer metadata and hashes are NOT trusted attestations.
   reviewersIndependentlyAuthenticated:false,
   evidenceAuthenticityIndependentlyVerified:false,
   realGpuOrMobileCertified:false,
   manualAccessibilityCertified:false,
   licensingApproved:false,securityAuditApproved:false,
   humanDesignApproved:false,task7Completed:false,
   task8Completed:false,ownerApproved:false,
   stage45Go:false,releaseGo:false,productionDeployed:false
  };
 };
 if(!isObject(manifest)){problems.push('E6_MANIFEST_REQUIRED');return result();}
 if(manifest.kind!==KIND||manifest.schemaVersion!==1)problems.push('E6_SCHEMA_INVALID');
 if(manifest.repository!=='teldigi5-wq/aetheris-ai-runtime')problems.push('E6_WRONG_REPOSITORY');
 if(!HEX40.test(manifest.exactCommit||'')||!HEX64.test(manifest.glbSha256||''))problems.push('E6_SOURCE_DIGESTS_REQUIRED');
 const timestamp=Date.parse(manifest.assembledAt);
 if(!Number.isFinite(timestamp)||timestamp>now+300000||timestamp<now-30*86400000)problems.push('E6_INVENTORY_DATE_INVALID');
 if(!safeAlias(manifest.authorAlias))problems.push('E6_AUTHOR_ALIAS_INVALID');
 if(!Array.isArray(manifest.domains)||manifest.domains.length!==DOMAIN_IDS.length){
  problems.push('E6_DOMAIN_MATRIX_INCOMPLETE');return result();
 }
 const indexed=new Map();
 for(const domain of manifest.domains){
  if(!isObject(domain)||typeof domain.id!=='string'){
   problems.push('E6_DOMAIN_INVALID');continue;
  }
  if(indexed.has(domain.id)){problems.push('E6_DUPLICATE_DOMAIN:'+domain.id);continue;}
  indexed.set(domain.id,domain);
 }
 for(const id of DOMAIN_IDS){
  const d=indexed.get(id);
  if(!d){problems.push('E6_MISSING_DOMAIN:'+id);continue;}
  if(d.state!=='submitted'){
   problems.push('E6_DOMAIN_NOT_SUBMITTED:'+id);continue;
  }
  if(!Array.isArray(d.files)||d.files.length<1||d.files.length>5){
   problems.push('E6_DOMAIN_EVIDENCE_COUNT:'+id);continue;
  }
  const verified=[];
  for(const record of d.files){
   try{
    if(!isObject(record))throw Error('E6_EVIDENCE_OBJECT_REQUIRED');
    if(seenPaths.has(record.path))throw Error('E6_EVIDENCE_PATH_REUSED');
    seenPaths.add(record.path);
    const matched=await verifyLocalFile(caseDir,record.path,record.sha256);
    if(seenDigests.has(matched.sha256))throw Error('E6_EVIDENCE_CONTENT_REUSED');
    seenDigests.add(matched.sha256);
    verified.push(matched);
   }catch(error){problems.push(id+':'+String(error.message||error));}
  }
  const review=d.reviewer;
  if(!isObject(review)||!safeAlias(review.alias)||
      review.alias===manifest.authorAlias||review.decision!=='recommend_review'||
      !Number.isFinite(Date.parse(review.reviewedAt))||
      Date.parse(review.reviewedAt)>now+300000||
      Date.parse(review.reviewedAt)<now-30*86400000){
   problems.push('E6_INDEPENDENT_REVIEW_METADATA_MISSING:'+id);
  }
  details.push({id,evidenceFiles:verified.length,evidenceBytes:verified.reduce((x,f)=>x+f.bytes,0),
   reviewerIdentityVerified:false,professionalSignoff:false});
 }
 for(const id of indexed.keys())if(!DOMAIN_IDS.includes(id))problems.push('E6_UNKNOWN_DOMAIN:'+id);
 if(manifest.stage45Go===true||manifest.releaseGo===true||manifest.task8Completed===true)
  problems.push('E6_UNAUTHORIZED_RELEASE_ASSERTION');
 return result();
}
