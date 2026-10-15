package io.aetheris.orchestrator.voice;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Stage 46.10: fixed, deterministic synthetic PCM analysis inside a CHILD
 * JVM. No real STT, VAD, wake word, model loader, microphone, playback,
 * network, shell, desktop commands, persistence or owner authentication.
 *
 * Input is a fixed binary record from the parent: 3 signed big-endian ints
 * (magic, 16000-Hz sample rate, PCM length) and <=3200 bytes signed 16-bit
 * little-endian mono PCM. No trailing data or text command is accepted.
 * Output is exactly one short ASCII protocol line and nothing else.
 */
public final class SyntraSyntheticPcmWorkerMain {
    static final int MAGIC = 0x53463431; // SF41
    static final String PREFIX = "SYNTRA46_10_V1|";
    static final int SAMPLE_RATE = 16_000;
    private SyntraSyntheticPcmWorkerMain() {}

    public static void main(String[] args) {
        if (args.length != 1 || !isMode(args[0])) {
            System.exit(4);
            return;
        }
        byte[] pcm = null;
        try {
            DataInputStream input = new DataInputStream(System.in);
            int magic = input.readInt();
            int rate = input.readInt();
            int length = input.readInt();
            if (magic != MAGIC || rate != SAMPLE_RATE ||
                    length < 640 || length > 3200 || (length & 1) != 0)
                throw new IOException("Invalid synthetic PCM header");
            pcm = new byte[length];
            input.readFully(pcm);
            if (input.read() != -1)
                throw new IOException("Trailing data not allowed");

            // Test fault injection modes are fixed, harmless and never
            // select a file, executable, model, address or OS operation.
            switch (args[0]) {
                case "STALL" -> {
                    Thread.sleep(60_000L);
                    return;
                }
                case "MALFORMED" -> {
                    emit("UNTRUSTED_OUTPUT\n");
                    return;
                }
                case "FLOOD" -> {
                    System.out.write(new byte[4096]);
                    System.out.flush();
                    return;
                }
                case "FAIL" -> {
                    System.exit(7);
                    return;
                }
                default -> { /* NORMAL deterministic fixture */ }
            }

            int above = 0;
            for (int i = 0; i < pcm.length; i += 2) {
                int sample = (short) (((pcm[i + 1] & 0xff) << 8) |
                        (pcm[i] & 0xff));
                if (Math.abs(sample) >= 1024) above++;
            }
            String verdict = above >= (pcm.length / 2 + 9) / 10 ?
                    "VOICE" : "SILENCE";
            emit(PREFIX + verdict + "\n");
        } catch (IOException | InterruptedException invalid) {
            System.exit(5);
        } finally {
            if (pcm != null) Arrays.fill(pcm, (byte) 0);
        }
    }

    private static boolean isMode(String mode) {
        return switch (mode) {
            case "NORMAL", "STALL", "MALFORMED", "FLOOD", "FAIL" -> true;
            default -> false;
        };
    }

    private static void emit(String line) throws IOException {
        System.out.write(line.getBytes(StandardCharsets.US_ASCII));
        System.out.flush();
    }
}
