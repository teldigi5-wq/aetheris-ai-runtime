package io.aetheris.orchestrator.vault;
/** Capability declaration only; Stage 7 does not call Windows DPAPI from CI or non-Windows hosts. */
public record WindowsDpapiVaultPlan(String backend,boolean implemented,boolean requiresPairedWindowsHost,String detail){
    public static WindowsDpapiVaultPlan planned(){return new WindowsDpapiVaultPlan("windows-dpapi",false,true,"Implementation is deferred until a paired Windows workstation is available; environment vault remains the portable development backend.");}
}
