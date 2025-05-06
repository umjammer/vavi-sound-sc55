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
import java.util.List;
import java.util.function.Consumer;

import static java.lang.System.getLogger;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_COLLISION;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_INT_ENABLE;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_INT_REQUEST;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_IPCE0;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_IPCM0;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_PRESCALER;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_RAM_DIR;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_SEMAPHORE;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_TIMER;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_TIMER_CTRL;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_UART1_CTRL;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_UART2_CTRL;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_UART3_CTRL;
import static vavi.sound.sc55.SubMcu.SmDev.SM_DEV_UART3_MODE_STATUS;
import static vavi.sound.sc55.SubMcu.SmStaus.SM_STATUS_C;
import static vavi.sound.sc55.SubMcu.SmStaus.SM_STATUS_D;
import static vavi.sound.sc55.SubMcu.SmStaus.SM_STATUS_I;
import static vavi.sound.sc55.SubMcu.SmStaus.SM_STATUS_N;
import static vavi.sound.sc55.SubMcu.SmStaus.SM_STATUS_T;
import static vavi.sound.sc55.SubMcu.SmStaus.SM_STATUS_Z;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_COLLISION;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_IPCM0;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_RESET;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_TIMER_X;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_UART1_TX;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_UART2_RX;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_UART2_TX;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_UART3_RX;
import static vavi.sound.sc55.SubMcu.SmVector.SM_VECTOR_UART3_TX;


class SubMcu {

    private static final Logger logger = getLogger(SubMcu.class.getName());

    enum SmStaus {
        SM_STATUS_C(1),
        SM_STATUS_Z(2),
        SM_STATUS_I(4),
        SM_STATUS_D(8),
        SM_STATUS_B(16),
        SM_STATUS_T(32),
        SM_STATUS_V(64),
        SM_STATUS_N(128);
        final int v;

        SmStaus(int v) {
            this.v = v;
        }
    }

//    static class submcu_t {

    short pc;
    byte a;
    byte x;
    byte y;
    byte s;
    byte sr;
    long cycles;
    byte sleep;
//    }

    enum SmVector {
        SM_VECTOR_UART3_TX,
        SM_VECTOR_UART2_TX,
        SM_VECTOR_UART1_TX,
        SM_VECTOR_COLLISION,
        SM_VECTOR_TIMER_X,
        SM_VECTOR_IPCM0,
        SM_VECTOR_UART3_RX,
        SM_VECTOR_UART2_RX,
        SM_VECTOR_UART1_RX,
        SM_VECTOR_RESET
    }

    enum SmDev {
        SM_DEV_P1_DATA(0x00),
        SM_DEV_P1_DIR(0x01),
        SM_DEV_RAM_DIR(0x02),
        SM_DEV_UART1_MODE_STATUS(0x05),
        SM_DEV_UART1_CTRL(0x06),
        SM_DEV_UART2_DATA(0x08),
        SM_DEV_UART2_MODE_STATUS(0x09),
        SM_DEV_UART2_CTRL(0x0a),
        SM_DEV_UART3_MODE_STATUS(0x0d),
        SM_DEV_UART3_CTRL(0x0e),
        SM_DEV_IPCM0(0x10),
        SM_DEV_IPCM1(0x11),
        SM_DEV_IPCM2(0x12),
        SM_DEV_IPCM3(0x13),
        SM_DEV_IPCE0(0x14),
        SM_DEV_IPCE1(0x15),
        SM_DEV_IPCE2(0x16),
        SM_DEV_IPCE3(0x17),
        SM_DEV_SEMAPHORE(0x19),
        SM_DEV_COLLISION(0x1a),
        SM_DEV_INT_ENABLE(0x1b),
        SM_DEV_INT_REQUEST(0x1c),
        SM_DEV_PRESCALER(0x1d),
        SM_DEV_TIMER(0x1e),
        SM_DEV_TIMER_CTRL(0x1f);
        final int v;

        SmDev(int v) {
            this.v = v;
        }
    }

    byte[] sm_rom;
    byte[] sm_ram = new byte[128];
    byte[] sm_shared_ram = new byte[192];
    byte[] sm_access = new byte[0x18];

    byte sm_p0_dir;
    byte sm_p1_dir;

    byte[] sm_device_mode = new byte[32];
    byte sm_cts;

    long sm_timer_cycles;
    byte sm_timer_prescaler;
    byte sm_timer_counter;

    Mcu mcu;

    SubMcu(Mcu mcu) {
        this.mcu = mcu;
    }

    private byte uart_rx_gotbyte;
    private byte uart_rx_byte;
    private long uart_rx_delay;

    void SM_ErrorTrap() {
        logger.log(Level.DEBUG, "%4x".formatted(this.pc));
    }

    byte SM_Read(short address) {
        address &= 0x1fff;
        if ((address & 0x1000) != 0) {
            return sm_rom[address & 0xfff];
        } else if (address < 0x80) {
            return sm_ram[address];
        } else if (address >= 0xc0 && address < 0xd8) {
            return sm_access[address & 0x1f];
        } else if (address >= 0xe0 && address < 0x100) {
            address &= 0x1f;
            switch (SmDev.values()[address]) {
                case SM_DEV_UART2_DATA: {
                    uart_rx_gotbyte = 0;
                    return uart_rx_byte;
                }
                case SM_DEV_UART1_MODE_STATUS: {
                    byte ret = 0;
                    ret |= 5;
                    return ret;
                }
                case SM_DEV_UART2_MODE_STATUS: {
                    byte ret = (byte) (uart_rx_gotbyte << 1);
                    ret |= 5;
                    return ret;
                }
                case SM_DEV_UART3_MODE_STATUS: {
                    byte ret = 0;
                    ret |= 5;
                    return ret;
                }
                case SM_DEV_P1_DATA:
                    return mcu.MCU_ReadP1();
                case SM_DEV_P1_DIR:
                    return sm_p1_dir;
                case SM_DEV_PRESCALER:
                    return sm_timer_prescaler;
                case SM_DEV_TIMER:
                    return sm_timer_counter;
            }
            return sm_device_mode[address];
        } else if (address >= 0x200 && address < 0x2c0) {
            address &= 0xff;
            if ((sm_device_mode[SM_DEV_RAM_DIR.v] & (1 << (address >> 5))) != 0)
                sm_access[address >> 3] &= ~(1 << (address & 7));
            return sm_shared_ram[address];
        } else {
            logger.log(Level.DEBUG, "sm: unknown read %x".formatted(address));
            return 0;
        }
    }

