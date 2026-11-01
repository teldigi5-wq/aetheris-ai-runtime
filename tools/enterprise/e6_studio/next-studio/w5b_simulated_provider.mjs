/** W5-B offline test doubles. Not model-authored, not training evidence. */
import {proposalFor} from './w5a_test_doubles.mjs';
export const MODEL='test-research:1';
export function syntheticDesign(brief,index){
  const base=structuredClone(proposalFor({
    projectSlug:index===2?'woven-light-workshop':'tidal-listening-atlas'
  }));
  base.projectSlug=brief.projectSlug;
  base.siteName=['Quiet Heat Ceramics Lab','Afterlight Mapping Atelier','Paper Migration Journal'][index];
  base.tagline=['Fictional clay and typographic study',
    'Imaginary after-dark mapping practice','Hypothetical fold and print research'][index];
  base.description=[
    'A fictional research collection of imagined ceramic reading objects and carefully framed material stories.',
    'An independent invented workshop exploring speculative mapping narratives without claiming field deployments.',
    'An imaginary paper-folding journal examining rhythm, typographic structure and forms only as concepts.'
  ][index];
  base.art=[
    {paper:'#f7f2e8',ink:'#23332d',accent:'#7e3424',surface:'#dfd7c5',composition:'split',motif:'rules',density:'airy'},
    {paper:'#172c31',ink:'#f8f5eb',accent:'#f1bf91',surface:'#29464a',composition:'offset',motif:'letterform',density:'compact'},
    {paper:'#f4ecdf',ink:'#342c2b',accent:'#85472c',surface:'#e1d5c8',composition:'typographic',motif:'material-block',density:'measured'}
  ][index];
  base.pages[0].heading=[
    'The warmth of an imagined reading object',
    'The city after the last bright hour',
    'Stories held inside an unfolding page'
  ][index];
  base.pages[1].heading=[
    'Material decisions without invented results',
    'A practice of speculative night routes',
    'How a fictional fold takes its shape'
  ][index];
  return base;
}
export function fakeLocalProvider({briefs,model=MODEL,rejectIndex=-1}){
  const digest='a'.repeat(64);
  return async (url,options)=>{
    if(!url.startsWith('http://127.0.0.1:11434/'))throw Error('OFF_NETWORK_DENIED');
    if(url.endsWith('/api/version'))
      return new Response(JSON.stringify({version:'0.14.0'}),{status:200});
    if(url.endsWith('/api/tags'))
      return new Response(JSON.stringify({models:[{name:model,model,digest}]}),{status:200});
    if(url.endsWith('/api/generate')){
      const request=JSON.parse(options.body);
      if(request.model!==model||request.stream!==false||request.format!=='json')
        throw Error('INVALID_MODEL_MOCK_REQUEST');
      const index=briefs.findIndex(b=>request.prompt.includes('"projectSlug":"'+b.projectSlug+'"'));
      if(index<0)throw Error('UNKNOWN_MOCK_BRIEF');
      const output=syntheticDesign(briefs[index],index);
      if(index===rejectIndex)output.siteName='<script>injection</script>';
      return new Response(JSON.stringify({model,done:true,response:JSON.stringify(output)}),
        {status:200});
    }
    throw Error('UNSUPPORTED_LOOPBACK_PATH');
  };
}
