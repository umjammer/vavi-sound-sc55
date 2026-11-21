/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.sc55.jep454;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.midi.Instrument;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDeviceReceiver;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Patch;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;
import javax.sound.midi.VoiceStatus;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import vavi.sound.midi.sc55.Sc55Soundbank.Sc55Instrument;
import vavi.sound.sc55.jep454.Mcu;
import vavi.util.StringUtil;

import static java.lang.System.getLogger;
import static vavi.sound.midi.sc55.Sc55MidiDeviceProvider.version;


/**
 * JEP454 version SC-55 Synthesizer.
 * <p>
 * the native library does not open any audio device, sound is pulled
 * by {@code SC55_readSamples()} and written to a java sound {@link SourceDataLine}.
 * <p>
 * env
 * <li>{@code SC55ROM} ... rom dir</li>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2025/03/12 umjammer initial version <br>
 */
public class Sc55Synthesizer implements Synthesizer {

    private static final Logger logger = getLogger(Sc55Synthesizer.class.getName());

    /** the device information */
    public static final Info info =
        new Info("JEP454 Nuked SC55 MIDI Synthesizer",
                            "vavi",
                            "JEP454 Nuked Software synthesizer for SC55",
                            "Version " + version) {};

    private long timestamp;

    private boolean isOpen;

    // ----

    @Override
    public Info getDeviceInfo() {
        return info;
    }

    @Override
    public void open() throws MidiUnavailableException {
        if (isOpen()) {
logger.log(Level.WARNING, "already open: " + hashCode());
            return;
        }

        AtomicReference<Exception> exception = new AtomicReference<>();

        nativeTask = executor.submit(() -> {
            try (Arena arena = Arena.ofConfined()) {
                Mcu.setenv(arena.allocateFrom("SDL_VIDEODRIVER"), arena.allocateFrom("dummy"), 1);
                String[] args = {"sc55", "-mk2", "-p:1"}; // TODO make machine type changeable
                MemorySegment argv = arena.allocate(ValueLayout.ADDRESS, args.length);
                for (int i = 0; i < args.length; i++) {
                    argv.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(args[i]));
                }
                int r = Mcu.SC55_run(args.length, argv);
logger.log(Level.DEBUG, "SC55_run returned: " + r);
                if (r != 0) exception.set(new IllegalStateException("SC55_run failed: " + r));
            } catch (Throwable e) {
logger.log(Level.ERROR, e.getMessage(), e);
                exception.set(e instanceof Exception ? (Exception) e : new Exception(e));
            }
        });

        // wait for the emulator to be ready
        long limit = System.currentTimeMillis() + OPEN_TIMEOUT;
        while (Mcu.SC55_isReady() == 0) {
            if (exception.get() != null) throw new MidiUnavailableException(exception.get().getMessage());
            if (nativeTask.isDone()) throw new MidiUnavailableException("SC55_run exited");
            if (System.currentTimeMillis() > limit) throw new MidiUnavailableException("SC55 initialization timeout");
            try { Thread.sleep(10); } catch (InterruptedException e) { throw new MidiUnavailableException(e.toString()); }
        }

        try {
            int sampleRate = Mcu.SC55_getSampleRate();
            AudioFormat format = new AudioFormat(sampleRate, 16, 2, true, ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, LINE_BUFFER_FRAMES * format.getFrameSize());
            line.start();
logger.log(Level.DEBUG, "line: " + format + ", buffer: " + line.getBufferSize());
        } catch (LineUnavailableException | IllegalArgumentException e) {
            Mcu.SC55_quit();
            throw (MidiUnavailableException) new MidiUnavailableException(e.getMessage()).initCause(e);
        }

        rendering = true;
        renderer.submit(this::render);

        //
        isOpen = true;

