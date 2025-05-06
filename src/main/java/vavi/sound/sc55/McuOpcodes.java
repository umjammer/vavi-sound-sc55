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

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

    private Mcu mcu;

    McuOpcodes(Mcu mcu) {
        this.mcu = mcu;
    }

    int MCU_SUB_Common(int t1, int t2, int c_bit, int siz) {
        int st1, st2;
        boolean N, Z, C, V = false;
        if (siz != 0) {
            st1 = (short) t1;
            st2 = (short) t2;
            t1 = (short) t1;
            t2 = (short) t2;
            t1 -= t2;
            t1 -= c_bit;
            C = ((t1 >> 16) & 1) != 0;

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
            t1 = (byte) t1;
            t2 = (byte) t2;
            t1 -= t2;
            t1 -= c_bit;
            C = ((t1 >> 8) & 1) != 0;

            t1 &= 0xff;
            N = (t1 & 0x80) != 0;
            Z = t1 == 0;

            st1 -= st2;
            st1 -= c_bit;
            if (st1 < Byte.MIN_VALUE || st1 > java.lang.Byte.MAX_VALUE)
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
            t1 = (short) t1;
            t2 = (short) t2;
            t1 += t2;
            t1 += c_bit;
            C = ((t1 >> 16) & 1) != 0;

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
            t1 = (byte) t1;
            t2 = (byte) t2;
            t1 += t2;
            t1 += c_bit;
            C = ((t1 >> 8) & 1) != 0;

            t1 &= 0xff;
            N = (t1 & 0x80) != 0;
            Z = t1 == 0;

            st1 += st2;
            st1 += c_bit;
            if (st1 < Byte.MIN_VALUE || st1 > java.lang.Byte.MAX_VALUE)
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
        byte rlist = mcu.MCU_ReadCodeAdvance();
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
        int opcode = mcu.MCU_ReadCodeAdvance();
        if ((opcode & 0xf0) == 0x10) {
            mcu.MCU_Interrupt_TRAPA(opcode & 0x0f);
        } else {
            mcu.MCU_ErrorTrap();
        }
    }

    void MCU_Jump_PJSR(byte operand) {
        int ocp = mcu.cp;
        int opc = mcu.pc;
        byte page = mcu.MCU_ReadCodeAdvance();
        short address;
        address = (short) (mcu.MCU_ReadCodeAdvance() << 8);
        address |= mcu.MCU_ReadCodeAdvance();
        mcu.MCU_PushStack(mcu.pc);
        mcu.MCU_PushStack(mcu.cp);
        mcu.cp = page;
        if (mcu.cp == 0x27)
            mcu.cp += 0;
        mcu.pc = address;
    }

    void MCU_Jump_JSR(byte operand) {
        short address;
        address = (short) (mcu.MCU_ReadCodeAdvance() << 8);
        address |= mcu.MCU_ReadCodeAdvance();
        mcu.MCU_PushStack(mcu.pc);
        mcu.pc = address;
    }

    void MCU_Jump_RTE(byte operand) {
        mcu.sr = mcu.MCU_PopStack();
        mcu.cp = (byte) mcu.MCU_PopStack();
        mcu.pc = mcu.MCU_PopStack();
        mcu.ex_ignore = true;
    }

    void MCU_Jump_Bcc(byte operand) {
        short disp;
        int cond;
        int branch = 0;
        int N, C, Z, V;
        if ((operand & 0x10) != 0) {
            disp = (short) (mcu.MCU_ReadCodeAdvance() << 8);
            disp |= mcu.MCU_ReadCodeAdvance();
        } else {
            disp = (byte) mcu.MCU_ReadCodeAdvance();
        }
        cond = operand & 0x0f;

        N = (mcu.sr & STATUS_N.v) != 0 ? 1 : 0;
        C = (mcu.sr & STATUS_C.v) != 0 ? 1 : 0;
        Z = (mcu.sr & STATUS_Z.v) != 0 ? 1 : 0;
        V = (mcu.sr & STATUS_V.v) != 0 ? 1 : 0;

        switch (cond) {
            case 0x0: // BRA/BT
                branch = 1;
                break;
            case 0x1: // BRN/BF
                branch = 0;
                break;
            case 0x2: // BHI
                branch = (C | Z) == 0 ? 1 : 0;
                break;
            case 0x3: // BLS
                branch = (C | Z) == 1 ? 1 : 0;
                break;
            case 0x4: // BCC/BHS
                branch = C == 0 ? 1 : 0;
                break;
            case 0x5: // BCS/BLO
                branch = C == 1 ? 1 : 0;
                break;
            case 0x6: // BNE
                branch = Z == 0 ? 1 : 0;
                break;
            case 0x7: // BEQ
                branch = Z == 1 ? 1 : 0;
                break;
            case 0x8: // BVC
                branch = V == 0 ? 1 : 0;
                break;
            case 0x9: // BVS
                branch = V == 1 ? 1 : 0;
                break;
            case 0xa: // BPL
                branch = N == 0 ? 1 : 0;
                break;
            case 0xb: // BMI
                branch = N == 1 ? 1 : 0;
                break;
            case 0xc: // BGE
                branch = (N ^ V) == 0 ? 1 : 0;
                break;
            case 0xd: // BLT
                branch = (N ^ V) == 1 ? 1 : 0;
                break;
            case 0xe: // BGT
                branch = (Z | (N ^ V)) == 0 ? 1 : 0;
                break;
            case 0xf: // BLE
                branch = (Z | (N ^ V)) == 1 ? 1 : 0;
                break;
        }

        if (branch != 0) {
            mcu.pc += disp;
        }
    }

    void MCU_Jump_RTS(byte operand) {
        mcu.pc = mcu.MCU_PopStack();
    }

    void MCU_Jump_RTD(byte operand) {
        short imm = (byte) mcu.MCU_ReadCodeAdvance();
        mcu.pc = mcu.MCU_PopStack();

        if (operand == 0x14) {
            mcu.r[7] += imm;
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
            byte opcode = mcu.MCU_ReadCodeAdvance();
            byte opcode_h = (byte) (opcode >> 3);
            byte opcode_l = (byte) (opcode & 0x07);
            if (opcode == 0x19) {
                mcu.cp = (byte) mcu.MCU_PopStack();
                mcu.pc = mcu.MCU_PopStack();
            } else if (opcode_h == 0x19) {
                mcu.MCU_PushStack(mcu.pc);
                mcu.MCU_PushStack(mcu.cp);
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
            byte opcode = mcu.MCU_ReadCodeAdvance();
            byte reg = (byte) (opcode & 0x07);
            opcode >>= 3;
            if (opcode == 0x17) {
                short disp = (byte) mcu.MCU_ReadCodeAdvance();
                mcu.r[reg]--;
                if (mcu.r[reg] != 0xffff) {
                    mcu.pc += disp;
                }
            } else {
                mcu.MCU_ErrorTrap();
            }
        } else if (operand == 0x10) {
            int addr;
            addr = mcu.MCU_ReadCodeAdvance() << 8;
            addr |= mcu.MCU_ReadCodeAdvance();
            mcu.pc = (short) addr;
        } else if (operand == 0x06) {
            byte opcode = mcu.MCU_ReadCodeAdvance();
            byte reg = (byte) (opcode & 0x07);
            opcode >>= 3;
            if (opcode == 0x17) {
                short disp = (byte) mcu.MCU_ReadCodeAdvance();
                boolean Z = (mcu.sr & STATUS_Z.v) != 0;
                if (Z) {
                    mcu.r[reg]--;
                    if (mcu.r[reg] != 0xffff) {
                        mcu.pc += disp;
                    }
                }
            } else {
                mcu.MCU_ErrorTrap();
            }
        } else if (operand == 0x07) {
            byte opcode = mcu.MCU_ReadCodeAdvance();
            byte reg = (byte) (opcode & 0x07);
            opcode >>= 3;
            if (opcode == 0x17) {
                short disp = (byte) mcu.MCU_ReadCodeAdvance();
                int Z = (mcu.sr & STATUS_Z.v) != 0 ? 1 : 0;
                if (Z == 0) {
                    mcu.r[reg]--;
                    if (mcu.r[reg] != 0xffff) {
                        mcu.pc += disp;
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
        short disp;
        if (operand == 0x0e) {
            disp = (byte) mcu.MCU_ReadCodeAdvance();
        } else {
            disp = (short) (mcu.MCU_ReadCodeAdvance() << 8);
            disp |= mcu.MCU_ReadCodeAdvance();
        }
        mcu.MCU_PushStack(mcu.pc);
        mcu.pc += disp;
    }

    void MCU_Jump_PJMP(byte operand) {
        byte page;
        short address;
        page = mcu.MCU_ReadCodeAdvance();
        address = (short) (mcu.MCU_ReadCodeAdvance() << 8);
        address |= mcu.MCU_ReadCodeAdvance();
        mcu.cp = page;
        mcu.pc = address;
    }

    int operand_type;
    short operand_ea;
    byte operand_ep;
    byte operand_size;
    byte operand_reg;
    byte operand_status;
    short operand_data;
    byte opcode_extended;

    int MCU_Operand_Read() {
        switch (General.values()[operand_type]) {
            case GENERAL_DIRECT:
                if (operand_size != 0)
                    return mcu.r[operand_reg];
                return mcu.r[operand_reg] & 0xff;
            case GENERAL_INDIRECT:
            case GENERAL_ABSOLUTE:
                if (operand_size != 0) {
                    if ((operand_ea & 1) != 0) {
                        mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
                    }
                    return mcu.MCU_Read16(mcu.MCU_GetAddress(operand_ep, operand_ea));
                }
                return mcu.MCU_Read(mcu.MCU_GetAddress(operand_ep, operand_ea));
            case GENERAL_IMMEDIATE:
                return operand_data;
        }
        return 0;
    }

    void MCU_Operand_Write(int data) {
        switch (General.values()[operand_type]) {
            case GENERAL_DIRECT:
                if (operand_size != 0)
                    mcu.r[operand_reg] = (short) data;
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
        byte opcode;
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
                disp = (byte) mcu.MCU_ReadCodeAdvance();
                break;
            case 0xf0:
                type = GENERAL_INDIRECT.ordinal();
                disp = mcu.MCU_ReadCodeAdvance();
                disp <<= 8;
                disp |= mcu.MCU_ReadCodeAdvance();
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
                    addr = mcu.br << 8;
                    addr |= mcu.MCU_ReadCodeAdvance();
                    addrpage = 0;
                } else if (reg == 4) {
                    type = GENERAL_IMMEDIATE.ordinal();
                    data = mcu.MCU_ReadCodeAdvance();
                    if (siz != 0) {
                        data <<= 8;
                        data |= mcu.MCU_ReadCodeAdvance();
                    }
                }
                break;
            case 0x10:
                if (reg == 5) {
                    type = GENERAL_ABSOLUTE.ordinal();
                    addr = mcu.MCU_ReadCodeAdvance() << 8;
                    addr |= mcu.MCU_ReadCodeAdvance();
                    addrpage = mcu.dp;
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
            ea = mcu.r[reg] + disp;
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

        opcode = mcu.MCU_ReadCodeAdvance();
        opcode_extended = (byte) (opcode == 0x00 ? 1 : 0);
        if (opcode_extended != 0) {
            opcode = mcu.MCU_ReadCodeAdvance();
        }
        opcode_reg = (byte) (opcode & 0x07);
        opcode >>= 3;

        operand_type = type;
        operand_ea = (short) ea;
        operand_ep = (byte) ep;
        operand_size = (byte) siz;
        operand_reg = (byte) reg;
        operand_data = (short) data;
        operand_status = 0;

        MCU_Opcode_Table[opcode].accept(opcode, opcode_reg);
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
        byte data = mcu.MCU_ReadCodeAdvance();
        mcu.r[reg] &= ~0xff;
        mcu.r[reg] |= data;
        MCU_SetStatusCommon(data, 0);
    }

    void MCU_Opcode_Short_MOVI(byte opcode) {
        int reg = opcode & 0x07;
        short data;
        data = (short) (mcu.MCU_ReadCodeAdvance() << 8);
        data |= mcu.MCU_ReadCodeAdvance();
        mcu.r[reg] = data;
        MCU_SetStatusCommon(data, 1);
    }

    void MCU_Opcode_Short_MOVF(byte opcode) {
        int reg = opcode & 0x07;
        int siz = (opcode & 0x08) != 0 ? 1 : 0;
        byte disp = mcu.MCU_ReadCodeAdvance();
        int addr = (mcu.r[6] + disp) & 0xffff;
        addr |= mcu.tp << 16;
        if ((opcode & 0x10) == 0) {
            short data;
            if (siz != 0) {
                data = mcu.MCU_Read16(addr);
                mcu.r[reg] &= ~0xff;
                mcu.r[reg] |= data;
                MCU_SetStatusCommon(data, 0);
            } else {
                data = mcu.MCU_Read(addr);
                mcu.r[reg] = data;
                MCU_SetStatusCommon(data, 1);
            }
        } else {
            short data;
            if (siz != 0) {
                data = (short) (mcu.r[reg] & 0xff);
                mcu.MCU_Write(addr, (byte) data);
                MCU_SetStatusCommon(data, 0);
            } else {
                data = mcu.r[reg];
                mcu.MCU_Write16(addr, data);
                MCU_SetStatusCommon(data, 1);
            }
        }
    }

    void MCU_Opcode_Short_MOVL(byte opcode) {
        int reg = opcode & 0x07;
        int siz = (opcode & 0x08) != 0 ? 1 : 0;
        short addr = (short) (mcu.br << 8);
        int data;
        addr |= mcu.MCU_ReadCodeAdvance();
        if (siz != 0) {
            if ((addr & 1) != 0)
                mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
            data = mcu.MCU_Read16(addr);
            mcu.r[reg] = (short) data;
            MCU_SetStatusCommon(data, 1);
        } else {
            data = mcu.MCU_Read(addr);
            mcu.r[reg] &= ~0xff;
            mcu.r[reg] |= data;
            MCU_SetStatusCommon(data, 0);
        }
    }

    void MCU_Opcode_Short_MOVS(byte opcode) {
        int reg = opcode & 0x07;
        int siz = (opcode & 0x08) != 0 ? 1 : 0;
        short addr = (short) (mcu.br << 8);
        int data;
        addr |= mcu.MCU_ReadCodeAdvance();
        if (siz != 0) {
            if ((addr & 1) != 0)
                mcu.interrupt.MCU_Interrupt_Exception(EXCEPTION_SOURCE_ADDRESS_ERROR.ordinal());
            data = mcu.r[reg];
            mcu.MCU_Write16((int) addr, (short) data);
            MCU_SetStatusCommon(data, 1);
        } else {
            data = mcu.r[reg] & 0xff;
            mcu.MCU_Write((int) addr, (byte) data);
            MCU_SetStatusCommon(data, 0);
        }
    }

    void MCU_Opcode_Short_CMP(byte opcode) {
        int reg = opcode & 0x07;
        int siz = (opcode & 0x08) != 0 ? 1 : 0;
        int t1, t2;
        if (siz != 0) {
            t2 = mcu.MCU_ReadCodeAdvance() << 8;
            t2 |= mcu.MCU_ReadCodeAdvance();
        } else {
            t2 = mcu.MCU_ReadCodeAdvance();
        }
        t1 = mcu.r[reg];
        MCU_SUB_Common(t1, t2, 0, siz);
    }

    void MCU_Opcode_NotImplemented(byte opcode, byte opcode_reg) {
        mcu.MCU_ErrorTrap();
    }

    void MCU_Opcode_MOVG_Immediate(byte opcode, byte opcode_reg) {
        int data;
        if (opcode_reg == 6 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal())) {
            data = (byte) mcu.MCU_ReadCodeAdvance();
            MCU_Operand_Write(data);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 7 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal())) {
            data = mcu.MCU_ReadCodeAdvance() << 8;
            data |= mcu.MCU_ReadCodeAdvance();
            MCU_Operand_Write(data);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 4 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == Operand.OPERAND_BYTE.ordinal()) {
            int t1 = MCU_Operand_Read();
            int t2 = mcu.MCU_ReadCodeAdvance();
            MCU_SUB_Common(t1, t2, 0, Operand.OPERAND_BYTE.ordinal());
        } else if (opcode_reg == 4 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == OPERAND_WORD.ordinal()) // FIXME
        {
            int t1 = MCU_Operand_Read();
            int t2 = (short) mcu.MCU_ReadCodeAdvance();
            MCU_SUB_Common(t1, t2, 0, OPERAND_WORD.ordinal());
        } else if (opcode_reg == 5 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == OPERAND_WORD.ordinal()) {
            int t1, t2;
            t1 = MCU_Operand_Read();
            t2 = mcu.MCU_ReadCodeAdvance() << 8;
            t2 |= mcu.MCU_ReadCodeAdvance();
            MCU_SUB_Common(t1, t2, 0, OPERAND_WORD.ordinal());
        } else if (opcode_reg == 5 && (operand_type == GENERAL_INDIRECT.ordinal() || operand_type == GENERAL_ABSOLUTE.ordinal()) && operand_size == OPERAND_BYTE.ordinal()) // FIXME
        {
            int t1, t2;
            t1 = MCU_Operand_Read();
            t2 = mcu.MCU_ReadCodeAdvance() << 8;
            t2 |= mcu.MCU_ReadCodeAdvance();
            MCU_SUB_Common(t1, t2, 0, OPERAND_BYTE.ordinal());
        } else {
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
            int data = (byte) mcu.r[operand_reg];
            mcu.r[operand_reg] = (short) data;
            mcu.MCU_SetStatus(false, STATUS_N.v);
            mcu.MCU_SetStatus(data == 0, STATUS_Z.v);
            mcu.MCU_SetStatus(false, STATUS_V.v);
            mcu.MCU_SetStatus(false, STATUS_C.v);
        } else if (opcode_reg == 0 && operand_type == GENERAL_DIRECT.ordinal() && operand_size == 0) { // SWAP
            int data = mcu.r[operand_reg];
            int data_h = data >> 8;
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
            int data = mcu.r[operand_reg];
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
        if (opcode_extended != 0) {
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
                        int r1 = mcu.r[opcode_reg];
                        int r2 = mcu.r[operand_reg];
                        mcu.r[opcode_reg] = (short) r2;
                        mcu.r[operand_reg] = (short) r1;
                    } else {
                        mcu.MCU_ErrorTrap();
                    }
                } else {
                    data = mcu.r[opcode_reg];
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
        int t1 = mcu.r[opcode_reg];
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
        int t1 = mcu.r[opcode_reg];
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
        int t1 = mcu.r[opcode_reg];
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
        int t1 = mcu.r[opcode_reg];
        int t2 = MCU_Operand_Read();
        if (operand_size != 0)
            mcu.r[opcode_reg] = (short) (t1 - t2);
        else
            mcu.r[opcode_reg] = (short) (t1 - (byte) t2);
    }

    void MCU_Opcode_AND(byte opcode, byte opcode_reg) {
        int data = mcu.r[opcode_reg];
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
            data >>= 1;
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
                data &= 0x7fff;
            } else {
                msb = data & 0x80;
                data &= 0x7f;
            }
            data >>= 1;
            data |= msb;
            MCU_Operand_Write(data);
            mcu.MCU_SetStatus(C, STATUS_C.v);
            MCU_SetStatusCommon(data, operand_size);
        } else if (opcode_reg == 0x05 && operand_type != GENERAL_IMMEDIATE.ordinal()) { // ROTR
            int data = MCU_Operand_Read();
            boolean C = (data & 0x1) != 0;
            data >>= 1;
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
        int t2 = mcu.r[opcode_reg];
        boolean N, Z;
        if (operand_size == 0)
            t2 &= 0xff;
        t1 *= t2;

        if (operand_size != 0) {
            opcode_reg &= ~1;
            mcu.r[opcode_reg | 0] = (short) (t1 >> 16);
            mcu.r[opcode_reg | 1] = (short) t1;
            N = (t1 & 0x80000000L) != 0; // FIXME
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
            t2 = mcu.r[opcode_reg | 0] << 16;
            t2 |= mcu.r[opcode_reg | 1];

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
            t2 = mcu.r[opcode_reg];

            R = t2 % t1;
            Q = t2 / t1;

            if (Q > 0xffff) {
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
        int t1 = mcu.r[opcode_reg];
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
        int t1 = mcu.r[opcode_reg];
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

    @SuppressWarnings("unchecked")
    Consumer<Byte>[] MCU_Operand_Table = List.<Consumer<Byte>>of(
            this::MCU_Operand_Nop, // 00
            this::MCU_Jump_JMP, // 01
            this::MCU_LDM, // 02
            this::MCU_Jump_PJSR, // 03
            this::MCU_Operand_General, // 04
            this::MCU_Operand_General, // 05
            this::MCU_Jump_JMP, // 06
            this::MCU_Jump_JMP, // 07
            this::MCU_TRAPA, // 08
            this::MCU_Operand_NotImplemented, // 09
            this::MCU_Jump_RTE, // 0A
            this::MCU_Operand_NotImplemented, // 0B
            this::MCU_Operand_General, // 0C
            this::MCU_Operand_General, // 0D
            this::MCU_Jump_BSR, // 0E
            this::MCU_Operand_NotImplemented, // 0F
            this::MCU_Jump_JMP, // 10
            this::MCU_Jump_JMP, // 11
            this::MCU_STM, // 12
            this::MCU_Jump_PJMP, // 13
            this::MCU_Jump_RTD, // 14
            this::MCU_Operand_General, // 15
            this::MCU_Operand_NotImplemented, // 16
            this::MCU_Operand_NotImplemented, // 17
            this::MCU_Jump_JSR, // 18
            this::MCU_Jump_RTS, // 19
            this::MCU_Operand_Sleep, // 1A
            this::MCU_Operand_NotImplemented, // 1B
            this::MCU_Jump_RTD, // 1C
            this::MCU_Operand_General, // 1D
            this::MCU_Jump_BSR, // 1E
            this::MCU_Operand_NotImplemented, // 1F
            this::MCU_Jump_Bcc, // 20
            this::MCU_Jump_Bcc, // 21
            this::MCU_Jump_Bcc, // 22
            this::MCU_Jump_Bcc, // 23
            this::MCU_Jump_Bcc, // 24
            this::MCU_Jump_Bcc, // 25
            this::MCU_Jump_Bcc, // 26
            this::MCU_Jump_Bcc, // 27
            this::MCU_Jump_Bcc, // 28
            this::MCU_Jump_Bcc, // 29
            this::MCU_Jump_Bcc, // 2A
            this::MCU_Jump_Bcc, // 2B
            this::MCU_Jump_Bcc, // 2C
            this::MCU_Jump_Bcc, // 2D
            this::MCU_Jump_Bcc, // 2E
            this::MCU_Jump_Bcc, // 2F
            this::MCU_Jump_Bcc, // 30
            this::MCU_Jump_Bcc, // 31
            this::MCU_Jump_Bcc, // 32
            this::MCU_Jump_Bcc, // 33
            this::MCU_Jump_Bcc, // 34
            this::MCU_Jump_Bcc, // 35
            this::MCU_Jump_Bcc, // 36
            this::MCU_Jump_Bcc, // 37
            this::MCU_Jump_Bcc, // 38
            this::MCU_Jump_Bcc, // 39
            this::MCU_Jump_Bcc, // 3A
            this::MCU_Jump_Bcc, // 3B
            this::MCU_Jump_Bcc, // 3C
            this::MCU_Jump_Bcc, // 3D
            this::MCU_Jump_Bcc, // 3E
            this::MCU_Jump_Bcc, // 3F
            this::MCU_Opcode_Short_CMP, // 40
            this::MCU_Opcode_Short_CMP, // 41
            this::MCU_Opcode_Short_CMP, // 42
            this::MCU_Opcode_Short_CMP, // 43
            this::MCU_Opcode_Short_CMP, // 44
            this::MCU_Opcode_Short_CMP, // 45
            this::MCU_Opcode_Short_CMP, // 46
            this::MCU_Opcode_Short_CMP, // 47
            this::MCU_Opcode_Short_CMP, // 48
            this::MCU_Opcode_Short_CMP, // 49
            this::MCU_Opcode_Short_CMP, // 4A
            this::MCU_Opcode_Short_CMP, // 4B
            this::MCU_Opcode_Short_CMP, // 4C
            this::MCU_Opcode_Short_CMP, // 4D
            this::MCU_Opcode_Short_CMP, // 4E
            this::MCU_Opcode_Short_CMP, // 4F
            this::MCU_Opcode_Short_MOVE, // 50
            this::MCU_Opcode_Short_MOVE, // 51
            this::MCU_Opcode_Short_MOVE, // 52
            this::MCU_Opcode_Short_MOVE, // 53
            this::MCU_Opcode_Short_MOVE, // 54
            this::MCU_Opcode_Short_MOVE, // 55
            this::MCU_Opcode_Short_MOVE, // 56
            this::MCU_Opcode_Short_MOVE, // 57
            this::MCU_Opcode_Short_MOVI, // 58
            this::MCU_Opcode_Short_MOVI, // 59
            this::MCU_Opcode_Short_MOVI, // 5A
            this::MCU_Opcode_Short_MOVI, // 5B
            this::MCU_Opcode_Short_MOVI, // 5C
            this::MCU_Opcode_Short_MOVI, // 5D
            this::MCU_Opcode_Short_MOVI, // 5E
            this::MCU_Opcode_Short_MOVI, // 5F
            this::MCU_Opcode_Short_MOVL, // 60
            this::MCU_Opcode_Short_MOVL, // 61
            this::MCU_Opcode_Short_MOVL, // 62
            this::MCU_Opcode_Short_MOVL, // 63
            this::MCU_Opcode_Short_MOVL, // 64
            this::MCU_Opcode_Short_MOVL, // 65
            this::MCU_Opcode_Short_MOVL, // 66
            this::MCU_Opcode_Short_MOVL, // 67
            this::MCU_Opcode_Short_MOVL, // 68
            this::MCU_Opcode_Short_MOVL, // 69
            this::MCU_Opcode_Short_MOVL, // 6A
            this::MCU_Opcode_Short_MOVL, // 6B
            this::MCU_Opcode_Short_MOVL, // 6C
            this::MCU_Opcode_Short_MOVL, // 6D
            this::MCU_Opcode_Short_MOVL, // 6E
            this::MCU_Opcode_Short_MOVL, // 6F
            this::MCU_Opcode_Short_MOVS, // 70
            this::MCU_Opcode_Short_MOVS, // 71
            this::MCU_Opcode_Short_MOVS, // 72
            this::MCU_Opcode_Short_MOVS, // 73
            this::MCU_Opcode_Short_MOVS, // 74
            this::MCU_Opcode_Short_MOVS, // 75
            this::MCU_Opcode_Short_MOVS, // 76
            this::MCU_Opcode_Short_MOVS, // 77
            this::MCU_Opcode_Short_MOVS, // 78
            this::MCU_Opcode_Short_MOVS, // 79
            this::MCU_Opcode_Short_MOVS, // 7A
            this::MCU_Opcode_Short_MOVS, // 7B
            this::MCU_Opcode_Short_MOVS, // 7C
            this::MCU_Opcode_Short_MOVS, // 7D
            this::MCU_Opcode_Short_MOVS, // 7E
            this::MCU_Opcode_Short_MOVS, // 7F
            this::MCU_Opcode_Short_MOVF, // 80
            this::MCU_Opcode_Short_MOVF, // 81
            this::MCU_Opcode_Short_MOVF, // 82
            this::MCU_Opcode_Short_MOVF, // 83
            this::MCU_Opcode_Short_MOVF, // 84
            this::MCU_Opcode_Short_MOVF, // 85
            this::MCU_Opcode_Short_MOVF, // 86
            this::MCU_Opcode_Short_MOVF, // 87
            this::MCU_Opcode_Short_MOVF, // 88
            this::MCU_Opcode_Short_MOVF, // 89
            this::MCU_Opcode_Short_MOVF, // 8A
            this::MCU_Opcode_Short_MOVF, // 8B
            this::MCU_Opcode_Short_MOVF, // 8C
            this::MCU_Opcode_Short_MOVF, // 8D
            this::MCU_Opcode_Short_MOVF, // 8E
            this::MCU_Opcode_Short_MOVF, // 8F
            this::MCU_Opcode_Short_MOVF, // 90
            this::MCU_Opcode_Short_MOVF, // 91
            this::MCU_Opcode_Short_MOVF, // 92
            this::MCU_Opcode_Short_MOVF, // 93
            this::MCU_Opcode_Short_MOVF, // 94
            this::MCU_Opcode_Short_MOVF, // 95
            this::MCU_Opcode_Short_MOVF, // 96
            this::MCU_Opcode_Short_MOVF, // 97
            this::MCU_Opcode_Short_MOVF, // 98
            this::MCU_Opcode_Short_MOVF, // 99
            this::MCU_Opcode_Short_MOVF, // 9A
            this::MCU_Opcode_Short_MOVF, // 9B
            this::MCU_Opcode_Short_MOVF, // 9C
            this::MCU_Opcode_Short_MOVF, // 9D
            this::MCU_Opcode_Short_MOVF, // 9E
            this::MCU_Opcode_Short_MOVF, // 9F
            this::MCU_Operand_General, // A0
            this::MCU_Operand_General, // A1
            this::MCU_Operand_General, // A2
            this::MCU_Operand_General, // A3
            this::MCU_Operand_General, // A4
            this::MCU_Operand_General, // A5
            this::MCU_Operand_General, // A6
            this::MCU_Operand_General, // A7
            this::MCU_Operand_General, // A8
            this::MCU_Operand_General, // A9
            this::MCU_Operand_General, // AA
            this::MCU_Operand_General, // AB
            this::MCU_Operand_General, // AC
            this::MCU_Operand_General, // AD
            this::MCU_Operand_General, // AE
            this::MCU_Operand_General, // AF
            this::MCU_Operand_General, // B0
            this::MCU_Operand_General, // B1
            this::MCU_Operand_General, // B2
            this::MCU_Operand_General, // B3
            this::MCU_Operand_General, // B4
            this::MCU_Operand_General, // B5
            this::MCU_Operand_General, // B6
            this::MCU_Operand_General, // B7
            this::MCU_Operand_General, // B8
            this::MCU_Operand_General, // B9
            this::MCU_Operand_General, // BA
            this::MCU_Operand_General, // BB
            this::MCU_Operand_General, // BC
            this::MCU_Operand_General, // BD
            this::MCU_Operand_General, // BE
            this::MCU_Operand_General, // BF
            this::MCU_Operand_General, // C0
            this::MCU_Operand_General, // C1
            this::MCU_Operand_General, // C2
            this::MCU_Operand_General, // C3
            this::MCU_Operand_General, // C4
            this::MCU_Operand_General, // C5
            this::MCU_Operand_General, // C6
            this::MCU_Operand_General, // C7
            this::MCU_Operand_General, // C8
            this::MCU_Operand_General, // C9
            this::MCU_Operand_General, // CA
            this::MCU_Operand_General, // CB
            this::MCU_Operand_General, // CC
            this::MCU_Operand_General, // CD
            this::MCU_Operand_General, // CE
            this::MCU_Operand_General, // CF
            this::MCU_Operand_General, // D0
            this::MCU_Operand_General, // D1
            this::MCU_Operand_General, // D2
            this::MCU_Operand_General, // D3
            this::MCU_Operand_General, // D4
            this::MCU_Operand_General, // D5
            this::MCU_Operand_General, // D6
            this::MCU_Operand_General, // D7
            this::MCU_Operand_General, // D8
            this::MCU_Operand_General, // D9
            this::MCU_Operand_General, // DA
            this::MCU_Operand_General, // DB
            this::MCU_Operand_General, // DC
            this::MCU_Operand_General, // DD
            this::MCU_Operand_General, // DE
            this::MCU_Operand_General, // DF
            this::MCU_Operand_General, // E0
            this::MCU_Operand_General, // E1
            this::MCU_Operand_General, // E2
            this::MCU_Operand_General, // E3
            this::MCU_Operand_General, // E4
            this::MCU_Operand_General, // E5
            this::MCU_Operand_General, // E6
            this::MCU_Operand_General, // E7
            this::MCU_Operand_General, // E8
            this::MCU_Operand_General, // E9
            this::MCU_Operand_General, // EA
            this::MCU_Operand_General, // EB
            this::MCU_Operand_General, // EC
            this::MCU_Operand_General, // ED
            this::MCU_Operand_General, // EE
            this::MCU_Operand_General, // EF
            this::MCU_Operand_General, // F0
            this::MCU_Operand_General, // F1
            this::MCU_Operand_General, // F2
            this::MCU_Operand_General, // F3
            this::MCU_Operand_General, // F4
            this::MCU_Operand_General, // F5
            this::MCU_Operand_General, // F6
            this::MCU_Operand_General, // F7
            this::MCU_Operand_General, // F8
            this::MCU_Operand_General, // F9
            this::MCU_Operand_General, // FA
            this::MCU_Operand_General, // FB
            this::MCU_Operand_General, // FC
            this::MCU_Operand_General, // FD
            this::MCU_Operand_General, // FE
            this::MCU_Operand_General // FF
    ).toArray(Consumer[]::new);

    @SuppressWarnings("unchecked")
    BiConsumer<Byte, Byte>[] MCU_Opcode_Table = List.<BiConsumer<Byte, Byte>>of(
            this::MCU_Opcode_MOVG_Immediate, // 00
            this::MCU_Opcode_ADDQ, // 01
            this::MCU_Opcode_CLR, // 02
            this::MCU_Opcode_SHLR, // 03
            this::MCU_Opcode_ADD, // 04
            this::MCU_Opcode_ADDS, // 05
            this::MCU_Opcode_SUB, // 06
            this::MCU_Opcode_SUBS, // 07
            this::MCU_Opcode_OR, // 08
            this::MCU_Opcode_BSET_ORC, // 09
            this::MCU_Opcode_AND, // 0A
            this::MCU_Opcode_BCLR_ANDC, // 0B
            this::MCU_Opcode_XOR, // 0C
            this::MCU_Opcode_NotImplemented, // 0D
            this::MCU_Opcode_CMP, // 0E
            this::MCU_Opcode_BTST, // 0F
            this::MCU_Opcode_MOVG, // 10
            this::MCU_Opcode_LDC, // 11
            this::MCU_Opcode_MOVG, // 12
            this::MCU_Opcode_STC, // 13
            this::MCU_Opcode_ADDX, // 14
            this::MCU_Opcode_MULXU, // 15
            this::MCU_Opcode_SUBX, // 16
            this::MCU_Opcode_DIVXU, // 17
            this::MCU_Opcode_BSET, // 18
            this::MCU_Opcode_BSET, // 19
            this::MCU_Opcode_BCLR, // 1A
            this::MCU_Opcode_BCLR, // 1B
            this::MCU_Opcode_BNOTI, // 1C
            this::MCU_Opcode_BNOTI, // 1D
            this::MCU_Opcode_BTSTI, // 1E
            this::MCU_Opcode_BTSTI // 1F
    ).toArray(BiConsumer[]::new);
}
