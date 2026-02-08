/*
 * Audio Quality Test - Mathematical comparison of Java SC55 output vs Original C++
 */

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import vavi.sound.sc55.Mcu;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Mathematical audio quality comparison test.
 * Compares Java emulator output with original C++ reference.
 */
class AudioQualityTest {

    static {
        System.setProperty("javax.sound.midi.Synthesizer", "#Gervill");
    }

    /**
     * Read WAV file samples as normalized float array (-1.0 to 1.0)
     */
    static float[] readWavSamples(String path) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(path, "r");

        // Read RIFF header
        byte[] header = new byte[44];
        raf.read(header);

        // Parse format info
        ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int riff = bb.getInt();  // "RIFF"
        int fileSize = bb.getInt();
        int wave = bb.getInt();  // "WAVE"
        int fmt = bb.getInt();   // "fmt "
        int fmtSize = bb.getInt();
        short audioFormat = bb.getShort();
        short numChannels = bb.getShort();
        int sampleRate = bb.getInt();
        int byteRate = bb.getInt();
        short blockAlign = bb.getShort();
        short bitsPerSample = bb.getShort();

        // Skip to data chunk
        int dataMarker = bb.getInt();  // "data"
        int dataSize = bb.getInt();

        System.out.printf("WAV: %s - %d Hz, %d ch, %d bit, %d samples%n",
            path, sampleRate, numChannels, bitsPerSample, dataSize / (bitsPerSample/8) / numChannels);

        // Read samples
        int numSamples = dataSize / (bitsPerSample / 8);
        float[] samples = new float[numSamples];

        byte[] data = new byte[dataSize];
        raf.read(data);
        raf.close();