    void SM_Write(short address, byte data) {
        address &= 0x1fff;
        if (address < 0x80) {
            sm_ram[address] = data;
        } else if (address >= 0xe0 && address < 0x100) {
            address &= 0x1f;
            switch (SmDev.values()[address]) {
                case SM_DEV_P1_DATA:
                    mcu.MCU_WriteP1(data);
                    break;
                case SM_DEV_P1_DIR:
                    sm_p1_dir = data;
                    break;
                case SM_DEV_IPCM0:
                case SM_DEV_IPCM1:
                case SM_DEV_IPCM2:
                case SM_DEV_IPCM3:
                    sm_device_mode[address] = data;
                    break;
                case SM_DEV_IPCE0:
                case SM_DEV_IPCE1:
                case SM_DEV_IPCE2:
                case SM_DEV_IPCE3:
                    sm_device_mode[address] = data;
                    break;
                case SM_DEV_INT_REQUEST:
                    sm_device_mode[SM_DEV_INT_REQUEST.v] &= data;
                    break;
                case SM_DEV_COLLISION:
                    sm_device_mode[SM_DEV_COLLISION.v] &= ~0x7f;
                    sm_device_mode[SM_DEV_COLLISION.v] |= (byte) (data & 0x7f);
                    if ((data & 0x80) == 0)
                        sm_device_mode[SM_DEV_COLLISION.v] &= (byte) ~0x80;
                    break;
                default:
                    sm_device_mode[address] = data;
                    break;
            }
            if (address == SM_DEV_UART3_MODE_STATUS.v || address == SM_DEV_UART3_CTRL.v)
                mcu.MCU_GA_SetGAInt(5, (sm_device_mode[SM_DEV_UART3_MODE_STATUS.v] & 0x80) != 0
                        && (sm_device_mode[SM_DEV_UART3_CTRL.v] & 0x20) == 0);
        } else if (address >= 0x200 && address < 0x2c0) {
            address &= 0xff;
            sm_access[address >> 3] |= (byte) (1 << (address & 7));
            sm_shared_ram[address] = data;
        } else {
            logger.log(Level.DEBUG, "sm: unknown write %x %x".formatted(address, data));
        }
    }

    void SM_SysWrite(int address, byte data) {
        address &= 0xff;
        if (address < 0xc0) {
            address &= 0xff;
            sm_access[address >> 3] |= (byte) (1 << (address & 7));
            sm_shared_ram[address] = data;
        } else if (address >= 0xf8 && address < 0xfc) {
            sm_device_mode[SM_DEV_IPCM0.v + (address & 3)] = data;
            if ((address & 3) == 0) {
                sm_device_mode[SM_DEV_INT_REQUEST.v] |= 0x10;
                sm_device_mode[SM_DEV_SEMAPHORE.v] &= (byte) ~0x80;
            }
        } else if (address == 0xff) {
            sm_device_mode[SM_DEV_SEMAPHORE.v] &= ~0x1f;
            sm_device_mode[SM_DEV_SEMAPHORE.v] |= (byte) (data & 0x1f);
        } else if (address == 0xf5) {
            mcu.MCU_WriteP1(data);
        } else if (address == 0xf6) {
            mcu.MCU_WriteP0(data);
        } else if (address == 0xf7) {
            sm_p0_dir = data;
        } else {
            logger.log(Level.DEBUG, "sm: unknown sys write %x %x\n", address, data);
        }
    }

    byte SM_SysRead(int address) {
        address &= 0xff;
        if (address < 0xc0) {
            if ((sm_device_mode[SM_DEV_RAM_DIR.v] & (1 << (address >> 5))) == 0)
                sm_access[address >> 3] &= (byte) ~(1 << (address & 7));
            return sm_shared_ram[address];
        } else if (address >= 0xf8 && address < 0xfc) {
            if ((address & 3) == 0) {
                sm_device_mode[SmDev.SM_DEV_INT_REQUEST.v] |= 0x10;
            }
            byte val = sm_device_mode[SM_DEV_IPCE0.v + (address & 3)];
            sm_device_mode[SM_DEV_IPCE0.v + (address & 3)] = 0; // FIXME
            return val;
        } else if (address == 0xff) {
            return sm_device_mode[SM_DEV_SEMAPHORE.v];
        } else if (address == 0xf5) {
            return mcu.MCU_ReadP1();
        } else if (address == 0xf6) {
            return mcu.MCU_ReadP0();
        } else if (address == 0xf7) {
            return sm_p0_dir;
        } else {
            logger.log(Level.DEBUG, "sm: unknown sys read %x".formatted(address));
            return 0;
        }
    }

    short SM_GetVectorAddress(int vector) {
        short pc = SM_Read((short) (0x1fec + vector * 2));
        pc |= (short) (SM_Read((short) (0x1fec + vector * 2 + 1)) << 8);
        return pc;
    }

    void SM_SetStatus(int condition, int mask) {
        if (condition != 0)
            this.sr |= (byte) mask;
        else
            this.sr &= (byte) ~mask;
    }

    void SM_Reset() {
        this.pc = SM_GetVectorAddress(SM_VECTOR_RESET.ordinal());
    }

    byte SM_ReadAdvance() {
        byte byte_ = SM_Read(this.pc);
        this.pc++;
        return byte_;
    }

    short SM_ReadAdvance16() {
        short word = SM_ReadAdvance();
        word |= (short) (SM_ReadAdvance() << 8);
        return word;
    }

    short SM_Read16(short address) {
        short word = SM_Read(address);
        word |= (short) (SM_Read(address) << 8);
        return word;
    }

