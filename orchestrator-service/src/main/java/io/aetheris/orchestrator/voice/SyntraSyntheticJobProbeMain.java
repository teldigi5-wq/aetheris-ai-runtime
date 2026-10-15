package io.aetheris.orchestrator.voice;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Stage 46.12: inert Windows Job Object LAB child. Never accepts microphone
 * bytes, transcripts, shell commands, paths, credentials or model requests.
 * The four exact modes are chosen by a local CI test harness, not user audio.
 * This class is neither registered in Spring nor used by the real runtime.
 */
public final class SyntraSyntheticJobProbeMain {
    private SyntraSyntheticJobProbeMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.exit(4);
            return;
        }
        switch (args[0]) {
            case "EXIT" -> { return; }
            case "FAIL" -> { System.exit(17); return; }
            case "STALL" -> Thread.sleep(60_000L);
            case "TRY_CHILD" -> {
                // Only attempts to launch the exact same fixed inert Java
                // fixture. JOB_OBJECT_LIMIT_ACTIVE_PROCESS=1 must deny it.
                String exe = Path.of(System.getProperty("java.home"), "bin",
                        "java.exe").toString();
                try {
                    Process child = new ProcessBuilder(exe, "-cp",
                            System.getProperty("java.class.path"),
                            SyntraSyntheticJobProbeMain.class.getName(), "EXIT")
                            .start();
                    child.waitFor();
                    System.exit(19); // Unexpected descendant creation
                } catch (IOException blockedByJobLimit) {
                    return; // Expected only on a locked-down Windows job.
                }
            }
            default -> System.exit(4);
        }
    }
}
