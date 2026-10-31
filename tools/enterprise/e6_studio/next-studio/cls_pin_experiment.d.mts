/** Never returns an experimental mode for normal/remote research. */
export type LocalPinMode='default'|'transform'|'off';
export function resolveLocalPinExperiment(hostname:string,assetVariant:'fixture'|'blender',search:string):LocalPinMode;
