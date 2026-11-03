/** Owner-only W5-L: fresh read-only authorization before every request.
 * No model/network calls except an explicitly injected GitHub GET transport.
 */
import {authorizeLivePairedCpuPr} from './w5l_owner_cpu_gate.mjs';
const fail=code=>{throw Error('E6_W5L_'+code);};
const URL='https://api.github.com/repos/teldigi5-wq/aetheris-ai-runtime/pulls/275';
export async function requireCurrentOwnerApprovalForRequest({context,token,transport}={}){
 if(typeof transport!=='function'||typeof token!=='string'||
    !/^[a-zA-Z0-9._-]{10,300}$/.test(token))
   fail('PER_REQUEST_APPROVAL_TRANSPORT_OR_TOKEN_REQUIRED');
 let response;
 try{
  response=await transport(URL,{
   method:'GET',redirect:'error',cache:'no-store',
   headers:{Accept:'application/vnd.github+json',
    Authorization:'Bearer '+token,
    'User-Agent':'aetheris-e6-w5l-per-request-owner-gate',
    'X-GitHub-Api-Version':'2022-11-28'},
   signal:AbortSignal.timeout(8000)
  });
 }catch{fail('PER_REQUEST_APPROVAL_LOOKUP_UNAVAILABLE');}
 if(response?.status!==200||!response.body)
   fail('PER_REQUEST_APPROVAL_HTTP_NOT_OK');
 let size=0;const chunks=[];
 try{
  for await(const chunk of response.body){
   const bytes=Buffer.from(chunk);
   size+=bytes.length;
   if(size>200000)fail('PER_REQUEST_APPROVAL_RESPONSE_TOO_LARGE');
   chunks.push(bytes);
  }
 }catch{fail('PER_REQUEST_APPROVAL_RESPONSE_UNTRUSTED');}
 if(size===0)fail('PER_REQUEST_APPROVAL_RESPONSE_EMPTY');
 let current;
 try{current=JSON.parse(Buffer.concat(chunks).toString('utf8'));}
 catch{fail('PER_REQUEST_APPROVAL_RESPONSE_NOT_JSON');}
 authorizeLivePairedCpuPr(current,context);
 return true;
}
