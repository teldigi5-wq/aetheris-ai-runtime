/** Browser-only local research comparison, no production-facing behavior. */
export const resolveLocalPinExperiment=(hostname,assetVariant,search)=>{
 if(assetVariant!=='blender'||!['localhost','127.0.0.1','[::1]'].includes(hostname))
  return 'default';
 if(typeof search!=='string'||search.length>2048)return 'default';
 let mode;
 try{mode=new URLSearchParams(search).get('e6_cls_pin');}
 catch{return 'default';}
 return mode==='transform'||mode==='off'?mode:'default';
};
