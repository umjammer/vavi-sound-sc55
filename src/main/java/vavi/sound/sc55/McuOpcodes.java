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

import static java.lang.System.getLogger;
import static vavi.sound.sc55.Mcu.Status.STATUS_C;
import static vavi.sound.sc55.Mcu.Status.STATUS_N;
import static vavi.sound.sc55.Mcu.Status.STATUS_V;
import static vavi.sound.sc55.Mcu.Status.STATUS_Z;
import static vavi.sound.sc55.McuInterrupt.Exception.EXCEPTION_SOURCE_ADDRESS_ERROR;
import static vavi.sound.sc55.McuInterrupt.Exception.EXCEPTION_SOURCE_INVALID_INSTRUCTION;
import static vavi.sound.sc55.McuOpcodes.General.GENERAL_ABSOLUTE;
import static vavi.sound.sc55.McuOpcodes.General.GENERAL_DIRECT;
import static vavi.sound.sc55.McuOpcodes.General.GENERAL_IMMEDIATE;
import static vavi.sound.sc55.McuOpcodes.General.GENERAL_INDIRECT;
import static vavi.sound.sc55.McuOpcodes.Operand.OPERAND_BYTE;
import static vavi.sound.sc55.McuOpcodes.Operand.OPERAND_WORD;


class McuOpcodes {

    private static final Logger logger = getLogger(McuOpcodes.class.getName());

    private final Mcu mcu;

    McuOpcodes(Mcu mcu) {
        this.mcu = mcu;
    }

    int MCU_SUB_Common(int t1, int t2, int c_bit, int siz) {
        int st1, st2;
        boolean N, Z, C, V = false;
        if (siz != 0) {
            st1 = (short) t1;
            st2 = (short) t2;
            t1 = t1 & 0xffff;
            t2 = t2 & 0xffff;
            t1 -= t2;
            t1 -= c_bit;
            C = ((t1 >>> 16) & 1) != 0;

            t1 &= 0xffff;
            N = (t1 & 0x8000) != 0;
            Z = t1 == 0;

            st1 -= st2;
            st1 -= c_bit;
            if (st1 < Short.MIN_VALUE || st1 > Short.MAX_VALUE)
                V = true;
        } else {
            st1 = (byte) t1;
            st2 = (byte) t2;
            t1 = t1 & 0xff;
            t2 = t2 & 0xff;
            t1 -= t2;
            t1 -= c_bit;
            C = ((t1 >>> 8) & 1) != 0;

            t1 &= 0xff;
            N = (t1 & 0x80) != 0;
            Z = t1 == 0;

            st1 -= st2;
            st1 -= c_bit;
            if (st1 < Byte.MIN_VALUE || st1 > Byte.MAX_VALUE)
                V = true;
        }
        mcu.MCU_SetStatus(N, STATUS_N.v);
        mcu.MCU_SetStatus(Z, STATUS_Z.v);
        mcu.MCU_SetStatus(C, STATUS_C.v);
        mcu.MCU_SetStatus(V, STATUS_V.v);

        return t1;
    }

    int MCU_ADD_Common(int t1, int t2, int c_bit, int siz) {
        int st1, st2;
        boolean N, Z, C, V = false;
        if (siz != 0) {
            st1 = (short) t1;
            st2 = (short) t2;
            t1 = t1 & 0xffff;
            t2 = t2 & 0xffff;
            t1 += t2;
            t1 += c_bit;
            C = ((t1 >>> 16) & 1) != 0;

            t1 &= 0xffff;
            N = (t1 & 0x8000) != 0;
            Z = t1 == 0;

            st1 += st2;
            st1 += c_bit;
            if (st1 < Short.MIN_VALUE || st1 > Short.MAX_VALUE)
                V = true;
        } else {
            st1 = (byte) t1;
            st2 = (byte) t2;
            t1 = t1 & 0xff;
            t2 = t2 & 0xff;
            t1 += t2;
            t1 += c_bit;
            C = ((t1 >>> 8) & 1) != 0;

            t1 &= 0xff;
            N = (t1 & 0x80) != 0;
            Z = t1 == 0;

            st1 += st2;
            st1 += c_bit;
            if (st1 < Byte.MIN_VALUE || st1 > Byte.MAX_VALUE)
                V = true;
        }
        mcu.MCU_SetStatus(N, STATUS_N.v);
        mcu.MCU_SetStatus(Z, STATUS_Z.v);
        mcu.MCU_SetStatus(C, STATUS_C.v);
        mcu.MCU_SetStatus(V, STATUS_V.v);

        return t1;
    }

    void MCU_Operand_Nop(byte operand) {
    }

    void MCU_Operand_Sleep(byte operand) {
        mcu.sleep = true;
    }

    void MCU_Operand_NotImplemented(byte operand) {
        mcu.MCU_ErrorTrap();
    }

    /**
     * Switch-based dispatch to avoid Consumer interface overhead.
     * This is the HOT PATH - called ~850K times/sec.
     */
    void MCU_DispatchOperand(byte operand) {
        switch (operand & 0xff) {
            case 0x00 -> MCU_Operand_Nop(operand);
            case 0x01, 0x06, 0x07, 0x10, 0x11 -> MCU_Jump_JMP(operand);
            case 0x02 -> MCU_LDM(operand);
            case 0x03 -> MCU_Jump_PJSR(operand);
            case 0x04, 0x05, 0x0C, 0x0D, 0x15, 0x1D -> MCU_Operand_General(operand);
            case 0x08 -> MCU_TRAPA(operand);
            case 0x09, 0x0B, 0x0F, 0x16, 0x17, 0x1B, 0x1F -> MCU_Operand_NotImplemented(operand);
            case 0x0A -> MCU_Jump_RTE(operand);
            case 0x0E, 0x1E -> MCU_Jump_BSR(operand);
            case 0x12 -> MCU_STM(operand);
            case 0x13 -> MCU_Jump_PJMP(operand);
            case 0x14, 0x1C -> MCU_Jump_RTD(operand);
            case 0x18 -> MCU_Jump_JSR(operand);
            case 0x19 -> MCU_Jump_RTS(operand);
            case 0x1A -> MCU_Operand_Sleep(operand);
            case 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
                 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
                 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,
                 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F -> MCU_Jump_Bcc(operand);
            case 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,
                 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F -> MCU_Opcode_Short_CMP(operand);
            case 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57 -> MCU_Opcode_Short_MOVE(operand);
            case 0x58, 0x59, 0x5A, 0x5B, 0x5C, 0x5D, 0x5E, 0x5F -> MCU_Opcode_Short_MOVI(operand);
            case 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,
                 0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F -> MCU_Opcode_Short_MOVL(operand);
            case 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,
                 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7D, 0x7E, 0x7F -> MCU_Opcode_Short_MOVS(operand);
            case 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
                 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F,
                 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97,
                 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F -> MCU_Opcode_Short_MOVF(operand);
            default -> MCU_Operand_General(operand); // 0xA0-0xFF
        }
    }