    void SM_Update_NZ(byte val) {
        SM_SetStatus(val == 0 ? 1 : 0, SM_STATUS_Z.v);
        SM_SetStatus(val & 0x80, SM_STATUS_N.v);
    }

    void SM_PushStack(byte data) {
        SM_Write(this.s, data);
        this.s--;
    }

    byte SM_PopStack() {
        this.s++;
        return SM_Read(this.s);
    }

    void SM_Opcode_NotImplemented(byte opcode) {
        SM_ErrorTrap();
    }

    void SM_Opcode_SEI(byte opcode) { // 78
        SM_SetStatus(1, SM_STATUS_I.v);
    }

    void SM_Opcode_CLD(byte opcode) { // d8
        SM_SetStatus(0, SM_STATUS_D.v);
    }

    void SM_Opcode_CLT(byte opcode) { // 12
        SM_SetStatus(0, SM_STATUS_T.v);
    }

    void SM_Opcode_LDX(byte opcode) { // a2, a6, ae, b6, be
        byte val = 0;
        switch (opcode & 0xff) {
            case 0xa2:
                val = SM_ReadAdvance();
                break;
            case 0xa6:
                val = SM_Read(SM_ReadAdvance());
                break;
            case 0xb6:
                val = SM_Read((short) ((SM_ReadAdvance() + this.y) & 0xff));
                break;
            case 0xae:
                val = SM_Read(SM_ReadAdvance16());
                break;
            case 0xbe:
                val = SM_Read((short) (SM_ReadAdvance16() + this.y));
                break;
        }
        this.x = val;
        SM_Update_NZ(this.x);
    }

    void SM_Opcode_LDY(byte opcode) { // a0, a4, ac, b4, bc
        byte val = 0;
        switch (opcode & 0xff) {
            case 0xa0:
                val = SM_ReadAdvance();
                break;
            case 0xa4:
                val = SM_Read(SM_ReadAdvance());
                break;
            case 0xac:
                val = SM_Read(SM_ReadAdvance16());
                break;
            case 0xb4:
                val = SM_Read((short) ((SM_ReadAdvance() + this.x) & 0xff));
                break;
            case 0xbc:
                val = SM_Read((short) (SM_ReadAdvance16() + this.x));
                break;
        }
        this.y = val;
        SM_Update_NZ(this.y);
    }

    void SM_Opcode_TXS(byte opcode) { // 9a
        this.s = this.x;
    }

    void SM_Opcode_TXA(byte opcode) { // 8a
        this.a = this.x;
        SM_Update_NZ(this.a);
    }

    void SM_Opcode_STA(byte opcode) { // 85, 95, 8d, 9d, 99, 81, 91
        short dest = 0;
        switch (opcode & 0xff) {
            case 0x85:
                dest = SM_ReadAdvance();
                break;
            case 0x95:
                dest = (short) (SM_ReadAdvance() + this.x);
                break;
            case 0x8d:
                dest = SM_ReadAdvance16();
                break;
            case 0x9d:
                dest = (short) (SM_ReadAdvance16() + this.x);
                break;
            case 0x99:
                dest = (short) (SM_ReadAdvance16() + this.y);
                break;
            case 0x81:
                dest = SM_Read16((short) ((SM_ReadAdvance() + this.x) & 0xff));
                break;
            case 0x91:
                dest = (short) (SM_Read16(SM_ReadAdvance()) + this.y);
                break;
        }

        SM_Write(dest, this.a);
    }

    void SM_Opcode_INX(byte opcode) { // e8
        this.x++;
        SM_Update_NZ(this.x);
    }

    void SM_Opcode_INY(byte opcode) { // c8
        this.y++;
        SM_Update_NZ(this.y);
    }

    void SM_Opcode_BBC_BBS(byte opcode) {
        int zp = (opcode & 4) != 0 ? 1 : 0;
        int bit = (opcode >> 5) & 7;
        int type = (opcode >> 4) & 1;
        byte val = 0;

        if (zp == 0) {
            val = this.a;
        } else {
            val = SM_Read(SM_ReadAdvance());
        }

        byte diff = SM_ReadAdvance();

        int set = (val >> bit) & 1;

        if (set != type)
            this.pc += diff;
    }

    void SM_Opcode_CPX(byte opcode) { // e0, e4, ec
        byte operand = 0;
        switch (opcode & 0xff) {
            case 0xe0:
                operand = SM_ReadAdvance();
                break;
            case 0xe4:
                operand = SM_Read(SM_ReadAdvance());
                break;
            case 0xec:
                operand = SM_Read(SM_ReadAdvance16());
                break;
        }
        int diff = this.x - operand;
        SM_SetStatus((diff & 0x100) == 0 ? 1 : 0, SM_STATUS_C.v);
        SM_Update_NZ((byte) (diff & 0xff));
    }

    void SM_Opcode_CPY(byte opcode) { // c0, c4, cc
        byte operand = 0;
        switch (opcode & 0xff) {
            case 0xc0:
                operand = SM_ReadAdvance();
                break;
            case 0xc4:
                operand = SM_Read(SM_ReadAdvance());
                break;
            case 0xcc:
                operand = SM_Read(SM_ReadAdvance16());
                break;
        }
        int diff = this.y - operand;
        SM_SetStatus((diff & 0x100) == 0 ? 1 : 0, SM_STATUS_C.v);
        SM_Update_NZ((byte) (diff & 0xff));
    }

    void SM_Opcode_BEQ(byte opcode) { // f0
        byte diff = SM_ReadAdvance();
        if ((this.sr & SM_STATUS_Z.v) != 0)
            this.pc += diff;
    }

    void SM_Opcode_BCC(byte opcode) { // 90
        byte diff = SM_ReadAdvance();
        if ((this.sr & SM_STATUS_C.v) == 0)
            this.pc += diff;
    }

    void SM_Opcode_BCS(byte opcode) { // b0
        byte diff = SM_ReadAdvance();
        if ((this.sr & SM_STATUS_C.v) != 0)
            this.pc += diff;
    }

