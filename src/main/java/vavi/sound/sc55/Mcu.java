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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import static java.lang.System.getLogger;
import static vavi.sound.sc55.Mcu.AnalogLevel.ANALOG_LEVEL_BATTERY;
import static vavi.sound.sc55.Mcu.AnalogLevel.ANALOG_LEVEL_SW_0;
import static vavi.sound.sc55.Mcu.AnalogLevel.ANALOG_LEVEL_SW_1;
import static vavi.sound.sc55.Mcu.AnalogLevel.ANALOG_LEVEL_SW_2;
import static vavi.sound.sc55.Mcu.AnalogLevel.ANALOG_LEVEL_SW_3;
import static vavi.sound.sc55.Mcu.Dev.DEV_ADDRAH;
import static vavi.sound.sc55.Mcu.Dev.DEV_ADDRAL;
import static vavi.sound.sc55.Mcu.Dev.DEV_RAME;
import static vavi.sound.sc55.Mcu.Dev.DEV_SCR;
import static vavi.sound.sc55.Mcu.Dev.DEV_SSR;
import static vavi.sound.sc55.Mcu.RomSet.ROM_SET_CM300;
import static vavi.sound.sc55.Mcu.RomSet.ROM_SET_COUNT;
import static vavi.sound.sc55.Mcu.RomSet.ROM_SET_JV880;
import static vavi.sound.sc55.Mcu.RomSet.ROM_SET_RLP3237;
import static vavi.sound.sc55.Mcu.RomSet.ROM_SET_SC155;
import static vavi.sound.sc55.Mcu.RomSet.ROM_SET_SC155MK2;
import static vavi.sound.sc55.Mcu.RomSet.ROM_SET_SCB55;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_RESET;
import static vavi.sound.sc55.McuInterrupt.Exception.EXCEPTION_SOURCE_ADDRESS_ERROR;
import static vavi.sound.sc55.McuInterrupt.Exception.EXCEPTION_SOURCE_TRACE;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_ANALOG;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_IRQ0;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_IRQ1;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_MAX;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_UART_RX;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_UART_TX;


/**
 * system property
 * <li>{@code sc55.dir} ... rom dir</li>
 */
public class Mcu {

    private static final Logger logger = getLogger(Mcu.class.getName());

    enum Dev {
        DEV_P1DDR(0x00),
        DEV_P5DDR(0x08),
        DEV_P6DDR(0x09),
        DEV_P7DDR(0x0c),
        DEV_P7DR(0x0e),
        DEV_FRT1_TCR(0x10),
        DEV_FRT1_TCSR(0x11),
        DEV_FRT1_FRCH(0x12),
        DEV_FRT1_FRCL(0x13),
        DEV_FRT1_OCRAH(0x14),
        DEV_FRT1_OCRAL(0x15),
        DEV_FRT2_TCR(0x20),
        DEV_FRT2_TCSR(0x21),
        DEV_FRT2_FRCH(0x22),
        DEV_FRT2_FRCL(0x23),
        DEV_FRT2_OCRAH(0x24),
        DEV_FRT2_OCRAL(0x25),
        DEV_FRT3_TCR(0x30),
        DEV_FRT3_TCSR(0x31),
        DEV_FRT3_FRCH(0x32),
        DEV_FRT3_FRCL(0x33),
        DEV_FRT3_OCRAH(0x34),
        DEV_FRT3_OCRAL(0x35),
        DEV_PWM1_TCR(0x40),
        DEV_PWM1_DTR(0x41),
        DEV_PWM2_TCR(0x44),
        DEV_PWM2_DTR(0x45),
        DEV_PWM3_TCR(0x48),
        DEV_PWM3_DTR(0x49),
        DEV_TMR_TCR(0x50),
        DEV_TMR_TCSR(0x51),
        DEV_TMR_TCORA(0x52),
        DEV_TMR_TCORB(0x53),
        DEV_TMR_TCNT(0x54),
        DEV_SMR(0x58),
        DEV_BRR(0x59),
        DEV_SCR(0x5a),
        DEV_TDR(0x5b),
        DEV_SSR(0x5c),
        DEV_RDR(0x5d),
        DEV_ADDRAH(0x60),
        DEV_ADDRAL(0x61),
        DEV_ADDRBH(0x62),
        DEV_ADDRBL(0x63),
        DEV_ADDRCH(0x64),
        DEV_ADDRCL(0x65),
        DEV_ADDRDH(0x66),
        DEV_ADDRDL(0x67),
        DEV_ADCSR(0x68),
        DEV_IPRA(0x70),
        DEV_IPRB(0x71),
        DEV_IPRC(0x72),
        DEV_IPRD(0x73),
        DEV_DTEA(0x74),
        DEV_DTEB(0x75),
        DEV_DTEC(0x76),
        DEV_DTED(0x77),
        DEV_WCR(0x78),
        DEV_RAME(0x79),
        DEV_P1CR(0x7c),
        DEV_P9DDR(0x7e),
        DEV_P9DR(0x7f),
        DEV_UNDEFINED(-1);
        final int v;

        Dev(int v) {
            this.v = v;
        }

        public static Dev valueOf(int address) {
            for (Dev dev : Dev.values())
                if (dev.v == address)
                    return dev;
            return DEV_UNDEFINED;
        }
    }

    static final short sr_mask = (short) 0x870f;

    enum Status {
        STATUS_T(0x8000),
        STATUS_N(0x08),
        STATUS_Z(0x04),
        STATUS_V(0x02),
        STATUS_C(0x01),
        STATUS_INT_MASK(0x700);
        final int v;

        Status(int v) {
            this.v = v;
        }
    }

    enum Vector {
        VECTOR_RESET, // 0
        VECTOR_RESERVED1, // UNUSED
        VECTOR_INVALID_INSTRUCTION,
        VECTOR_DIVZERO,
        VECTOR_TRAP,
        VECTOR_RESERVED2, // UNUSED, 5
        VECTOR_RESERVED3, // UNUSED
        VECTOR_RESERVED4, // UNUSED
        VECTOR_ADDRESS_ERROR,
        VECTOR_TRACE,
        VECTOR_RESERVED5, // UNUSED, 10
        VECTOR_NMI,
        VECTOR_RESERVED6, // UNUSED
        VECTOR_RESERVED7, // UNUSED
        VECTOR_RESERVED8, // UNUSED
        VECTOR_RESERVED9, // UNUSED, 15
        VECTOR_TRAPA_0,
        VECTOR_TRAPA_1,
        VECTOR_TRAPA_2,
        VECTOR_TRAPA_3,
        VECTOR_TRAPA_4, // 20
        VECTOR_TRAPA_5,
        VECTOR_TRAPA_6,
        VECTOR_TRAPA_7,
        VECTOR_TRAPA_8,
        VECTOR_TRAPA_9, // 25
        VECTOR_TRAPA_A,
        VECTOR_TRAPA_B,
        VECTOR_TRAPA_C,
        VECTOR_TRAPA_D,
        VECTOR_TRAPA_E, // 30
        VECTOR_TRAPA_F,
        VECTOR_IRQ0,
        VECTOR_IRQ1,
        VECTOR_INTERNAL_INTERRUPT_88, // UNUSED
        VECTOR_INTERNAL_INTERRUPT_8C, // UNUSED, 35
        VECTOR_INTERNAL_INTERRUPT_90, // FRT1 ICI
        VECTOR_INTERNAL_INTERRUPT_94, // FRT1 OCIA
        VECTOR_INTERNAL_INTERRUPT_98, // FRT1 OCIB
        VECTOR_INTERNAL_INTERRUPT_9C, // FRT1 FOVI
        VECTOR_INTERNAL_INTERRUPT_A0, // FRT2 ICI, 40
        VECTOR_INTERNAL_INTERRUPT_A4, // FRT2 OCIA
        VECTOR_INTERNAL_INTERRUPT_A8, // FRT2 OCIB
        VECTOR_INTERNAL_INTERRUPT_AC, // FRT2 FOVI
        VECTOR_INTERNAL_INTERRUPT_B0, // FRT3 ICI
        VECTOR_INTERNAL_INTERRUPT_B4, // FRT3 OCIA, 45
        VECTOR_INTERNAL_INTERRUPT_B8, // FRT3 OCIB
        VECTOR_INTERNAL_INTERRUPT_BC, // FRT3 FOVI
        VECTOR_INTERNAL_INTERRUPT_C0, // CMIA
        VECTOR_INTERNAL_INTERRUPT_C4, // CMIB
        VECTOR_INTERNAL_INTERRUPT_C8, // OVI, 50
        VECTOR_INTERNAL_INTERRUPT_CC, // UNUSED
        VECTOR_INTERNAL_INTERRUPT_D0, // ERI
        VECTOR_INTERNAL_INTERRUPT_D4, // RXI
        VECTOR_INTERNAL_INTERRUPT_D8, // TXI
        VECTOR_INTERNAL_INTERRUPT_DC, // UNUSED, 55
        VECTOR_INTERNAL_INTERRUPT_E0 // ADI
    }

//    static class mcu_t {

    short[] r = new short[8];
    short pc;
    short sr;
    byte cp, dp, ep, tp, br;
    boolean sleep;
    boolean ex_ignore;
    int exception_pending;
    boolean[] interrupt_pending = new boolean[INTERRUPT_SOURCE_MAX.ordinal()];
    boolean[] trapa_pending = new boolean[16];
    long cycles;
//    }

    private final McuOpcodes opcodes;
    private final McuTimer timer;
    final McuInterrupt interrupt;
    private final Lcd lcd;
    private final Pcm pcm;
    private final SubMcu sm;
    private final Midi midi;

    public Mcu() {
        opcodes = new McuOpcodes(this);
        interrupt = new McuInterrupt(this);
        timer = new McuTimer(this);
        lcd = new Lcd(this);
        pcm = new Pcm(this);
        sm = new SubMcu(this);
        midi = new Midi(this);
    }

