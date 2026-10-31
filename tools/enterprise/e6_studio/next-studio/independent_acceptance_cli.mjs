#!/usr/bin/env node
/** E6 Task 8 local independent-acceptance inventory verifier.
 * Reads only local owner-provided files, never uploads, and never approves release.
 */
import {lstat, realpath} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {evaluateAcceptanceInventory,readBoundedRegularFile} from './independent_acceptance_policy.mjs';
const app=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(app,'../../../../');
const defaultCase=path.join(root,'target/e6-studio/independent-acceptance');
const args=process.argv.slice(2);
if(args.length!==0&&!(args.length===2&&args[0]==='--case-dir')){
 throw Error('E6_USAGE: node independent_acceptance_cli.mjs [--case-dir /local/evidence/directory]');
}
const dir=args.length===2?path.resolve(args[1]):defaultCase;
let manifest=null,missing=false,invalid=false;
try {
 const filename=path.join(dir,'manifest.json');
 const stat=await lstat(filename);
 if(stat.isSymbolicLink()||!stat.isFile()||stat.nlink!==1){
  throw Error('E6_MANIFEST_UNTRUSTED_FILE_TYPE');
 }
 const expectedPath=path.join(await realpath(dir),'manifest.json');
 if(await realpath(filename)!==expectedPath)throw Error('E6_MANIFEST_REDIRECT_DENIED');
 const opened=await readBoundedRegularFile(filename,256*1024);
 const afterward=await lstat(filename);
 if(!afterward.isFile()||afterward.isSymbolicLink()||
    afterward.dev!==opened.dev||afterward.ino!==opened.ino||
    afterward.nlink!==1||await realpath(filename)!==expectedPath){
  throw Error('E6_MANIFEST_REPLACED_DURING_READ');
 }
 manifest=JSON.parse(opened.data.toString('utf8'));
} catch(e) {
 if(e?.code==='ENOENT'){missing=true;}
 else {invalid=true;}
}
const report=await evaluateAcceptanceInventory(manifest,dir);
if(missing)report.problems.unshift('E6_LOCAL_MANIFEST_NOT_PROVIDED');
if(invalid)report.problems.unshift('E6_LOCAL_MANIFEST_READ_DENIED');
console.log(JSON.stringify(report,null,2));
if(!report.readyForIndependentHumanReview)process.exitCode=2;
