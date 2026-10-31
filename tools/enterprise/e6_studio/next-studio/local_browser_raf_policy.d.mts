export const KIND: "E6_LOCAL_BROWSER_RAF_DIAGNOSTIC_V2";
export const EXPECTED_RUNS: number;
export interface LocalRafRun {
 frameIntervalsMs: number[];
 progressMinimum: number;
 progressMaximum: number;
 progressStart: number;
 progressMidpoint: number;
 progressEnd: number;
 observedScrollMinPx: number;
 observedScrollMaxPx: number;
 scrollRangePx: number;
 reverseObserved: boolean;
 longTaskObserverSupported: boolean;
 longTaskCount: number;
 maxLongTaskMs: number;
}
export interface LocalRafReport {
 kind: typeof KIND;
 schemaVersion: 2;
 captureMode: "user-click-visible-loopback-browser";
 localhost: boolean;
 capturedAt: string;
 webgl2: boolean;
 engine: string;
 renderer: {unmaskedRenderer: string|null; unmaskedVendor: string|null};
 glbSha256: string|null;
 viewport: {width: number;height: number;dpr: number};
 browserErrorCount: number;
 contextLossCount: number;
 runs: LocalRafRun[];
 metadata: { browser: string;sourceCommitAttested: false;actualGpuTimeMeasured: false;externalUpload: false };
}
export interface LocalRafAssessment {
 kind: string;
 diagnosticValid: boolean;
 reasons: string[];
 runs: Array<{run: number;frames: number;p50Ms: number;p95Ms: number;p99Ms: number;over33_3Ratio: number;maxLongTaskMs: number}>;
 screenBudgetMet: boolean;
 scope?: string;
 hardwareIdentityVerified?: false;
 sourceCheckoutIndependentlyVerified?: false;
 physicalHardwareCertified: false;
 gpuTimeMeasured?: false;
 gpuVramMeasured?: false;
 thermalsMeasured?: false;
 physicalMobileTested?: false;
 task7Completed: false;
 task8Completed: false;
 stage45Go: false;
 releaseGo: false;
}
export function evaluateLocalBrowserRaf(report:unknown):LocalRafAssessment;
