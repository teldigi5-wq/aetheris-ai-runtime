/** Human-authored CSS source TEST DOUBLES. Not model-authored output. */
import {fakeLocalProvider,MODEL} from './w5b_simulated_provider.mjs';
export const EXAMPLE_CODES=[
  '.hero h1 { letter-spacing: -0.04em; max-width: 42ch; } .hero__figure { border-radius: 18px; border: 1px solid var(--accent); } .section__text { max-width: 42ch; text-align: left; } .section h2 { font-style: italic; }',
  '.hero__figure:before { transform: rotate(-12deg); border-radius: 8px; } .eyebrow { font-weight: 800; letter-spacing: 0.12em; } .section__content { gap: 32px; } .section:nth-child(even) { background-color: var(--paper); }',
  '.hero h1 { letter-spacing: -0.03em; font-style: italic; } .hero__figure { background-color: var(--paper); border-radius: 4px; } .lede { font-style: italic; max-width: 52ch; } .section h2 { letter-spacing: 0.01em; }'
];
export function fakeCssSourceProvider({briefs,model=MODEL,badIndex=-1}){
  const first=fakeLocalProvider({briefs,model});
  return async (url,options)=>{
    if(!url.endsWith('/api/generate'))return first(url,options);
    const req=JSON.parse(options.body);
    if(req.prompt.includes('BRIEF AS JSON:'))return first(url,options);
    if(!req.prompt.includes('BRIEF_AND_DESIGN:'))throw Error('UNEXPECTED_MOCK_PROMPT');
    const i=briefs.findIndex(b=>req.prompt.includes('"slug":"'+b.projectSlug+'"'));
    if(i<0)throw Error('UNKNOWN_HELDOUT_BRIEF');
    const cssSource=i===badIndex?
      '.hero h1 { max-width: 30ch; } .hero__figure { background-color: var(--surface); } .section h2 { font-style: italic; } @import url(https://malicious.example/css);':
      EXAMPLE_CODES[i];
    return new Response(JSON.stringify({model,done:true,response:JSON.stringify({
      version:1,projectSlug:briefs[i].projectSlug,cssSource
    })}),{status:200});
  };
}
