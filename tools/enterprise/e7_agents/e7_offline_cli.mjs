#!/usr/bin/env node
/** E7 operator entry point. Plan-only default: zero file reads, zero children.
 * Explicit opt-in is required for offline deterministic local specialists.
 * Signed local export of REVIEW EVIDENCE is not owner authentication or GO.
 */
import {parseArgs} from 'node:util';
import fs from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {canonical,plan,review,exportReview} from './e7_offline_delegation.mjs';

export async function run(argv=process.argv.slice(2),environment=process.env){
  let args;
  try{
    args=parseArgs({args:argv,allowPositionals:false,strict:true,options:{
      'run-local':{type:'boolean',default:false},
      'workspace':{type:'string'},
      'request-id':{type:'string'},
      'export':{type:'string'},
      'approval':{type:'string'},
      'approver-key':{type:'string'}
    }}).values;
  }catch{return {exit:2,body:{status:'E7_INVALID_CLI_ARGUMENTS',ownerApproved:false}};}
  const isExport=[args.export,args.approval,args['approver-key']].some(x=>x!==undefined);
  if(!args['run-local']){
    if(isExport) return {exit:2,body:{status:'E7_EXPORT_REQUIRES_EXECUTED_REVIEW',ownerApproved:false}};
    return {exit:0,body:plan()};
  }
  if(environment.AETHERIS_E7_OFFLINE_DELEGATION_OPT_IN!=='yes')
    return {exit:2,body:{status:'E7_EXPLICIT_OFFLINE_OPT_IN_REQUIRED',
      workerProcessCount:0,ownerApproved:false}};
  if(isExport&&[args.export,args.approval,args['approver-key']].some(x=>x===undefined))
    return {exit:2,body:{status:'E7_SIGNED_EXPORT_FILES_ALL_REQUIRED',ownerApproved:false}};
  try{
    const result=await review({workspace:args.workspace,requestId:args['request-id'],
      operatorOptIn:environment.AETHERIS_E7_OFFLINE_DELEGATION_OPT_IN});
    if(!isExport)return {exit:result.status==='REVIEW_CANDIDATE_NOT_APPROVED'?0:3,body:result};
    const [receipt,pem]=await Promise.all([
      fs.readFile(args.approval,'utf8'),fs.readFile(args['approver-key'],'utf8')]);
    if(receipt.length>4000||pem.length>4000)throw Error('E7_APPROVAL_BUDGET');
    const outcome=await exportReview({decision:result,outputPath:args.export,
      receipt:JSON.parse(receipt),pem,
      pinnedKeySha256:environment.AETHERIS_E7_TRUSTED_APPROVER_PUBLIC_KEY_SHA256});
    return {exit:0,body:outcome};
  }catch{
    // No untrusted input, private paths, bearer values or stack trace in CLI.
    return {exit:2,body:{status:'E7_OFFLINE_DELEGATION_DENIED',
      ownerApproved:false,stage45Go:false}};
  }
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const outcome=await run();
  process.stdout.write(canonical(outcome.body)+'\n');
  process.exitCode=outcome.exit;
}
