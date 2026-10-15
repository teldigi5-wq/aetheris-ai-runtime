package io.aetheris.orchestrator.voice;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SyntraLocalTextCompanionTest {
    private static String simulate(String text) throws Exception {
        var output = new StringWriter();
        SyntraLocalTextCompanion.run(new StringReader(text), output, () -> false);
        return output.toString();
    }

    @Test
    void acceptsTypedWakeAndCreatesOnlyNonExecutableOwnerApprovalPreview() throws Exception {
        String output=simulate("open editor\nhi sintra\nopen editor\nquit\n");
        assertThat(output).contains("DENIED: unsupported command");
        assertThat(output).contains("text wake acknowledged");
        assertThat(output).contains("PREVIEW ONLY: proposal recorded");
        assertThat(output).contains("no action will run");
        assertThat(output).doesNotContain("SIMULATED ONLY: no desktop action executed");
        assertThat(output).contains("preview closed");
    }

    @Test
    void statusReportsActualDisconnectedSpeechCapabilitiesAndLocalOnlyOperation()
            throws Exception {
        String output=simulate("status\nquit\n");
        assertThat(output).contains("command execution OFF", "network NOT USED",
                "SPEECH: HARDWARE_GATED", "microphone=false",
                "local-STT=false", "local-TTS=false");
    }

    @Test
    void standaloneConsoleCannotMintOwnerApprovalOrExecuteApps() throws Exception {
        String output=simulate("hi syntra\nopen project notes\n"
                + "approve\nyes\nrun powershell\nopen https://example.org\n"
                + "open editor\nquit\n");
        assertThat(output).contains("PREVIEW ONLY");
        assertThat(output).contains("DENIED");
        assertThat(output).doesNotContain("SIMULATED ONLY:");
        assertThat(output).doesNotContain("Executing", "Launched", "Installed");
    }

    @Test
    void localEmergencyStopRevokesOutstandingProposalIrreversibly() throws Exception {
        String output=simulate("hi syntra\nopen editor\nstop everything\n"
                + "hi syntra\nopen editor\nstatus\nquit\n");
        assertThat(output).contains("STOP: latched. Pending previews invalidated");
        assertThat(output).contains("STOP: latched. Restarting this demo");
        assertThat(output).doesNotContain("SIMULATED ONLY:");
    }

    @Test
    void externallyEngagedStopBlocksConsoleStartup() throws Exception {
        var output=new StringWriter();
        SyntraLocalTextCompanion.run(
                new StringReader("hi syntra\nopen editor\n"),output,()->true);
        assertThat(output.toString()).contains("local console disabled");
        assertThat(output.toString()).doesNotContain("PREVIEW ONLY");
    }

    @Test
    void externalStopAfterWakeBlocksPendingRequestsAndCannotBeReleasedInSession()
            throws Exception {
        var stop=new AtomicBoolean(false);
        var writer=new StringWriter();
        // Switch external STOP after the second signal read. No host
        // process or real asynchronous watcher is used.
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        SyntraLocalTextCompanion.run(
                new StringReader("hi syntra\nopen editor\nquit\n"),
                writer, () -> reads.incrementAndGet() >= 3);
        assertThat(writer.toString()).contains("text wake acknowledged");
        assertThat(writer.toString()).contains("STOP: external signal active");
        assertThat(writer.toString()).doesNotContain("PREVIEW ONLY");
    }

    @Test
    void boundedLineDiscardPreventsCommandsFromSplicingIntoNextTurn() throws Exception {
        String input = "z".repeat(2000)+"\nhi syntra\nshow system status\nquit\n";
        String output = simulate(input);
        assertThat(output).contains("DENIED: command too long");
        assertThat(output).contains("text wake acknowledged");
        assertThat(output).contains("PREVIEW ONLY");
    }

    @Test
    void invalidMultilinePayloadAndBlankCommandsFailClosed() throws Exception {
        String output=simulate("\nhi syntra\nopen editor; rm -rf\n"
                + "<script>alert(1)</script>\nquit\n");
        assertThat(output).contains("DENIED");
        assertThat(output).doesNotContain("SIMULATED ONLY:");
    }

    @Test
    void eofExitsWithoutInstallingOrLaunchingAnything() throws Exception {
        String output=simulate("help\nhi sintra\n");
        assertThat(output).contains("Desktop requests create proposals only");
        assertThat(output).contains("text wake acknowledged");
        assertThat(output).doesNotContain("Launched application");
    }
}
