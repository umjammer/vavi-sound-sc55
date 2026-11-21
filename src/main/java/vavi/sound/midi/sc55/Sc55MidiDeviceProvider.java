/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.sc55;

import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Properties;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.spi.MidiDeviceProvider;

import static java.lang.System.getLogger;


/**
 * Sc55MidiDeviceProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 260208 nsano initial version <br>
 */
public class Sc55MidiDeviceProvider extends MidiDeviceProvider {

    private static final Logger logger = getLogger(Sc55MidiDeviceProvider.class.getName());

    static {
        try {
            try (InputStream is = Sc55MidiDeviceProvider.class.getResourceAsStream("/META-INF/maven/vavi/vavi-sound-sc55/pom.properties")) {
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

    public static final String version;

    /** Roland */
    public final static int MANUFACTURER_ID = 0x41;

    @Override
    public MidiDevice.Info[] getDeviceInfo() {
        return new MidiDevice.Info[] {
                vavi.sound.midi.sc55.pj.Sc55Synthesizer.info,
                vavi.sound.midi.sc55.jep454.Sc55Synthesizer.info
        };
    }

    @Override
    public MidiDevice getDevice(MidiDevice.Info info)
        throws IllegalArgumentException {

        if (info == vavi.sound.midi.sc55.pj.Sc55Synthesizer.info) {
logger.log(Level.DEBUG, "★1 info: " + info);
            Synthesizer synthesizer = new vavi.sound.midi.sc55.pj.Sc55Synthesizer();
            return synthesizer;
        } if (info == vavi.sound.midi.sc55.jep454.Sc55Synthesizer.info) {
logger.log(Level.DEBUG, "★1 info: " + info);
            Synthesizer synthesizer = new vavi.sound.midi.sc55.jep454.Sc55Synthesizer();
            return synthesizer;
        } else {
logger.log(Level.DEBUG, "★1 here: " + info);
            throw new IllegalArgumentException();
        }
    }
}
