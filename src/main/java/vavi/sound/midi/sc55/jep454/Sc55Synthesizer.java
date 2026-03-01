/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.sc55.jep454;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

import vavi.sound.midi.sc55.Sc55Soundbank.Sc55Instrument;
import vavi.sound.sc55.jep454.Mcu;
import vavi.util.StringUtil;

import static java.lang.System.getLogger;


/**
 * JEP454 version SC-55 Synthesizer.
 * <p>
 * env
 * <li>{@code SC55ROM} ... rom dir</li>
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2025/03/12 umjammer initial version <br>
 */
public class Sc55Synthesizer implements Synthesizer {

    private static final Logger logger = getLogger(Sc55Synthesizer.class.getName());

    static {
        try {
            try (InputStream is = Sc55Synthesizer.class.getResourceAsStream("/META-INF/maven/vavi/vavi-sound-sc55/pom.properties")) {
                if (is != null) {
                    Properties props = new Properties();
                    props.load(is);
                    version = props.getProperty("version", "undefined in pom.properties");
                } else {
                    version = System.getProperty("vavi.test.version", "undefined");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final String version;

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

        executor.submit(() -> {
            try (Arena arena = Arena.ofConfined()) {
                Mcu.setenv(arena.allocateFrom("SDL_VIDEODRIVER"), arena.allocateFrom("dummy"), 1);
                String[] args = {"sc55", "-mk2", "-p:1"}; // TODO make machine type changeable
                MemorySegment argv = arena.allocate(ValueLayout.ADDRESS, args.length);
                for (int i = 0; i < args.length; i++) {
                    argv.setAtIndex(ValueLayout.ADDRESS, i, arena.allocateFrom(args[i]));
                }
                Mcu.SC55_run(args.length, argv);
            } catch (Throwable e) {
logger.log(Level.ERROR, e.getMessage(), e);
                exception.set(e instanceof Exception ? (Exception) e : new Exception(e));
            }
        });

        try { Thread.sleep(2000); } catch (InterruptedException ignore) {}

        if (exception.get() != null) throw new MidiUnavailableException(exception.get().getMessage());

        //
        isOpen = true;

        timestamp = System.currentTimeMillis();
        start = timestamp;
    }

    /** when midi spi */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });

    private long start;

    @Override
    @SuppressWarnings("ForLoopReplaceableByForEach")
    public void close() {
        isOpen = false;
        for (int i = 0; i < receivers.size(); i++) receivers.get(i).close();
        executor.shutdown();
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
        return 33;
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
