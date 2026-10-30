import CinematicExperience from "../../components/CinematicExperience";

/**
 * Build-time opt-in: production/default static exports do not attempt to load
 * the CI-only original Blender asset. This isolated route is unlinked.
 */
export default function BlenderResearchPreview() {
  if (process.env.NEXT_PUBLIC_E6_BLENDER_PREVIEW !== "1") {
    return (
      <main style={{minHeight:"100svh",padding:"clamp(28px,8vw,120px)",
        background:"#11181d",color:"#f4f1e9",fontFamily:"system-ui,sans-serif"}}>
        <p style={{letterSpacing:"0.18em",fontSize:"0.75rem"}}>AETHERIS / E6 RESEARCH</p>
        <h1 style={{fontFamily:"Georgia,serif",fontSize:"clamp(2.5rem,7vw,6rem)",
          fontWeight:400,maxWidth:"15ch"}}>Blender preview is not included in this build.</h1>
        <p style={{maxWidth:"58ch",lineHeight:1.8}}>
          The preview requires an explicitly enabled, source-validated local research build.
          No model is fetched, generated, or loaded in this default mode.
        </p>
        <a href="/">Return to the static research experience</a>
      </main>
    );
  }
  return <main id="e6-blender-concept-preview"><CinematicExperience assetVariant="blender"/></main>;
}
