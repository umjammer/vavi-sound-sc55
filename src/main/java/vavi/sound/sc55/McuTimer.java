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
import java.util.Arrays;

import vavi.sound.sc55.Mcu.Dev;

import static java.lang.System.getLogger;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_FRT0_FOVI;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_FRT0_OCIA;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_FRT0_OCIB;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_TIMER_CMIA;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_TIMER_CMIB;
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_TIMER_OVI;


class McuTimer {

    private static final Logger logger = getLogger(McuTimer.class.getName());

    static class frt_t {

        byte tcr;
        byte tcsr;
        short frc;
        short ocra;
        short ocrb;
        short icr;
        byte status_rd;
    }

//    static class mcu_timer_t {

        byte tcr;
        byte tcsr;
        byte tcora;
        byte tcorb;
        byte tcnt;
        byte status_rd;
//    }

    private Mcu mcu;

    McuTimer(Mcu mcu) {
        this.mcu = mcu;
    }

    long timer_cycles;
    byte timer_tempreg;

    frt_t[] frt = new frt_t[] {new frt_t(), new frt_t(), new frt_t()};

    enum Reg {
        REG_TCR,
        REG_TCSR,
        REG_FRCH,
        REG_FRCL,
        REG_OCRAH,
        REG_OCRAL,
        REG_OCRBH,
        REG_OCRBL,
        REG_ICRH,
        REG_ICRL
    }

    void TIMER_Reset() {
        timer_cycles = 0;
        timer_tempreg = 0;
        Arrays.setAll(frt, i -> new frt_t());
        tcr = 0;
        tcsr = 0;
        tcora = 0;
        tcorb = 0;
        tcnt = 0;
        status_rd = 0;
    }