    void SM_Opcode_LDM(byte opcode) { // 3c
        byte val = SM_ReadAdvance();
        SM_Write(SM_ReadAdvance(), val);
    }

    void SM_Opcode_LDA(byte opcode) { // a9, a5, b5, ad, bd, b9, a1, b1
        byte val = 0;
        switch (opcode & 0xff) {
            case 0xa9:
                val = SM_ReadAdvance();
                break;
            case 0xa5:
                val = SM_Read(SM_ReadAdvance());
                break;
            case 0xb5:
                val = SM_Read((short) ((SM_ReadAdvance() + this.x) & 0xff));
                break;
            case 0xad:
                val = SM_Read(SM_ReadAdvance16());
                break;
            case 0xbd:
                val = SM_Read((short) (SM_ReadAdvance16() + this.x));
                break;
            case 0xb9:
                val = SM_Read((short) (SM_ReadAdvance16() + this.y));
                break;
            case 0xa1:
                val = SM_Read(SM_Read16((short) ((SM_ReadAdvance() + this.x) & 0xff)));
                break;
            case 0xb1:
                val = SM_Read((short) (SM_Read16(SM_ReadAdvance()) + this.y));
                break;
        }

        if ((this.sr & SM_STATUS_T.v) == 0) {
            this.a = val;
            SM_Update_NZ(val);
        } else {
            // FIXME
            SM_Write(this.x, val);
        }
    }

    void SM_Opcode_CLI(byte opcode) { // 58
        SM_SetStatus(0, SM_STATUS_I.v);
    }

    void SM_Opcode_STP(byte opcode) { // 42
        this.sleep = 1;
    }

    void SM_Opcode_PHA(byte opcode) { // 48
        SM_PushStack(this.a);
    }

    void SM_Opcode_SEB_CLB(byte opcode) {
        int zp = (opcode & 4) != 0 ? 1 : 0;
        int bit = (opcode >> 5) & 7;
        int type = (opcode >> 4) & 1;
        byte val = 0;
        byte dest = 0;

        if (zp == 0) {
            val = this.a;
        } else {
            dest = SM_ReadAdvance();
            val = SM_Read(dest);
        }

        if (type != 0)
            val &= (byte) ~(1 << bit);
        else
            val |= (byte) (1 << bit);

        if (zp == 0) {
            this.a = val;
        } else {
            SM_Write(dest, val);
        }
    }

    void SM_Opcode_RTI(byte opcode) { // 40
        this.sr = SM_PopStack();
        this.pc = SM_PopStack();
        this.pc |= (short) (SM_PopStack() << 8);
    }

    void SM_Opcode_PLA(byte opcode) { // 68
        this.a = SM_PopStack();
        SM_Update_NZ(this.a);
    }

    void SM_Opcode_BRA(byte opcode) { // 80
        byte disp = SM_ReadAdvance();
        this.pc += disp;
    }

    void SM_Opcode_JSR(byte opcode) { // 20, 02, 22
        short newpc = 0;
        switch (opcode) {
            case 0x20:
                newpc = SM_ReadAdvance16();
                break;
            case 0x02:
                newpc = SM_Read16(SM_ReadAdvance());
                break;
            case 0x22:
                newpc = (short) (0xff00 | SM_ReadAdvance());
                break;
        }

        SM_PushStack((byte) (this.pc >> 8));
        SM_PushStack((byte) (this.pc & 0xff));
        this.pc = newpc;
    }

    void SM_Opcode_CMP(byte opcode) // c9, c5, d5, cd, dd, d9, c1, d1
    {
        byte operand = 0;
        switch (opcode & 0xff) {
            case 0xc9:
                operand = SM_ReadAdvance();
                break;
            case 0xc5:
                operand = SM_Read(SM_ReadAdvance());
                break;
            case 0xd5:
                operand = SM_Read((short) ((SM_ReadAdvance() + this.x) & 0xff));
                break;
            case 0xcd:
                operand = SM_Read(SM_ReadAdvance16());
                break;
            case 0xdd:
                operand = SM_Read((short) (SM_ReadAdvance16() + this.x));
                break;
            case 0xd9:
                operand = SM_Read((short) (SM_ReadAdvance16() + this.y));
                break;
            case 0xc1:
                operand = SM_Read(SM_Read16((short) ((SM_ReadAdvance() + this.x) & 0xff)));
                break;
            case 0xd1:
                operand = SM_Read((short) (SM_Read16(SM_ReadAdvance()) + this.y));
                break;
        }
        int diff = this.a - operand;
        SM_SetStatus((diff & 0x100) == 0 ? 1 : 0, SM_STATUS_C.v);
        SM_Update_NZ((byte) (diff & 0xff));
    }

    void SM_Opcode_BNE(byte opcode) { // d0
        byte diff = SM_ReadAdvance();
        if ((this.sr & SM_STATUS_Z.v) == 0)
            this.pc += diff;
    }

    void SM_Opcode_RTS(byte opcode) { // 60
        this.pc = SM_PopStack();
        this.pc |= SM_PopStack() << 8;
    }

    void SM_Opcode_JMP(byte opcode) { // 4c, 6c, b2
        switch (opcode & 0xff) {
            case 0x4c:
                this.pc = SM_ReadAdvance16();
                break;
            case 0x6c:
                this.pc = SM_Read16(SM_ReadAdvance16());
                break;
            case 0xb2:
                this.pc = SM_Read16(SM_ReadAdvance());
                break;
        }
    }