    enum General {
        GENERAL_DIRECT,
        GENERAL_INDIRECT,
        GENERAL_ABSOLUTE,
        GENERAL_IMMEDIATE
    }

    enum Operand {
        OPERAND_BYTE,
        OPERAND_WORD
    }

    enum Increase {
        INCREASE_NONE,
        INCREASE_DECREASE,
        INCREASE_INCREASE
    }

    void MCU_LDM(byte operand) {
        byte rlist = mcu.MCU_ReadCodeAdvance();
        int i;
        for (i = 0; i < 8; i++) {
            if ((rlist & (1 << i)) != 0) {
                short data = mcu.MCU_PopStack();
                if (i != 7)
                    mcu.r[i] = data;
            }
        }
    }

    void MCU_STM(byte operand) {
        int rlist = mcu.MCU_ReadCodeAdvance() & 0xff;
        int i;
        for (i = 7; i >= 0; i--) {
            if ((rlist & (1 << i)) != 0) {
                short data = mcu.r[i];
                if (i == 7)
                    data -= 2;
                mcu.MCU_PushStack(data);
            }
        }
    }

    void MCU_TRAPA(byte operand) {
        int opcode = mcu.MCU_ReadCodeAdvance() & 0xff;
        if ((opcode & 0xf0) == 0x10) {
            mcu.interrupt.MCU_Interrupt_TRAPA(opcode & 0x0f);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Jump_PJSR(byte operand) {
        int ocp = mcu.cp & 0xff;
        int opc = mcu.pc & 0xffff;
        int page = mcu.MCU_ReadCodeAdvance() & 0xff;
        int address = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
        address |= (mcu.MCU_ReadCodeAdvance() & 0xff);
        mcu.MCU_PushStack(mcu.pc);
        mcu.MCU_PushStack((short) (mcu.cp & 0xff));
        mcu.cp = (byte) page;
        if (mcu.cp == 0x27)
            mcu.cp += 0;
        mcu.pc = (short) address;
    }

    void MCU_Jump_JSR(byte operand) {
        int address = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
        address |= (mcu.MCU_ReadCodeAdvance() & 0xff);
        mcu.MCU_PushStack(mcu.pc);
        mcu.pc = (short) address;
    }

    void MCU_Jump_RTE(byte operand) {
        mcu.sr = mcu.MCU_PopStack();
        mcu.cp = (byte) mcu.MCU_PopStack();
        mcu.pc = mcu.MCU_PopStack();
        mcu.ex_ignore = true;
    }

    void MCU_Jump_Bcc(byte operand) {
        int disp;
        int cond;
        boolean branch = false;
        boolean N, C, Z, V;
        if ((operand & 0x10) != 0) {
            disp = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
            disp |= (mcu.MCU_ReadCodeAdvance() & 0xff);
        } else {
            disp = mcu.MCU_ReadCodeAdvance();
        }
        cond = operand & 0x0f;

        N = (mcu.sr & STATUS_N.v) != 0;
        C = (mcu.sr & STATUS_C.v) != 0;
        Z = (mcu.sr & STATUS_Z.v) != 0;
        V = (mcu.sr & STATUS_V.v) != 0;
//logger.log(Level.DEBUG, "disp: %04x, cond: %x, N:%s, C:%s, Z:%s, V:%s".formatted(disp & 0xffff, cond, N ? "+" : "-", C ? "+" : "-", Z ? "+" : "-", V ? "+" : "-"));

        switch (cond) {
            case 0x0: // BRA/BT
                branch = true;
                break;
            case 0x1: // BRN/BF
                branch = false;
                break;
            case 0x2: // BHI
                branch = !(C | Z);
                break;
            case 0x3: // BLS
                branch = (C | Z);
                break;
            case 0x4: // BCC/BHS
                branch = !C;
                break;
            case 0x5: // BCS/BLO
                branch = C;
                break;
            case 0x6: // BNE
                branch = !Z;
                break;
            case 0x7: // BEQ
                branch = Z;
                break;
            case 0x8: // BVC
                branch = !V;
                break;
            case 0x9: // BVS
                branch = V;
                break;
            case 0xa: // BPL
                branch = !N;
                break;
            case 0xb: // BMI
                branch = N;
                break;
            case 0xc: // BGE
                branch = !(N ^ V);
                break;
            case 0xd: // BLT
                branch = (N ^ V);
                break;
            case 0xe: // BGT
                branch = !(Z | (N ^ V));
                break;
            case 0xf: // BLE
                branch = (Z | (N ^ V));
                break;
        }

        if (branch) {
            mcu.pc += (short) disp;
        }
    }

    void MCU_Jump_RTS(byte operand) {
        mcu.pc = mcu.MCU_PopStack();
    }

    void MCU_Jump_RTD(byte operand) {
        int imm = mcu.MCU_ReadCodeAdvance(); // signed
        mcu.pc = mcu.MCU_PopStack();

        if (operand == 0x14) {
            mcu.r[7] += (short) imm;
            if ((mcu.r[7] & 1) != 0)
                mcu.MCU_ErrorTrap();
        } else if (operand == 0x1c) {
            // TODO
            mcu.MCU_ErrorTrap();
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Jump_JMP(byte operand) {
        if (operand == 0x11) {
            int opcode = mcu.MCU_ReadCodeAdvance() & 0xff;
            int opcode_h = opcode >>> 3;
            int opcode_l = opcode & 0x07;
            if (opcode == 0x19) {
                mcu.cp = (byte) mcu.MCU_PopStack();
                mcu.pc = mcu.MCU_PopStack();
            } else if (opcode_h == 0x19) {
                mcu.MCU_PushStack(mcu.pc);
                mcu.MCU_PushStack((short) (mcu.cp & 0xff));
                opcode_l &= ~1;
                mcu.cp = (byte) (mcu.r[opcode_l] & 0xff);
                mcu.pc = mcu.r[opcode_l + 1];
            } else if (opcode_h == 0x1a) {
                mcu.pc = mcu.r[opcode_l];
            } else if (opcode_h == 0x1b) {
                mcu.MCU_PushStack(mcu.pc);
                mcu.pc = mcu.r[opcode_l];
            } else {
                mcu.MCU_ErrorTrap();
            }
        } else if (operand == 0x01) {
            int opcode = mcu.MCU_ReadCodeAdvance() & 0xff;
            int reg = opcode & 0x07;
            opcode >>>= 3;
            if (opcode == 0x17) {
                int disp = mcu.MCU_ReadCodeAdvance(); // signed
                mcu.r[reg]--;
                if (mcu.r[reg] != (short) 0xffff) {
                    mcu.pc += (short) disp;
                }
            } else {
                mcu.MCU_ErrorTrap();
            }
        } else if (operand == 0x10) {
            int addr = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
            addr |= (mcu.MCU_ReadCodeAdvance() & 0xff);
            mcu.pc = (short) addr;
        } else if (operand == 0x06) {
            int opcode = mcu.MCU_ReadCodeAdvance() & 0xff;
            int reg = opcode & 0x07;
            opcode >>>= 3;
            if (opcode == 0x17) {
                int disp = mcu.MCU_ReadCodeAdvance(); // signed
                boolean Z = (mcu.sr & STATUS_Z.v) != 0;
                if (Z) {
                    mcu.r[reg]--;
                    if (mcu.r[reg] != (short) 0xffff) {
                        mcu.pc += (short) disp;
                    }
                }
            } else {
                mcu.MCU_ErrorTrap();
            }
        } else if (operand == 0x07) {
            int opcode = mcu.MCU_ReadCodeAdvance() & 0xff;
            int reg = opcode & 0x07;
            opcode >>>= 3;
            if (opcode == 0x17) {
                int disp = mcu.MCU_ReadCodeAdvance(); // signed
                boolean Z = (mcu.sr & STATUS_Z.v) != 0;
                if (!Z) {
                    mcu.r[reg]--;
                    if (mcu.r[reg] != (short) 0xffff) {
                        mcu.pc += (short) disp;
                    }
                }
            } else {
                mcu.MCU_ErrorTrap();
            }
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Jump_BSR(byte operand) {
        int disp;
        if (operand == 0x0e) {
            disp = mcu.MCU_ReadCodeAdvance(); // signed
        } else {
            disp = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
            disp |= (mcu.MCU_ReadCodeAdvance() & 0xff);
        }
        mcu.MCU_PushStack(mcu.pc);
        mcu.pc += (short) disp;
    }

    void MCU_Jump_PJMP(byte operand) {
        int page = mcu.MCU_ReadCodeAdvance() & 0xff;
        int address = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
        address |= (mcu.MCU_ReadCodeAdvance() & 0xff);
        mcu.cp = (byte) page;
        mcu.pc = (short) address;
    }

    int operand_type;
    short operand_ea;
    byte operand_ep;
    byte operand_size;
    byte operand_reg;
    byte operand_status;
    short operand_data;
    boolean opcode_extended;

    int MCU_Operand_Read() {
        switch (General.values()[operand_type]) {
            case GENERAL_DIRECT:
                if (operand_size != 0)
                    return mcu.r[operand_reg] & 0xffff;
                return mcu.r[operand_reg] & 0xff; // return byte size
            case GENERAL_INDIRECT:
            case GENERAL_ABSOLUTE:
                if (operand_size != 0) {
                    if ((operand_ea & 1) != 0) {
                        mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
                    }
                    return mcu.MCU_Read16(mcu.MCU_GetAddress(operand_ep, operand_ea)) & 0xffff;
                }
                return mcu.MCU_Read(mcu.MCU_GetAddress(operand_ep, operand_ea)) & 0xff;
            case GENERAL_IMMEDIATE:
                return operand_data & 0xffff;
        }
        return 0;
    }

    void MCU_Operand_Write(int data) {
        switch (General.values()[operand_type]) {
            case GENERAL_DIRECT:
                if (operand_size != 0)
                    mcu.r[operand_reg] = (short) (data & 0xffff);
                else {
                    mcu.r[operand_reg] &= ~0xff;
                    mcu.r[operand_reg] |= (short) (data & 0xff);
                }
                break;
            case GENERAL_INDIRECT:
            case GENERAL_ABSOLUTE:
                if (operand_size != 0) {
                    if ((operand_ea & 1) != 0) {
                        mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
                    }
                    mcu.MCU_Write16(mcu.MCU_GetAddress(operand_ep, operand_ea), (short) data);
                } else
                    mcu.MCU_Write(mcu.MCU_GetAddress(operand_ep, operand_ea), (byte) data);
                break;
            case GENERAL_IMMEDIATE:
                mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_INVALID_INSTRUCTION.ordinal());
                break;
        }
    }

    void MCU_Operand_General(byte operand) {
        int type = GENERAL_DIRECT.ordinal();
        int disp = 0;
        int increase = Increase.INCREASE_NONE.ordinal();
        int absolute = 0;
        int reg = 0;
        int siz = Operand.OPERAND_BYTE.ordinal();
        int data = 0;
        int addr = 0;
        int addrpage = 0;
        int ea = 0;
        int ep = 0;
        int opcode;
        byte opcode_reg;
        if ((operand & 0x08) != 0)
            siz = OPERAND_WORD.ordinal();
        else
            siz = Operand.OPERAND_BYTE.ordinal();
        reg = operand & 0x07;
        switch (operand & 0xf0) {
            case 0xa0:
                type = GENERAL_DIRECT.ordinal();
                break;
            case 0xd0:
                type = GENERAL_INDIRECT.ordinal();
                break;
            case 0xe0:
                type = GENERAL_INDIRECT.ordinal();
                disp = mcu.MCU_ReadCodeAdvance(); // signed
                break;
            case 0xf0:
                type = GENERAL_INDIRECT.ordinal();
                disp = mcu.MCU_ReadCodeAdvance() & 0xff;
                disp <<= 8;
                disp |= mcu.MCU_ReadCodeAdvance() & 0xff;
                break;
            case 0xb0:
                type = GENERAL_INDIRECT.ordinal();
                increase = Increase.INCREASE_DECREASE.ordinal();
                break;
            case 0xc0:
                type = GENERAL_INDIRECT.ordinal();
                increase = Increase.INCREASE_INCREASE.ordinal();
                break;
            case 0x00:
                if (reg == 5) {
                    type = GENERAL_ABSOLUTE.ordinal();
                    addr = (mcu.br & 0xff) << 8;
                    addr |= mcu.MCU_ReadCodeAdvance() & 0xff;
                    addrpage = 0;
                } else if (reg == 4) {
                    type = GENERAL_IMMEDIATE.ordinal();
                    data = mcu.MCU_ReadCodeAdvance() & 0xff;
                    if (siz != 0) {
                        data <<= 8;
                        data |= mcu.MCU_ReadCodeAdvance() & 0xff;
                    }
                }
                break;
            case 0x10:
                if (reg == 5) {
                    type = GENERAL_ABSOLUTE.ordinal();
                    addr = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
                    addr |= (mcu.MCU_ReadCodeAdvance() & 0xff);
                    addrpage = mcu.dp & 0xff;
                }
                break;
        }
        if (type == GENERAL_INDIRECT.ordinal()) {
            if (increase == Increase.INCREASE_DECREASE.ordinal()) {
                if (siz != 0 || reg == 7) {
                    mcu.r[reg] -= 2;
                } else {
                    mcu.r[reg] -= 1;
                }
            }
            ea = (mcu.r[reg] & 0xffff) + disp;
            if (increase == Increase.INCREASE_INCREASE.ordinal()) {
                if (siz != 0 || reg == 7) {
                    mcu.r[reg] += 2;
                } else {
                    mcu.r[reg] += 1;
                }
            }

            ea &= 0xffff;

            ep = mcu.MCU_GetPageForRegister(reg) & 0xff;
        } else if (type == GENERAL_ABSOLUTE.ordinal()) {
            ea = addr & 0xffff;

            ep = addrpage & 0xff;
        }

        opcode = mcu.MCU_ReadCodeAdvance() & 0xff;
        opcode_extended = opcode == 0x00;
        if (opcode_extended) {
            opcode = mcu.MCU_ReadCodeAdvance() & 0xff;
        }
        opcode_reg = (byte) (opcode & 0x07);
        opcode >>>= 3;

        operand_type = type;
        operand_ea = (short) ea;
        operand_ep = (byte) ep;
        operand_size = (byte) siz;
        operand_reg = (byte) reg;
        operand_data = (short) data;
        operand_status = 0;

        MCU_DispatchOpcode((byte) opcode, opcode_reg);  // Switch-based dispatch (no interface overhead)
    }

    /**
     * Switch-based dispatch for MCU_Opcode_Table (inside MCU_Operand_General).
     */
    void MCU_DispatchOpcode(byte opcode, byte opcode_reg) {
        switch (opcode & 0x1f) {
            case 0x00 -> MCU_Opcode_MOVG_Immediate(opcode, opcode_reg);
            case 0x01 -> MCU_Opcode_ADDQ(opcode, opcode_reg);
            case 0x02 -> MCU_Opcode_CLR(opcode, opcode_reg);
            case 0x03 -> MCU_Opcode_SHLR(opcode, opcode_reg);
            case 0x04 -> MCU_Opcode_ADD(opcode, opcode_reg);
            case 0x05 -> MCU_Opcode_ADDS(opcode, opcode_reg);
            case 0x06 -> MCU_Opcode_SUB(opcode, opcode_reg);
            case 0x07 -> MCU_Opcode_SUBS(opcode, opcode_reg);
            case 0x08 -> MCU_Opcode_OR(opcode, opcode_reg);
            case 0x09 -> MCU_Opcode_BSET_ORC(opcode, opcode_reg);
            case 0x0A -> MCU_Opcode_AND(opcode, opcode_reg);
            case 0x0B -> MCU_Opcode_BCLR_ANDC(opcode, opcode_reg);
            case 0x0C -> MCU_Opcode_XOR(opcode, opcode_reg);
            case 0x0D -> MCU_Opcode_NotImplemented(opcode, opcode_reg);
            case 0x0E -> MCU_Opcode_CMP(opcode, opcode_reg);
            case 0x0F -> MCU_Opcode_BTST(opcode, opcode_reg);
            case 0x10, 0x12 -> MCU_Opcode_MOVG(opcode, opcode_reg);
            case 0x11 -> MCU_Opcode_LDC(opcode, opcode_reg);
            case 0x13 -> MCU_Opcode_STC(opcode, opcode_reg);
            case 0x14 -> MCU_Opcode_ADDX(opcode, opcode_reg);
            case 0x15 -> MCU_Opcode_MULXU(opcode, opcode_reg);
            case 0x16 -> MCU_Opcode_SUBX(opcode, opcode_reg);
            case 0x17 -> MCU_Opcode_DIVXU(opcode, opcode_reg);
            case 0x18, 0x19 -> MCU_Opcode_BSET(opcode, opcode_reg);
            case 0x1A, 0x1B -> MCU_Opcode_BCLR(opcode, opcode_reg);
            case 0x1C, 0x1D -> MCU_Opcode_BNOTI(opcode, opcode_reg);
            case 0x1E, 0x1F -> MCU_Opcode_BTSTI(opcode, opcode_reg);
            default -> MCU_Opcode_NotImplemented(opcode, opcode_reg);
        }
    }

    void MCU_SetStatusCommon(int val, int siz) {
        if (siz != 0)
            val &= 0xffff;
        else
            val &= 0xff;
        if (siz != 0)
            mcu.MCU_SetStatus((val & 0x8000) != 0, STATUS_N.v);
        else
            mcu.MCU_SetStatus((val & 0x80) != 0, STATUS_N.v);
        mcu.MCU_SetStatus(val == 0, STATUS_Z.v);
        mcu.MCU_SetStatus(false, STATUS_V.v);
    }

    void MCU_Opcode_Short_NotImplemented(byte opcode) {
        mcu.MCU_ErrorTrap();
    }

    void MCU_Opcode_Short_MOVE(byte opcode) {
        int reg = opcode & 0x07;
        int data = mcu.MCU_ReadCodeAdvance() & 0xff;
        mcu.r[reg] &= ~0xff;
        mcu.r[reg] |= (short) (data & 0xff);
        MCU_SetStatusCommon(data, 0);
    }

    void MCU_Opcode_Short_MOVI(byte opcode) {
        int reg = opcode & 0x07;
        int data = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
        data |= (mcu.MCU_ReadCodeAdvance() & 0xff);
        mcu.r[reg] = (short) data;
        MCU_SetStatusCommon(data, 1);
    }

    void MCU_Opcode_Short_MOVF(byte opcode) {
        int reg = opcode & 0x07;
        int siz = (opcode & 0x08) != 0 ? 1 : 0;
        int disp = mcu.MCU_ReadCodeAdvance(); // signed
        int addr = ((mcu.r[6] & 0xffff) + disp) & 0xffff;
        addr |= (mcu.tp & 0xff) << 16;
        if ((opcode & 0x10) == 0) {
            int data;
            if (siz != 0) {
                data = mcu.MCU_Read16(addr) & 0xffff;
                mcu.r[reg] &= ~0xff;
                mcu.r[reg] |= (short) data;
                MCU_SetStatusCommon(data, 0);
            } else {
                data = mcu.MCU_Read(addr) & 0xff;
                mcu.r[reg] = (short) data;
                MCU_SetStatusCommon(data, 1);
            }
        } else {
            int data;
            if (siz != 0) {
                data = mcu.r[reg] & 0xff; // byte size
                mcu.MCU_Write(addr, (byte) data);
                MCU_SetStatusCommon(data, 0);
            } else {
                data = mcu.r[reg] & 0xffff;
                mcu.MCU_Write16(addr, (short) data);
                MCU_SetStatusCommon(data, 1);
            }
        }
    }

    void MCU_Opcode_Short_MOVL(byte opcode) {
        int reg = opcode & 0x07;
        boolean siz = (opcode & 0x08) != 0;
        short addr = (short) ((mcu.br & 0xff) << 8);
        int data;
        addr |= (short) (mcu.MCU_ReadCodeAdvance() & 0xff);
        if (siz) {
            if ((addr & 1) != 0)
                mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
            data = mcu.MCU_Read16(addr & 0xffff) & 0xffff;
            mcu.r[reg] = (short) data;
            MCU_SetStatusCommon(data, 1);
        } else {
            data = mcu.MCU_Read(addr & 0xffff) & 0xff;
            mcu.r[reg] &= ~0xff;
            mcu.r[reg] |= (short) data;
            MCU_SetStatusCommon(data, 0);
        }
    }

    void MCU_Opcode_Short_MOVS(byte opcode) {
        int reg = opcode & 0x07;
        boolean siz = (opcode & 0x08) != 0;
        short addr = (short) ((mcu.br & 0xff) << 8);
        int data;
        addr |= (short) (mcu.MCU_ReadCodeAdvance() & 0xff);
        if (siz) {
            if ((addr & 1) != 0)
                mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
            data = mcu.r[reg] & 0xffff;
            mcu.MCU_Write16(addr & 0xffff, (short) data);
            MCU_SetStatusCommon(data, 1);
        } else {
            data = mcu.r[reg] & 0xff; // byte size
            mcu.MCU_Write(addr & 0xffff, (byte) data);
            MCU_SetStatusCommon(data, 0);
        }
    }

    void MCU_Opcode_Short_CMP(byte opcode) {
        int reg = opcode & 0x07;
        int siz = (opcode & 0x08) != 0 ? 1 : 0;
        int t1, t2;
        if (siz != 0) {
            t2 = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
            t2 |= (mcu.MCU_ReadCodeAdvance() & 0xff);
        } else {
            t2 = mcu.MCU_ReadCodeAdvance() & 0xff;
        }
        t1 = mcu.r[reg] & 0xffff;
        MCU_SUB_Common(t1, t2, 0, siz);
    }

    void MCU_Opcode_NotImplemented(byte opcode, byte opcode_reg) {
        mcu.MCU_ErrorTrap();
    }

    void MCU_Opcode_MOVG_Immediate(byte opcode, byte opcode_reg) {
        int data;
        if (opcode_reg == 6 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal())) {
            data = mcu.MCU_ReadCodeAdvance(); // signed
            MCU_Operand_Write(data);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 7 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal())) {
            data = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
            data |= (mcu.MCU_ReadCodeAdvance() & 0xff);
            MCU_Operand_Write(data);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 4 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == Operand.OPERAND_BYTE.ordinal()) {
            int t1 = MCU_Operand_Read();
            int t2 = mcu.MCU_ReadCodeAdvance() & 0xff;
            MCU_SUB_Common(t1, t2, 0, Operand.OPERAND_BYTE.ordinal());
        } else if (opcode_reg == 4 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == OPERAND_WORD.ordinal()) { // FIXME
            int t1 = MCU_Operand_Read();
            int t2 = ((int) mcu.MCU_ReadCodeAdvance()) & 0xffff;
            MCU_SUB_Common(t1, t2, 0, OPERAND_WORD.ordinal());
        } else if (opcode_reg == 5 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == OPERAND_WORD.ordinal()) {
            int t1, t2;
            t1 = MCU_Operand_Read();
            t2 = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
            t2 |= (mcu.MCU_ReadCodeAdvance() & 0xff);
            MCU_SUB_Common(t1, t2, 0, OPERAND_WORD.ordinal());
        } else if (opcode_reg == 5 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == OPERAND_BYTE.ordinal()) { // FIXME
            int t1, t2;
            t1 = MCU_Operand_Read();
            t2 = (mcu.MCU_ReadCodeAdvance() & 0xff) << 8;
            t2 |= (mcu.MCU_ReadCodeAdvance() & 0xff);
            MCU_SUB_Common(t1, t2, 0, OPERAND_BYTE.ordinal());
        } else {
logger.log(Level.DEBUG, "opcode_reg: %d, operand_type: %s".formatted(opcode_reg, operand_type));
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_BSET_ORC(byte opcode, byte opcode_reg) {
        if (operand_type == GENERAL_IMMEDIATE.ordinal()) { // ORC
            int data = MCU_Operand_Read();
            int val = mcu.MCU_ControlRegisterRead(opcode_reg, operand_size);
            val |= data;
            mcu.MCU_ControlRegisterWrite(opcode_reg, operand_size, val);
            if (opcode_reg >= 2) {
                MCU_SetStatusCommon(val, operand_size);
            }
            mcu.ex_ignore = true;
        } else { // BSET
            int data = MCU_Operand_Read();
            int bit = mcu.r[opcode_reg] & 0x0f;
            mcu.MCU_SetStatus((data & (1 << bit)) == 0, STATUS_Z.v);
            data |= 1 << bit;
            MCU_Operand_Write(data);
        }
    }

    void MCU_Opcode_BCLR_ANDC(byte opcode, byte opcode_reg) {
        if (operand_type == GENERAL_IMMEDIATE.ordinal()) { // ANDC
            int data = MCU_Operand_Read();
            int val = mcu.MCU_ControlRegisterRead(opcode_reg, operand_size);
            val &= data;
            mcu.MCU_ControlRegisterWrite(opcode_reg, operand_size, val);
            if (opcode_reg >= 2) {
                MCU_SetStatusCommon(val, operand_size);
            }
            mcu.ex_ignore = true;
        } else { // BCLR
            int data = MCU_Operand_Read();
            int bit = mcu.r[opcode_reg] & 0x0f;
            mcu.MCU_SetStatus((data & (1 << bit)) == 0, STATUS_Z.v);
            data &= ~(1 << bit);
            MCU_Operand_Write(data);
        }
    }

    void MCU_Opcode_BTST(byte opcode, byte opcode_reg) {
        if (operand_type != GENERAL_IMMEDIATE.ordinal()) {
            int data = MCU_Operand_Read();
            int bit = mcu.r[opcode_reg] & 0x0f;
            mcu.MCU_SetStatus((data & (1 << bit)) == 0, STATUS_Z.v);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_CLR(byte opcode, byte opcode_reg) {
        if (opcode_reg == 3 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // CLR
            MCU_Operand_Write(0);
            mcu.MCU_SetStatus(false, STATUS_N.v);
            mcu.MCU_SetStatus(true, STATUS_Z.v);
            mcu.MCU_SetStatus(false, STATUS_V.v);
            mcu.MCU_SetStatus(false, STATUS_C.v);
        } else if (opcode_reg == 6 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // TST
            int data = MCU_Operand_Read();
            MCU_SetStatusCommon(data, operand_size);
            mcu.MCU_SetStatus(false, STATUS_C.v);
        } else if (opcode_reg == 2 && operand_type == GENERAL_DIRECT.ordinal() && operand_size == 0) { // EXTU
            int data = mcu.r[operand_reg] & 0xff; // byte size
            mcu.r[operand_reg] = (short) data;
            mcu.MCU_SetStatus(false, STATUS_N.v);
            mcu.MCU_SetStatus(data == 0, STATUS_Z.v);
            mcu.MCU_SetStatus(false, STATUS_V.v);
            mcu.MCU_SetStatus(false, STATUS_C.v);
        } else if (opcode_reg == 0 && operand_type == GENERAL_DIRECT.ordinal() && operand_size == 0) { // SWAP
            int data = mcu.r[operand_reg] & 0xffff;
            int data_h = (data >>> 8) & 0xff;
            int data_l = data & 0xff;
            data = (data_l << 8) | data_h;
            mcu.r[operand_reg] = (short) data;
            MCU_SetStatusCommon(data, OPERAND_WORD.ordinal());
        } else if (opcode_reg == 5 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // NOT
            int data = MCU_Operand_Read();
            data = ~data;
            MCU_Operand_Write(data);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 4 && operand_type != GENERAL_IMMEDIATE.ordinal()) // NEG
        {
            int data = MCU_Operand_Read();
            data = MCU_SUB_Common(0, data, 0, operand_size);
            MCU_Operand_Write(data);
        } else if (opcode_reg == 1 && operand_type == GENERAL_DIRECT.ordinal() && operand_size == 0) { // EXTS
            int data = mcu.r[operand_reg] & 0xffff;
            mcu.r[operand_reg] = (byte) data;
            MCU_SetStatusCommon(data, OPERAND_WORD.ordinal());
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_LDC(byte opcode, byte opcode_reg) {
        int data = MCU_Operand_Read();
        mcu.MCU_ControlRegisterWrite(opcode_reg, operand_size, data);
        mcu.ex_ignore = true;
    }

    void MCU_Opcode_STC(byte opcode, byte opcode_reg) {
        int data = mcu.MCU_ControlRegisterRead(opcode_reg, operand_size);
        MCU_Operand_Write(data);
    }

    void MCU_Opcode_BSET(byte opcode, byte opcode_reg) {
        if (operand_type != GENERAL_IMMEDIATE.ordinal()) {
            int data = MCU_Operand_Read();
            int bit = opcode_reg | ((opcode & 1) << 3);
            mcu.MCU_SetStatus((data & (1 << bit)) == 0, STATUS_Z.v);
            data |= 1 << bit;
            MCU_Operand_Write(data);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_BCLR(byte opcode, byte opcode_reg) {
        if (operand_type != GENERAL_IMMEDIATE.ordinal()) {
            int data = MCU_Operand_Read();
            int bit = opcode_reg | ((opcode & 1) << 3);
            mcu.MCU_SetStatus((data & (1 << bit)) == 0, STATUS_Z.v);
            data &= ~(1 << bit);
            MCU_Operand_Write(data);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_MOVG(byte opcode, byte opcode_reg) {
        if (opcode_extended) {
            if (opcode == 0x12) {
                // FIXME
                mcu.MCU_ErrorTrap();
            } else {
                mcu.MCU_ErrorTrap();
            }
        } else {
            boolean d = (opcode & 2) != 0;
            int data;
            if (d) {
                if (operand_type == GENERAL_DIRECT.ordinal()) { // XCH
                    if (operand_size != 0) {
                        int r1 = mcu.r[opcode_reg] & 0xffff;
                        int r2 = mcu.r[operand_reg] & 0xffff;
                        mcu.r[opcode_reg] = (short) r2;
                        mcu.r[operand_reg] = (short) r1;
                    } else {
                        mcu.MCU_ErrorTrap();
                    }
                } else {
                    data = mcu.r[opcode_reg] & 0xffff;
                    MCU_Operand_Write(data);
                    MCU_SetStatusCommon(data, operand_size);
                }
            } else {
                data = MCU_Operand_Read();
                if (operand_size != 0)
                    mcu.r[opcode_reg] = (short) data;
                else {
                    mcu.r[opcode_reg] &= ~0xff;
                    mcu.r[opcode_reg] |= (short) (data & 0xff);
                }
                MCU_SetStatusCommon(data, operand_size);
            }
        }
    }

    void MCU_Opcode_BTSTI(byte opcode, byte opcode_reg) {
        if (operand_type != GENERAL_IMMEDIATE.ordinal()) {
            int data = MCU_Operand_Read();
            int bit = opcode_reg | ((opcode & 1) << 3);
//logger.log(Level.DEBUG, "data: %02x, bit: %02x".formatted(data & 0xff, bit & 0xff));
            mcu.MCU_SetStatus((data & (1 << bit)) == 0, STATUS_Z.v);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_BNOTI(byte opcode, byte opcode_reg) {
        if (operand_type != GENERAL_IMMEDIATE.ordinal()) {
            int data = MCU_Operand_Read();
            int bit = opcode_reg | ((opcode & 1) << 3);
            mcu.MCU_SetStatus((data & (1 << bit)) == 0, STATUS_Z.v);
            data ^= (1 << bit);
            MCU_Operand_Write(data);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_OR(byte opcode, byte opcode_reg) {
        int data = MCU_Operand_Read();
        mcu.r[opcode_reg] |= (short) data;
        MCU_SetStatusCommon(mcu.r[opcode_reg], operand_size);
    }

    void MCU_Opcode_CMP(byte opcode, byte opcode_reg) {
        int t1 = mcu.r[opcode_reg] & 0xffff;
        int t2 = MCU_Operand_Read();
        MCU_SUB_Common(t1, t2, 0, operand_size);
    }

    void MCU_Opcode_ADDQ(byte opcode, byte opcode_reg) {
        int t1 = MCU_Operand_Read();
        int t2 = 0;
        switch (opcode_reg) {
            case 0:
                t2 = 1;
                break;
            case 1:
                t2 = 2;
                break;
            case 4:
                t2 = -1;
                break;
            case 5:
                t2 = -2;
                break;
            default:
                mcu.MCU_ErrorTrap();
                break;
        }
        t1 = MCU_ADD_Common(t1, t2, 0, operand_size);
        MCU_Operand_Write(t1);
    }

    void MCU_Opcode_ADD(byte opcode, byte opcode_reg) {
        int t1 = mcu.r[opcode_reg] & 0xffff;
        int t2 = MCU_Operand_Read();
        t1 = MCU_ADD_Common(t1, t2, 0, operand_size);
        if (operand_size != 0)
            mcu.r[opcode_reg] = (short) t1;
        else {
            mcu.r[opcode_reg] &= ~0xff;
            mcu.r[opcode_reg] |= (short) (t1 & 0xff);
        }
    }

    void MCU_Opcode_SUB(byte opcode, byte opcode_reg) {
        int t1 = mcu.r[opcode_reg] & 0xffff;
        int t2 = MCU_Operand_Read();
        t1 = MCU_SUB_Common(t1, t2, 0, operand_size);
        if (operand_size != 0)
            mcu.r[opcode_reg] = (short) t1;
        else {
            mcu.r[opcode_reg] &= ~0xff;
            mcu.r[opcode_reg] |= (short) (t1 & 0xff);
        }
    }

    void MCU_Opcode_SUBS(byte opcode, byte opcode_reg) {
        int t1 = mcu.r[opcode_reg] & 0xffff;
        int t2 = MCU_Operand_Read();
        if (operand_size != 0)
            mcu.r[opcode_reg] = (short) (t1 - t2);
        else
            mcu.r[opcode_reg] = (short) (t1 - (byte) t2);
    }

    void MCU_Opcode_AND(byte opcode, byte opcode_reg) {
        int data = mcu.r[opcode_reg] & 0xffff;
        data &= MCU_Operand_Read();
        if (operand_size != 0)
            mcu.r[opcode_reg] = (short) data;
        else {
            mcu.r[opcode_reg] &= ~0xff;
            mcu.r[opcode_reg] |= data & 0xff;
        }
        MCU_SetStatusCommon(mcu.r[opcode_reg], operand_size);
    }

    void MCU_Opcode_SHLR(byte opcode, byte opcode_reg) {
        if (opcode_reg == 0x03 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // SHLR
            int data = MCU_Operand_Read();
            boolean C = (data & 1) != 0;
            data >>>= 1;
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 0x02 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // SHLL
            int data = MCU_Operand_Read();
            boolean C;
            if (operand_size != 0)
                C = (data & 0x8000) != 0;
            else
                C = (data & 0x80) != 0;
            data <<= 1;
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 0x06 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // ROTXL
            int data = MCU_Operand_Read();
            int bit = (mcu.sr & STATUS_C.v) != 0 ? 1 : 0;
            boolean C;
            if (operand_size != 0)
                C = (data & 0x8000) != 0;
            else
                C = (data & 0x80) != 0;
            data <<= 1;
            data |= bit;
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 0x04 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // ROTL
            int data = MCU_Operand_Read();
            boolean C;
            if (operand_size != 0)
                C = (data & 0x8000) != 0;
            else
                C = (data & 0x80) != 0;
            data <<= 1;
            data |= (C ? 1 : 0);
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 0x00 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // SHAL
            int data = MCU_Operand_Read();
            boolean C;
            if (operand_size != 0)
                C = (data & 0x8000) != 0;
            else
                C = (data & 0x80) != 0;
            data <<= 1;
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 0x01 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // SHAR
            int data = MCU_Operand_Read();
            boolean C = (data & 0x1) != 0;
            int msb;
            if (operand_size != 0) {
                msb = data & 0x8000;
                data &= 0xffff;
            } else {
                msb = data & 0x80;
                data &= 0xff;
            }
            data >>>= 1;
            data |= msb;
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 0x05 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // ROTR
            int data = MCU_Operand_Read();
            boolean C = (data & 0x1) != 0;
            data >>>= 1;
            if (operand_size != 0)
                data |= (C ? 1 : 0) << 15;
            else
                data |= (C ? 1 : 0) << 7;
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Opcode_MULXU(byte opcode, byte opcode_reg) {
        int t1 = MCU_Operand_Read();
        int t2 = mcu.r[opcode_reg] & 0xffff;
        boolean N, Z;
        if (operand_size == 0)
            t2 &= 0xff;
        t1 *= t2;

        if (operand_size != 0) {
            opcode_reg &= ~1;
            mcu.r[opcode_reg | 0] = (short) (t1 >>> 16);
            mcu.r[opcode_reg | 1] = (short) t1;
            N = (t1 & 0x8000_0000L) != 0; // FIXME
        } else {
            t1 &= 0xffff;
            mcu.r[opcode_reg] = (short) t1;
            N = (t1 & 0x8000L) != 0; // FIXME
        }
        Z = t1 == 0;
        mcu.MCU_SetStatus(N, STATUS_N.v);
        mcu.MCU_SetStatus(Z, STATUS_Z.v);
        mcu.MCU_SetStatus(false, STATUS_V.v);
        mcu.MCU_SetStatus(false, STATUS_C.v);
    }

    void MCU_Opcode_DIVXU(byte opcode, byte opcode_reg) {
        int t1 = MCU_Operand_Read();
        int t2;
        int R, Q;

        if (t1 == 0) {
            mcu.MCU_ErrorTrap(); // FIXME: implement proper exception
            mcu.MCU_SetStatus(false, STATUS_N.v);
            mcu.MCU_SetStatus(true, STATUS_Z.v);
            mcu.MCU_SetStatus(false, STATUS_V.v);
            mcu.MCU_SetStatus(false, STATUS_C.v);
            return;
        }

        if (operand_size != 0) {
            opcode_reg &= ~1;
            t2 = (mcu.r[opcode_reg | 0] & 0xffff) << 16;
            t2 |= (mcu.r[opcode_reg | 1] & 0xffff);

            R = t2 % t1;
            Q = t2 / t1;

            if (Q > 0xffff) {
                mcu.MCU_SetStatus(false, STATUS_N.v);
                mcu.MCU_SetStatus(false, STATUS_Z.v);
                mcu.MCU_SetStatus(true, STATUS_V.v);
                mcu.MCU_SetStatus(false, STATUS_C.v);
            } else {
                mcu.r[opcode_reg | 0] = (short) R;
                mcu.r[opcode_reg | 1] = (short) Q;
                MCU_SetStatusCommon(Q, OPERAND_WORD.ordinal());
                mcu.MCU_SetStatus(false, STATUS_C.v);
            }
        } else {
            t2 = mcu.r[opcode_reg] & 0xffff;

            R = t2 % t1;
            Q = t2 / t1;

            if (Q > 0xff) {
                mcu.MCU_SetStatus(false, STATUS_N.v);
                mcu.MCU_SetStatus(false, STATUS_Z.v);
                mcu.MCU_SetStatus(true, STATUS_V.v);
                mcu.MCU_SetStatus(false, STATUS_C.v);
            } else {
                R &= 0xff;
                Q &= 0xff;
                mcu.r[opcode_reg] = (short) ((R << 8) | Q);
                MCU_SetStatusCommon(Q, OPERAND_BYTE.ordinal());
                mcu.MCU_SetStatus(false, STATUS_C.v);
            }
        }
    }

    void MCU_Opcode_ADDS(byte opcode, byte opcode_reg) {
        int data = MCU_Operand_Read();
        if (operand_size == 0)
            data = (byte) data;
        mcu.r[opcode_reg] += data;
    }

    void MCU_Opcode_XOR(byte opcode, byte opcode_reg) {
        int data = MCU_Operand_Read();
        mcu.r[opcode_reg] ^= data;
        MCU_SetStatusCommon(mcu.r[opcode_reg], operand_size);
    }

    void MCU_Opcode_ADDX(byte opcode, byte opcode_reg) {
        int t1 = mcu.r[opcode_reg] & 0xffff;
        int t2 = MCU_Operand_Read();
        int C = (mcu.sr & STATUS_C.v) != 0 ? 1 : 0;
        int Z = (mcu.sr & STATUS_Z.v) != 0 ? 1 : 0;
        t1 = MCU_ADD_Common(t1, t2, C, operand_size);
        if (Z == 0)
            mcu.MCU_SetStatus(false, STATUS_Z.v);

        if (operand_size != 0)
            mcu.r[opcode_reg] = (short) t1;
        else {
            mcu.r[opcode_reg] &= ~0xff;
            mcu.r[opcode_reg] |= t1 & 0xff;
        }
    }

    void MCU_Opcode_SUBX(byte opcode, byte opcode_reg) {
        int t1 = mcu.r[opcode_reg] & 0xffff;
        int t2 = MCU_Operand_Read();
        int C = (mcu.sr & STATUS_C.v) != 0 ? 1 : 0;
        t1 = MCU_SUB_Common(t1, t2, C, operand_size);
        if (operand_size != 0)
            mcu.r[opcode_reg] = (short) t1;
        else {
            mcu.r[opcode_reg] &= ~0xff;
            mcu.r[opcode_reg] |= t1 & 0xff;
        }
    }
}
