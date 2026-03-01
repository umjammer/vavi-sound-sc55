/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.midi.sc55;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.Instrument;
import javax.sound.midi.Patch;
import javax.sound.midi.Soundbank;
import javax.sound.midi.SoundbankResource;
import com.sun.media.sound.ModelPatch;
import com.sun.media.sound.SimpleInstrument;

import vavi.sound.midi.sc55.pj.Sc55Synthesizer;

import static java.lang.System.getLogger;


/**
 * Sc55Soundbank.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2025/05/07 umjammer initial version <br>
 */
public class Sc55Soundbank implements Soundbank {

    private static final Logger logger = getLogger(Sc55Soundbank.class.getName());

    /** */
    private final List<Instrument> instruments = new ArrayList<>();

    public Sc55Soundbank() {
    }

    @Override
    public String getName() {
        return "SC55Soundbank";
    }

    @Override
    public String getVersion() {
        return Sc55Synthesizer.info.getVersion();
    }

    @Override
    public String getVendor() {
        return Sc55Synthesizer.info.getVendor();
    }

    @Override
    public String getDescription() {
        return "Soundbank for SC55";
    }

    @Override
    public SoundbankResource[] getResources() {
        return getInstruments();
    }

    @Override
    public Instrument[] getInstruments() {
        return instruments.toArray(Instrument[]::new);
    }

    @Override
    public Instrument getInstrument(Patch patch) {
        for (Instrument instrument : instruments) {
            if (instrument.getPatch().getProgram() == patch.getProgram() &&
                    instrument.getPatch().getBank() == patch.getBank()) {
                return instrument;
            }
        }
logger.log(Level.DEBUG, "no instrument for: " + patch);
        return null;
    }

    /** */
    public static class Sc55Instrument extends SimpleInstrument {
        final Object data;
        protected Sc55Instrument(int bank, int program, Object data) {
            setPatch(new ModelPatch(bank, program, false));
            this.name = bank + "." + program;
            this.data = data;
        }

        @Override
        public Class<?> getDataClass() {
            return Object.class;
        }

        @Override
        public Object getData() {
            return data;
        }
    }
}