        timestamp = System.currentTimeMillis();
        start = timestamp;
    }

    /** max wait time for the native emulator to start [msec] */
    private static final long OPEN_TIMEOUT = 10_000;

    /** samples (int16) pulled from the native side at once, 512 frames (~8 msec) */
    private static final int READ_SAMPLES = 512 * 2;

    /** java sound line buffer size, ~31 msec at 66207 Hz, must be smaller than the native ring buffer (8192 frames) */
    private static final int LINE_BUFFER_FRAMES = 2048;

    private SourceDataLine line;

    private volatile boolean rendering;

    /** runs {@code SC55_run()} */
    private Future<?> nativeTask;

    /** pulls samples from the native side, writes them to the line, paced by the line's blocking write */
    private void render() {
        byte[] buf = new byte[READ_SAMPLES * Short.BYTES];
        MemorySegment segment = MemorySegment.ofArray(buf);
        try {
            while (rendering) {
                int n = Mcu.SC55_readSamples(segment, READ_SAMPLES);
                if (n <= 0) {
                    Thread.sleep(1);
                    continue;
                }
                line.write(buf, 0, n * Short.BYTES);
            }
        } catch (InterruptedException ignore) {
        } catch (Throwable e) {
logger.log(Level.ERROR, e.getMessage(), e);
        }
logger.log(Level.DEBUG, "render: exit");
    }

    /** for audio output */
    private final ExecutorService renderer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sc55-jep454-render");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.setDaemon(true);
        return thread;
    });

    /** when midi spi */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "sc55-jep454-native");
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.setDaemon(true);
        return thread;
    });

    private long start;

    @Override
    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void close() {
        isOpen = false;
        for (int i = receivers.size() - 1; i >= 0; i--) receivers.get(i).close();

        // stop pulling first, the native side frees its buffer when SC55_run returns
        rendering = false;
        renderer.shutdown();
        try {
            if (!renderer.awaitTermination(1, TimeUnit.SECONDS)) {
logger.log(Level.WARNING, "render thread did not stop");
                if (line != null) line.flush(); // unblock write
                renderer.shutdownNow();
                renderer.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Mcu.SC55_quit();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
logger.log(Level.WARNING, "SC55_run did not return");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public long getMicrosecondPosition() {
        return (timestamp - start) / 10;
    }

    @Override
    public int getMaxReceivers() {
        return -1;
    }

    @Override
    public int getMaxTransmitters() {
        return 0;
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        return new Sc55Receiver();
    }

    @Override
    public List<Receiver> getReceivers() {
        return receivers;
    }

    @Override
    public Transmitter getTransmitter() throws MidiUnavailableException {
        throw new MidiUnavailableException("No transmitter available");
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return Collections.emptyList();
    }

    @Override
    public int getMaxPolyphony() {
        return 18; // TODO
    }

    @Override
    public long getLatency() {
        // native ring buffer (8192 frames) + line buffer, in microseconds
        return (8192 + LINE_BUFFER_FRAMES) * 1_000_000L / 66207;
    }

    @Override
    public MidiChannel[] getChannels() {
        return null;
    }

    @Override
    public VoiceStatus[] getVoiceStatus() {
        return null;
    }

    @Override
    public boolean isSoundbankSupported(Soundbank soundbank) {
        return soundbank instanceof Sc55Instrument;
    }

    @Override
    public boolean loadInstrument(Instrument instrument) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void unloadInstrument(Instrument instrument) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public boolean remapInstrument(Instrument from, Instrument to) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Soundbank getDefaultSoundbank() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Instrument[] getAvailableInstruments() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public Instrument[] getLoadedInstruments() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public boolean loadAllInstruments(Soundbank soundbank) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void unloadAllInstruments(Soundbank soundbank) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public boolean loadInstruments(Soundbank soundbank, Patch[] patchList) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    @Override
    public void unloadInstruments(Soundbank soundbank, Patch[] patchList) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    private final List<Receiver> receivers = new ArrayList<>();

    private class Sc55Receiver implements MidiDeviceReceiver {

        private boolean isOpen;

        public Sc55Receiver() {
            receivers.add(this);
            isOpen = true;
        }

        @Override
        public void send(MidiMessage message, long timeStamp) {
            if (!isOpen) throw new IllegalStateException("Receiver is not open");

            switch (message) {
                case ShortMessage shortMessage -> {
                    int channel = shortMessage.getChannel();
                    int command = shortMessage.getCommand();
                    int data1 = shortMessage.getData1();
                    int data2 = shortMessage.getData2();
logger.log(Level.TRACE, "[%d] ev: %d, ch: %d, p1: %d, p2: %d".formatted(timeStamp, command, channel, data1, data2));
                    switch (command) {
                        case ShortMessage.NOTE_ON,
                             ShortMessage.NOTE_OFF,
                             ShortMessage.POLY_PRESSURE,
                             ShortMessage.CONTROL_CHANGE,
                             ShortMessage.PITCH_BEND -> {

                            Mcu.SC55_postUART((byte) (command | channel));
                            Mcu.SC55_postUART((byte) data1);
                            Mcu.SC55_postUART((byte) data2);
                        }
                        case ShortMessage.PROGRAM_CHANGE,
                             ShortMessage.CHANNEL_PRESSURE -> {

                            Mcu.SC55_postUART((byte) (command | channel));
                            Mcu.SC55_postUART((byte) data1);
                        }
                    }
                }
                case SysexMessage sysexMessage -> {
                    byte[] data = sysexMessage.getData();
logger.log(Level.DEBUG, "sysex: %02X\n%s".formatted(sysexMessage.getStatus(), StringUtil.getDump(data, 32)));
                    switch (data[0]) {
                        case 0x7f -> { // Universal Realtime
                            int c = data[1]; // 0x7f: Disregards channel
                            // Sub-ID, Sub-ID2
                            if (data[2] == 0x04 && data[3] == 0x01) { // Device Control / Master Volume
                                float gain = ((data[4] & 0x7f) | ((data[5] & 0x7f) << 7)) / 16383f;
logger.log(Level.DEBUG, "sysex volume: gain: %3.0f".formatted(gain * 127));
//                                volume(line, gain);
                            }
                        }
                    }
                    for (byte b : data) {
                        Mcu.SC55_postUART(b);
                    }
                }
                default -> {}
            }
        }

        @Override
        public void close() {
            isOpen = false;
            receivers.remove(this);
        }

        @Override
        public MidiDevice getMidiDevice() {
            return Sc55Synthesizer.this;
        }
    }
}