        ByteBuffer dataBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numSamples; i++) {
            short sample = dataBuf.getShort();
            samples[i] = sample / 32768.0f;
        }

        return samples;
    }

    /**
     * Calculate RMS (Root Mean Square) of signal
     */
    static double rms(float[] samples) {
        double sum = 0;
        for (float s : samples) {
            sum += s * s;
        }
        return Math.sqrt(sum / samples.length);
    }

    /**
     * Calculate max absolute value
     */
    static double maxAbs(float[] samples) {
        double max = 0;
        for (float s : samples) {
            max = Math.max(max, Math.abs(s));
        }
        return max;
    }

    /**
     * Calculate mean of absolute differences between consecutive samples (measures high-freq content)
     */
    static double meanDelta(float[] samples) {
        if (samples.length < 2) return 0;
        double sum = 0;
        for (int i = 1; i < samples.length; i++) {
            sum += Math.abs(samples[i] - samples[i-1]);
        }
        return sum / (samples.length - 1);
    }

    /**
     * Calculate max delta (measures transients/high-freq content)
     */
    static double maxDelta(float[] samples) {
        if (samples.length < 2) return 0;
        double max = 0;
        for (int i = 1; i < samples.length; i++) {
            max = Math.max(max, Math.abs(samples[i] - samples[i-1]));
        }
        return max;
    }

    /**
     * Remove leading silence (samples below threshold)
     */
    static float[] trimSilence(float[] samples, float threshold) {
        int start = 0;
        for (int i = 0; i < samples.length; i++) {
            if (Math.abs(samples[i]) > threshold) {
                start = i;
                break;
            }
        }
        int end = samples.length;
        for (int i = samples.length - 1; i >= 0; i--) {
            if (Math.abs(samples[i]) > threshold) {
                end = i + 1;
                break;
            }
        }
        float[] trimmed = new float[end - start];
        System.arraycopy(samples, start, trimmed, 0, trimmed.length);
        return trimmed;
    }

    /**
     * Normalize samples to peak = 1.0
     */
    static float[] normalize(float[] samples) {
        double peak = maxAbs(samples);
        if (peak == 0) return samples;
        float[] normalized = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            normalized[i] = (float)(samples[i] / peak);
        }
        return normalized;
    }

    /**
     * Print audio quality metrics
     */
    static void printMetrics(String label, float[] samples) {
        System.out.printf("%n=== %s ===%n", label);
        System.out.printf("  Samples: %d%n", samples.length);
        System.out.printf("  Max amplitude: %.4f%n", maxAbs(samples));
        System.out.printf("  RMS: %.4f%n", rms(samples));
        System.out.printf("  Max delta: %.4f%n", maxDelta(samples));
        System.out.printf("  Mean delta: %.4f%n", meanDelta(samples));
        System.out.printf("  dB level: %.1f dB%n", 20 * Math.log10(maxAbs(samples)));
    }

    @Test
    @Disabled("for ai iteration")
    @DisplayName("Compare Java audio quality with original C++ reference")
    void compareAudioQuality() throws Exception {
        // Check if reference file exists
        String refPath = "tmp/original_sc55_b3.wav";
        if (!Files.exists(Paths.get(refPath))) {
            System.out.println("Reference file not found: " + refPath);
            System.out.println("Please create reference audio using original C++ SC55");
            return;
        }

        // Read and analyze reference
        float[] refSamples = readWavSamples(refPath);
        float[] refTrimmed = trimSilence(refSamples, 0.001f);
        float[] refNorm = normalize(refTrimmed);
        printMetrics("REFERENCE (C++)", refNorm);

        // Generate Java audio
        System.out.println("\n--- Running Java emulator ---");
        Mcu mcu = new Mcu();
        Mcu.Config config = new Mcu.Config();
        config.pageSize = 512;
        config.pageNum = 32;

        Thread emulatorThread = new Thread(() -> mcu.run(config), "EmulatorThread");
        emulatorThread.start();

        Thread.sleep(5000);  // Wait for init

        // Send GS Reset
        byte[] gsReset = {(byte) 0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41, (byte) 0xF7};
        for (byte b : gsReset) mcu.MCU_PostUART(b);
        Thread.sleep(1000);

        // Play notes (C4, E4, G4 chord)
        int velocity = 100;
        int[] notes = {60, 64, 67};
        for (int note : notes) {
            mcu.MCU_PostUART((byte) 0x90);
            mcu.MCU_PostUART((byte) note);
            mcu.MCU_PostUART((byte) velocity);
        }
        Thread.sleep(2000);
        for (int note : notes) {
            mcu.MCU_PostUART((byte) 0x80);
            mcu.MCU_PostUART((byte) note);
            mcu.MCU_PostUART((byte) 0);
        }
        Thread.sleep(1000);

        emulatorThread.join(5000);

        // Read Java output
        String javaPath = "/tmp/sc55_direct.wav";
        if (!Files.exists(Paths.get(javaPath))) {
            System.out.println("Java output not found: " + javaPath);
            fail("Java emulator did not produce output");
            return;
        }

        float[] javaSamples = readWavSamples(javaPath);
        float[] javaTrimmed = trimSilence(javaSamples, 0.001f);
        float[] javaNorm = normalize(javaTrimmed);
        printMetrics("JAVA OUTPUT", javaNorm);

        // Compare metrics
        System.out.println("\n=== QUALITY COMPARISON ===");

        double refMaxDelta = maxDelta(refNorm);
        double javaMaxDelta = maxDelta(javaNorm);
        double deltaRatio = javaMaxDelta / refMaxDelta;
        System.out.printf("Max Delta Ratio (Java/Ref): %.2f (should be ~1.0)%n", deltaRatio);

        double refMeanDelta = meanDelta(refNorm);
        double javaMeanDelta = meanDelta(javaNorm);
        double meanDeltaRatio = javaMeanDelta / refMeanDelta;
        System.out.printf("Mean Delta Ratio (Java/Ref): %.2f (should be ~1.0)%n", meanDeltaRatio);

        double refRms = rms(refNorm);
        double javaRms = rms(javaNorm);
        double rmsRatio = javaRms / refRms;
        System.out.printf("RMS Ratio (Java/Ref): %.2f (should be ~1.0)%n", rmsRatio);

        // Quality thresholds
        System.out.println("\n=== QUALITY ASSESSMENT ===");

        boolean deltaPassed = deltaRatio > 0.5 && deltaRatio < 2.0;
        System.out.printf("Max Delta Check: %s (%.2f, expect 0.5-2.0)%n",
            deltaPassed ? "PASS" : "FAIL", deltaRatio);

        boolean meanDeltaPassed = meanDeltaRatio > 0.3 && meanDeltaRatio < 3.0;
        System.out.printf("Mean Delta Check: %s (%.2f, expect 0.3-3.0)%n",
            meanDeltaPassed ? "PASS" : "FAIL", meanDeltaRatio);

        boolean rmsPassed = rmsRatio > 0.5 && rmsRatio < 2.0;
        System.out.printf("RMS Check: %s (%.2f, expect 0.5-2.0)%n",
            rmsPassed ? "PASS" : "FAIL", rmsRatio);

        // Overall verdict
        boolean passed = deltaPassed && meanDeltaPassed && rmsPassed;
        System.out.printf("%nOVERALL: %s%n", passed ? "AUDIO QUALITY OK" : "AUDIO QUALITY DEGRADED");

        if (!passed) {
            System.out.println("\nDiagnosis:");
            if (deltaRatio < 0.5) {
                System.out.println("- Low max delta suggests missing high-frequency transients");
            }
            if (meanDeltaRatio < 0.3) {
                System.out.println("- Low mean delta suggests overall lack of high-frequency content");
            }
            if (rmsRatio < 0.5 || rmsRatio > 2.0) {
                System.out.println("- RMS mismatch suggests amplitude/volume problem");
            }
        }
    }
}