    int MCU_GetAddress(byte page, short address) {
        return ((page & 0xff) << 16) + (address & 0xffff);
    }

    private byte MCU_ReadCode() {
        return MCU_Read(MCU_GetAddress(this.cp, this.pc));
    }

    byte MCU_ReadCodeAdvance() {
        byte ret = MCU_ReadCode();
        this.pc++;
        return ret;
    }

    private void MCU_SetRegisterByte(byte reg, byte val) {
        this.r[reg & 0xff] = (short) (val & 0xff);
    }

    int MCU_GetVectorAddress(int vector) {
        return MCU_Read32(vector * 4);
    }

    int MCU_GetPageForRegister(int reg) {
        if (reg >= 6)
            return this.tp & 0xff;
        else if (reg >= 4)
            return this.ep & 0xff;
        return this.dp & 0xff;
    }

    void MCU_ControlRegisterWrite(int reg, int siz, int data) {
        if (siz != 0) {
            if (reg == 0) {
                this.sr = (short) data;
                this.sr &= sr_mask;
            } else if (reg == 5) { // FIXME: undocumented
                this.dp = (byte) (data & 0xff);
            } else if (reg == 4) { // FIXME: undocumented
                this.ep = (byte) (data & 0xff);
            } else if (reg == 3) { // FIXME: undocumented
                this.br = (byte) (data & 0xff);
            } else {
                MCU_ErrorTrap();
            }
        } else {
            if (reg == 1) {
                this.sr &= ~0xff;
                this.sr |= (short) (data & 0xff);
                this.sr &= sr_mask;
            } else if (reg == 3) {
                this.br = (byte) data;
            } else if (reg == 4) {
                this.ep = (byte) data;
            } else if (reg == 5) {
                this.dp = (byte) data;
            } else if (reg == 7) {
                this.tp = (byte) data;
            } else {
                MCU_ErrorTrap();
            }
        }
    }

    int MCU_ControlRegisterRead(int reg, int siz) {
        int ret = 0;
        if (siz != 0) {
            if (reg == 0) {
                ret = this.sr & sr_mask;
            } else if (reg == 5) { // FIXME: undocumented
                ret = (this.dp & 0xff) | ((this.dp & 0xff) << 8);
            } else if (reg == 4) { // FIXME: undocumented
                ret = (this.ep & 0xff) | ((this.ep & 0xff) << 8);
            } else if (reg == 3) { // FIXME: undocumented
                ret = (this.br & 0xff) | ((this.br & 0xff) << 8);
            } else {
                MCU_ErrorTrap();
            }
            ret &= 0xffff;
        } else {
            if (reg == 1) {
                ret = this.sr & sr_mask;
            } else if (reg == 3) {
                ret = this.br & 0xff;
            } else if (reg == 4) {
                ret = this.ep & 0xff;
            } else if (reg == 5) {
                ret = this.dp & 0xff;
            } else if (reg == 7) {
                ret = this.tp & 0xff;
            } else {
                MCU_ErrorTrap();
            }
            ret &= 0xff;
        }
        return ret;
    }

    void MCU_SetStatus(boolean condition, int mask) {
        if (condition)
            this.sr |= (short) mask;
        else
            this.sr &= (short) ~mask;
    }

    void MCU_PushStack(short data) {
        if ((this.r[7] & 1) != 0)
            interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
        this.r[7] -= 2;
        MCU_Write16(this.r[7] & 0xffff, data);
    }

    short MCU_PopStack() {
        short ret;
        if ((this.r[7] & 1) != 0)
            interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
        ret = MCU_Read16(this.r[7] & 0xffff);
        this.r[7] += 2;
        return ret;
    }

    enum McuButton {
        // SC55
        MCU_BUTTON_POWER(0),
        MCU_BUTTON_INST_L(3),
        MCU_BUTTON_INST_R(4),
        MCU_BUTTON_INST_MUTE(5),
        MCU_BUTTON_INST_ALL(6),

        MCU_BUTTON_MIDI_CH_L(8),
        MCU_BUTTON_MIDI_CH_R(9),
        MCU_BUTTON_CHORUS_L(10),
        MCU_BUTTON_CHORUS_R(11),
        MCU_BUTTON_PAN_L(12),
        MCU_BUTTON_PAN_R(13),
        MCU_BUTTON_PART_R(14),

        MCU_BUTTON_KEY_SHIFT_L(16),
        MCU_BUTTON_KEY_SHIFT_R(17),
        MCU_BUTTON_REVERB_L(18),
        MCU_BUTTON_REVERB_R(19),
        MCU_BUTTON_LEVEL_L(20),
        MCU_BUTTON_LEVEL_R(21),
        MCU_BUTTON_PART_L(22),

        // SC155 extra buttons
        MCU_BUTTON_USER(1),
        MCU_BUTTON_PART_SEL(2),
        MCU_BUTTON_INST_CALL(7),
        MCU_BUTTON_PAN(15),
        MCU_BUTTON_LEVEL(23),
        MCU_BUTTON_PART1(24),
        MCU_BUTTON_PART2(25),
        MCU_BUTTON_PART3(26),
        MCU_BUTTON_PART4(27),
        MCU_BUTTON_PART5(28),
        MCU_BUTTON_PART6(29),
        MCU_BUTTON_PART7(30),
        MCU_BUTTON_PART8(31),

        // JV880
        MCU_BUTTON_CURSOR_L(0),
        MCU_BUTTON_CURSOR_R(1),
        MCU_BUTTON_TONE_SELECT(2),
        MCU_BUTTON_MUTE(3),
        MCU_BUTTON_DATA(4),
        MCU_BUTTON_MONITOR(5),
        MCU_BUTTON_COMPARE(6),
        MCU_BUTTON_ENTER(7),
        MCU_BUTTON_UTILITY(8),
        MCU_BUTTON_PREVIEW(9),
        MCU_BUTTON_PATCH_PERFORM(10),
        MCU_BUTTON_EDIT(11),
        MCU_BUTTON_SYSTEM(12),
        MCU_BUTTON_RHYTHM(13);
        final int v;

        McuButton(int v) {
            this.v = v;
        }
    }

    enum RomSet {
        ROM_SET_MK2,
        ROM_SET_ST,
        ROM_SET_MK1,
        ROM_SET_CM300,
        ROM_SET_JV880,
        ROM_SET_SCB55,
        ROM_SET_RLP3237,
        ROM_SET_SC155,
        ROM_SET_SC155MK2,
        ROM_SET_COUNT
    }

    static final int uart_buffer_size = 8192;

    final String[] rs_name = {
            "SC-55mk2",
            "SC-55st",
            "SC-55mk1",
            "CM-300/SCC-1",
            "JV-880",
            "SCB-55",
            "RLP-3237",
            "SC-155",
            "SC-155mk2"
    };

    private static final int ROM_SET_N_FILES = 6;

    private final String[][] roms = {
            {
                    "rom1.bin",
                    "rom2.bin",
                    "waverom1.bin",
                    "waverom2.bin",
                    "rom_sm.bin",
                    "",
            },
            {
                    "rom1.bin",
                    "rom2_st.bin",
                    "waverom1.bin",
                    "waverom2.bin",
                    "rom_sm.bin",
                    "",
            },
            {
                    "sc55_rom1.bin",
                    "sc55_rom2.bin",
                    "sc55_waverom1.bin",
                    "sc55_waverom2.bin",
                    "sc55_waverom3.bin",
                    "",
            },
            {
                    "cm300_rom1.bin",
                    "cm300_rom2.bin",
                    "cm300_waverom1.bin",
                    "cm300_waverom2.bin",
                    "cm300_waverom3.bin",
                    "",
            },
            {
                    "jv880_rom1.bin",
                    "jv880_rom2.bin",
                    "jv880_waverom1.bin",
                    "jv880_waverom2.bin",
                    "jv880_waverom_expansion.bin",
                    "jv880_waverom_pcmcard.bin",
            },
            {
                    "scb55_rom1.bin",
                    "scb55_rom2.bin",
                    "scb55_waverom1.bin",
                    "scb55_waverom2.bin",
                    "",
                    "",
            },
            {
                    "rlp3237_rom1.bin",
                    "rlp3237_rom2.bin",
                    "rlp3237_waverom1.bin",
                    "",
                    "",
                    "",
            },
            {
                    "sc155_rom1.bin",
                    "sc155_rom2.bin",
                    "sc155_waverom1.bin",
                    "sc155_waverom2.bin",
                    "sc155_waverom3.bin",
                    "",
            },
            {
                    "rom1.bin",
                    "rom2.bin",
                    "waverom1.bin",
                    "waverom2.bin",
                    "rom_sm.bin",
                    "",
            }
    };

    int romset = RomSet.ROM_SET_MK2.ordinal();

    private static final int ROM1_SIZE = 0x8000;
    private static final int ROM2_SIZE = 0x80000;
    private final int RAM_SIZE = 0x400;
    private final int SRAM_SIZE = 0x8000;
    private final int NVRAM_SIZE = 0x8000; // JV880 only
    private final int CARDRAM_SIZE = 0x8000; // JV880 only
    private static final int ROMSM_SIZE = 0x1000;

    private int audio_buffer_size;
    private int audio_page_size;
    private short[] sample_buffer;

    public AtomicInteger sample_read_ptr = new AtomicInteger(0);
    public AtomicInteger sample_write_ptr = new AtomicInteger(0);

    private SourceDataLine audioOut;

    // Audio capture callback for testing
    public interface AudioCaptureCallback {
        void onSamples(short left, short right);
    }
    private AudioCaptureCallback audioCaptureCallback;
    public void setAudioCaptureCallback(AudioCaptureCallback callback) {
        this.audioCaptureCallback = callback;
    }

