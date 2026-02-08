/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;

import vavi.sound.midi.MidiUtil.MidiMatcher;
import vavi.sound.midi.Sc55Synthesizer;
import vavi.sound.sc55.Mcu;
import vavi.util.Debug;
import vavi.util.StringUtil;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;
import vavi.util.properties.annotation.PropsEntity.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static vavi.sound.midi.MidiUtil.getMidiDevice;
import static vavi.sound.sc55.LcdFont.lcd_font;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-05-07 nsano initial version <br>
 */
@PropsEntity(url = "file:local.properties")
class TestCase {

    static boolean localPropertiesExists() {
        return Files.exists(Paths.get("local.properties"));
    }

    static {
        System.setProperty("javax.sound.midi.Sequencer", "#Real Time Sequencer");
        System.setProperty("javax.sound.midi.Synthesizer", "#Gervill");
    }

    @Property(name = "sc55.dir")
    String baseDir;

    @Property
    String midi;

    @Property(name = "in.name")
    String inName;

    @Property(name = "in.vendor")
    String inVendor;

    @Property(name = "in.description")
    String inDescription;

    @Property(name = "out.name")
    String outName;

    @Property(name = "out.vendor")
    String outVendor;

    @Property(name = "out.description")
    String outDescription;

    @Property(name = "vavi.test.volume")
    double volume = 0.2;

    @Property(name = "vavi.test.volume.midi")
    float midiVolume = 0.2f;

    static boolean onIde = System.getProperty("vavi.test", "").equals("ide");
    static long time = onIde ? 1000 * 1000 : 10 * 1000;

