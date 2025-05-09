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

import vavi.sound.sc55.Mcu.Vector;

import static vavi.sound.sc55.Mcu.Dev.DEV_IPRA;
import static vavi.sound.sc55.Mcu.Dev.DEV_IPRB;
import static vavi.sound.sc55.Mcu.Dev.DEV_IPRC;
import static vavi.sound.sc55.Mcu.Dev.DEV_IPRD;
import static vavi.sound.sc55.Mcu.Dev.DEV_P1CR;
import static vavi.sound.sc55.Mcu.Status.STATUS_INT_MASK;
import static vavi.sound.sc55.Mcu.Status.STATUS_T;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_ADDRESS_ERROR;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_94;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_98;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_9C;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_A4;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_A8;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_AC;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_B4;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_B8;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_BC;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_C0;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_C4;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_C8;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_D4;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_D8;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INTERNAL_INTERRUPT_E0;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_INVALID_INSTRUCTION;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_IRQ0;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_IRQ1;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_NMI;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_TRACE;
import static vavi.sound.sc55.Mcu.Vector.VECTOR_TRAPA_0;


class McuInterrupt {

    enum Interrupt {
        INTERRUPT_SOURCE_NMI,
        INTERRUPT_SOURCE_IRQ0, // GPINT
        INTERRUPT_SOURCE_IRQ1,
        INTERRUPT_SOURCE_FRT0_ICI,
        INTERRUPT_SOURCE_FRT0_OCIA,
        INTERRUPT_SOURCE_FRT0_OCIB,
        INTERRUPT_SOURCE_FRT0_FOVI,
        INTERRUPT_SOURCE_FRT1_ICI,
        INTERRUPT_SOURCE_FRT1_OCIA,
        INTERRUPT_SOURCE_FRT1_OCIB,
        INTERRUPT_SOURCE_FRT1_FOVI,
        INTERRUPT_SOURCE_FRT2_ICI,
        INTERRUPT_SOURCE_FRT2_OCIA,
        INTERRUPT_SOURCE_FRT2_OCIB,
        INTERRUPT_SOURCE_FRT2_FOVI,
        INTERRUPT_SOURCE_TIMER_CMIA,
        INTERRUPT_SOURCE_TIMER_CMIB,
        INTERRUPT_SOURCE_TIMER_OVI,
        INTERRUPT_SOURCE_ANALOG,
        INTERRUPT_SOURCE_UART_RX,
        INTERRUPT_SOURCE_UART_TX,
        INTERRUPT_SOURCE_MAX
    }

    enum Exception {
        EXCEPTION_SOURCE_ADDRESS_ERROR,
        EXCEPTION_SOURCE_INVALID_INSTRUCTION,
        EXCEPTION_SOURCE_TRACE,
    }

    private final Mcu mcu;

    McuInterrupt(Mcu mcu) {
        this.mcu = mcu;
    }

    void MCU_Interrupt_Start(int mask) {
        mcu.MCU_PushStack(mcu.pc);
        mcu.MCU_PushStack((short) (mcu.cp & 0xff));
        mcu.MCU_PushStack(mcu.sr);
        mcu.sr &= (short) ~STATUS_T.v;
        if (mask >= 0) {
            mcu.sr &= (short) ~STATUS_INT_MASK.v;
            mcu.sr |= (short) (mask << 8);
        }
        mcu.sleep = false;
    }

    void MCU_Interrupt_SetRequest(int interrupt, boolean value) {
//if (interrupt == 12) { System.err.printf("%d: interrupt: %x, value: %s%n", mcu.CC - 1, interrupt, value); }
        mcu.interrupt_pending[interrupt] = value;
    }

    void MCU_Interrupt_Exception(int exception) {
//#if 0
//        if (interrupt == INTERRUPT_SOURCE_IRQ0.ordinal() && (mcu.dev_register[DEV_P1CR.v] & 0x20) == 0)
//            return;
//        if (interrupt == INTERRUPT_SOURCE_IRQ1.ordinal() && (mcu.dev_register[DEV_P1CR.v] & 0x40) == 0)
//            return;
//#endif
        mcu.exception_pending = exception;
    }

    void MCU_Interrupt_TRAPA(int vector) {
//{ System.err.printf("%d: vector: %x%n", mcu.CC - 1, vector); }
        mcu.trapa_pending[vector] = 1;
    }

    void MCU_Interrupt_StartVector(int vector, int mask) {
        int address = mcu.MCU_GetVectorAddress(vector);
//if (mcu.CC >= 57540) { System.err.printf("address: %x, vector: %x, mask: %x%n", address, vector, mask); }
        MCU_Interrupt_Start(mask);
        mcu.cp = (byte) (address >> 16);
        mcu.pc = (short) address;
    }