    void MCU_ErrorTrap() {
        logger.log(Level.DEBUG, "cp: %2x pc: %4x".formatted(this.cp & 0xff, this.pc & 0xffff), new Exception("MCU_ErrorTrap"));
    }

    boolean mcu_mk1 = false; // 0 - SC-55mkII, SC-55ST. 1 - SC-55, CM-300/SCC-1
    boolean mcu_cm300 = false; // 0 - SC-55, 1 - CM-300/SCC-1
    boolean mcu_st = false; // 0 - SC-55mk2, 1 - SC-55ST
    boolean mcu_jv880 = false; // 0 - SC-55, 1 - JV880
    boolean mcu_scb55 = false; // 0 - sub mcu (e.g SC-55mk2), 1 - no sub mcu (e.g SCB-55)
    boolean mcu_sc155 = false; // 0 - SC-55(MK2), 1 - SC-155(MK2)

    private boolean[] ga_int = new boolean[8];
    private int ga_int_enable = 0;
    private int ga_int_trigger = 0;
    private int ga_lcd_counter = 0;

    byte[] dev_register = new byte[0x80];

    private short[] ad_val = new short[4];
    private byte ad_nibble = 0x00;
    private byte sw_pos = 3;
    private byte io_sd = 0x00;

    AtomicInteger mcu_button_pressed = new AtomicInteger();

    byte RCU_Read() {
        return 0;
    }

    enum AnalogLevel {
        ANALOG_LEVEL_RCU_LOW(0),
        ANALOG_LEVEL_RCU_HIGH(0),
        ANALOG_LEVEL_SW_0(0),
        ANALOG_LEVEL_SW_1(0x155),
        ANALOG_LEVEL_SW_2(0x2aa),
        ANALOG_LEVEL_SW_3(0x3ff),
        ANALOG_LEVEL_BATTERY(0x2a0);
        final int v;

        AnalogLevel(int v) {
            this.v = v;
        }
    }

    short MCU_SC155Sliders(int index) {
        // 0 - 1/9
        // 1 - 2/10
        // 2 - 3/11
        // 3 - 4/12
        // 4 - 5/13
        // 5 - 6/14
        // 6 - 7/15
        // 7 - 8/16
        // 8 - ALL
        return 0x0;
    }

    short MCU_AnalogReadPin(int pin) {
        if (mcu_cm300)
            return 0;
        if (mcu_jv880) {
            if (pin == 1)
                return (short) ANALOG_LEVEL_BATTERY.v;
            return 0x3ff;
        }
READ_RCU:
        {
            if (mcu_mk1) {
                if (mcu_sc155 && (dev_register[Dev.DEV_P9DR.v] & 1) != 0) {
                    return MCU_SC155Sliders(pin);
                }
                if (pin == 7) {
                    if (mcu_sc155 && (dev_register[Dev.DEV_P9DR.v] & 2) != 0)
                        return MCU_SC155Sliders(8);
                    else
                        return (short) ANALOG_LEVEL_BATTERY.v;
                } else
                    break READ_RCU;
            } else {
                if (mcu_sc155 && (io_sd & 16) != 0) {
                    return MCU_SC155Sliders(pin);
                }
                if (pin == 7) {
                    if (mcu_mk1)
                        return (short) ANALOG_LEVEL_BATTERY.v;
                    switch ((io_sd >>> 2) & 3) {
                        case 0: // Battery voltage
                            return (short) ANALOG_LEVEL_BATTERY.v;
                        case 1: // NC
                            if (mcu_sc155)
                                return MCU_SC155Sliders(8);
                            return 0;
                        case 2: // SW
                            switch (sw_pos) {
                                case 0:
                                default:
                                    return (short) ANALOG_LEVEL_SW_0.v;
                                case 1:
                                    return (short) ANALOG_LEVEL_SW_1.v;
                                case 2:
                                    return (short) ANALOG_LEVEL_SW_2.v;
                                case 3:
                                    return (short) ANALOG_LEVEL_SW_3.v;
                            }
                        case 3: // RCU
                            break READ_RCU;
                    }
                } else
                    break READ_RCU;
            }
        }
        byte rcu = RCU_Read();
        if ((rcu & (1 << pin)) != 0)
            return (short) AnalogLevel.ANALOG_LEVEL_RCU_HIGH.v;
        else
            return (short) AnalogLevel.ANALOG_LEVEL_RCU_LOW.v;
    }

    void MCU_AnalogSample(int channel) {
        int value = MCU_AnalogReadPin(channel) & 0xffff;
        int dest = (channel << 1) & 6;
        dev_register[DEV_ADDRAH.v + dest] = (byte) (value >>> 2);
        dev_register[DEV_ADDRAL.v + dest] = (byte) ((value << 6) & 0xc0);
    }

    boolean adf_rd = false;

    long analog_end_time;

    int ssr_rd = 0;

    AtomicInteger uart_write_ptr = new AtomicInteger();
    AtomicInteger uart_read_ptr = new AtomicInteger();
    byte[] uart_buffer = new byte[uart_buffer_size];

    private byte uart_rx_byte;
    private long uart_rx_delay;
    private long uart_tx_delay;

