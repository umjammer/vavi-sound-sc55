/*
 * Copyright (C) 2021, 2024 nukeykt
 *
 *  Redistribution and use of this code or any derivative works are permitted
 *  provided that the following conditions are met:
 *
 *   - Redistributions may not be sold, nor may they be used in a commercial
 *     product or activity.
 *
 *   - Redistributions that are modified from the original source must include the
 *     complete source code, including the source code for all components used by a
 *     binary built from the modified sources. However, as a special exception, the
 *     source code distributed need not include anything that is normally distributed
 *     (in either source or binary form) with the major components (compiler, kernel,
 *     and so on) of the operating system on which the executable runs, unless that
 *     component itself accompanies the executable.
 *
 *   - Redistributions must reproduce the above copyright notice, this list of
 *     conditions and the following disclaimer in the documentation and/or other
 *     materials provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */

package vavi.sound.sc55;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.sound.midi.Receiver;

import static java.lang.System.getLogger;


class Midi {

    private static final Logger logger = getLogger(Midi.class.getName());

    private final Mcu mcu;

    Receiver s_midi_in;

    Midi(Mcu mcu) {
        this.mcu = mcu;
    }

    // callback
    private void MidiOnReceive(double a, byte[] message, byte[] x) {

        for (byte b : message)
            mcu.MCU_PostUART(b);
    }

    int MIDI_Init(int port) {
        if (s_midi_in != null) {
            logger.log(Level.DEBUG, "MIDI already running");
            return 0; // Already running
        }

//        s_midi_in = new RtMidiIn(RtMidi.UNSPECIFIED, "Nuked SC55", 1024);
//        s_midi_in.ignoreTypes(false, false, false); // SysEx disabled by default
//        s_midi_in.setCallback(MidiOnReceive, nullptr); // FIXME: (local bug) Fix the linking error
//        s_midi_in.setErrorCallback(MidiOnError, nullptr);
//
//        unsigned count = Midis_midi_in.getPortCount();
//
//        if (count == 0) {
//            logger.log(Level.TRACE, "No midi input");
//            delete s_midi_in;
//            s_midi_in = null;
//            return 0;
//        }
//
//        if (port < 0 || port >= count) {
//            logger.log(Level.TRACE, "Out of range midi port is requested. Defaulting to port 0");
//            port = 0;
//        }
//
//        s_midi_in.openPort(port, "Nuked SC55");

        return 1;
    }

    void MIDI_Quit() {
        if (s_midi_in != null) {
            s_midi_in.close();
            s_midi_in = null;
        }
    }
}