    void TIMER_Write(int address, byte data) {
        int t = (address >>> 4) - 1;
        if (t > 2)
            return;
        address &= 0x0f;
        frt_t timer = frt[t];
        switch (Reg.values()[address]) {
            case REG_TCR:
                timer.tcr = data;
                break;
            case REG_TCSR:
                timer.tcsr &= ~0xf;
                timer.tcsr |= (byte) (data & 0xf);
                if ((data & 0x10) == 0 && (timer.status_rd & 0x10) != 0) {
                    timer.tcsr &= ~0x10;
                    timer.status_rd &= ~0x10;
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_FRT0_FOVI.ordinal() + t * 4, false);
                }
                if ((data & 0x20) == 0 && (timer.status_rd & 0x20) != 0) {
                    timer.tcsr &= ~0x20;
                    timer.status_rd &= ~0x20;
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_FRT0_OCIA.ordinal() + t * 4, false);
                }
                if ((data & 0x40) == 0 && (timer.status_rd & 0x40) != 0) {
                    timer.tcsr &= ~0x40;
                    timer.status_rd &= ~0x40;
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_FRT0_OCIB.ordinal() + t * 4, false);
                }
                break;
            case REG_FRCH:
            case REG_OCRAH:
            case REG_OCRBH:
            case REG_ICRH:
                timer_tempreg = data;
                break;
            case REG_FRCL:
                timer.frc = (short) (((timer_tempreg & 0xff) << 8) | (data & 0xff));
                break;
            case REG_OCRAL:
                timer.ocra = (short) (((timer_tempreg & 0xff) << 8) | (data & 0xff));
                break;
            case REG_OCRBL:
                timer.ocrb = (short) (((timer_tempreg & 0xff) << 8) | (data & 0xff));
                break;
            case REG_ICRL:
                timer.icr = (short) (((timer_tempreg & 0xff) << 8) | (data & 0xff));
                break;
        }
    }

    byte TIMER_Read(int address) {
        int t = (address >>> 4) - 1;
        if (t > 2)
            return (byte) 0xff;
        address &= 0x0f;
        frt_t timer = frt[t];
        switch (Reg.values()[address]) {
            case REG_TCR:
                return timer.tcr;
            case REG_TCSR: {
                byte ret = timer.tcsr;
                timer.status_rd |= (byte) (timer.tcsr & 0xf0);
                //timer.status_rd |= 0xf0;
                return ret;
            }
            case REG_FRCH:
                timer_tempreg = (byte) (timer.frc & 0xff);
                return (byte) (timer.frc >> 8);
            case REG_OCRAH:
                timer_tempreg = (byte) (timer.ocra & 0xff);
                return (byte) (timer.ocra >> 8);
            case REG_OCRBH:
                timer_tempreg = (byte) (timer.ocrb & 0xff);
                return (byte) (timer.ocrb >> 8);
            case REG_ICRH:
                timer_tempreg = (byte) (timer.icr & 0xff);
                return (byte) (timer.icr >> 8);
            case REG_FRCL:
            case REG_OCRAL:
            case REG_OCRBL:
            case REG_ICRL:
                return timer_tempreg;
        }
        return (byte) 0xff;
    }

    void TIMER2_Write(int address, byte data) {
        switch (Dev.valueOf(address)) {
            case DEV_TMR_TCR:
                this.tcr = data;
                break;
            case DEV_TMR_TCSR:
                this.tcsr &= ~0xf;
                this.tcsr |= (byte) (data & 0xf);
                if ((data & 0x20) == 0 && (this.status_rd & 0x20) != 0) {
                    this.tcsr &= ~0x20;
                    this.status_rd &= ~0x20;
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_TIMER_OVI.ordinal(), false);
                }
                if ((data & 0x40) == 0 && (this.status_rd & 0x40) != 0) {
                    this.tcsr &= ~0x40;
                    this.status_rd &= ~0x40;
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_TIMER_CMIA.ordinal(), false);
                }
                if ((data & 0x80) == 0 && (this.status_rd & 0x80) != 0) {
                    this.tcsr &= (byte) ~0x80;
                    this.status_rd &= (byte) ~0x80;
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_TIMER_CMIB.ordinal(), false);
                }
                break;
            case DEV_TMR_TCORA:
                this.tcora = data;
                break;
            case DEV_TMR_TCORB:
                this.tcorb = data;
                break;
            case DEV_TMR_TCNT:
                this.tcnt = data;
                break;
        }
    }

    byte TIMER_Read2(int address) {
        switch (Dev.valueOf(address)) {
            case DEV_TMR_TCR:
                return this.tcr;
            case DEV_TMR_TCSR: {
                byte ret = this.tcsr;
                this.status_rd |= (byte) (this.tcsr & 0xe0);
                return ret;
            }
            case DEV_TMR_TCORA:
                return this.tcora;
            case DEV_TMR_TCORB:
                return this.tcorb;
            case DEV_TMR_TCNT:
                return this.tcnt;
        }
        return (byte) 0xff;
    }

    void TIMER_Clock(long cycles) {
        int i;
//if (mcu.CC >= 57519) { System.err.printf("timer_cycles: %d, cycles: %d%n", timer_cycles, cycles); }
        while (timer_cycles * 2 < cycles) { // FIXME
            for (i = 0; i < 3; i++) {
                frt_t timer = frt[i];
                int offset = 0x10 * i;

                switch (timer.tcr & 3) {
                    case 0: // o / 4
                        if ((timer_cycles & 3) != 0)
                            continue;
                        break;
                    case 1: // o / 8
                        if ((timer_cycles & 7) != 0)
                            continue;
                        break;
                    case 2: // o / 32
                        if ((timer_cycles & 31) != 0)
                            continue;
                        break;
                    case 3: // ext (o / 2)
                        if (mcu.mcu_mk1) {
                            if ((timer_cycles & 3) != 0)
                                continue;
                        } else {
                            if ((timer_cycles & 1) != 0)
                                continue;
                        }
                        break;
                }

                int value = timer.frc & 0xffff;
                boolean matcha = value == (timer.ocra & 0xffff);
                boolean matchb = value == (timer.ocrb & 0xffff);
                if ((timer.tcsr & 1) != 0 && matcha) // CCLRA
                    value = 0;
                else
                    value++;
                int of = (value >> 16) & 1;
                value &= 0xffff;
                timer.frc = (short) value;

                // flags
                if (of != 0)
                    timer.tcsr |= 0x10;
                if (matcha)
                    timer.tcsr |= 0x20;
                if (matchb)
                    timer.tcsr |= 0x40;
                if ((timer.tcr & 0x10) != 0 && (timer.tcsr & 0x10) != 0)
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_FRT0_FOVI.ordinal() + i * 4, true);
//if (mcu.CC > 57519 && mcu.CC < 57583) { System.err.printf("timer_cycles: %d, cycles: %d, t:%d, tcr: %02x, tcsr: %02x%n", timer_cycles, cycles, i, timer.tcr, timer.tcsr); }
                if ((timer.tcr & 0x20) != 0 && (timer.tcsr & 0x20) != 0) {
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_FRT0_OCIA.ordinal() + i * 4, true);
                }
                if ((timer.tcr & 0x40) != 0 && (timer.tcsr & 0x40) != 0)
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_FRT0_OCIB.ordinal() + i * 4, true);
            }

            int timer_step = 0;

            switch (this.tcr & 7) {
                case 0:
                case 4:
                    break;
                case 1: // o / 8
                    if ((timer_cycles & 7) == 0)
                        timer_step = 1;
                    break;
                case 2: // o / 64
                    if ((timer_cycles & 63) == 0)
                        timer_step = 1;
                    break;
                case 3: // o / 1024
                    if ((timer_cycles & 1023) == 0)
                        timer_step = 1;
                    break;
                case 5:
                case 6:
                case 7: // ext (o / 2)
                    if (mcu.mcu_mk1) {
                        if ((timer_cycles & 3) == 0)
                            timer_step = 1;
                    } else {
                        if ((timer_cycles & 1) == 0)
                            timer_step = 1;
                    }
                    break;
            }
            if (timer_step != 0) {
                int value = this.tcnt & 0xff;
                boolean matcha = value == (this.tcora & 0xffff);
                boolean matchb = value == (this.tcorb & 0xffff);
                if ((this.tcr & 24) == 8 && matcha)
                    value = 0;
                else if ((this.tcr & 24) == 16 && matchb)
                    value = 0;
                else
                    value++;
                int of = (value >> 8) & 1;
                value &= 0xff;
                this.tcnt = (byte) value;

                // flags
                if (of != 0)
                    this.tcsr |= 0x20;
                if (matcha)
                    this.tcsr |= 0x40;
                if (matchb)
                    this.tcsr |= (byte) 0x80;
                if ((this.tcr & 0x20) != 0 && (this.tcsr & 0x20) != 0)
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_TIMER_OVI.ordinal(), true);
                if ((this.tcr & 0x40) != 0 && (this.tcsr & 0x40) != 0)
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_TIMER_CMIA.ordinal(), true);
                if ((this.tcr & 0x80) != 0 && (this.tcsr & 0x80) != 0)
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_TIMER_CMIB.ordinal(), true);
            }

            timer_cycles++;
        }
    }
}