    void MCU_DeviceWrite(int address, byte data) {
        address &= 0x7f;
        if (address >= 0x10 && address < 0x40) {
            timer.TIMER_Write(address, data);
            return;
        }
        if (address >= 0x50 && address < 0x55) {
            timer.TIMER2_Write(address, data);
            return;
        }
        switch (Dev.valueOf(address)) {
            case DEV_P1DDR: // P1DDR
                break;
            case DEV_P5DDR:
                break;
            case DEV_P6DDR:
                break;
            case DEV_P7DDR:
                break;
            case DEV_SCR:
                break;
            case DEV_WCR:
                break;
            case DEV_P9DDR:
                break;
            case DEV_RAME: // RAME
                break;
            case DEV_P1CR: // P1CR
                break;
            case DEV_DTEA:
                break;
            case DEV_DTEB:
                break;
            case DEV_DTEC:
                break;
            case DEV_DTED:
                break;
            case DEV_SMR:
                break;
            case DEV_BRR:
                break;
            case DEV_IPRA:
                break;
            case DEV_IPRB:
                break;
            case DEV_IPRC:
                break;
            case DEV_IPRD:
                break;
            case DEV_PWM1_DTR:
                break;
            case DEV_PWM1_TCR:
                break;
            case DEV_PWM2_DTR:
                break;
            case DEV_PWM2_TCR:
                break;
            case DEV_PWM3_DTR:
                break;
            case DEV_PWM3_TCR:
                break;
            case DEV_P7DR:
                break;
            case DEV_TMR_TCNT:
                break;
            case DEV_TMR_TCR:
                break;
            case DEV_TMR_TCSR:
                break;
            case DEV_TMR_TCORA:
                break;
            case DEV_TDR:
                break;
            case DEV_ADCSR: {
                dev_register[address] &= ~0x7f;
                dev_register[address] |= (byte) (data & 0x7f);
                if ((data & 0x80) == 0 && adf_rd) {
                    dev_register[address] &= (byte) ~0x80;
                    interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_ANALOG.ordinal(), false);
                }
                if ((data & 0x40) == 0)
                    interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_ANALOG.ordinal(), false);
                return;
            }
            case DEV_SSR: {
                if ((data & 0x80) == 0 && (ssr_rd & 0x80) != 0) {
                    dev_register[address] &= (byte) ~0x80;
                    uart_tx_delay = this.cycles + 3000;
                    interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_UART_TX.ordinal(), false);
                }
                if ((data & 0x40) == 0 && (ssr_rd & 0x40) != 0) {
                    uart_rx_delay = this.cycles + 3000;
                    dev_register[address] &= ~0x40;
                    interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_UART_RX.ordinal(), false);
                }
                if ((data & 0x20) == 0 && (ssr_rd & 0x20) != 0) {
                    dev_register[address] &= ~0x20;
                }
                if ((data & 0x10) == 0 && (ssr_rd & 0x10) != 0) {
                    dev_register[address] &= ~0x10;
                }
                break;
            }
            default:
                address += 0;
                break;
        }
        dev_register[address] = data;
    }

    byte MCU_DeviceRead(int address) {
        address &= 0x7f;
        if (address >= 0x10 && address < 0x40) {
            return timer.TIMER_Read(address);
        }
        if (address >= 0x50 && address < 0x55) {
            return timer.TIMER_Read2(address);
        }
        switch (Dev.valueOf(address)) {
            case DEV_ADDRAH:
            case DEV_ADDRAL:
            case DEV_ADDRBH:
            case DEV_ADDRBL:
            case DEV_ADDRCH:
            case DEV_ADDRCL:
            case DEV_ADDRDH:
            case DEV_ADDRDL:
                return dev_register[address];
            case DEV_ADCSR:
                adf_rd = (dev_register[address] & 0x80) != 0;
                return dev_register[address];
            case DEV_SSR:
                ssr_rd = dev_register[address];
                return dev_register[address];
            case DEV_RDR:
                return uart_rx_byte;
            case DEV_P1DDR:
                return (byte) 0xff;
            case DEV_P7DR: {
                if (!mcu_jv880) return (byte) 0xff;

                byte data = (byte) 0xff;
                int button_pressed = mcu_button_pressed.get();

                if (io_sd == (byte) 0b1111_1011)
                    data &= (byte) (((button_pressed >>> 0) & 0b1_1111) ^ 0xff);
                if (io_sd == (byte) 0b1111_0111)
                    data &= (byte) (((button_pressed >>> 5) & 0b1_1111) ^ 0xff);
                if (io_sd == (byte) 0b1110_1111)
                    data &= (byte) (((button_pressed >>> 10) & 0b1111) ^ 0xff);

                data |= (byte) 0b1000_0000;
                return data;
            }
            case DEV_P9DR: {
                int cfg = 0;
                if (!mcu_mk1)
                    cfg = mcu_sc155 ? 0 : 2; // bit 1: 0 - SC-155mk2 (???), 1 - SC-55mk2

                int dir = dev_register[Dev.DEV_P9DDR.v] & 0xff;

                int val = cfg & (dir ^ 0xff);
                val |= (dev_register[Dev.DEV_P9DR.v] & 0xff) & dir;
                return (byte) val;
            }
            case DEV_SCR:
            case DEV_TDR:
            case DEV_SMR:
                return dev_register[address];
            case DEV_IPRC:
            case DEV_IPRD:
            case DEV_DTEC:
            case DEV_DTED:
            case DEV_FRT2_TCSR:
            case DEV_FRT1_TCSR:
            case DEV_FRT1_TCR:
            case DEV_FRT1_FRCH:
            case DEV_FRT1_FRCL:
            case DEV_FRT3_TCSR:
            case DEV_FRT3_OCRAH:
            case DEV_FRT3_OCRAL:
                return dev_register[address];
        }
        return dev_register[address];
    }

    void MCU_DeviceReset() {
        // dev_register[0x00] = 0x03;
        // dev_register[0x7c] = 0x87;
        dev_register[Dev.DEV_RAME.v] = (byte) 0x80;
        dev_register[DEV_SSR.v] = (byte) 0x80;
    }

    void MCU_UpdateAnalog(long cycles) {
        int ctrl = dev_register[Dev.DEV_ADCSR.v] & 0xff;
        boolean isscan = (ctrl & 16) != 0;

        if ((ctrl & 0x20) != 0) {
            if (analog_end_time == 0)
                analog_end_time = cycles + 200;
            else if (analog_end_time < cycles) {
                if (isscan) {
                    int base = ctrl & 4;
                    for (int i = 0; i <= (ctrl & 3); i++)
                        MCU_AnalogSample(base + i);
                    analog_end_time = cycles + 200;
                } else {
                    MCU_AnalogSample(ctrl & 7);
                    dev_register[Dev.DEV_ADCSR.v] &= ~0x20;
                    analog_end_time = 0;
                }
                dev_register[Dev.DEV_ADCSR.v] |= (byte) 0x80;
                if ((ctrl & 0x40) != 0)
                    interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_ANALOG.ordinal(), true);
            }
        } else
            analog_end_time = 0;
    }

    byte[] rom1;
    byte[] rom2;
    byte[] ram = new byte[RAM_SIZE];
    byte[] sram = new byte[SRAM_SIZE];
    byte[] nvram = new byte[NVRAM_SIZE];
    byte[] cardram = new byte[CARDRAM_SIZE];

    int rom2_mask = ROM2_SIZE - 1;

    byte MCU_Read(int address) {
        int address_rom = address & 0x3_ffff;
        if ((address & 0x8_0000) != 0 && !mcu_jv880)
            address_rom |= 0x4_0000;
        byte page = (byte) ((address >>> 16) & 0xf);
//if (CC == 800) { System.err.printf("address: %08x, address_rom: %04x, rom2_mask: %04x, page: %02x%n", address, address_rom, rom2_mask, page); }
        address &= 0xffff;
        byte ret = (byte) 0xff;
        switch (page) {
            case 0:
                if ((address & 0x8000) == 0)
                    ret = rom1[address & 0x7fff];
                else {
                    if (!mcu_mk1) {
                        int base = mcu_jv880 ? 0xf000 : 0xe000;
                        if (address >= base && address < (base | 0x400)) {
                            ret = pcm.PCM_Read(address & 0x3f);
                        } else if (!mcu_scb55 && address >= 0xec00 && address < 0xf000) {
                            ret = sm.SM_SysRead(address & 0xff);
                        } else if (address >= 0xff80) {
                            ret = MCU_DeviceRead(address & 0x7f);
                        } else if (address >= 0xfb80 && address < 0xff80
                                && (dev_register[DEV_RAME.v] & 0x80) != 0)
                            ret = ram[(address - 0xfb80) & 0x3ff];
                        else if (address >= 0x8000 && address < 0xe000) {
                            ret = sram[address & 0x7fff];
                        } else if (address == (base | 0x402)) {
                            ret = (byte) ga_int_trigger;
                            ga_int_trigger = 0;
                            interrupt.MCU_Interrupt_SetRequest(mcu_jv880 ? INTERRUPT_SOURCE_IRQ0.ordinal() : INTERRUPT_SOURCE_IRQ1.ordinal(), false);
                        } else {
                            logger.log(Level.TRACE, "Unknown read %x".formatted(address));
                            ret = (byte) 0xff;
                        }
                        //
                        // e402:2-0 irq source
                        //
                    } else {
                        if (address >= 0xe000 && address < 0xe040) {
                            ret = pcm.PCM_Read(address & 0x3f);
                        } else if (address >= 0xff80) {
                            ret = MCU_DeviceRead(address & 0x7f);
                        } else if (address >= 0xfb80 && address < 0xff80
                                && (dev_register[DEV_RAME.v] & 0x80) != 0) {
                            ret = ram[(address - 0xfb80) & 0x3ff];
                        } else if (address >= 0x8000 && address < 0xe000) {
                            ret = sram[address & 0x7fff];
                        } else if (address >= 0xf000 && address < 0xf100) {
                            io_sd = (byte) (address & 0xff);

                            if (mcu_cm300)
                                return (byte) 0xff;

                            lcd.LCD_Enable((io_sd & 8) != 0);

                            byte data = (byte) 0xff;
                            int button_pressed = mcu_button_pressed.get();

                            if ((io_sd & 1) == 0)
                                data &= (byte) (((button_pressed >>> 0) & 255) ^ 255);
                            if ((io_sd & 2) == 0)
                                data &= (byte) (((button_pressed >>> 8) & 255) ^ 255);
                            if ((io_sd & 4) == 0)
                                data &= (byte) (((button_pressed >>> 16) & 255) ^ 255);
                            if ((io_sd & 8) == 0)
                                data &= (byte) (((button_pressed >>> 24) & 255) ^ 255);
                            return data;
                        } else if (address == 0xf106) {
                            ret = (byte) ga_int_trigger;
                            ga_int_trigger = 0;
                            interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ1.ordinal(), false);
                        } else {
                            logger.log(Level.DEBUG, "Unknown read %x".formatted(address));
                            ret = (byte) 0xff;
                        }
                        //
                        // f106:2-0 irq source
                        //
                    }
                }
                break;
//#if 0
//            case 3:
//                ret = rom2[address | 0x30000];
//                break;
//            case 4:
//                ret = rom2[address];
//                break;
//            case 10:
//                ret = rom2[address | 0x60000]; // FIXME
//                break;
//            case 1:
//                ret = rom2[address | 0x10000];
//                break;
//#endif
            case 1:
                ret = rom2[address_rom & rom2_mask];
                break;
            case 2:
                ret = rom2[address_rom & rom2_mask];
                break;
            case 3:
                ret = rom2[address_rom & rom2_mask];
                break;
            case 4:
                ret = rom2[address_rom & rom2_mask];
                break;
            case 8:
                if (!mcu_jv880)
                    ret = rom2[address_rom & rom2_mask];
                else
                    ret = (byte) 0xff;
                break;
            case 9:
                if (!mcu_jv880)
                    ret = rom2[address_rom & rom2_mask];
                else
                    ret = (byte) 0xff;
                break;
            case 14:
            case 15:
                if (!mcu_jv880)
                    ret = rom2[address_rom & rom2_mask];
                else
                    ret = cardram[address & 0x7fff]; // FIXME
                break;
            case 10:
            case 11:
                if (!mcu_mk1)
                    ret = sram[address & 0x7fff]; // FIXME
                else
                    ret = (byte) 0xff;
                break;
            case 12:
            case 13:
                if (mcu_jv880)
                    ret = nvram[address & 0x7fff]; // FIXME
                else
                    ret = (byte) 0xff;
                break;
            case 5:
                if (mcu_mk1)
                    ret = sram[address & 0x7fff]; // FIXME
                else
                    ret = (byte) 0xff;
                break;
            default:
                ret = 0x00;
                break;
        }
        return ret;
    }

    short MCU_Read16(int address) {
        address &= ~1;
        byte b0, b1;
        b0 = MCU_Read(address);
        b1 = MCU_Read(address + 1);
        return (short) (((b0 & 0xff) << 8) + (b1 & 0xff));
    }

    int MCU_Read32(int address) {
        address &= ~3;
        byte b0, b1, b2, b3;
        b0 = MCU_Read(address);
        b1 = MCU_Read(address + 1);
        b2 = MCU_Read(address + 2);
        b3 = MCU_Read(address + 3);
        return ((b0 & 0xff) << 24) + ((b1 & 0xff) << 16) + ((b2 & 0xff) << 8) + (b3 & 0xff);
    }

    void MCU_Write(int address, byte value) {
        int page = (address >>> 16) & 0xf;
        address &= 0xffff;
        if (page == 0) {
            if ((address & 0x8000) != 0) {
                if (!mcu_mk1) {
                    int base = mcu_jv880 ? 0xf000 : 0xe000;
                    if (address >= (base | 0x400) && address < (base | 0x800)) {
                        if (address == (base | 0x404) || address == (base | 0x405))
                            lcd.LCD_Write(address & 1, value);
                        else if (address == (base | 0x401)) {
                            io_sd = value;
                            lcd.LCD_Enable((value & 1) == 0);
                        } else if (address == (base | 0x402))
                            ga_int_enable = (value & 0xff) << 1;
                        else
                            logger.log(Level.DEBUG, "Unknown write %x %x".formatted(address, value));
                        //
                        // e400: always 4?
                        // e401: SC0-6?
                        // e402: enable/disable IRQ?
                        // e403: always 1?
                        // e404: LCD
                        // e405: LCD
                        // e406: 0 or 40
                        // e407: 0, e406 continuation?
                        //
                    } else if (address >= (base | 0x000) && address < (base | 0x400)) {
                        pcm.PCM_Write(address & 0x3f, value);
                    } else if (!mcu_scb55 && address >= 0xec00 && address < 0xf000) {
                        sm.SM_SysWrite(address & 0xff, value);
                    } else if (address >= 0xff80) {
                        MCU_DeviceWrite(address & 0x7f, value);
                    } else if (address >= 0xfb80 && address < 0xff80
                            && (dev_register[Dev.DEV_RAME.v] & 0x80) != 0) {
                        ram[(address - 0xfb80) & 0x3ff] = value;
                    } else if (address >= 0x8000 && address < 0xe000) {
                        sram[address & 0x7fff] = value;
                    } else {
                        logger.log(Level.DEBUG, "Unknown write %x %x".formatted(address, value));
                    }
                } else {
                    if (address >= 0xe000 && address < 0xe040) {
                        pcm.PCM_Write(address & 0x3f, value);
                    } else if (address >= 0xff80) {
                        MCU_DeviceWrite(address & 0x7f, value);
                    } else if (address >= 0xfb80 && address < 0xff80
                            && (dev_register[Dev.DEV_RAME.v] & 0x80) != 0) {
                        ram[(address - 0xfb80) & 0x3ff] = value;
                    } else if (address >= 0x8000 && address < 0xe000) {
                        sram[address & 0x7fff] = value;
                    } else if (address >= 0xf000 && address < 0xf100) {
                        io_sd = (byte) (address & 0xff);
                        lcd.LCD_Enable((io_sd & 8) != 0);
                    } else if (address == 0xf105) {
                        lcd.LCD_Write(0, value);
                        ga_lcd_counter = 500;
                    } else if (address == 0xf104) {
                        lcd.LCD_Write(1, value);
                        ga_lcd_counter = 500;
                    } else if (address == 0xf107) {
                        io_sd = value;
                    } else {
                        logger.log(Level.DEBUG, "Unknown write %x %x".formatted(address, value));
                    }
                }
            } else if (mcu_jv880 && address >= 0x6196 && address <= 0x6199) {
                // nop: the jv880 rom writes into the rom at 002E77-002E7D
            } else {
                logger.log(Level.DEBUG, "Unknown write %x %x".formatted(address, value));
            }
        } else if (page == 5 && mcu_mk1) {
            sram[address & 0x7fff] = value; // FIXME
        } else if (page == 10 && !mcu_mk1) {
            sram[address & 0x7fff] = value; // FIXME
        } else if (page == 12 && mcu_jv880) {
            nvram[address & 0x7fff] = value; // FIXME
        } else if (page == 14 && mcu_jv880) {
            cardram[address & 0x7fff] = value; // FIXME
        } else {
            logger.log(Level.DEBUG, "Unknown write %x %x".formatted(((page & 0xff) << 16) | address, value));
        }
    }

    void MCU_Write16(int address, short value) {
        address &= ~1;
        MCU_Write(address, (byte) (value >>> 8));
        MCU_Write(address + 1, (byte) (value & 0xff));
    }