    @BeforeEach
    void setupEach() throws IOException {
        if (localPropertiesExists()) {
            Util.bind(this);
        }

        if (baseDir != null) {
            System.setProperty("sc55.dir", baseDir);
        }
Debug.println("volume: " + volume + ", sc55.dir: " + System.getProperty("sc55.dir"));
    }

    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test01() throws Exception {
        int c = 0;
        for (byte[] f : lcd_font) {
            System.out.println("font: " + c++);
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 5; j++) {
                    boolean col = (f[i] & (1 << (4 - j))) != 0;
                    System.out.print(col ? "⬛️" : "⬜️");
                }
                System.out.println();
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test1() throws Exception {
        Mcu.main(new String[] {});
    }

    @Test
    @DisplayName("connect specified input device to the synthesizer")
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test2() throws Exception {

        Info info = getMidiDevice(new MidiMatcher(inName, inVendor, inDescription, null), true);
        MidiDevice device =  MidiSystem.getMidiDevice(info);
Debug.println("---- " + info +" (" + device.getClass().getName() + ")" + " ----");
Debug.println("name      : " + info.getName());
Debug.println("vendor    : " + info.getVendor());
Debug.println("descriptor: " + info.getDescription());
Debug.println("version   : " + info.getVersion());
        device.open();

        // Now, display strings from synthInfos list in GUI.

        Synthesizer synthesizer = new Sc55Synthesizer();
Debug.println("synthesizer: " + synthesizer);
        synthesizer.open();
        Receiver receiver = synthesizer.getReceiver();
        Transmitter transmitter = device.getTransmitter();

        transmitter.setReceiver(new Receiver() {
            @Override
            public void send(MidiMessage message, long timeStamp) {
                if (message instanceof ShortMessage shortMessage) {
                    int channel = shortMessage.getChannel();
                    int command = shortMessage.getCommand();
                    int data1 = shortMessage.getData1();
                    int data2 = shortMessage.getData2();
Debug.printf("short: command: %02x, channel: %d, data1: %d, data2: %d", command, channel, data1, data2);
                    switch (command) {
                        case ShortMessage.NOTE_OFF:
                            break;
                        case ShortMessage.NOTE_ON:
                            break;
                        case ShortMessage.POLY_PRESSURE:
                            break;
                        case ShortMessage.CONTROL_CHANGE:
                            break;
                        case ShortMessage.PROGRAM_CHANGE:
                            break;
                        case ShortMessage.CHANNEL_PRESSURE:
                            break;
                        case ShortMessage.PITCH_BEND:
                            break;
                    }
                } else if (message instanceof SysexMessage sysexMessage) {
                    byte[] data = sysexMessage.getData();
Debug.println("sysex: %02X\n%s".formatted(sysexMessage.getStatus(), StringUtil.getDump(data, 32)));
                } else if (message instanceof MetaMessage metaMessage) {
Debug.println("meta: %02x".formatted(metaMessage.getType()));
                } else {
                    assert false;
                }

                receiver.send(message, timeStamp);
            }

            @Override
            public void close() {
            }
        });

        CountDownLatch cdl = new CountDownLatch(1);
Debug.println("waiting...");
        cdl.await();
    }

    @Test
    @Disabled("for ai iteration")
    @DisplayName("B3 note test for audio quality comparison")
    void testB3Note() throws Exception {
        Mcu mcu = new Mcu();
        Mcu.Config config = new Mcu.Config();
        config.pageSize = 512;
        config.pageNum = 32;

        Thread emulatorThread = new Thread(() -> mcu.run(config), "EmulatorThread");
        emulatorThread.start();

        // Wait for emulator to start (2 seconds)
        Thread.sleep(2000);
        Debug.println("Sending B3 note...");

        // Play B3 note (MIDI 59)
        int note = 59;
        int velocity = 100;
        int channel = 0;

        // Note On
        mcu.MCU_PostUART((byte) (0x90 + channel));
        mcu.MCU_PostUART((byte) note);
        mcu.MCU_PostUART((byte) velocity);

        // Hold for 15 seconds
        Thread.sleep(15000);

        // Note Off
        mcu.MCU_PostUART((byte) (0x80 + channel));
        mcu.MCU_PostUART((byte) note);
        mcu.MCU_PostUART((byte) 0);

        Thread.sleep(3000);
        Debug.println("Test complete.");

        // Let the emulator continue running for user to hear audio
        // User can close the LCD window to stop
        emulatorThread.join();
    }

    @Test
    @Disabled("for ai iteration")
    @DisplayName("Audio quality analysis - detect choppiness")
    void testAudioQuality() throws Exception {
        // Capture audio samples for analysis
        final int SAMPLE_RATE = 44100;
        final int CAPTURE_SECONDS = 8;
        final int CAPTURE_SAMPLES = SAMPLE_RATE * CAPTURE_SECONDS;
        short[] capturedLeft = new short[CAPTURE_SAMPLES];
        short[] capturedRight = new short[CAPTURE_SAMPLES];
        int[] captureIndex = {0};
        boolean[] capturing = {false};

        Mcu mcu = new Mcu();

        // Set capture callback
        mcu.setAudioCaptureCallback((left, right) -> {
            if (capturing[0] && captureIndex[0] < CAPTURE_SAMPLES) {
                capturedLeft[captureIndex[0]] = left;
                capturedRight[captureIndex[0]] = right;
                captureIndex[0]++;
            }
        });

        Mcu.Config config = new Mcu.Config();
        config.pageSize = 512;
        config.pageNum = 32;

        Thread emulatorThread = new Thread(() -> mcu.run(config), "EmulatorThread");
        emulatorThread.start();

        // Wait for emulator to start
        if (!mcu.waitForReady(5000)) {
            Debug.print("Emulator did not become ready within 5 seconds");
        }

        // Start capture and play note
        Debug.println("Starting audio capture and playing B3 note...");
        capturing[0] = true;

        mcu.MCU_PostUART((byte) 0x90);
        mcu.MCU_PostUART((byte) 59);
        mcu.MCU_PostUART((byte) 100);

        // Wait for capture to complete
        Thread.sleep(CAPTURE_SECONDS * 1000 + 1000);

        // Note off
        mcu.MCU_PostUART((byte) 0x80);
        mcu.MCU_PostUART((byte) 59);
        mcu.MCU_PostUART((byte) 0);

        capturing[0] = false;
        int captured = captureIndex[0];
        Debug.println("Captured " + captured + " samples");

        // === AUDIO QUALITY ANALYSIS ===
        Debug.println("\n=== AUDIO QUALITY ANALYSIS ===\n");

        // 1. Count silence gaps (consecutive zero samples)
        int silenceGaps = 0;
        int currentSilence = 0;
        int maxSilence = 0;
        int totalSilenceSamples = 0;
        for (int i = 0; i < captured; i++) {
            if (capturedLeft[i] == 0 && capturedRight[i] == 0) {
                currentSilence++;
                totalSilenceSamples++;
            } else {
                if (currentSilence > 100) { // Gap > 2.3ms at 44.1kHz
                    silenceGaps++;
                    if (currentSilence > maxSilence) maxSilence = currentSilence;
                }
                currentSilence = 0;
            }
        }
        double silencePercent = (double) totalSilenceSamples / captured * 100;
        Debug.println("1. SILENCE ANALYSIS:");
        Debug.println("   Total silence: " + String.format("%.2f%%", silencePercent));
        Debug.println("   Silence gaps (>2.3ms): " + silenceGaps);
        Debug.println("   Max gap duration: " + String.format("%.1fms", maxSilence / 44.1));

        // 2. Detect sudden amplitude changes (clicks/pops)
        int clickCount = 0;
        int maxDelta = 0;
        long totalDelta = 0;
        for (int i = 1; i < captured; i++) {
            int delta = Math.abs(capturedLeft[i] - capturedLeft[i-1]);
            totalDelta += delta;
            if (delta > maxDelta) maxDelta = delta;
            // Click detection: sudden change > 10000 in single sample
            if (delta > 10000) clickCount++;
        }
        double avgDelta = (double) totalDelta / (captured - 1);
        Debug.println("2. DISCONTINUITY ANALYSIS:");
        Debug.println("   Clicks/pops detected: " + clickCount);
        Debug.println("   Max sample delta: " + maxDelta);
        Debug.println("   Avg sample delta: " + String.format("%.1f", avgDelta));

        // 3. Detect repeated sample patterns (buffer underrun symptom)
        int repeatedPatterns = 0;
        for (int i = 100; i < captured - 100; i++) {
            // Check if 50 consecutive samples repeat
            boolean allSame = true;
            short val = capturedLeft[i];
            for (int j = 1; j < 50 && allSame; j++) {
                if (capturedLeft[i + j] != val) allSame = false;
            }
            if (allSame && val != 0) repeatedPatterns++;
        }
        Debug.println("3. REPEATED PATTERN ANALYSIS:");
        Debug.println("   Stuck samples detected: " + repeatedPatterns);

        // 4. RMS amplitude analysis (should be stable during sustained note)
        int windowSize = SAMPLE_RATE / 10; // 100ms windows
        double minRms = Double.MAX_VALUE;
        double maxRms = 0;
        int numWindows = 0;
        // Skip first 0.5s (attack) and analyze sustain portion
        for (int i = SAMPLE_RATE / 2; i < captured - windowSize; i += windowSize) {
            double sum = 0;
            for (int j = 0; j < windowSize; j++) {
                sum += (double) capturedLeft[i + j] * capturedLeft[i + j];
            }
            double rms = Math.sqrt(sum / windowSize);
            if (rms > 100) { // Only consider non-silent windows
                if (rms < minRms) minRms = rms;
                if (rms > maxRms) maxRms = rms;
                numWindows++;
            }
        }
        double rmsVariation = (maxRms > 0) ? (maxRms - minRms) / maxRms * 100 : 0;
        Debug.println("4. RMS STABILITY (sustain portion):");
        Debug.println("   Min RMS: " + String.format("%.1f", minRms));
        Debug.println("   Max RMS: " + String.format("%.1f", maxRms));
        Debug.println("   RMS variation: " + String.format("%.1f%%", rmsVariation));
        Debug.println("   Windows analyzed: " + numWindows);

        // 5. Overall quality score
        Debug.println("=== QUALITY VERDICT ===");
        boolean hasProblems = false;
        if (silencePercent > 10) {
            Debug.println("FAIL: Too much silence (" + String.format("%.1f%%", silencePercent) + " > 10%)");
            hasProblems = true;
        }
        if (silenceGaps > 5) {
            Debug.println("FAIL: Too many silence gaps (" + silenceGaps + " > 5)");
            hasProblems = true;
        }
        if (clickCount > 10) {
            Debug.println("FAIL: Too many clicks (" + clickCount + " > 10)");
            hasProblems = true;
        }
        if (rmsVariation > 50) {
            Debug.println("FAIL: RMS too unstable (" + String.format("%.1f%%", rmsVariation) + " > 50%)");
            hasProblems = true;
        }
        if (!hasProblems) {
            Debug.println("PASS: Audio quality metrics acceptable");
        }

        Thread.sleep(1000);
        Debug.println("\nTest complete. Stopping emulator...");
        mcu.stop();
        emulatorThread.join(5000);  // Wait up to 5 seconds for graceful shutdown
    }

    @Test
    @DisplayName("Multi-note test - detect muddiness after many notes")
    void testMultiNoteQuality() throws Exception {
        final int SAMPLE_RATE = 44100;
        final int SAMPLES_PER_NOTE = SAMPLE_RATE / 4; // 0.25 seconds per note (faster playing)
        final int NUM_NOTES = 50;  // Increased from 15 to catch voice accumulation
        final int TOTAL_SAMPLES = SAMPLES_PER_NOTE * NUM_NOTES;

        short[] capturedLeft = new short[TOTAL_SAMPLES];
        short[] capturedRight = new short[TOTAL_SAMPLES];
        int[] captureIndex = {0};
        boolean[] capturing = {false};

        Mcu mcu = new Mcu();
        mcu.setAudioCaptureCallback((left, right) -> {
            if (capturing[0] && captureIndex[0] < TOTAL_SAMPLES) {
                capturedLeft[captureIndex[0]] = left;
                capturedRight[captureIndex[0]] = right;
                captureIndex[0]++;
            }
        });

        Mcu.Config config = new Mcu.Config();
        config.pageSize = 512;
        config.pageNum = 32;

        Thread emulatorThread = new Thread(() -> mcu.run(config), "EmulatorThread");
        emulatorThread.start();

        if (!mcu.waitForReady(5000)) {
            Debug.print("Emulator did not become ready within 5 seconds");
        }

        Debug.println("=== MULTI-NOTE QUALITY TEST ===");
        Debug.println("Playing " + NUM_NOTES + " notes sequentially...");

        capturing[0] = true;
        int baseNote = 48; // C3

        // Play notes one at a time, release previous before playing next
        for (int i = 0; i < NUM_NOTES; i++) {
            int note = baseNote + i;

            // Note On
            mcu.MCU_PostUART((byte) 0x90);
            mcu.MCU_PostUART((byte) note);
            mcu.MCU_PostUART((byte) 100);

            System.out.println("Note " + (i + 1) + ": Playing MIDI note " + note);
            Thread.sleep(200); // Hold for 0.2 seconds (faster playing)

            // Note Off
            mcu.MCU_PostUART((byte) 0x80);
            mcu.MCU_PostUART((byte) note);
            mcu.MCU_PostUART((byte) 0);

            Thread.sleep(50); // Short gap between notes (realistic MIDI timing)
        }

        capturing[0] = false;
        int captured = captureIndex[0];
        Debug.println("Captured " + captured + " samples total");

        // Analyze each note's audio quality
        Debug.println("=== PER-NOTE ANALYSIS ===");

        for (int noteIdx = 0; noteIdx < NUM_NOTES; noteIdx++) {
            int startSample = noteIdx * SAMPLES_PER_NOTE;
            int endSample = Math.min(startSample + SAMPLES_PER_NOTE, captured);

            if (startSample >= captured) break;

            // Calculate RMS for this note
            double sumSquares = 0;
            int nonZeroSamples = 0;
            int silentSamples = 0;

            for (int i = startSample; i < endSample; i++) {
                if (capturedLeft[i] == 0 && capturedRight[i] == 0) {
                    silentSamples++;
                } else {
                    sumSquares += (double) capturedLeft[i] * capturedLeft[i];
                    nonZeroSamples++;
                }
            }

            double rms = nonZeroSamples > 0 ? Math.sqrt(sumSquares / nonZeroSamples) : 0;
            double silencePercent = 100.0 * silentSamples / (endSample - startSample);

            // Check for high-frequency content (potential muddiness indicator)
            // Simple check: count zero-crossings (fewer = more low-frequency = potentially muddy)
            int zeroCrossings = 0;
            for (int i = startSample + 1; i < endSample; i++) {
                if ((capturedLeft[i-1] >= 0 && capturedLeft[i] < 0) ||
                    (capturedLeft[i-1] < 0 && capturedLeft[i] >= 0)) {
                    zeroCrossings++;
                }
            }
            double crossingRate = 1000.0 * zeroCrossings / (endSample - startSample);

            String status = silencePercent > 20 ? "CHOPPY" :
                           (crossingRate < 50 ? "POSSIBLY MUDDY" : "OK");

            System.out.printf("Note %2d: RMS=%6.1f, Silence=%5.1f%%, ZeroCrossRate=%5.1f/1000 [%s]%n",
                    noteIdx + 1, rms, silencePercent, crossingRate, status);
        }

        Debug.println("Test complete. Stopping emulator...");
        mcu.stop();
        emulatorThread.join(5000);
    }
}