    void SM_Opcode_ORA(byte opcode) { // 09, 05, 15, 0d, 1d, 01, 11
        byte val = 0;
        byte val2 = 0;

        if ((this.sr & SM_STATUS_T.v) == 0) {
            val = this.a;
        } else {
            // FIXME
            val = SM_Read(this.x);
        }

        switch (opcode) {
            case 0x09:
                val2 = SM_ReadAdvance();
                break;
            case 0x05:
                val2 = SM_Read(SM_ReadAdvance());
                break;
            case 0x15:
                val2 = SM_Read((short) ((SM_ReadAdvance() + this.x) & 0xff));
                break;
            case 0x0d:
                val2 = SM_Read(SM_ReadAdvance16());
                break;
            case 0x1d:
                val2 = SM_Read((short) (SM_ReadAdvance16() + this.x));
                break;
            case 0x19:
                val2 = SM_Read((short) (SM_ReadAdvance16() + this.y));
                break;
            case 0x01:
                val2 = SM_Read(SM_Read16((short) ((SM_ReadAdvance() + this.x) & 0xff)));
                break;
            case 0x11:
                val2 = SM_Read((short) (SM_Read16(SM_ReadAdvance()) + this.y));
                break;
        }

        val |= val2;

        if ((this.sr & SM_STATUS_T.v) == 0) {
            this.a = val;

            SM_Update_NZ(val);
        } else {
            // FIXME
            SM_Write(this.x, val);
        }
    }

    void SM_Opcode_DEC(byte opcode) { // 1a, c6, d6, ce, de
        byte val = 0;
        short dest = 0;
        switch (opcode & 0xff) {
            case 0x1a:
                this.a--;
                SM_Update_NZ(this.a);
                return;
            case 0xc6:
                dest = SM_ReadAdvance();
                break;
            case 0xd6:
                dest = (short) ((SM_ReadAdvance() + this.x) & 0xff);
                break;
            case 0xce:
                dest = SM_ReadAdvance16();
                break;
            case 0xde:
                dest = (short) (SM_ReadAdvance16() + this.x);
                break;
        }
        val = SM_Read(dest);
        val--;
        SM_Write(dest, val);
        SM_Update_NZ(val);
    }

    void SM_Opcode_TAX(byte opcode) { // aa
        this.x = this.a;
        SM_Update_NZ(this.x);
    }

    void SM_Opcode_STX(byte opcode) { // 86 96 8e
        short dest = 0;
        switch (opcode & 0xff) {
            case 0x86:
                dest = SM_ReadAdvance();
                break;
            case 0x96:
                dest = (short) (SM_ReadAdvance() + this.x);
                break;
            case 0x8e:
                dest = SM_ReadAdvance16();
                break;
        }

        SM_Write(dest, this.x);
    }

    void SM_Opcode_STY(byte opcode) { // 84 8c 94
        short dest = 0;
        switch (opcode & 0xff) {
            case 0x84:
                dest = SM_ReadAdvance();
                break;
            case 0x94:
                dest = (short) ((SM_ReadAdvance() + this.x) & 0xff);
                break;
            case 0x8c:
                dest = SM_ReadAdvance16();
                break;
        }

        SM_Write(dest, this.y);
    }

    void SM_Opcode_SEC(byte opcode) { // 38
        SM_SetStatus(1, SM_STATUS_C.v);
    }

    void SM_Opcode_NOP(byte opcode) { // EA
    }

    void SM_Opcode_BPL(byte opcode) { // 10
        byte diff = SM_ReadAdvance();
        if ((this.sr & SM_STATUS_N.v) == 0)
            this.pc += diff;
    }

    void SM_Opcode_CLC(byte opcode) { // 18
        SM_SetStatus(0, SM_STATUS_C.v);
    }

    void SM_Opcode_AND(byte opcode) { // 29, 25, 35, 2d, 3d, 21, 31
        byte val = 0;
        byte val2 = 0;

        if ((this.sr & SM_STATUS_T.v) == 0) {
            val = this.a;
        } else {
            // FIXME
            val = SM_Read(this.x);
        }

        switch (opcode) {
            case 0x29:
                val2 = SM_ReadAdvance();
                break;
            case 0x25:
                val2 = SM_Read(SM_ReadAdvance());
                break;
            case 0x35:
                val2 = SM_Read((short) ((SM_ReadAdvance() + this.x) & 0xff));
                break;
            case 0x2d:
                val2 = SM_Read(SM_ReadAdvance16());
                break;
            case 0x3d:
                val2 = SM_Read((short) (SM_ReadAdvance16() + this.x));
                break;
            case 0x39:
                val2 = SM_Read((short) (SM_ReadAdvance16() + this.y));
                break;
            case 0x21:
                val2 = SM_Read(SM_Read16((short) ((SM_ReadAdvance() + this.x) & 0xff)));
                break;
            case 0x31:
                val2 = SM_Read((short) (SM_Read16(SM_ReadAdvance()) + this.y));
                break;
        }

        val &= val2;

        if ((this.sr & SM_STATUS_T.v) == 0) {
            this.a = val;

            SM_Update_NZ(val);
        } else {
            // FIXME
            SM_Write(this.x, val);
        }
    }

    void SM_Opcode_INC(byte opcode) { // 3a, e6, f6, ee, fe
        byte val = 0;
        short dest = 0;
        switch (opcode & 0xff) {
            case 0x3a:
                this.a++;
                SM_Update_NZ(this.a);
                return;
            case 0xe6:
                dest = SM_ReadAdvance();
                break;
            case 0xf6:
                dest = (short) ((SM_ReadAdvance() + this.x) & 0xff);
                break;
            case 0xee:
                dest = SM_ReadAdvance16();
                break;
            case 0xfe:
                dest = (short) (SM_ReadAdvance16() + this.x);
                break;
        }
        val = SM_Read(dest);
        val++;
        SM_Write(dest, val);
        SM_Update_NZ(val);
    }