int CC = 0;
    void MCU_ReadInstruction() {
        byte operand = MCU_ReadCodeAdvance();

//System.err.printf("%10d pc: %04x, oprand: %02x, sr: %04x%n", CC++, (pc - 1) & 0xffff, operand & 0xff, sr & 0xffff);
//if (CC > 100000) { System.exit(1); }
        opcodes.MCU_DispatchOperand(operand);  // Switch-based dispatch (no interface overhead)

        if ((this.sr & Status.STATUS_T.v) != 0) {
            interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_TRACE.ordinal());
        }
    }

    void MCU_Init() {
        Arrays.fill(this.r, (short) 0);

        this.pc = 0;
        this.sr = 0;
        this.cp = 0;
        this.dp = 0;
        this.ep = 0;
        this.tp = 0;
        this.br = 0;

        this.sleep = false;
        this.ex_ignore = false;
        this.exception_pending = 0;

        Arrays.fill(this.interrupt_pending, false);
        Arrays.fill(this.trapa_pending, false);

        this.cycles = 0;

        Arrays.fill(this.dev_register, (byte) 0);

        this.CC = 0;
    }

    void MCU_Reset() {
        this.r[0] = 0;
        this.r[1] = 0;
        this.r[2] = 0;
        this.r[3] = 0;
        this.r[4] = 0;
        this.r[5] = 0;
        this.r[6] = 0;
        this.r[7] = 0;

        this.pc = 0;

        this.sr = 0x700;

        this.cp = 0;
        this.dp = 0;
        this.ep = 0;
        this.tp = 0;
        this.br = 0;

        int reset_address = MCU_GetVectorAddress(VECTOR_RESET.ordinal());
        this.cp = (byte) ((reset_address >>> 16) & 0xff);
        this.pc = (short) (reset_address & 0xffff);

        this.exception_pending = -1;

        MCU_DeviceReset();

        if (mcu_mk1) {
            ga_int_enable = 255;
        }
    }

    // MIDI message tracking for diagnostic
    private int diagMidiStatus = 0;
    private int diagMidiData1 = 0;
    private int diagMidiByteCount = 0;

    public void MCU_PostUART(byte data) {
        // Warn if MIDI arrives before emulator is ready - these messages will be buffered
        // but could cause issues if there's a burst of messages waiting when processing starts
        if (!emulatorReady && (data & 0x80) != 0) {
            logger.log(Level.WARNING, "MIDI status byte %02x received before emulator ready".formatted(data & 0xff));
        }
        uart_buffer[uart_write_ptr.get()] = data;
logger.log(Level.DEBUG, "%02x, %d".formatted(data & 0xff, uart_write_ptr.get()));
        uart_write_ptr.set((uart_write_ptr.get() + 1) % uart_buffer_size);

        // Track MIDI messages for diagnostic (commented out for normal operation)
        // int d = data & 0xff;
        // if (d >= 0x80) {
        //     diagMidiStatus = d;
        //     diagMidiByteCount = 1;
        // } else {
        //     diagMidiByteCount++;
        //     if (diagMidiByteCount == 2) diagMidiData1 = d;
        //     else if (diagMidiByteCount == 3) {
        //         int cmd = diagMidiStatus & 0xf0;
        //         int ch = diagMidiStatus & 0x0f;
        //         if (cmd == 0x80) {
        //             System.err.printf("MIDI_NOTE_OFF: ch=%d note=%d vel=%d mask=0x%08x%n",
        //                     ch, diagMidiData1, d, pcm.voice_mask);
        //         } else if (cmd == 0x90) {
        //             if (d > 0) {
        //                 System.err.printf("MIDI_NOTE_ON: ch=%d note=%d vel=%d mask=0x%08x%n",
        //                         ch, diagMidiData1, d, pcm.voice_mask);
        //             } else {
        //                 System.err.printf("MIDI_NOTE_OFF: ch=%d note=%d vel=%d mask=0x%08x (via 0x90)%n",
        //                         ch, diagMidiData1, d, pcm.voice_mask);
        //             }
        //         }
        //     }
        // }
    }

    void MCU_UpdateUART_RX() {
        if ((dev_register[Dev.DEV_SCR.v] & 16) == 0) // RX disabled
            return;
        if (uart_write_ptr.get() == uart_read_ptr.get()) // no byte
            return;

        if ((dev_register[DEV_SSR.v] & 0x40) != 0)
            return;

        if (this.cycles < uart_rx_delay)
            return;

        uart_rx_byte = uart_buffer[uart_read_ptr.get()];
        uart_read_ptr.set((uart_read_ptr.get() + 1) % uart_buffer_size);
        dev_register[DEV_SSR.v] |= 0x40;
        interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_UART_RX.ordinal(), (dev_register[Dev.DEV_SCR.v] & 0x40) != 0);
    }

    // dummy TX
    void MCU_UpdateUART_TX() {
        if ((dev_register[DEV_SCR.v] & 32) == 0) // TX disabled
            return;

        if ((dev_register[DEV_SSR.v] & 0x80) != 0)
            return;

        if (this.cycles < uart_tx_delay)
            return;

        dev_register[DEV_SSR.v] |= (byte) 0x80;
        interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_UART_TX.ordinal(), (dev_register[Dev.DEV_SCR.v] & 0x80) != 0);