    void MCU_Interrupt_Handle() {
//#if 0
//        if (mcu.cycles % 2000 == 0 && mcu.sleep) {
//            MCU_Interrupt_StartVector(VECTOR_INTERNAL_INTERRUPT_94);
//            return;
//        }
//        if (mcu.cycles % 2000 == 1000 && mcu.sleep) {
//            MCU_Interrupt_StartVector(VECTOR_INTERNAL_INTERRUPT_A4);
//            return;
//        }
//        if (mcu.cycles % 2000 == 1500 && mcu.sleep) {
//            MCU_Interrupt_StartVector(VECTOR_INTERNAL_INTERRUPT_B4);
//            return;
//        }
//#endif
        int i;
        for (i = 0; i < 16; i++) {
            if (mcu.trapa_pending[i] != 0) {
                mcu.trapa_pending[i] = 0;
                MCU_Interrupt_StartVector(VECTOR_TRAPA_0.ordinal() + i, -1);
                return;
            }
        }
        if (mcu.exception_pending >= 0) {
            switch (Exception.values()[mcu.exception_pending]) {
                case EXCEPTION_SOURCE_ADDRESS_ERROR:
                    MCU_Interrupt_StartVector(VECTOR_ADDRESS_ERROR.ordinal(), -1);
                    break;
                case EXCEPTION_SOURCE_INVALID_INSTRUCTION:
                    MCU_Interrupt_StartVector(VECTOR_INVALID_INSTRUCTION.ordinal(), -1);
                    break;
                case EXCEPTION_SOURCE_TRACE:
                    MCU_Interrupt_StartVector(VECTOR_TRACE.ordinal(), -1);
                    break;

            }
            mcu.exception_pending = -1;
            return;
        }
        if (mcu.interrupt_pending[Interrupt.INTERRUPT_SOURCE_NMI.ordinal()]) {
            // mcu.interrupt_pending[INTERRUPT_SOURCE_NMI] = 0;
            MCU_Interrupt_StartVector(VECTOR_NMI.ordinal(), 7);
            return;
        }
        int mask = (mcu.sr >> 8) & 7;
        for (i = Interrupt.INTERRUPT_SOURCE_NMI.ordinal() + 1; i < Interrupt.INTERRUPT_SOURCE_MAX.ordinal(); i++) {
            Vector vector = null;
            int level = 0;
            if (!mcu.interrupt_pending[i])
                continue;
            switch (Interrupt.values()[i]) {
                case INTERRUPT_SOURCE_IRQ0:
                    if ((mcu.dev_register[DEV_P1CR.v] & 0x20) == 0)
                        continue;
                    vector = VECTOR_IRQ0;
                    level = (mcu.dev_register[DEV_IPRA.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_IRQ1:
                    if ((mcu.dev_register[DEV_P1CR.v] & 0x40) == 0)
                        continue;
                    vector = VECTOR_IRQ1;
                    level = (mcu.dev_register[DEV_IPRA.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT0_OCIA:
                    vector = VECTOR_INTERNAL_INTERRUPT_94;
                    level = (mcu.dev_register[DEV_IPRB.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT0_OCIB:
                    vector = VECTOR_INTERNAL_INTERRUPT_98;
                    level = (mcu.dev_register[DEV_IPRB.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT0_FOVI:
                    vector = VECTOR_INTERNAL_INTERRUPT_9C;
                    level = (mcu.dev_register[DEV_IPRB.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT1_OCIA:
                    vector = VECTOR_INTERNAL_INTERRUPT_A4;
                    level = (mcu.dev_register[DEV_IPRB.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT1_OCIB:
                    vector = VECTOR_INTERNAL_INTERRUPT_A8;
                    level = (mcu.dev_register[DEV_IPRB.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT1_FOVI:
                    vector = VECTOR_INTERNAL_INTERRUPT_AC;
                    level = (mcu.dev_register[DEV_IPRB.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT2_OCIA:
                    vector = VECTOR_INTERNAL_INTERRUPT_B4;
                    level = (mcu.dev_register[DEV_IPRC.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT2_OCIB:
                    vector = VECTOR_INTERNAL_INTERRUPT_B8;
                    level = (mcu.dev_register[DEV_IPRC.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_FRT2_FOVI:
                    vector = VECTOR_INTERNAL_INTERRUPT_BC;
                    level = (mcu.dev_register[DEV_IPRC.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_TIMER_CMIA:
                    vector = VECTOR_INTERNAL_INTERRUPT_C0;
                    level = (mcu.dev_register[DEV_IPRC.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_TIMER_CMIB:
                    vector = VECTOR_INTERNAL_INTERRUPT_C4;
                    level = (mcu.dev_register[DEV_IPRC.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_TIMER_OVI:
                    vector = VECTOR_INTERNAL_INTERRUPT_C8;
                    level = (mcu.dev_register[DEV_IPRC.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_ANALOG:
                    vector = VECTOR_INTERNAL_INTERRUPT_E0;
                    level = (mcu.dev_register[DEV_IPRD.v] >> 0) & 7;
                    break;
                case INTERRUPT_SOURCE_UART_RX:
                    vector = VECTOR_INTERNAL_INTERRUPT_D4;
                    level = (mcu.dev_register[DEV_IPRD.v] >> 4) & 7;
                    break;
                case INTERRUPT_SOURCE_UART_TX:
                    vector = VECTOR_INTERNAL_INTERRUPT_D8;
                    level = (mcu.dev_register[DEV_IPRD.v] >> 4) & 7;
                    break;
                default:
                    break;
            }

            if (mask < level) {
                // mcu.interrupt_pending[INTERRUPT_SOURCE_NMI] = 0;
                MCU_Interrupt_StartVector(vector != null ? vector.ordinal() : -1, level);
                return;
            }
        }
    }
}