    @SuppressWarnings("unchecked")
    Consumer<Byte>[] SM_Opcode_Table = List.<Consumer<Byte>>of(
            this::SM_Opcode_NotImplemented, // 00
            this::SM_Opcode_ORA, // 01
            this::SM_Opcode_JSR, // 02
            this::SM_Opcode_BBC_BBS, // 03
            this::SM_Opcode_NotImplemented, // 04
            this::SM_Opcode_ORA, // 05
            this::SM_Opcode_NotImplemented, // 06
            this::SM_Opcode_BBC_BBS, // 07
            this::SM_Opcode_NotImplemented, // 08
            this::SM_Opcode_ORA, // 09
            this::SM_Opcode_NotImplemented, // 0a
            this::SM_Opcode_SEB_CLB, // 0b
            this::SM_Opcode_NotImplemented, // 0c
            this::SM_Opcode_ORA, // 0d
            this::SM_Opcode_NotImplemented, // 0e
            this::SM_Opcode_SEB_CLB, // 0f
            this::SM_Opcode_BPL, // 10
            this::SM_Opcode_ORA, // 11
            this::SM_Opcode_CLT, // 12
            this::SM_Opcode_BBC_BBS, // 13
            this::SM_Opcode_NotImplemented, // 14
            this::SM_Opcode_ORA, // 15
            this::SM_Opcode_NotImplemented, // 16
            this::SM_Opcode_BBC_BBS, // 17
            this::SM_Opcode_CLC, // 18
            this::SM_Opcode_ORA, // 19
            this::SM_Opcode_DEC, // 1a
            this::SM_Opcode_SEB_CLB, // 1b
            this::SM_Opcode_NotImplemented, // 1c
            this::SM_Opcode_ORA, // 1d
            this::SM_Opcode_NotImplemented, // 1e
            this::SM_Opcode_SEB_CLB, // 1f
            this::SM_Opcode_JSR, // 20
            this::SM_Opcode_AND, // 21
            this::SM_Opcode_JSR, // 22
            this::SM_Opcode_BBC_BBS, // 23
            this::SM_Opcode_NotImplemented, // 24
            this::SM_Opcode_AND, // 25
            this::SM_Opcode_NotImplemented, // 26
            this::SM_Opcode_BBC_BBS, // 27
            this::SM_Opcode_NotImplemented, // 28
            this::SM_Opcode_AND, // 29
            this::SM_Opcode_NotImplemented, // 2a
            this::SM_Opcode_SEB_CLB, // 2b
            this::SM_Opcode_NotImplemented, // 2c
            this::SM_Opcode_AND, // 2d
            this::SM_Opcode_NotImplemented, // 2e
            this::SM_Opcode_SEB_CLB, // 2f
            this::SM_Opcode_NotImplemented, // 30
            this::SM_Opcode_AND, // 31
            this::SM_Opcode_NotImplemented, // 32
            this::SM_Opcode_BBC_BBS, // 33
            this::SM_Opcode_NotImplemented, // 34
            this::SM_Opcode_AND, // 35
            this::SM_Opcode_NotImplemented, // 36
            this::SM_Opcode_BBC_BBS, // 37
            this::SM_Opcode_SEC, // 38
            this::SM_Opcode_AND, // 39
            this::SM_Opcode_INC, // 3a
            this::SM_Opcode_SEB_CLB, // 3b
            this::SM_Opcode_LDM, // 3c
            this::SM_Opcode_AND, // 3d
            this::SM_Opcode_NotImplemented, // 3e
            this::SM_Opcode_SEB_CLB, // 3f
            this::SM_Opcode_RTI, // 40
            this::SM_Opcode_NotImplemented, // 41
            this::SM_Opcode_STP, // 42
            this::SM_Opcode_BBC_BBS, // 43
            this::SM_Opcode_NotImplemented, // 44
            this::SM_Opcode_NotImplemented, // 45
            this::SM_Opcode_NotImplemented, // 46
            this::SM_Opcode_BBC_BBS, // 47
            this::SM_Opcode_PHA, // 48
            this::SM_Opcode_NotImplemented, // 49
            this::SM_Opcode_NotImplemented, // 4a
            this::SM_Opcode_SEB_CLB, // 4b
            this::SM_Opcode_JMP, // 4c
            this::SM_Opcode_NotImplemented, // 4d
            this::SM_Opcode_NotImplemented, // 4e
            this::SM_Opcode_SEB_CLB, // 4f
            this::SM_Opcode_NotImplemented, // 50
            this::SM_Opcode_NotImplemented, // 51
            this::SM_Opcode_NotImplemented, // 52
            this::SM_Opcode_BBC_BBS, // 53
            this::SM_Opcode_NotImplemented, // 54
            this::SM_Opcode_NotImplemented, // 55
            this::SM_Opcode_NotImplemented, // 56
            this::SM_Opcode_BBC_BBS, // 57
            this::SM_Opcode_CLI, // 58
            this::SM_Opcode_NotImplemented, // 59
            this::SM_Opcode_NotImplemented, // 5a
            this::SM_Opcode_SEB_CLB, // 5b
            this::SM_Opcode_NotImplemented, // 5c
            this::SM_Opcode_NotImplemented, // 5d
            this::SM_Opcode_NotImplemented, // 5e
            this::SM_Opcode_SEB_CLB, // 5f
            this::SM_Opcode_RTS, // 60
            this::SM_Opcode_NotImplemented, // 61
            this::SM_Opcode_NotImplemented, // 62
            this::SM_Opcode_BBC_BBS, // 63
            this::SM_Opcode_NotImplemented, // 64
            this::SM_Opcode_NotImplemented, // 65
            this::SM_Opcode_NotImplemented, // 66
            this::SM_Opcode_BBC_BBS, // 67
            this::SM_Opcode_PLA, // 68
            this::SM_Opcode_NotImplemented, // 69
            this::SM_Opcode_NotImplemented, // 6a
            this::SM_Opcode_SEB_CLB, // 6b
            this::SM_Opcode_JMP, // 6c
            this::SM_Opcode_NotImplemented, // 6d
            this::SM_Opcode_NotImplemented, // 6e
            this::SM_Opcode_SEB_CLB, // 6f
            this::SM_Opcode_NotImplemented, // 70
            this::SM_Opcode_NotImplemented, // 71
            this::SM_Opcode_NotImplemented, // 72
            this::SM_Opcode_BBC_BBS, // 73
            this::SM_Opcode_NotImplemented, // 74
            this::SM_Opcode_NotImplemented, // 75
            this::SM_Opcode_NotImplemented, // 76
            this::SM_Opcode_BBC_BBS, // 77
            this::SM_Opcode_SEI, // 78
            this::SM_Opcode_NotImplemented, // 79
            this::SM_Opcode_NotImplemented, // 7a
            this::SM_Opcode_SEB_CLB, // 7b
            this::SM_Opcode_NotImplemented, // 7c
            this::SM_Opcode_NotImplemented, // 7d
            this::SM_Opcode_NotImplemented, // 7e
            this::SM_Opcode_SEB_CLB, // 7f
            this::SM_Opcode_BRA, // 80
            this::SM_Opcode_STA, // 81
            this::SM_Opcode_NotImplemented, // 82
            this::SM_Opcode_BBC_BBS, // 83
            this::SM_Opcode_STY, // 84
            this::SM_Opcode_STA, // 85
            this::SM_Opcode_STX, // 86
            this::SM_Opcode_BBC_BBS, // 87
            this::SM_Opcode_NotImplemented, // 88
            this::SM_Opcode_NotImplemented, // 89
            this::SM_Opcode_TXA, // 8a
            this::SM_Opcode_SEB_CLB, // 8b
            this::SM_Opcode_STY, // 8c
            this::SM_Opcode_STA, // 8d
            this::SM_Opcode_STX, // 8e
            this::SM_Opcode_SEB_CLB, // 8f
            this::SM_Opcode_BCC, // 90
            this::SM_Opcode_STA, // 91
            this::SM_Opcode_NotImplemented, // 92
            this::SM_Opcode_BBC_BBS, // 93
            this::SM_Opcode_STY, // 94
            this::SM_Opcode_STA, // 95
            this::SM_Opcode_STX, // 96
            this::SM_Opcode_BBC_BBS, // 97
            this::SM_Opcode_NotImplemented, // 98
            this::SM_Opcode_STA, // 99
            this::SM_Opcode_TXS, // 9a
            this::SM_Opcode_SEB_CLB, // 9b
            this::SM_Opcode_NotImplemented, // 9c
            this::SM_Opcode_STA, // 9d
            this::SM_Opcode_NotImplemented, // 9e
            this::SM_Opcode_SEB_CLB, // 9f
            this::SM_Opcode_LDY, // a0
            this::SM_Opcode_LDA, // a1
            this::SM_Opcode_LDX, // a2
            this::SM_Opcode_BBC_BBS, // a3
            this::SM_Opcode_LDY, // a4
            this::SM_Opcode_LDA, // a5
            this::SM_Opcode_LDX, // a6
            this::SM_Opcode_BBC_BBS, // a7
            this::SM_Opcode_NotImplemented, // a8
            this::SM_Opcode_LDA, // a9
            this::SM_Opcode_TAX, // aa
            this::SM_Opcode_SEB_CLB, // ab
            this::SM_Opcode_LDY, // ac
            this::SM_Opcode_LDA, // ad
            this::SM_Opcode_LDX, // ae
            this::SM_Opcode_SEB_CLB, // af
            this::SM_Opcode_BCS, // b0
            this::SM_Opcode_LDA, // b1
            this::SM_Opcode_JMP, // b2
            this::SM_Opcode_BBC_BBS, // b3
            this::SM_Opcode_LDY, // b4
            this::SM_Opcode_LDA, // b5
            this::SM_Opcode_LDX, // b6
            this::SM_Opcode_BBC_BBS, // b7
            this::SM_Opcode_NotImplemented, // b8
            this::SM_Opcode_LDA, // b9
            this::SM_Opcode_NotImplemented, // ba
            this::SM_Opcode_SEB_CLB, // bb
            this::SM_Opcode_LDY, // bc
            this::SM_Opcode_LDA, // bd
            this::SM_Opcode_LDX, // be
            this::SM_Opcode_SEB_CLB, // bf
            this::SM_Opcode_CPY, // c0
            this::SM_Opcode_CMP, // c1
            this::SM_Opcode_NotImplemented, // c2
            this::SM_Opcode_BBC_BBS, // c3
            this::SM_Opcode_CPY, // c4
            this::SM_Opcode_CMP, // c5
            this::SM_Opcode_DEC, // c6
            this::SM_Opcode_BBC_BBS, // c7
            this::SM_Opcode_INY, // c8
            this::SM_Opcode_CMP, // c9
            this::SM_Opcode_NotImplemented, // ca
            this::SM_Opcode_SEB_CLB, // cb
            this::SM_Opcode_CPY, // cc
            this::SM_Opcode_CMP, // cd
            this::SM_Opcode_DEC, // ce
            this::SM_Opcode_SEB_CLB, // cf
            this::SM_Opcode_BNE, // d0
            this::SM_Opcode_CMP, // d1
            this::SM_Opcode_NotImplemented, // d2
            this::SM_Opcode_BBC_BBS, // d3
            this::SM_Opcode_NotImplemented, // d4
            this::SM_Opcode_CMP, // d5
            this::SM_Opcode_DEC, // d6
            this::SM_Opcode_BBC_BBS, // d7
            this::SM_Opcode_CLD, // d8
            this::SM_Opcode_CMP, // d9
            this::SM_Opcode_NotImplemented, // da
            this::SM_Opcode_SEB_CLB, // db
            this::SM_Opcode_NotImplemented, // dc
            this::SM_Opcode_CMP, // dd
            this::SM_Opcode_DEC, // de
            this::SM_Opcode_SEB_CLB, // df
            this::SM_Opcode_CPX, // e0
            this::SM_Opcode_NotImplemented, // e1
            this::SM_Opcode_NotImplemented, // e2
            this::SM_Opcode_BBC_BBS, // e3
            this::SM_Opcode_CPX, // e4
            this::SM_Opcode_NotImplemented, // e5
            this::SM_Opcode_INC, // e6
            this::SM_Opcode_BBC_BBS, // e7
            this::SM_Opcode_INX, // e8
            this::SM_Opcode_NotImplemented, // e9
            this::SM_Opcode_NOP, // ea
            this::SM_Opcode_SEB_CLB, // eb
            this::SM_Opcode_CPX, // ec
            this::SM_Opcode_NotImplemented, // ed
            this::SM_Opcode_INC, // ee
            this::SM_Opcode_SEB_CLB, // ef
            this::SM_Opcode_BEQ, // f0
            this::SM_Opcode_NotImplemented, // f1
            this::SM_Opcode_NotImplemented, // f2
            this::SM_Opcode_BBC_BBS, // f3
            this::SM_Opcode_NotImplemented, // f4
            this::SM_Opcode_NotImplemented, // f5
            this::SM_Opcode_INC, // f6
            this::SM_Opcode_BBC_BBS, // f7
            this::SM_Opcode_NotImplemented, // f8
            this::SM_Opcode_NotImplemented, // f9
            this::SM_Opcode_NotImplemented, // fa
            this::SM_Opcode_SEB_CLB, // fb
            this::SM_Opcode_NotImplemented, // fc
            this::SM_Opcode_NotImplemented, // fd
            this::SM_Opcode_INC, // fe
            this::SM_Opcode_SEB_CLB // ff
    ).toArray(Consumer[]::new);

