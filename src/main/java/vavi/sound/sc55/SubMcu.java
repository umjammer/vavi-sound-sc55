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
    boolean sleep;
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
        SM_DEV_TIMER_CTRL(0x1f),
        SM_UNKNOWN(-1);
        final int v;

        SmDev(int v) {
            this.v = v;
        }

        public static SmDev valueOf(int address) {
            for (SmDev dev : values())
                if (dev.v == address)
                    return dev;
            return SM_UNKNOWN;
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
    int sm_timer_prescaler;
    int sm_timer_counter;

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
            switch (SmDev.valueOf(address)) {
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
                    return (byte) sm_timer_prescaler;
                case SM_DEV_TIMER:
                    return (byte) sm_timer_counter;
            }
            return sm_device_mode[address];
        } else if (address >= 0x200 && address < 0x2c0) {
            address &= 0xff;
            if ((sm_device_mode[SM_DEV_RAM_DIR.v] & (1 << (address >>> 5))) != 0)
                sm_access[(address & 0xffff) >>> 3] &= (byte) ~(1 << (address & 7));
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
            switch (SmDev.valueOf(address)) {
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
            sm_access[address >>> 3] |= (byte) (1 << (address & 7));
            sm_shared_ram[address] = data;
        } else {
            logger.log(Level.DEBUG, "sm: unknown write %x %x".formatted(address, data));
        }
    }

    void SM_SysWrite(int address, byte data) {
        address &= 0xff;
        if (address < 0xc0) {
            address &= 0xff;
            sm_access[address >>> 3] |= (byte) (1 << (address & 7));
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
            logger.log(Level.TRACE, "sm: unknown sys write %x %x", address, data);
        }
    }

    byte SM_SysRead(int address) {
        address &= 0xff;
        if (address < 0xc0) {
            if ((sm_device_mode[SM_DEV_RAM_DIR.v] & (1 << (address >>> 5))) == 0)
                sm_access[address >>> 3] &= (byte) ~(1 << (address & 7));
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
            logger.log(Level.TRACE, "sm: unknown sys read %x".formatted(address));
            return 0;
        }
    }

    short SM_GetVectorAddress(int vector) {
        int pc = SM_Read((short) (0x1fec + vector * 2)) & 0xff;
        pc |= (SM_Read((short) (0x1fec + vector * 2 + 1)) & 0xff) << 8;
        return (short) pc;
    }

    void SM_SetStatus(int condition, int mask) {
        if (condition != 0)
            this.sr |= (byte) mask;
        else
            this.sr &= (byte) ~mask;
    }

    void SM_Reset() {
        this.a = 0;
        this.x = 0;
        this.y = 0;
        this.s = 0;
        this.sr = 0;
        this.cycles = 0;
        this.sleep = false;

        this.sm_timer_cycles = 0;

        this.pc = SM_GetVectorAddress(SM_VECTOR_RESET.ordinal());
    }

    byte SM_ReadAdvance() {
        byte byte_ = SM_Read(this.pc);
        this.pc++;
        return byte_;
    }

    short SM_ReadAdvance16() {
        int word = SM_ReadAdvance() & 0xff;
        word |= (SM_ReadAdvance() & 0xff) << 8;
        return (short) word;
    }

    short SM_Read16(short address) {
        int word = SM_Read(address) & 0xff;
        word |= (SM_Read(address) & 0xff) << 8;
        return (short) word;
    }

    void SM_Update_NZ(byte val) {
        SM_SetStatus(val == 0 ? 1 : 0, SM_STATUS_Z.v);
        SM_SetStatus(val & 0x80, SM_STATUS_N.v);
    }

    void SM_PushStack(byte data) {
        SM_Write((short) (this.s & 0xff), data);
        this.s--;
    }

    byte SM_PopStack() {
        this.s++;
        return SM_Read((short) (this.s & 0xff));
    }

    void SM_Opcode_NotImplemented(byte opcode) {
        SM_ErrorTrap();
    }

    /**
     * Switch-based dispatch to avoid Consumer interface overhead.
     * This is the HOT PATH - called ~1M times/sec.
     */
    void SM_DispatchOpcode(byte opcode) {
        switch (opcode & 0xff) {
            case 0x01, 0x05, 0x09, 0x0d, 0x11, 0x15, 0x19, 0x1d -> SM_Opcode_ORA(opcode);
            case 0x02, 0x20, 0x22 -> SM_Opcode_JSR(opcode);
            case 0x03, 0x07, 0x13, 0x17, 0x23, 0x27, 0x33, 0x37,
                 0x43, 0x47, 0x53, 0x57, 0x63, 0x67, 0x73, 0x77,
                 0x83, 0x87, 0x93, 0x97, 0xa3, 0xa7, 0xb3, 0xb7,
                 0xc3, 0xc7, 0xd3, 0xd7, 0xe3, 0xe7, 0xf3, 0xf7 -> SM_Opcode_BBC_BBS(opcode);
            case 0x0b, 0x0f, 0x1b, 0x1f, 0x2b, 0x2f, 0x3b, 0x3f,
                 0x4b, 0x4f, 0x5b, 0x5f, 0x6b, 0x6f, 0x7b, 0x7f,
                 0x8b, 0x8f, 0x9b, 0x9f, 0xab, 0xaf, 0xbb, 0xbf,
                 0xcb, 0xcf, 0xdb, 0xdf, 0xeb, 0xef, 0xfb, 0xff -> SM_Opcode_SEB_CLB(opcode);
            case 0x10 -> SM_Opcode_BPL(opcode);
            case 0x12 -> SM_Opcode_CLT(opcode);
            case 0x18 -> SM_Opcode_CLC(opcode);
            case 0x1a, 0xc6, 0xd6, 0xce, 0xde -> SM_Opcode_DEC(opcode);
            case 0x21, 0x25, 0x29, 0x2d, 0x31, 0x35, 0x39, 0x3d -> SM_Opcode_AND(opcode);
            case 0x38 -> SM_Opcode_SEC(opcode);
            case 0x3a, 0xe6, 0xf6, 0xee, 0xfe -> SM_Opcode_INC(opcode);
            case 0x3c -> SM_Opcode_LDM(opcode);
            case 0x40 -> SM_Opcode_RTI(opcode);
            case 0x42 -> SM_Opcode_STP(opcode);
            case 0x48 -> SM_Opcode_PHA(opcode);
            case 0x4c, 0x6c, 0xb2 -> SM_Opcode_JMP(opcode);
            case 0x58 -> SM_Opcode_CLI(opcode);
            case 0x60 -> SM_Opcode_RTS(opcode);
            case 0x68 -> SM_Opcode_PLA(opcode);
            case 0x78 -> SM_Opcode_SEI(opcode);
            case 0x80 -> SM_Opcode_BRA(opcode);
            case 0x81, 0x85, 0x8d, 0x91, 0x95, 0x99, 0x9d -> SM_Opcode_STA(opcode);
            case 0x84, 0x8c, 0x94 -> SM_Opcode_STY(opcode);
            case 0x86, 0x8e, 0x96 -> SM_Opcode_STX(opcode);
            case 0x8a -> SM_Opcode_TXA(opcode);
            case 0x90 -> SM_Opcode_BCC(opcode);
            case 0x9a -> SM_Opcode_TXS(opcode);
            case 0xa0, 0xa4, 0xac, 0xb4, 0xbc -> SM_Opcode_LDY(opcode);
            case 0xa1, 0xa5, 0xa9, 0xad, 0xb1, 0xb5, 0xb9, 0xbd -> SM_Opcode_LDA(opcode);
            case 0xa2, 0xa6, 0xae, 0xb6, 0xbe -> SM_Opcode_LDX(opcode);
            case 0xaa -> SM_Opcode_TAX(opcode);
            case 0xb0 -> SM_Opcode_BCS(opcode);
            case 0xc0, 0xc4, 0xcc -> SM_Opcode_CPY(opcode);
            case 0xc1, 0xc5, 0xc9, 0xcd, 0xd1, 0xd5, 0xd9, 0xdd -> SM_Opcode_CMP(opcode);
            case 0xc8 -> SM_Opcode_INY(opcode);
            case 0xd0 -> SM_Opcode_BNE(opcode);
            case 0xd8 -> SM_Opcode_CLD(opcode);
            case 0xe0, 0xe4, 0xec -> SM_Opcode_CPX(opcode);
            case 0xe8 -> SM_Opcode_INX(opcode);
            case 0xea -> SM_Opcode_NOP(opcode);
            case 0xf0 -> SM_Opcode_BEQ(opcode);
            default -> SM_Opcode_NotImplemented(opcode);
        }
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
                val = SM_Read((short) (SM_ReadAdvance() & 0xff));
                break;
            case 0xb6:
                val = SM_Read((short) (((SM_ReadAdvance() & 0xff) + (this.y & 0xff)) & 0xff));
                break;
            case 0xae:
                val = SM_Read(SM_ReadAdvance16());
                break;
            case 0xbe:
                val = SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.y & 0xff)));
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
                val = SM_Read((short) (SM_ReadAdvance() & 0xff));
                break;
            case 0xac:
                val = SM_Read(SM_ReadAdvance16());
                break;
            case 0xb4:
                val = SM_Read((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff));
                break;
            case 0xbc:
                val = SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff)));
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
                dest = (short) (SM_ReadAdvance() & 0xff);
                break;
            case 0x95:
                dest = (short) ((SM_ReadAdvance() & 0xff) + (this.x & 0xff));
                break;
            case 0x8d:
                dest = SM_ReadAdvance16();
                break;
            case 0x9d:
                dest = (short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff));
                break;
            case 0x99:
                dest = (short) ((SM_ReadAdvance16() & 0xffff) + (this.y & 0xff));
                break;
            case 0x81:
                dest = SM_Read16((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff)); // byte size
                break;
            case 0x91:
                dest = (short) (SM_Read16((short) (SM_ReadAdvance() & 0xff)) + (this.y & 0xff));
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
        int bit = (opcode >>> 5) & 7;
        int type = (opcode >>> 4) & 1;
        byte val = 0;

        if (zp == 0) {
            val = this.a;
        } else {
            val = SM_Read((short) (SM_ReadAdvance() & 0xff));
        }

        byte diff = SM_ReadAdvance(); // signed

        int set = ((val& 0xff) >>> bit) & 1;

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
                operand = SM_Read((short) (SM_ReadAdvance() & 0xff));
                break;
            case 0xec:
                operand = SM_Read(SM_ReadAdvance16());
                break;
        }
        int diff = (this.x & 0xff) - (operand & 0xff);
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
                operand = SM_Read((short) (SM_ReadAdvance() & 0xff));
                break;
            case 0xcc:
                operand = SM_Read(SM_ReadAdvance16());
                break;
        }
        int diff = (this.y & 0xff) - (operand & 0xff);
        SM_SetStatus((diff & 0x100) == 0 ? 1 : 0, SM_STATUS_C.v);
        SM_Update_NZ((byte) (diff & 0xff));
    }

    void SM_Opcode_BEQ(byte opcode) { // f0
        int diff = SM_ReadAdvance(); // signed
        if ((this.sr & SM_STATUS_Z.v) != 0)
            this.pc += (short) diff;
    }

    void SM_Opcode_BCC(byte opcode) { // 90
        int diff = SM_ReadAdvance(); // signed
        if ((this.sr & SM_STATUS_C.v) == 0)
            this.pc += (short) diff;
    }

    void SM_Opcode_BCS(byte opcode) { // b0
        int diff = SM_ReadAdvance(); // signed
        if ((this.sr & SM_STATUS_C.v) != 0)
            this.pc += (short) diff;
    }

    void SM_Opcode_LDM(byte opcode) { // 3c
        byte val = SM_ReadAdvance();
        SM_Write((short) (SM_ReadAdvance() & 0xff), val);
    }

    void SM_Opcode_LDA(byte opcode) { // a9, a5, b5, ad, bd, b9, a1, b1
        byte val = 0;
        switch (opcode & 0xff) {
            case 0xa9:
                val = SM_ReadAdvance();
                break;
            case 0xa5:
                val = SM_Read((short) (SM_ReadAdvance() & 0xff));
                break;
            case 0xb5:
                val = SM_Read((short) (((SM_ReadAdvance() & 0xff) + this.x) & 0xff)); // byte size
                break;
            case 0xad:
                val = SM_Read(SM_ReadAdvance16());
                break;
            case 0xbd:
                val = SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff)));
                break;
            case 0xb9:
                val = SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.y & 0xff)));
                break;
            case 0xa1:
                val = SM_Read(SM_Read16((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff))); // byte size
                break;
            case 0xb1:
                val = SM_Read((short) (SM_Read16((short) (SM_ReadAdvance() & 0xff)) + (this.y & 0xff)));
                break;
        }

        if ((this.sr & SM_STATUS_T.v) == 0) {
            this.a = val;
            SM_Update_NZ(val);
        } else {
            // FIXME
            SM_Write((short) (this.x & 0xff), val);
        }
    }

    void SM_Opcode_CLI(byte opcode) { // 58
        SM_SetStatus(0, SM_STATUS_I.v);
    }

    void SM_Opcode_STP(byte opcode) { // 42
        this.sleep = true;
    }

    void SM_Opcode_PHA(byte opcode) { // 48
        SM_PushStack(this.a);
    }

    void SM_Opcode_SEB_CLB(byte opcode) {
        int zp = (opcode & 4) != 0 ? 1 : 0;
        int bit = (opcode >>> 5) & 7;
        int type = (opcode >>> 4) & 1;
        byte val = 0;
        byte dest = 0;

        if (zp == 0) {
            val = this.a;
        } else {
            dest = SM_ReadAdvance();
            val = SM_Read((short) (dest & 0xff));
        }

        if (type != 0)
            val &= (byte) ~(1 << bit);
        else
            val |= (byte) (1 << bit);

        if (zp == 0) {
            this.a = val;
        } else {
            SM_Write((short) (dest & 0xff), val);
        }
    }

    void SM_Opcode_RTI(byte opcode) { // 40
        this.sr = SM_PopStack();
        this.pc = (short) (SM_PopStack() & 0xff);
        this.pc |= (short) ((SM_PopStack() & 0xff) << 8);
    }

    void SM_Opcode_PLA(byte opcode) { // 68
        this.a = SM_PopStack();
        SM_Update_NZ(this.a);
    }

    void SM_Opcode_BRA(byte opcode) { // 80
        int disp = SM_ReadAdvance(); // signed
        this.pc += (short) disp;
    }

    void SM_Opcode_JSR(byte opcode) { // 20, 02, 22
        short newpc = 0;
        switch (opcode) {
            case 0x20:
                newpc = SM_ReadAdvance16();
                break;
            case 0x02:
                newpc = SM_Read16((short) (SM_ReadAdvance() & 0xff));
                break;
            case 0x22:
                newpc = (short) (0xff00 | (SM_ReadAdvance() & 0xff));
                break;
        }

        SM_PushStack((byte) ((this.pc & 0xffff) >>> 8));
        SM_PushStack((byte) (this.pc & 0xff));
        this.pc = newpc;
    }

    void SM_Opcode_CMP(byte opcode) { // c9, c5, d5, cd, dd, d9, c1, d1
        byte operand = switch (opcode & 0xff) {
            case 0xc9 -> SM_ReadAdvance();
            case 0xc5 -> SM_Read((short) (SM_ReadAdvance() & 0xff));
            case 0xd5 -> SM_Read((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff)); // byte size
            case 0xcd -> SM_Read(SM_ReadAdvance16());
            case 0xdd -> SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff)));
            case 0xd9 -> SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.y & 0xff)));
            case 0xc1 -> SM_Read(SM_Read16((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff))); // byte size
            case 0xd1 -> SM_Read((short) (SM_Read16((short) (SM_ReadAdvance() & 0xff)) + (this.y & 0xff)));
            default -> 0;
        };
        int diff = (this.a & 0xff) - (operand & 0xff);
        SM_SetStatus((diff & 0x100) == 0 ? 1 : 0, SM_STATUS_C.v);
        SM_Update_NZ((byte) (diff & 0xff));
    }

    void SM_Opcode_BNE(byte opcode) { // d0
        int diff = SM_ReadAdvance(); // signed
        if ((this.sr & SM_STATUS_Z.v) == 0)
            this.pc += (short) diff;
    }

    void SM_Opcode_RTS(byte opcode) { // 60
        this.pc = (short) (SM_PopStack() & 0xff);
        this.pc |= (short) ((SM_PopStack() & 0xff) << 8);
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
                this.pc = SM_Read16((short) (SM_ReadAdvance() & 0xff));
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
            val = SM_Read((short) (this.x & 0xff));
        }

        val2 = switch (opcode) {
            case 0x09 -> SM_ReadAdvance();
            case 0x05 -> SM_Read((short) (SM_ReadAdvance() & 0xff));
            case 0x15 -> SM_Read((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff)); // byte size
            case 0x0d -> SM_Read(SM_ReadAdvance16());
            case 0x1d -> SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff)));
            case 0x19 -> SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.y & 0xff)));
            case 0x01 -> SM_Read(SM_Read16((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff))); // byte size
            case 0x11 -> SM_Read((short) (SM_Read16((short) (SM_ReadAdvance() & 0xff)) + (this.y & 0xff)));
            default -> val2;
        };

        val |= val2;

        if ((this.sr & SM_STATUS_T.v) == 0) {
            this.a = val;

            SM_Update_NZ(val);
        } else {
            // FIXME
            SM_Write((short) (this.x & 0xff), val);
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
                dest = (short) (SM_ReadAdvance() & 0xff);
                break;
            case 0xd6:
                dest = (short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff); // byte size
                break;
            case 0xce:
                dest = SM_ReadAdvance16();
                break;
            case 0xde:
                dest = (short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff));
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
        short dest = switch (opcode & 0xff) {
            case 0x86 -> (short) (SM_ReadAdvance() & 0xff);
            case 0x96 -> (short) ((SM_ReadAdvance() & 0xff) + (this.x & 0xff));
            case 0x8e -> SM_ReadAdvance16();
            default -> 0;
        };

        SM_Write(dest, this.x);
    }

    void SM_Opcode_STY(byte opcode) { // 84 8c 94
        short dest = switch (opcode & 0xff) {
            case 0x84 -> (short) (SM_ReadAdvance() & 0xff);
            case 0x94 -> (short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff);
            case 0x8c -> SM_ReadAdvance16();
            default -> 0;
        };

        SM_Write(dest, this.y);
    }

    void SM_Opcode_SEC(byte opcode) { // 38
        SM_SetStatus(1, SM_STATUS_C.v);
    }

    void SM_Opcode_NOP(byte opcode) { // EA
    }

    void SM_Opcode_BPL(byte opcode) { // 10
        int diff = SM_ReadAdvance(); // signed
        if ((this.sr & SM_STATUS_N.v) == 0)
            this.pc += (short) diff;
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
            val = SM_Read((short) (this.x & 0xff));
        }

        val2 = switch (opcode) {
            case 0x29 -> SM_ReadAdvance();
            case 0x25 -> SM_Read((short) (SM_ReadAdvance() & 0xff));
            case 0x35 -> SM_Read((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff)); // byte size
            case 0x2d -> SM_Read(SM_ReadAdvance16());
            case 0x3d -> SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff)));
            case 0x39 -> SM_Read((short) ((SM_ReadAdvance16() & 0xffff) + (this.y & 0xff)));
            case 0x21 -> SM_Read(SM_Read16((short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff))); // byte size
            case 0x31 -> SM_Read((short) (SM_Read16((short) (SM_ReadAdvance() & 0xff)) + (this.y & 0xff)));
            default -> val2;
        };

        val &= val2;

        if ((this.sr & SM_STATUS_T.v) == 0) {
            this.a = val;

            SM_Update_NZ(val);
        } else {
            // FIXME
            SM_Write((short) (this.x & 0xff), val);
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
                dest = (short) (SM_ReadAdvance() & 0xff);
                break;
            case 0xf6:
                dest = (short) (((SM_ReadAdvance() & 0xff) + (this.x & 0xff)) & 0xff);
                break;
            case 0xee:
                dest = SM_ReadAdvance16();
                break;
            case 0xfe:
                dest = (short) ((SM_ReadAdvance16() & 0xffff) + (this.x & 0xff));
                break;
        }
        val = SM_Read(dest);
        val++;
        SM_Write(dest, val);
        SM_Update_NZ(val);
    }

    void SM_StartVector(int vector) {
        SM_PushStack((byte) ((this.pc & 0xffff) >>> 8));
        SM_PushStack((byte) (this.pc & 0xff));
        SM_PushStack(this.sr);

        this.sr |= (byte) SM_STATUS_I.v;
        this.sleep = false;

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
            sm_device_mode[SM_DEV_COLLISION.v] &= (byte) ~0x80;
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
            if ((sm_device_mode[SM_DEV_TIMER_CTRL.v] & 0x20) == 0 && !this.sleep) {
                if (sm_timer_prescaler == 0) {
                    sm_timer_prescaler = sm_device_mode[SM_DEV_PRESCALER.v] & 0xff;

                    if (sm_timer_counter == 0) {
                        sm_timer_counter = sm_device_mode[SM_DEV_TIMER.v] & 0xff;
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
        if ((sm_device_mode[SM_DEV_UART1_CTRL.v] & 4) == 0) { // RX disabled
            return;
        }
        if (mcu.uart_write_ptr.get() == mcu.uart_read_ptr.get()) { // no byte
            return;
        }

        if (uart_rx_gotbyte != 0) {
            return;
        }

        if (this.cycles < uart_rx_delay) {
            return;
        }

        uart_rx_byte = mcu.uart_buffer[mcu.uart_read_ptr.get()];
        mcu.uart_read_ptr.set((mcu.uart_read_ptr.get() + 1) % Mcu.uart_buffer_size);
        uart_rx_gotbyte = 1;
        sm_device_mode[SM_DEV_INT_REQUEST.v] |= 0x40;

        uart_rx_delay = this.cycles + 3000 * 4;
    }

    void SM_Update(long cycles) {
        while (this.cycles < cycles * 5) {
            SM_HandleInterrupt();

            if (!this.sleep) {
                byte opcode = SM_ReadAdvance();

                SM_DispatchOpcode(opcode);  // Switch-based dispatch (no interface overhead)
            }

            this.cycles += 12 * 4; // FIXME

            SM_UpdateTimer();
            SM_UpdateUART();
        }
    }
}
