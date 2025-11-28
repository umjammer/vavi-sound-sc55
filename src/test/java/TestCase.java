/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.io.IOException;
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

//import vavi.sound.midi.MidiUtil.MidiMatcher;
import vavi.sound.midi.Sc55Synthesizer;
import vavi.sound.sc55.Mcu;
import vavi.util.Debug;
import vavi.util.StringUtil;
import vavi.util.properties.annotation.Property;
import vavi.util.properties.annotation.PropsEntity;
import vavi.util.properties.annotation.PropsEntity.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

//import static vavi.sound.midi.MidiUtil.getMidiDevice;
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
//        System.setProperty("javax.sound.midi.Synthesizer", "#VoiceVox MIDI Synthesizer"); // TODO make this pluggable
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

//    @Test
//    @DisplayName("connect specified input device to the synthesizer")
//    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
//    void test2() throws Exception {
//
//        Info info = getMidiDevice(new MidiMatcher(inName, inVendor, inDescription, null), true);
//        MidiDevice device =  MidiSystem.getMidiDevice(info);
//Debug.println("---- " + info +" (" + device.getClass().getName() + ")" + " ----");
//Debug.println("name      : " + info.getName());
//Debug.println("vendor    : " + info.getVendor());
//Debug.println("descriptor: " + info.getDescription());
//Debug.println("version   : " + info.getVersion());
//        device.open();
//
//        // Now, display strings from synthInfos list in GUI.
//
//        Synthesizer synthesizer = new Sc55Synthesizer();
//Debug.println("synthesizer: " + synthesizer);
//        synthesizer.open();
//        Receiver receiver = synthesizer.getReceiver();
//        Transmitter transmitter = device.getTransmitter();
//
//        transmitter.setReceiver(new Receiver() {
//            @Override
//            public void send(MidiMessage message, long timeStamp) {
//                if (message instanceof ShortMessage shortMessage) {
//                    int channel = shortMessage.getChannel();
//                    int command = shortMessage.getCommand();
//                    int data1 = shortMessage.getData1();
//                    int data2 = shortMessage.getData2();
//Debug.printf("short: command: %02x, channel: %d, data1: %d, data2: %d", command, channel, data1, data2);
//                    switch (command) {
//                        case ShortMessage.NOTE_OFF:
//                            break;
//                        case ShortMessage.NOTE_ON:
//                            break;
//                        case ShortMessage.POLY_PRESSURE:
//                            break;
//                        case ShortMessage.CONTROL_CHANGE:
//                            break;
//                        case ShortMessage.PROGRAM_CHANGE:
//                            break;
//                        case ShortMessage.CHANNEL_PRESSURE:
//                            break;
//                        case ShortMessage.PITCH_BEND:
//                            break;
//                    }
//                } else if (message instanceof SysexMessage sysexMessage) {
//                    byte[] data = sysexMessage.getData();
//Debug.println("sysex: %02X\n%s".formatted(sysexMessage.getStatus(), StringUtil.getDump(data, 32)));
//                } else if (message instanceof MetaMessage metaMessage) {
//Debug.println("meta: %02x".formatted(metaMessage.getType()));
//                } else {
//                    assert false;
//                }
//
//                receiver.send(message, timeStamp);
//            }
//
//            @Override
//            public void close() {
//            }
//        });
//
//        CountDownLatch cdl = new CountDownLatch(1);
//Debug.println("waiting...");
//        cdl.await();
//    }
}