    void SM_StartVector(int vector) {
        SM_PushStack((byte) (this.pc >> 8));
        SM_PushStack((byte) (this.pc & 0xff));
        SM_PushStack(this.sr);

        this.sr |= (byte) SM_STATUS_I.v;
        this.sleep = 0;

        this.pc = SM_GetVectorAddress(vector);
    }

    void SM_HandleInterrupt() {
        if ((this.sr & SM_STATUS_I.v) != 0)
            return;

        if ((sm_device_mode[SM_DEV_UART1_CTRL.v] & 0x8) != 0
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x80) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x80) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= (byte) ~0x80;
            SM_StartVector(SmVector.SM_VECTOR_UART1_RX.ordinal());
            return;
        }
        if ((sm_device_mode[SM_DEV_UART2_CTRL.v] & 0x8) != 0
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x40) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x40) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= ~0x40;
            SM_StartVector(SM_VECTOR_UART2_RX.ordinal());
            return;
        }
        if ((sm_device_mode[SM_DEV_UART3_CTRL.v] & 0x8) != 0
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x20) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x20) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= ~0x20;
            SM_StartVector(SM_VECTOR_UART3_RX.ordinal());
            return;
        }
        if ((sm_device_mode[SM_DEV_TIMER_CTRL.v] & 0x80) != 0
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x10) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x10) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= ~0x10;
            SM_StartVector(SM_VECTOR_IPCM0.ordinal());
            return;
        }
        if ((sm_device_mode[SM_DEV_TIMER_CTRL.v] & 0x40) != 0
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x8) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x8) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= ~0x8;
            SM_StartVector(SM_VECTOR_TIMER_X.ordinal());
            return;
        }
        if ((sm_device_mode[SM_DEV_COLLISION.v] & 0xc0) == 0xc0) {
            sm_device_mode[SM_DEV_COLLISION.v] &= ~0x80;
            SM_StartVector(SM_VECTOR_COLLISION.ordinal());
            return;
        }
        if (((sm_device_mode[SM_DEV_UART1_CTRL.v] & 0x10) == 0
                || (sm_cts & 1) != 0)
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x4) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x4) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= ~0x4;
            SM_StartVector(SM_VECTOR_UART1_TX.ordinal());
            return;
        }
        if (((sm_device_mode[SM_DEV_UART2_CTRL.v] & 0x10) == 0
                || (sm_cts & 2) != 0)
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x2) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x2) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= ~0x2;
            SM_StartVector(SM_VECTOR_UART2_TX.ordinal());
            return;
        }
        if (((sm_device_mode[SM_DEV_UART3_CTRL.v] & 0x10) == 0
                || (sm_cts & 4) != 0)
                && (sm_device_mode[SM_DEV_INT_ENABLE.v] & 0x1) != 0
                && (sm_device_mode[SM_DEV_INT_REQUEST.v] & 0x1) != 0) {
            sm_device_mode[SM_DEV_INT_REQUEST.v] &= ~0x1;
            SM_StartVector(SM_VECTOR_UART3_TX.ordinal());
            return;
        }
    }

    void SM_UpdateTimer() {
        while (sm_timer_cycles < this.cycles) {
            if ((sm_device_mode[SM_DEV_TIMER_CTRL.v] & 0x20) == 0 && this.sleep == 0) {
                if (sm_timer_prescaler == 0) {
                    sm_timer_prescaler = sm_device_mode[SM_DEV_PRESCALER.v];

                    if (sm_timer_counter == 0) {
                        sm_timer_counter = sm_device_mode[SM_DEV_TIMER.v];
                        sm_device_mode[SM_DEV_INT_REQUEST.v] |= 0x8;
                    } else
                        sm_timer_counter--;
                } else
                    sm_timer_prescaler--;
            }
            sm_timer_cycles += 16;
        }
    }

    void SM_UpdateUART() {
        if ((sm_device_mode[SM_DEV_UART1_CTRL.v] & 4) == 0) // RX disabled
            return;
        if (mcu.uart_write_ptr == mcu.uart_read_ptr) // no byte
            return;

        if (uart_rx_gotbyte != 0)
            return;

        if (this.cycles < uart_rx_delay)
            return;

        uart_rx_byte = mcu.uart_buffer[mcu.uart_read_ptr];
        mcu.uart_read_ptr = (mcu.uart_read_ptr + 1) % mcu.uart_buffer_size;
        uart_rx_gotbyte = 1;
        sm_device_mode[SM_DEV_INT_REQUEST.v] |= 0x40;

        uart_rx_delay = this.cycles + 3000 * 4;
    }

    void SM_Update(long cycles) {
        while (this.cycles < cycles * 5) {
            SM_HandleInterrupt();

            if (this.sleep == 0) {
                byte opcode = SM_ReadAdvance();

                SM_Opcode_Table[opcode].accept(opcode);
            }

            this.cycles += 12 * 4; // FIXME

            SM_UpdateTimer();
            SM_UpdateUART();
        }
    }
}