//        logger.log(Level.TRACE, "tx:%x\n", dev_register[DEV_TDR]);
    }

    private volatile boolean work_thread_run = false;

    /** Indicates when the emulator is ready to process MIDI messages */
    private volatile boolean emulatorReady = false;

    /** Number of samples to process before marking emulator as ready */
    private static final int READY_SAMPLE_THRESHOLD = 66207; // ~1 second of audio at 66207 Hz

    /** Stops the emulator threads. Safe to call from any thread. */
    public void stop() {
        work_thread_run = false;
        lcd.requestQuit();
    }

    /**
     * Waits for the emulator to be ready to process MIDI messages.
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if emulator became ready, false if timeout
     */
    public boolean waitForReady(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!emulatorReady && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return emulatorReady;
    }

    /** Returns true if the emulator is ready to process MIDI */
    public boolean isReady() {
        return emulatorReady;
    }

    final ReentrantLock work_thread_lock = new ReentrantLock();

    void MCU_WorkThread_Lock() {
        work_thread_lock.lock();
logger.log(Level.TRACE, "mutex lock");
    }

    void MCU_WorkThread_Unlock() {
        work_thread_lock.unlock();
logger.log(Level.TRACE, "mutex unlock");
    }

    // Diagnostic counters
    private long diagStartTime = 0;
    private long diagMcuIterations = 0;
    private long diagLastReportTime = 0;
    private long diagWaitCount = 0;
    private long diagWaitTime = 0;

    // Batch size for MCU instruction execution
    // Larger = less overhead, but more latency. 64 instructions ≈ 1 PCM sample.
    private static final int MCU_BATCH_SIZE = 1;  // Must be 1 to match C++ timing exactly

    // PCM clock rate (24 MHz for MCU, PCM runs in sync)
    private static final long PCM_CLOCK_RATE = 24_000_000L;

    /**
     * Separate PCM synthesis thread - syncs with MCU cycles but runs in parallel.
     * This allows MCU to run fast while PCM catches up in a separate thread.
     */
    void pcm_thread() {
        logger.log(Level.DEBUG, "PCM thread start");
        try {
            while (work_thread_run) {
                // Check buffer status - wait if buffer is almost full
                int w = sample_write_ptr.get();
                int r = sample_read_ptr.get();
                int used = (w >= r) ? w - r : audio_buffer_size - r + w;
                int free = audio_buffer_size - used - 1;

                if (free < 256) {
                    // Buffer full - wait for audio consumer to drain
                    LockSupport.parkNanos(100_000); // 0.1ms
                    continue;
                }

                // Align write pointer based on PCM config
                if ((pcm.config_reg_3c & 0x40) != 0) {
                    w &= ~3;
                } else {
                    w &= ~1;
                }
                sample_write_ptr.set(w);

                // Run PCM to catch up with MCU cycles (not real-time)
                // This ensures PCM only generates audio for state that MCU has processed
                long targetCycles = this.cycles; // Use MCU's current cycle count
                if (pcm.cycles < targetCycles) {
                    pcm.PCM_Update(targetCycles);
                } else {
                    // PCM is caught up with MCU - wait briefly for MCU to advance
                    LockSupport.parkNanos(50_000); // 0.05ms
                }
            }
        } catch (Throwable t) {
            logger.log(Level.ERROR, "PCM thread error: " + t.getMessage(), t);
        }
        logger.log(Level.DEBUG, "PCM thread end");
    }

    void work_thread() {
logger.log(Level.DEBUG, "task start: ex_ignore: " + ex_ignore + ", sleep: " + sleep);
try {
        MCU_WorkThread_Lock();
        diagStartTime = System.nanoTime();
        diagLastReportTime = diagStartTime;

        while (work_thread_run) {
            // Check buffer status once per batch
            int w = sample_write_ptr.get();
            if ((pcm.config_reg_3c & 0x40) != 0) {
                w &= ~3;
                sample_write_ptr.set(w);
            } else {
                w &= ~1;
                sample_write_ptr.set(w);
            }

            int r = sample_read_ptr.get();
            int used = (w >= r) ? w - r : audio_buffer_size - r + w;
            int free = audio_buffer_size - used - 1;
            if (free < 256) {
                diagWaitCount++;
                long waitStart = System.nanoTime();
                MCU_WorkThread_Unlock();
                while (work_thread_run) {
                    w = sample_write_ptr.get();
                    r = sample_read_ptr.get();
                    used = (w >= r) ? w - r : audio_buffer_size - r + w;
                    free = audio_buffer_size - used - 1;
                    if (free >= 256) break;
                    LockSupport.parkNanos(100_000);
                }
                MCU_WorkThread_Lock();
                diagWaitTime += System.nanoTime() - waitStart;
            }

            // === BATCH EXECUTION: Run MCU instructions in tight loop ===
            // This reduces per-iteration overhead (atomic ops, method calls, condition checks)
            for (int batch = 0; batch < MCU_BATCH_SIZE && work_thread_run; batch++) {
                if (!this.ex_ignore)
                    interrupt.MCU_Interrupt_Handle();
                else
                    this.ex_ignore = false;

                if (!this.sleep)
                    MCU_ReadInstruction();

                this.cycles += 12;
                diagMcuIterations++;
            }

            // === SUBSYSTEM UPDATES: Called once per batch ===
            // PCM, Timer, SM will process all accumulated cycles at once
            if (pcm.cycles < this.cycles)
                pcm.PCM_Update(this.cycles);

            // Mark emulator as ready after processing enough samples
            if (!emulatorReady && pcm.getTotalSamples() >= READY_SAMPLE_THRESHOLD) {
                emulatorReady = true;
                logger.log(Level.INFO, "Emulator ready after " + pcm.getTotalSamples() + " samples");
            }

            if (timer.timer_cycles * 2 < this.cycles)
                timer.TIMER_Clock(this.cycles);

            if (!mcu_mk1 && !mcu_jv880 && !mcu_scb55) {
                if (sm.cycles < this.cycles * 5) {
                    sm.SM_Update(this.cycles);
                }
            } else {
                MCU_UpdateUART_RX();
                MCU_UpdateUART_TX();
            }
            // Debug: check if SM is being called
            if (diagMcuIterations == 1) {
                System.err.printf("SM_CHECK: mcu_mk1=%b, mcu_jv880=%b, mcu_scb55=%b, sm.cycles=%d, mcu.cycles=%d%n",
                        mcu_mk1, mcu_jv880, mcu_scb55, sm.cycles, this.cycles);
            }

            if ((dev_register[Dev.DEV_ADCSR.v] & 0x20) != 0)
                MCU_UpdateAnalog(this.cycles);

            if (mcu_mk1) {
                if (ga_lcd_counter != 0) {
                    ga_lcd_counter -= MCU_BATCH_SIZE;
                    if (ga_lcd_counter <= 0) {
                        ga_lcd_counter = 0;
                        MCU_GA_SetGAInt(1, false);
                        MCU_GA_SetGAInt(1, true);
                    }
                }
            }

            // Diagnostic reporting every 2 seconds
            long now = System.nanoTime();
            long elapsed = now - diagLastReportTime;
            if (elapsed >= 2_000_000_000L) {
                double secs = elapsed / 1_000_000_000.0;
                long mcuRate = (long)(diagMcuIterations / secs);
                double waitPct = 100.0 * diagWaitTime / elapsed;
                System.out.printf("DIAG: MCU=%,d/s, waits=%d (%.1f%% of time waiting)%n",
                    mcuRate, diagWaitCount, waitPct);
                diagMcuIterations = 0;
                diagLastReportTime = now;
                diagWaitCount = 0;
                diagWaitTime = 0;
            }
        }

        MCU_WorkThread_Unlock();
logger.log(Level.DEBUG, "task end");
} catch (Throwable t) {
 logger.log(Level.ERROR, t.getMessage(), t);
 throw t;
}
    }

    private void MCU_Run() {
        try {
            boolean working = true;

            work_thread_run = true;
            es.submit(this::work_thread); // MCU emulation thread (includes PCM)
logger.log(Level.DEBUG, "thread start");

            while (working) {
                if (lcd.LCD_QuitRequested()) {
                    working = false;
logger.log(Level.DEBUG, "working is false");
                }

                lcd.LCD_Update();
                Thread.sleep(15);
            }

            work_thread_run = false;
            es.shutdown();
            es.close();
logger.log(Level.DEBUG, "thread end");

        } catch (Exception ignoree) {
} catch (Throwable t) {
 logger.log(Level.ERROR, t.getMessage(), t);
 throw t;
        }
    }

    void MCU_PatchROM() {
        //rom2[0x1333] = 0x11;
        //rom2[0x1334] = 0x19;
        //rom1[0x622d] = 0x19;
    }

    byte mcu_p0_data = 0x00;
    byte mcu_p1_data = 0x00;

    byte MCU_ReadP0() {
        return (byte) 0xff;
    }

    byte MCU_ReadP1() {
        byte data = (byte) 0xff;
        int button_pressed = mcu_button_pressed.get();

        if ((mcu_p0_data & 1) == 0)
            data &= (byte) (((button_pressed >>> 0) & 255) ^ 255);
        if ((mcu_p0_data & 2) == 0)
            data &= (byte) (((button_pressed >>> 8) & 255) ^ 255);
        if ((mcu_p0_data & 4) == 0)
            data &= (byte) (((button_pressed >>> 16) & 255) ^ 255);
        if ((mcu_p0_data & 8) == 0)
            data &= (byte) (((button_pressed >>> 24) & 255) ^ 255);

        return data;
    }

    void MCU_WriteP0(byte data) {
        mcu_p0_data = data;
    }

    void MCU_WriteP1(byte data) {
        mcu_p1_data = data;
    }

    byte[] tempbuf;

    void unscramble(byte[] src, byte[] dst, int len) {
        for (int i = 0; i < len; i++) {
            int address = i & ~0xfffff;
            int[] aa = {2, 0, 3, 4, 1, 9, 13, 10, 18, 17, 6, 15, 11, 16, 8, 5, 12, 7, 14, 19};
            for (int j = 0; j < 20; j++) {
                if ((i & (1 << j)) != 0)
                    address |= 1 << aa[j];
            }
            byte srcdata = src[address];
            byte data = 0;
            int[] dd = {2, 0, 4, 5, 7, 6, 3, 1};
            for (int j = 0; j < 8; j++) {
                if ((srcdata & (1 << dd[j])) != 0)
                    data |= (byte) (1 << j);
            }
            dst[i] = data;
        }
    }

    // Match C++ SDL audio_callback: simple memcpy, no resampling
    // SDL outputs at native 66207 Hz, so does this version now
    public void audioLoop() {
        // Match SDL: spec.samples = audio_page_size / 4 = 128 frames
        final int OUTPUT_FRAMES = audio_page_size / 4;
        final int SAMPLES_PER_FRAME = 2; // stereo
        final int BYTES_PER_SAMPLE = 2;  // 16-bit
        byte[] outBytes = new byte[OUTPUT_FRAMES * SAMPLES_PER_FRAME * BYTES_PER_SAMPLE];

        while (work_thread_run) {
            try {
                int r = sample_read_ptr.get();
                int w = sample_write_ptr.get();

                // Calculate available samples (stereo pairs)
                int available = (w >= r) ? w - r : audio_buffer_size - r + w;

                // Number of samples to copy (stereo samples = frames * 2)
                int samplesToRead = OUTPUT_FRAMES * SAMPLES_PER_FRAME;

                // --- DIRECT COPY (like C++ memcpy) ---
                int outIdx = 0;
                for (int i = 0; i < samplesToRead; i++) {
                    short sample = 0;
                    if (i < available) {
                        int idx = (r + i) % audio_buffer_size;
                        sample = sample_buffer[idx];
                    }
                    // Else: output silence (0) for underrun

                    // Capture callback for testing (every other sample = left channel)
                    if (audioCaptureCallback != null && (i & 1) == 0) {
                        short left = sample;
                        short right = (i + 1 < available) ? sample_buffer[(r + i + 1) % audio_buffer_size] : 0;
                        audioCaptureCallback.onSamples(left, right);
                    }

                    outBytes[outIdx++] = (byte) (sample & 0xff);
                    outBytes[outIdx++] = (byte) ((sample >> 8) & 0xff);
                }

                // --- ADVANCE POINTER & CLEAR (like C++ memset + ptr advance) ---
                int actualConsumed = Math.min(samplesToRead, available);

                // Clear consumed slots
                int clearPtr = r;
                for (int k = 0; k < actualConsumed; k++) {
                    sample_buffer[clearPtr] = 0;
                    clearPtr = (clearPtr + 1) % audio_buffer_size;
                }

                // Advance read pointer
                sample_read_ptr.set((r + actualConsumed) % audio_buffer_size);

                // --- 3. BLOCKING WRITE ---
                // This call takes ~11.6ms.
                // While this happens, the Emulator is running in parallel filling the buffer.
                audioOut.write(outBytes, 0, outIdx);

            } catch (Exception ignore) {
            }
        }
    }

    private final ExecutorService es = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Emulator Core");
        thread.setPriority(Thread.MAX_PRIORITY); // Give emulator slightly higher priority
        return thread;
    });

    private final ExecutorService ses = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Audio Output");
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });

    int MCU_OpenAudio(int deviceIndex, int pageSize, int pageNum) {

        audio_page_size = (pageSize / 2) * 2; // must be even
        audio_buffer_size = audio_page_size * pageNum;

        // Use native SC-55 sample rate (66207 Hz) to avoid resampling artifacts
        // This matches the C++ SDL version which also outputs at native rate
        float sampleRate = (mcu_mk1 || mcu_jv880) ? 64000.0f : 66207.0f;
        AudioFormat spec = new AudioFormat(
                sampleRate,
                16,
                2,
                true,
                false);

        sample_buffer = new short[audio_buffer_size];
        sample_read_ptr.set(0);
        sample_write_ptr.set(0);

        String audioDevicename;

        try {
            audioOut = AudioSystem.getSourceDataLine(spec);
            audioDevicename = audioOut.getLineInfo().toString();
            audioOut.open(spec, 4096);
            audioOut.start();
        } catch (LineUnavailableException e) {
            logger.log(Level.ERROR, "No audio output device found.");
            return 0;
        }

        logger.log(Level.INFO, "Audio device: " + audioDevicename);

        logger.log(Level.INFO, "Audio Actual: F=%s, C=%d, R=%d, B=%d".formatted(
                spec.getEncoding(),
                spec.getChannels(),
                (int) spec.getSampleRate(),
                spec.getSampleSizeInBits()));

        work_thread_run = true;
        ses.submit(this::audioLoop);

        return 1;
    }

    void MCU_CloseAudio() {
        ses.shutdown();
        audioOut.close();
    }

    void MCU_PostSample(int[] sample) {
        sample[0] >>= 15;
        if (sample[0] > Short.MAX_VALUE)
            sample[0] = Short.MAX_VALUE;
        else if (sample[0] < Short.MIN_VALUE)
            sample[0] = Short.MIN_VALUE;
        sample[1] >>= 15;
        if (sample[1] > Short.MAX_VALUE)
            sample[1] = Short.MAX_VALUE;
        else if (sample[1] < Short.MIN_VALUE)
            sample[1] = Short.MIN_VALUE;
        // Cache write pointer to reduce atomic operations (3 gets -> 1 get)
        int wp = sample_write_ptr.get();
        sample_buffer[wp] = (short) sample[0];
        sample_buffer[wp + 1] = (short) sample[1];
        sample_write_ptr.set((wp + 2) % audio_buffer_size);
    }

    void MCU_GA_SetGAInt(int line, boolean value) {
        // guesswork
        if (value && !ga_int[line] && (ga_int_enable & (1 << line)) != 0)
            ga_int_trigger = line;
        ga_int[line] = value;

        if (mcu_jv880)
            interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ0.ordinal(), ga_int_trigger != 0);
        else
            interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ1.ordinal(), ga_int_trigger != 0);
    }

    void MCU_EncoderTrigger(int dir) {
        if (!mcu_jv880) return;
        MCU_GA_SetGAInt(dir == 0 ? 3 : 4, false);
        MCU_GA_SetGAInt(dir == 0 ? 3 : 4, true);
    }

    private InputStream[] s_rf = {
            null,
            null,
            null,
            null,
            null,
            null
    };

    private void closeAllR() {
        for (int i = 0; i < ROM_SET_N_FILES; ++i) {
            if (s_rf[i] != null)
                try {
                    s_rf[i].close();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            s_rf[i] = null;
        }
    }

    enum ResetType {
        NONE,
        GS_RESET,
        GM_RESET,
    }

    void MIDI_Reset(ResetType resetType) {
        byte[] gmReset = {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7};
        byte[] gsReset = {(byte) 0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41, (byte) 0xF7};

        if (resetType == ResetType.GS_RESET) {
            for (byte b : gsReset) {
                MCU_PostUART(b);
            }
        } else if (resetType == ResetType.GM_RESET) {
            for (byte b : gmReset) {
                MCU_PostUART(b);
            }
        }
    }

    public static class Config {
        public int port = 0;
        public int audioDeviceIndex = -1;
        public int pageSize = 512;
        public int pageNum = 32;
        public boolean autodetect = true;
        public ResetType resetType = ResetType.NONE;
    }

    public static void main(String[] args) {

        Config config = new Config();

        Mcu mcu = new Mcu();

        {
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("-p:")) {
                    config.port = Integer.parseInt(args[i].substring(3));
                } else if (args[i].startsWith("-a:")) {
                    config.audioDeviceIndex = Integer.parseInt(args[i].substring(3));
                } else if (args[i].startsWith("-ab:")) {
                    String pageArgs = args[i].substring(4);

                    String[] pages = pageArgs.split(":");
                    if (pages.length == 1) {
                        config.pageSize = Integer.parseInt(pages[0]);
                    }
                    if (pages.length == 2) {
                        config.pageSize = Integer.parseInt(pages[0]);
                        config.pageNum = Integer.parseInt(pages[1]);
                    }

                    // reset both if either is invalid
                    if (config.pageSize <= 0 || config.pageNum <= 0) {
                        config.pageSize = 512;
                        config.pageNum = 32;
                    }
                } else if (args[i].equals("-mk2")) {
                    mcu.romset = RomSet.ROM_SET_MK2.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-st")) {
                    mcu.romset = RomSet.ROM_SET_ST.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-mk1")) {
                    mcu.romset = RomSet.ROM_SET_MK1.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-cm300")) {
                    mcu.romset = ROM_SET_CM300.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-jv880")) {
                    mcu.romset = ROM_SET_JV880.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-scb55")) {
                    mcu.romset = ROM_SET_SCB55.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-rlp3237")) {
                    mcu.romset = ROM_SET_RLP3237.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-gs")) {
                    config.resetType = ResetType.GS_RESET;
                } else if (args[i].equals("-gm")) {
                    config.resetType = ResetType.GM_RESET;
                } else if (args[i].equals("-h") || args[i].equals("-help") || args[i].equals("--help")) {
                    // TODO: Might want to try to find a way to print out the executable's actual name (without any full paths).
                    System.err.println("Usage: nuked-sc55 [options]");
                    System.err.println("Options:");
                    System.err.println("  -h, -help, --help              Display this information.");
                    System.err.println();
                    System.err.println("  -p:<port_number>               Set MIDI port.");
                    System.err.println("  -a:<device_number>             Set Audio Device index.");
                    System.err.println("  -ab:<page_size>:[page_count]   Set Audio Buffer size.");
                    System.err.println();
                    System.err.println("  -mk2                           Use SC-55mk2 ROM set.");
                    System.err.println("  -st                            Use SC-55st ROM set.");
                    System.err.println("  -mk1                           Use SC-55mk1 ROM set.");
                    System.err.println("  -cm300                         Use CM-300/SCC-1 ROM set.");
                    System.err.println("  -jv880                         Use JV-880 ROM set.");
                    System.err.println("  -scb55                         Use SCB-55 ROM set.");
                    System.err.println("  -rlp3237                       Use RLP-3237 ROM set.");
                    System.err.println();
                    System.err.println("  -gs                            Reset system in GS mode.");
                    System.err.println("  -gm                            Reset system in GM mode.");
                    return;
                } else if (args[i].equals("-sc155")) {
                    mcu.romset = ROM_SET_SC155.ordinal();
                    config.autodetect = false;
                } else if (args[i].equals("-sc155mk2")) {
                    mcu.romset = ROM_SET_SC155MK2.ordinal();
                    config.autodetect = false;
                }
            }
        }

        mcu.run(config);
    }

    /** */
    public void run(Config config) {
        Path basePath = Path.of(System.getProperty("sc55.dir"));

        if (!Files.exists(basePath))
            logger.log(Level.WARNING, "Base path doesn't exist: " + basePath);
        else
            logger.log(Level.DEBUG, "Base path is: " + basePath);

        if (config.autodetect) {
            for (int i = 0; i < ROM_SET_COUNT.ordinal(); i++) {
                boolean good = true;
                for (int j = 0; j < 5; j++) {
                    if (this.roms[i][j].isEmpty())
                        continue;
                    Path path = basePath.resolve(this.roms[i][j]);
                    try (var f = Files.newInputStream(path)) {
                    } catch (IOException e) {
                        good = false;
                        break;
                    }
                }
                if (good) {
                    this.romset = i;
                    break;
                }
            }
            logger.log(Level.DEBUG, "ROM set autodetect: " + this.rs_name[this.romset]);
        }

        this.mcu_mk1 = false;
        this.mcu_cm300 = false;
        this.mcu_st = false;
        this.mcu_jv880 = false;
        this.mcu_scb55 = false;
        this.mcu_sc155 = false;
        switch (RomSet.values()[this.romset]) {
            case ROM_SET_MK2:
            case ROM_SET_SC155MK2:
                if (this.romset == ROM_SET_SC155MK2.ordinal())
                    this.mcu_sc155 = true;
                break;
            case ROM_SET_ST:
                this.mcu_st = true;
                break;
            case ROM_SET_MK1:
            case ROM_SET_SC155:
                this.mcu_mk1 = true;
                this.mcu_st = false;
                if (this.romset == ROM_SET_SC155.ordinal())
                    this.mcu_sc155 = true;
                break;
            case ROM_SET_CM300:
                this.mcu_mk1 = true;
                this.mcu_cm300 = true;
                break;
            case ROM_SET_JV880:
                this.mcu_jv880 = true;
                this.rom2_mask /= 2; // rom is half the size
                this.lcd.lcd_width = 820;
                this.lcd.lcd_height = 100;
                this.lcd.lcd_col1 = 0x000000;
                this.lcd.lcd_col2 = 0x78b500;
                break;
            case ROM_SET_SCB55:
            case ROM_SET_RLP3237:
                this.mcu_scb55 = true;
                break;
        }

        Path[] rpaths = new Path[ROM_SET_N_FILES];

        boolean r_ok = true;
        List<Path> errors_list = new ArrayList<>();

        for (int i = 0; i < ROM_SET_N_FILES; ++i) {
            if (this.roms[this.romset][i].isEmpty()) {
                rpaths[i] = null;
                continue;
            }
            rpaths[i] = basePath.resolve(this.roms[this.romset][i]);
            try {
                this.s_rf[i] = Files.newInputStream(rpaths[i]);
                boolean optional = this.mcu_jv880 && i >= 4;
                r_ok &= optional || (this.s_rf[i] != null);
            } catch (IOException e) {
                logger.log(Level.WARNING, e.getMessage() + " " + rpaths[i]);
                errors_list.add(rpaths[i]);
            }
        }

        if (!r_ok) {
            logger.log(Level.ERROR, "FATAL ERROR: One of required data ROM files is missing: " + errors_list);
            this.closeAllR();
            return;
        }

//        this.lcd.LCD_SetBackPath(basePath + "/back.data");

        try {
            this.rom1 = this.s_rf[0].readAllBytes();
            if (this.rom1.length != ROM1_SIZE)
                throw new IllegalStateException("ROM1 size: " + this.rom1.length + "/" + ROM1_SIZE);
        } catch (Exception e) {
            logger.log(Level.ERROR, e.getMessage());
            logger.log(Level.ERROR, "FATAL ERROR: Failed to read the mcu ROM1.");
            this.closeAllR();
            return;
        }

        try {
            this.rom2 = this.s_rf[1].readAllBytes();

            if (this.rom2.length == ROM2_SIZE || this.rom2.length == ROM2_SIZE / 2) {
                this.rom2_mask = this.rom2.length - 1;
            } else
                throw new IllegalStateException("ROM1 size: " + this.rom2.length + "/" + ROM2_SIZE);
        } catch (IOException e) {
            logger.log(Level.ERROR, "FATAL ERROR: Failed to read the mcu ROM2.");
            this.closeAllR();
            return;
        }

        if (this.mcu_mk1) {
            try {
                this.tempbuf = this.s_rf[2].readAllBytes();
                if (this.tempbuf.length != 0x100000)
                    throw new IllegalStateException("WaveRom1 size:" + this.tempbuf.length + "/" + 0x100000);
            } catch (IOException e) {
                logger.log(Level.ERROR, "FATAL ERROR: Failed to read the WaveRom1.");
                this.closeAllR();
                return;
            }

            this.unscramble(this.tempbuf, this.pcm.waverom1, 0x100000);

            try {
                this.tempbuf = this.s_rf[3].readAllBytes();
                if (this.tempbuf.length != 0x100000)
                    throw new IllegalStateException("WaveRom2 size: " + this.tempbuf.length + "/" + 0x100000);
            } catch (IOException e) {
                logger.log(Level.ERROR, "FATAL ERROR: Failed to read the WaveRom2.");
                this.closeAllR();
                return;
            }

            this.unscramble(this.tempbuf, this.pcm.waverom2, 0x100000);

            try {
                this.tempbuf = this.s_rf[4].readAllBytes();
                if (this.tempbuf.length != 0x100000)
                    throw new IllegalStateException("WaveRom3 size: " + this.tempbuf.length + "/" + 0x100000);
            } catch (IOException e) {
                logger.log(Level.ERROR, "FATAL ERROR: Failed to read the WaveRom3.");
                this.closeAllR();
                return;
            }

            this.unscramble(this.tempbuf, this.pcm.waverom3, 0x100000);
        } else if (this.mcu_jv880) {
            try {
                this.tempbuf = this.s_rf[2].readAllBytes();
                if (this.tempbuf.length != 0x200000)
                    throw new IllegalStateException("the size: " + this.tempbuf.length + "/" + 0x200000);
            } catch (IOException e) {
                logger.log(Level.ERROR, "FATAL ERROR: Failed to read the WaveRom1.");
                this.closeAllR();
                return;
            }

            this.unscramble(this.tempbuf, this.pcm.waverom1, 0x200000);

            try {
                this.tempbuf = this.s_rf[3].readAllBytes();
                if (this.tempbuf.length != 0x200000)
                    throw new IllegalStateException("the size: " + this.tempbuf.length + "/" + 0x200000);
            } catch (IOException e) {
                logger.log(Level.ERROR, "FATAL ERROR: Failed to read the WaveRom2.");
                this.closeAllR();
                return;
            }

            this.unscramble(this.tempbuf, this.pcm.waverom2, 0x200000);

            try {
                if (this.s_rf[4] != null) {
                    this.tempbuf = this.s_rf[4].readAllBytes();
                    if (this.tempbuf.length != 0x800000)
                        throw new IllegalStateException("the size: " + this.tempbuf.length + "/" + 0x800000);
                    this.unscramble(this.tempbuf, this.pcm.waverom_exp, 0x800000);
                }
            } catch (IOException e) {
                logger.log(Level.ERROR, "WaveRom EXP not found, skipping it.");
            }

            try {
                if (this.s_rf[5] != null) {
                    this.tempbuf = this.s_rf[5].readAllBytes();
                    if (this.tempbuf.length != 0x200000)
                        throw new IllegalStateException("the size: " + this.tempbuf.length + "/" + 0x200000);
                    this.unscramble(this.tempbuf, this.pcm.waverom_card, 0x200000);
                }
            } catch (IOException e) {
                logger.log(Level.ERROR, "WaveRom PCM not found, skipping it.");
            }
        } else {
            try {
                this.tempbuf = this.s_rf[2].readAllBytes();
                if (this.tempbuf.length != 0x200000)
                    throw new IllegalStateException("the size: " + this.tempbuf.length + "/" + 0x200000);
            } catch (IOException e) {
                logger.log(Level.ERROR, "FATAL ERROR: Failed to read the WaveRom1.");
                this.closeAllR();
                return;
            }

            this.unscramble(this.tempbuf, this.pcm.waverom1, 0x200000);

            try {
                if (this.s_rf[3] != null) {
                    this.tempbuf = this.s_rf[3].readAllBytes();
                    if (this.tempbuf.length != 0x100000)
                        throw new IllegalStateException("the size: " + this.tempbuf.length + "/" + 0x100000);
                }
            } catch (IOException e) {
                logger.log(Level.ERROR, "FATAL ERROR: Failed to read the WaveRom2.");
                this.closeAllR();
                return;
            }

            this.unscramble(this.tempbuf, this.mcu_scb55 ? this.pcm.waverom3 : this.pcm.waverom2, 0x100000);
        }

        try {
            if (this.s_rf[4] != null) {
                this.sm.sm_rom = this.s_rf[4].readAllBytes();
                if (this.sm.sm_rom.length != ROMSM_SIZE)
                    throw new IllegalStateException("the size: " + this.sm.sm_rom.length + "/" + ROMSM_SIZE);
            }
        } catch (IOException e) {
            logger.log(Level.ERROR, "FATAL ERROR: Failed to read the sub mcu ROM.");
            this.closeAllR();
            return;
        }

        // Close all files as they no longer needed being open
        this.closeAllR();

        if (this.MCU_OpenAudio(config.audioDeviceIndex, config.pageSize, config.pageNum) == 0) {
            logger.log(Level.ERROR, "FATAL ERROR: Failed to open the audio stream.");
            return;
        }

        if (this.midi.MIDI_Init(config.port) == 0) {
            logger.log(Level.ERROR, "ERROR: Failed to initialize the MIDI Input.\nWARNING: Continuing without MIDI Input...");
        }

        this.lcd.LCD_Init();
        this.MCU_Init();
        this.MCU_PatchROM();
        this.MCU_Reset();
        this.sm.SM_Reset();
        this.pcm.PCM_Reset();

        if (config.resetType != ResetType.NONE)
            this.MIDI_Reset(config.resetType);

        this.MCU_Run();

        this.MCU_CloseAudio();

        this.midi.MIDI_Quit();
        this.lcd.LCD_UnInit();
    }
}
