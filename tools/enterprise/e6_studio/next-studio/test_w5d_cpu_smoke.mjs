import test from 'node:test';
import assert from 'node:assert/strict';
import {runCpuSmoke} from './w5d_cpu_smoke.mjs';
const model='qwen2.5:0.5b';
function mock({malformed=false,wrongModel=false,noEvaluation=false,offRegistry=false,badOutput=null}={}){
  const calls=[];
  const transport=async(url,init)=>{
    assert(url.startsWith('http://127.0.0.1:11434/'));
    calls.push({url,init});
    if(url.endsWith('/api/version'))
      return new Response(JSON.stringify({version:'0.40.13'}),{status:200});
    if(url.endsWith('/api/tags'))
      return new Response(JSON.stringify({models:[
        {name:offRegistry?'something-else':model,digest:'a'.repeat(64)}
      ]}),{status:200});
    if(url.endsWith('/api/generate')){
      const request=JSON.parse(init.body);
      assert.equal(request.model,model);
      assert.equal(request.stream,false);
      assert.equal(request.format,'json');
      assert.equal(request.options.num_gpu,0);
      assert.equal(request.options.num_predict,96);
      return new Response(malformed?'not json':JSON.stringify({
        done:true,model:wrongModel?'wrong:tag':model,
        response:badOutput??'{"research_status":"fictional study only"}',
        eval_count:noEvaluation?0:12,total_duration:123456
      }),{status:200});
    }
    throw Error('UNEXPECTED_LOCAL_PATH');
  };
  return {transport,calls};
}
test('mocked CPU smoke validates true loopback contract but NEVER claims actual model',async()=>{
  const m=mock();
  const r=await runCpuSmoke({transport:m.transport});
  assert.deepEqual(m.calls.map(x=>new URL(x.url).pathname),[
    '/api/version','/api/tags','/api/generate'
  ]);
  assert.equal(r.modelReportedName,model);
  assert.equal(r.realLoopbackApiCalled,false);
  assert.equal(r.gpuUseRequested,false);
  assert.equal(r.actualWeightsExecutionCryptographicallyAttested,false);
  assert.equal(r.threeHeldoutSitesGenerated,false);
  assert.equal(r.releaseGo,false);
  assert.match(r.responseSha256,/^[a-f0-9]{64}$/);
});
test('fake provider missing approved model digest fails closed',async()=>{
  const m=mock({offRegistry:true});
  await assert.rejects(runCpuSmoke({transport:m.transport}),/PINNED_MODEL_NOT_INSTALLED/);
});
test('fake provider using a different model name is rejected',async()=>{
  const m=mock({wrongModel:true});
  await assert.rejects(runCpuSmoke({transport:m.transport}),/NO_COMPLETE_REAL_PROVIDER_GENERATION/);
});
test('provider response must be JSON and contain positive token count',async()=>{
  await assert.rejects(runCpuSmoke({transport:mock({malformed:true}).transport}),
    /PROVIDER_JSON_INVALID/);
  await assert.rejects(runCpuSmoke({transport:mock({noEvaluation:true}).transport}),
    /NO_COMPLETE_REAL_PROVIDER_GENERATION/);
});
test('model provider must return the exact harmless JSON schema and no HTML or URLs',async()=>{
  const examples=[
    '{}',
    '{"other":"fictional study only"}',
    '{"research_status":"fine","extra":"unapproved"}',
    '{"research_status":"https://example.com/exfiltrate"}',
    '{"research_status":"<script>alert(1)</script>"}',
    '{"research_status":"short"}',
    '{"research_status":12}'
  ];
  for(const response of examples){
    await assert.rejects(runCpuSmoke({transport:mock({badOutput:response}).transport}),
      /MODEL_OUTPUT_UNSAFE_OR_WRONG_SCHEMA/,response);
  }
});
test('no outside URL or arbitrary script/runner inputs are allowed by API implementation',async()=>{
  const testTransport=async(url)=>{assert(url.startsWith('http://127.0.0.1:11434/'));return new Response('x',{status:500});};
  await assert.rejects(runCpuSmoke({transport:testTransport}),/LOCAL_OLLAMA_UNAVAILABLE/);
});
