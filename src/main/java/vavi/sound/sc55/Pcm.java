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
import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_IRQ0;


class Pcm {

    private static final Logger logger = getLogger(Pcm.class.getName());

//    static class pcm_t {

        int[][] ram1 = new int[32][8];
        short[][] ram2 = new short[32][16];
        int select_channel;
        int voice_mask;
        int voice_mask_pending;
        int voice_mask_updating;
        int write_latch;
        int wave_read_address;
        byte wave_byte_latch;
        int read_latch;
        byte config_reg_3c; // SC55:c3 JV880:c0
        byte config_reg_3d;
        int irq_channel;
        int irq_assert;

        int nfs;

        int tv_counter;

        long cycles;

        short[] eram = new short[0x4000];

        int accum_l;
        int accum_r;
        int[] rcsum = new int[2];
//    }

    private Mcu mcu;

    Pcm(Mcu mcu) {
        this.mcu = mcu;
    }

    byte[] waverom1 = new byte[0x20_0000];
    byte[] waverom2 = new byte[0x20_0000];
    byte[] waverom3 = new byte[0x10_0000];
    byte[] waverom_card = new byte[0x20_0000];
    byte[] waverom_exp = new byte[0x80_0000];

    byte PCM_ReadROM(int address) {
        int bank;
        if ((this.config_reg_3d & 0x20) != 0)
            bank = (address >> 21) & 7;
        else
            bank = (address >> 19) & 7;
        switch (bank) {
            case 0:
                if (mcu.mcu_mk1)
                    return waverom1[address & 0xf_ffff];
                else
                    return waverom1[address & 0x1f_ffff];
            case 1:
                if (!mcu.mcu_jv880)
                    return waverom2[address & 0xf_ffff];
                else
                    return waverom2[address & 0x1f_ffff];
            case 2:
                if (mcu.mcu_jv880)
                    return waverom_card[address & 0x1f_ffff];
                else
                    return waverom3[address & 0xf_ffff];
            case 3:
            case 4:
            case 5:
            case 6:
                if (mcu.mcu_jv880)
                    return waverom_exp[(address & 0x1f_ffff) + (bank - 3) * 0x20_0000];
            default:
                break;
        }
        return 0;
    }

    void PCM_Write(int address, byte data) {
        address &= 0x3f;
        if (address < 0x4) { // voice enable
            switch (address & 3) {
                case 0:
                    this.voice_mask_pending &= ~0xf000000;
                    this.voice_mask_pending |= (data & 0xf) << 24;
                    break;
                case 1:
                    this.voice_mask_pending &= ~0xff0000;
                    this.voice_mask_pending |= (data & 0xff) << 16;
                    break;
                case 2:
                    this.voice_mask_pending &= ~0xff00;
                    this.voice_mask_pending |= (data & 0xff) << 8;
                    break;
                case 3:
                    this.voice_mask_pending &= ~0xff;
                    this.voice_mask_pending |= (data & 0xff) << 0;
                    break;
            }
            this.voice_mask_updating = 1;
        } else if (address >= 0x20 && address < 0x24) // wave rom
        {
            switch (address & 3) {
                case 1:
                    this.wave_read_address &= ~0xff0000;
                    this.wave_read_address |= (data & 0xff) << 16;
                    break;
                case 2:
                    this.wave_read_address &= ~0xff00;
                    this.wave_read_address |= (data & 0xff) << 8;
                    break;
                case 3:
                    this.wave_read_address &= ~0xff;
                    this.wave_read_address |= (data & 0xff) << 0;
                    this.wave_byte_latch = PCM_ReadROM(this.wave_read_address);
                    break;
            }
        } else if (address == 0x3c) {
            this.config_reg_3c = data;
        } else if (address == 0x3d) {
            this.config_reg_3d = data;
        } else if (address == 0x3e) {
            this.select_channel = data & 0x1f;
        } else if ((address >= 0x4 && address < 0x10) || (address >= 0x24 && address < 0x30)) {
            switch (address & 3) {
                case 1:
                    this.write_latch &= ~0xf0000;
                    this.write_latch |= (data & 0xf) << 16;
                    break;
                case 2:
                    this.write_latch &= ~0xff00;
                    this.write_latch |= (data & 0xff) << 8;
                    break;
                case 3:
                    this.write_latch &= ~0xff;
                    this.write_latch |= (data & 0xff) << 0;
                    break;
            }
            if ((address & 3) == 3) {
                int ix = 0;
                if ((address & 32) != 0)
                    ix |= 1;
                if ((address & 8) == 0)
                    ix |= 4;
                if ((address & 4) == 0)
                    ix |= 2;

                this.ram1[this.select_channel][ix] = this.write_latch;
            }
        } else if ((address >= 0x10 && address < 0x20) || (address >= 0x30 && address < 0x38)) {
            switch (address & 1) {
                case 0:
                    this.write_latch &= ~0xff00;
                    this.write_latch |= (data & 0xff) << 8;
                    break;
                case 1:
                    this.write_latch &= ~0xff;
                    this.write_latch |= (data & 0xff) << 0;
                    break;
            }
            if ((address & 1) == 1) {
                int ix = (address >> 1) & 7;
                if ((address & 32) != 0)
                    ix |= 8;

                this.ram2[this.select_channel][ix] = (short) this.write_latch;
            }
        }
    }

    // rv: [30][2], [30][3]
    // ch: [31][2], [31][5]
    byte PCM_Read(int address) {
        address &= 0x3f;
//        logger.log(Level.DEBUG, "PCM Read: %2x, read_latch: %x".formatted(address, read_latch));

        if (address < 0x4) {
            if (this.voice_mask_updating != 0)
                this.voice_mask = this.voice_mask_pending;
            this.voice_mask_updating = 0;
        } else if (address == 0x3c || address == 0x3e) { // status
            byte status = 0;
            if (address == 0x3e && this.irq_assert != 0) {
                this.irq_assert = 0;
                if (mcu.mcu_jv880)
                    mcu.MCU_GA_SetGAInt(5, false);
                else
                    mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ0.ordinal(), false);
            }

            status |= (byte) this.irq_channel;
            if (this.voice_mask_updating != 0)
                status |= 32;

            return status;
        } else if (address == 0x3f) {
            return this.wave_byte_latch;
        } else if ((address >= 0x4 && address < 0x10) || (address >= 0x24 && address < 0x30)) {
            if ((address & 3) == 1) {
                int ix = 0;
                if ((address & 32) != 0)
                    ix |= 1;
                if ((address & 8) == 0)
                    ix |= 4;
                if ((address & 4) == 0)
                    ix |= 2;

                this.read_latch = this.ram1[this.select_channel][ix];
            }
        } else if ((address >= 0x10 && address < 0x20) || (address >= 0x30 && address < 0x38)) {
            if ((address & 1) == 0) {
                int ix = (address >> 1) & 7;
                if ((address & 32) != 0)
                    ix |= 8;

                this.read_latch = this.ram2[this.select_channel][ix];
            }
        } else if (address >= 0x39 && address <= 0x3b) {
            switch (address & 3) {
                case 1:
                    return (byte) ((this.read_latch >> 16) & 0xf);
                case 2:
                    return (byte) ((this.read_latch >> 8) & 0xff);
                case 3:
                    return (byte) ((this.read_latch >> 0) & 0xff);
            }
        }

        return 0;
    }

    void PCM_Reset() {
    }

    private int addclip20(int add1, int add2, int cin) {
        int sum = (add1 + add2 + cin) & 0xf_ffff;
        if ((add1 & 0x8_0000) != 0 && (add2 & 0x8_0000) != 0 && (sum & 0x8_0000) == 0)
            sum = 0x8_0000;
        else if ((add1 & 0x8_0000) == 0 && (add2 & 0x8_0000) == 0 && (sum & 0x8_0000) != 0)
            sum = 0x7_ffff;
        return sum;
    }

    private int multi(int val1, byte val2) {
        if ((val1 & 0x8_0000) != 0)
            val1 |= ~0xf_ffff;
        else
            val1 &= 0x7_ffff;

        val1 *= val2;
        if ((val1 & 0x800_0000) != 0)
            val1 |= ~0x1ff_ffff;
        else
            val1 &= 0x1ff_ffff;
        return val1;
    }

    private static final int[][] interp_lut = {
            {
                    3385, 3401, 3417, 3432, 3448, 3463, 3478, 3492, 3506, 3521, 3534, 3548, 3562, 3575, 3588, 3601,
                    3614, 3626, 3638, 3650, 3662, 3673, 3685, 3696, 3707, 3718, 3728, 3739, 3749, 3759, 3768, 3778,
                    3787, 3796, 3805, 3814, 3823, 3831, 3839, 3847, 3855, 3863, 3870, 3878, 3885, 3892, 3899, 3905,
                    3912, 3918, 3924, 3930, 3936, 3942, 3948, 3953, 3958, 3963, 3968, 3973, 3978, 3983, 3987, 3991,
                    3995, 4000, 4004, 4007, 4011, 4015, 4018, 4022, 4025, 4028, 4031, 4034, 4037, 4040, 4042, 4045,
                    4047, 4050, 4052, 4054, 4057, 4059, 4061, 4063, 4064, 4066, 4068, 4070, 4071, 4073, 4074, 4076,
                    4077, 4078, 4079, 4081, 4082, 4083, 4084, 4085, 4086, 4086, 4087, 4088, 4089, 4089, 4090, 4091,
                    4091, 4092, 4092, 4093, 4093, 4094, 4094, 4094, 4094, 4095, 4095, 4095, 4095, 4095, 4095, 4095,
            },
            {
                    710, 726, 742, 758, 775, 792, 809, 826, 844, 861, 879, 897, 915, 933, 952, 971,
                    990, 1009, 1028, 1047, 1067, 1087, 1106, 1126, 1147, 1167, 1188, 1208, 1229, 1250, 1271, 1292,
                    1314, 1335, 1357, 1379, 1400, 1423, 1445, 1467, 1489, 1512, 1534, 1557, 1580, 1602, 1625, 1648,
                    1671, 1695, 1718, 1741, 1764, 1788, 1811, 1835, 1858, 1882, 1906, 1929, 1953, 1977, 2000, 2024,
                    2048, 2069, 2095, 2119, 2143, 2166, 2190, 2214, 2237, 2261, 2284, 2308, 2331, 2355, 2378, 2401,
                    2425, 2448, 2471, 2494, 2517, 2539, 2562, 2585, 2607, 2630, 2652, 2674, 2696, 2718, 2740, 2762,
                    2783, 2805, 2826, 2847, 2868, 2889, 2910, 2931, 2951, 2971, 2991, 3011, 3031, 3051, 3070, 3089,
                    3108, 3127, 3146, 3164, 3182, 3200, 3218, 3236, 3253, 3271, 3288, 3304, 3321, 3338, 3354, 3370,
            },
            {
                    0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 3, 4, 4, 5, 5, 6,
                    6, 7, 8, 8, 9, 10, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                    20, 22, 23, 24, 26, 27, 29, 30, 32, 34, 36, 38, 40, 42, 44, 46,
                    49, 51, 53, 56, 59, 62, 65, 68, 71, 74, 77, 81, 84, 88, 92, 96,
                    100, 104, 109, 113, 118, 122, 127, 132, 137, 143, 148, 154, 160, 165, 171, 178,
                    184, 191, 197, 204, 211, 219, 226, 234, 241, 249, 257, 266, 274, 283, 292, 301,
                    310, 319, 329, 339, 349, 359, 369, 380, 391, 402, 413, 424, 436, 448, 460, 472,
                    484, 497, 510, 523, 536, 549, 563, 577, 591, 605, 619, 634, 648, 663, 679, 694,
            }
    };

    private void calc_tv(int e, int adjust, short[] levelcur, int lp, boolean active, int[] volmul) {
        // int adjust = ram2[3+e];
        // int levelcur = ram2[9+e] & 0x7fff;
        levelcur[lp] &= 0x7fff;
        int speed = adjust & 0xff;
        int target = (adjust >> 8) & 0xff;


        int w1 = (speed & 0xf0) == 0 ? 1 : 0;
        int w2 = w1 != 0 || (speed & 0x10) != 0 ? 1 : 0;
        int w3 = this.nfs != 0 &&
                ((speed & 0x80) == 0 || ((speed & 0x40) == 0 && (w2 == 0 || (speed & 0x20) == 0))) ? 1 : 0;

        int type = w2 | (w3 << 3);
        if ((speed & 0x20) != 0)
            type |= 2;
        if ((speed & 0x80) == 0 || (speed & 0x40) == 0)
            type |= 4;


        boolean write = !active;
        int addlow = 0;
        if ((type & 4) != 0) {
            if ((this.tv_counter & 8) == 0)
                addlow |= 1;
            if ((this.tv_counter & 4) == 0)
                addlow |= 2;
            if ((this.tv_counter & 2) == 0)
                addlow |= 4;
            if ((this.tv_counter & 1) == 0)
                addlow |= 8;
            write |= true;
        } else {
            switch (type & 3) {
                case 0:
                    if ((this.tv_counter & 0x20) == 0)
                        addlow |= 1;
                    if ((this.tv_counter & 0x10) == 0)
                        addlow |= 2;
                    if ((this.tv_counter & 8) == 0)
                        addlow |= 4;
                    if ((this.tv_counter & 4) == 0)
                        addlow |= 8;
                    write |= (this.tv_counter & 3) == 0;
                    break;
                case 1:
                    if ((this.tv_counter & 0x80) == 0)
                        addlow |= 1;
                    if ((this.tv_counter & 0x40) == 0)
                        addlow |= 2;
                    if ((this.tv_counter & 0x20) == 0)
                        addlow |= 4;
                    if ((this.tv_counter & 0x10) == 0)
                        addlow |= 8;
                    write |= (this.tv_counter & 15) == 0;
                    break;
                case 2:
                    if ((this.tv_counter & 0x200) == 0)
                        addlow |= 1;
                    if ((this.tv_counter & 0x100) == 0)
                        addlow |= 2;
                    if ((this.tv_counter & 0x80) == 0)
                        addlow |= 4;
                    if ((this.tv_counter & 0x40) == 0)
                        addlow |= 8;
                    write |= (this.tv_counter & 63) == 0;
                    break;
                case 3:
                    if ((this.tv_counter & 0x800) == 0)
                        addlow |= 1;
                    if ((this.tv_counter & 0x400) == 0)
                        addlow |= 2;
                    if ((this.tv_counter & 0x200) == 0)
                        addlow |= 4;
                    if ((this.tv_counter & 0x100) == 0)
                        addlow |= 8;
                    write |= (this.tv_counter & 127) == 0;
                    break;
            }
        }

        if ((type & 8) == 0) {
            int shift = speed & 15;
            shift = (10 - shift) & 15;

            int sum1 = (target << 11); // 5
            if (e != 2 || active)
                sum1 -= (levelcur[lp] << 4); // 6
            int neg = (sum1 & 0x8_0000) != 0 ? 1 : 0;

            int preshift = sum1;

            int shifted = preshift >> shift;
            shifted -= sum1;

            int sum2 = (target << 11) + addlow + shifted;
            if (write && this.nfs != 0)
                levelcur[lp] = (short) ((sum2 >> 4) & 0x7fff);

            if (e == 0) {
                volmul[0] = (sum2 >> 4) & 0x7ffe;
            } else if (e == 1) {
                volmul[0] = (sum2 >> 4) & 0x7ffe;
            }
        } else {
            int shift = (speed >> 4) & 14;
            shift |= w2;
            shift = (10 - shift) & 15;

            int sum1 = target << 11; // 5
            if (e != 2 || active)
                sum1 -= (levelcur[lp] << 4); // 6
            int neg = (sum1 & 0x8_0000) != 0 ? 1 : 0;
            int preshift = (speed & 15) << 9;
            if (w1 == 0)
                preshift |= 0x2000;
            if (neg != 0)
                preshift ^= ~0x3f;

            int shifted = preshift >> shift;
            int sum2 = shifted;
            if (e != 2 || active)
                sum2 += (levelcur[lp] << 4) | addlow;

            int sum2_l = (sum2 >> 4);

            int sum3 = (target << 11) - (sum2_l << 4);

            int neg2 = (sum3 & 0x8_0000) != 0 ? 1 : 0;
            int xnor = (neg2 ^ neg) == 0 ? 1 : 0;

            if (write && this.nfs != 0) {
                if (xnor != 0)
                    levelcur[lp] = (short) (sum2_l & 0x7fff);
                else
                    levelcur[lp] = (short) (target << 7);
            }

            if (e == 0) {
                volmul[0] = sum2_l & 0x7ffe;
            } else if (e == 1) {
                if (xnor != 0)
                    volmul[0] = sum2_l & 0x7ffe;
                else
                    volmul[0] = target << 7;
            }
        }
    }

    private final int eram_unpack(int addr, int type /* = 0 */) {
        addr &= 0x3fff;
        int data = this.eram[addr];
        int val = data & 0x3fff;
        int sh = (data >> 14) & 3;

        val <<= 18;
        return val >> (18 - sh * 2 + type);
    }

    private final void eram_pack(int addr, int val) {
        addr &= 0x3fff;
        int sh = 0;
        int top = (val >> 13) & 0x7f;
        if ((top & 0x40) != 0)
            top ^= 0x7f;
        if (top >= 16)
            sh = 3;
        else if (top >= 4)
            sh = 2;
        else if (top >= 1)
            sh = 1;
        else
            sh = 0;

        int data = (val >> (sh * 2)) & 0x3fff;
        data |= sh << 14;
        this.eram[addr] = (short) data;
    }

    void PCM_Update(long cycles) {
        int reg_slots = (this.config_reg_3d & 31) + 1;
        int voice_active = this.voice_mask & this.voice_mask_pending;
        while (this.cycles < cycles) {
            int[] tt = new int[2];

            { // final mixing
                int noise_mask = 0;
                int orval = 0;
                int write_mask = 0;
                int dac_mask = 0;
                if ((this.config_reg_3c & 0x30) != 0) {
                    switch ((this.config_reg_3c >> 2) & 3) {
                        case 1:
                            noise_mask = 3;
                            break;
                        case 2:
                            noise_mask = 7;
                            break;
                        case 3:
                            noise_mask = 15;
                            break;
                    }
                    switch (this.config_reg_3c & 3) {
                        case 1:
                            orval |= 1 << 8;
                            break;
                        case 2:
                            orval |= 1 << 10;
                            break;
                    }
                    write_mask = 15;
                    dac_mask = ~15;
                } else {
                    switch ((this.config_reg_3c >> 2) & 3) {
                        case 2:
                            noise_mask = 1;
                            break;
                        case 3:
                            noise_mask = 3;
                            break;
                    }
                    switch (this.config_reg_3c & 3) {
                        case 1:
                            orval |= 1 << 6;
                            break;
                        case 2:
                            orval |= 1 << 8;
                            break;
                    }
                    write_mask = 3;
                    dac_mask = ~3;
                }
                if ((this.config_reg_3c & 0x80) == 0)
                    write_mask = 0;
                if ((this.config_reg_3c & 0x30) == 0x30)
                    orval |= 1 << 12;

                int shifter = this.ram2[30][10] & 0xffff;
                int xr = ((shifter >> 0) ^ (shifter >> 1) ^ (shifter >> 7) ^ (shifter >> 12)) & 1;
                shifter = (shifter >> 1) | (xr << 15);
                this.ram2[30][10] = (short) shifter;

                this.accum_l = addclip20(this.accum_l, this.ram1[30][0], 0);
                this.accum_r = addclip20(this.accum_r, this.ram1[30][1], 0);

                this.ram1[30][2] = addclip20(this.accum_l,
                        orval | (shifter & noise_mask), 0);

                this.ram1[30][4] = addclip20(this.accum_r,
                        orval | (shifter & noise_mask), 0);

                this.ram1[30][0] = this.accum_l & write_mask;
                this.ram1[30][1] = this.accum_r & write_mask;


                tt[0] = (this.ram1[30][2] & ~write_mask) << 12;
                tt[1] = (this.ram1[30][4] & ~write_mask) << 12;

                mcu.MCU_PostSample(tt);

                xr = ((shifter >> 0) ^ (shifter >> 1) ^ (shifter >> 7) ^ (shifter >> 12)) & 1;
                shifter = (shifter >> 1) | (xr << 15);

                this.accum_l = addclip20(this.accum_l, this.ram1[30][0], 0);
                this.accum_r = addclip20(this.accum_r, this.ram1[30][1], 0);

                this.ram1[30][3] = addclip20(this.accum_l,
                        orval | (shifter & noise_mask), 0);

                this.ram1[30][5] = addclip20(this.accum_r,
                        orval | (shifter & noise_mask), 0);

                if ((this.config_reg_3c & 0x40) != 0) { // oversampling
                    this.ram2[30][10] = (short) shifter;

                    this.ram1[30][0] = this.accum_l & write_mask;
                    this.ram1[30][1] = this.accum_r & write_mask;


                    tt[0] = (this.ram1[30][3] & ~write_mask) << 12;
                    tt[1] = (this.ram1[30][5] & ~write_mask) << 12;

                    mcu.MCU_PostSample(tt);
                }
            }

            { // global counter for envelopes
                if (this.nfs == 0)
                    this.tv_counter = this.ram2[31][8] & 0xffff; // fixme

                this.tv_counter -= 1;

                this.tv_counter &= 0x3fff;
            }

            // chorus/reverb

            { // fixme
                if ((this.ram2[31][8] & 0x8000) != 0)
                    this.ram2[31][9] = (short) (this.ram2[31][8] & 0x7fff);
                else
                    this.ram2[31][10] = (short) (this.ram2[31][8] & 0x7fff);

                if (((0x4000 - this.ram2[31][8]) & 0x8000) != 0)
                    this.ram2[31][10] = (short) ((0x4000 - this.ram2[31][8]) & 0x7fff);
                else
                    this.ram2[31][9] = (short) ((0x4000 - this.ram2[31][8]) & 0x7fff);
            }

            {
                int v1 = this.ram2[31][1] & 0xffff;

                int m1 = multi(this.ram1[29][1], (byte) (v1 >> 8)) >> 5; // 14
                int m2 = multi(this.rcsum[1], (byte) (v1 & 255)) >> 5; // 15

                this.ram1[29][1] = addclip20(m1 >> 1, m2 >> 1, (m1 | m2) & 1); // 16
            }

            {
                boolean okey = (this.ram2[31][7] & 0x20) != 0;
                boolean key = true;
                boolean active = okey && key;
                int[] u = new int[1];
                calc_tv(1, this.ram2[30][0] & 0xffff, this.ram2[30], 9, active, u);
            }

            {
                int v1 = this.ram2[30][1] & 0xffff;
                int m1 = multi(this.ram1[29][0], (byte) (v1 >> 8)) >> 5; // 17
                int m2 = multi(this.rcsum[0], (byte) (v1 & 255)) >> 5; // 18

                this.ram1[29][0] = addclip20(m1 >> 1, m2 >> 1, (m1 | m2) & 1); // 19
            }

            int[] rcadd = new int[6];
            int[] rcadd2 = new int[ 6];

            {
                {
                    // 1
                    int v1 = this.ram2[30][4] & 0xffff;
                    int m1 = multi(this.ram1[29][0], (byte) (v1 >> 8)) >> 6;
                    int v2 = 0;
                    int s1 = eram_unpack((this.ram2[28][1] & 0xffff) + this.tv_counter, 1);
                    int s2 = eram_unpack((this.ram2[28][1] & 0xffff) + this.tv_counter, 0);
                    if ((v1 & 0x30) != 0) {
                        v2 = s1;
                    }
                    int v3 = addclip20(m1, v2 ^ 0xf_ffff, 1);
                    this.ram1[29][4] = v3;
                    int m2 = multi(v3, (byte) (v1 & 255)) >> 5;
                    this.ram1[29][5] = addclip20(m2 >> 1, s2, m2 & 1);
                }
                {
                    // 2
                    int v1 = this.ram2[30][4] & 0xffff;
                    int v2 = 0;
                    int s1 = eram_unpack((this.ram2[28][2] & 0xffff) + this.tv_counter, 1);
                    int s2 = eram_unpack((this.ram2[28][2] & 0xffff) + this.tv_counter, 0);
                    if ((v1 & 0x30) != 0) {
                        v2 = s1;
                    }
                    int v3 = addclip20(this.ram1[29][5], v2 ^ 0xf_ffff, 1);
                    this.ram1[29][5] = v3;
                    int m2 = multi(v3, (byte) (v1 & 255)) >> 5;
                    this.ram1[28][0] = addclip20(m2 >> 1, s2, m2 & 1);
                }
                {
                    // 3
                    int v1 = this.ram2[30][4] & 0xffff;
                    int v2 = 0;
                    int s1 = eram_unpack((this.ram2[28][3] & 0xffff) + this.tv_counter, 1);
                    int s2 = eram_unpack((this.ram2[28][3] & 0xffff) + this.tv_counter, 0);
                    if ((v1 & 0x30) != 0) {
                        v2 = s1;
                    }
                    int v3 = addclip20(this.ram1[28][0], v2 ^ 0xf_ffff, 1);
                    this.ram1[28][0] = v3;
                    int m2 = multi(v3, (byte) (v1 & 255)) >> 5;
                    this.ram1[28][1] = addclip20(m2 >> 1, s2, m2 & 1);


                    this.ram1[28][2] = eram_unpack((this.ram2[28][5] & 0xffff) + this.tv_counter, 0);
                }
                {
                    // 4
                    int v1 = this.ram2[30][5] & 0xffff;
                    int v2 = 0;
                    int s1 = eram_unpack((this.ram2[28][4] & 0xffff) + this.tv_counter, 1);
                    int s2 = eram_unpack((this.ram2[28][4] & 0xffff) + this.tv_counter, 0);
                    if ((v1 & 0x30) != 0) {
                        v2 = s1;
                    }
                    int v3 = addclip20(this.ram1[28][1], v2 ^ 0xf_ffff, 1);
                    this.ram1[28][1] = v3;
                    int m2 = multi(v3, (byte) (v1 & 255)) >> 5;
                    this.ram1[28][3] = addclip20(m2 >> 1, s2, m2 & 1);


                    this.ram1[28][4] = eram_unpack((this.ram2[29][1] & 0xffff) + this.tv_counter, 0);
                }
                {
                    // 5

                    int v1 = this.ram2[30][7] & 0xffff;
                    int m1 = multi(this.ram1[29][2], (byte) (v1 >> 8)) >> 5;
                    int s1 = eram_unpack((this.ram2[29][0] & 0xffff) + this.tv_counter, 0);
                    int m2 = multi(s1, (byte) (v1 & 255)) >> 5;
                    this.ram1[29][2] = addclip20(m1 >> 1, m2 >> 1, (m1 | m2) & 1);

                    eram_pack((this.ram2[28][0] & 0xffff) + this.tv_counter, this.ram1[29][4]);
                }
                {
                    // 6

                    int v1 = this.ram2[30][8] & 0xffff;
                    int m1 = multi(this.ram1[29][3], (byte) (v1 >> 8)) >> 5;
                    int s1 = eram_unpack((this.ram2[29][8] & 0xffff) + this.tv_counter, 0);
                    int m2 = multi(s1, (byte) (v1 & 255)) >> 5;
                    this.ram1[29][3] = addclip20(m1 >> 1, m2 >> 1, (m1 | m2) & 1);

                    eram_pack((this.ram2[28][1] & 0xffff) + this.tv_counter, this.ram1[29][5]);

                    eram_pack((this.ram2[28][2] & 0xffff) + this.tv_counter, this.ram1[28][0]);
                }
                {
                    // 7

                    int v1 = this.ram2[30][9] & 0xffff;
                    int v2 = this.ram1[28][3];
                    int m1 = multi(this.ram1[29][2], (byte) (v1 >> 8)) >> 5;
                    int m2 = multi(this.ram1[29][3], (byte) (v1 >> 8)) >> 5;
                    this.ram1[28][3] = addclip20(v2, m1 >> 1, m1 & 1);
                    this.ram1[28][5] = addclip20(v2, m2 >> 1, m2 & 1);

                    eram_pack((this.ram2[28][3] & 0xffff) + this.tv_counter, this.ram1[28][1]);
                }
                {
                    // 8

                    int v1 = this.ram2[30][6] & 0xffff;
                    int m1 = multi(this.ram1[28][2], (byte) (v1 >> 8)) >> 5;

                    int v2 = addclip20(this.ram1[28][3], m1 >> 1, m1 & 1);
                    this.ram1[28][3] = v2;
                    int m2 = multi(v2, (byte) (v1 & 255)) >> 5;
                    this.ram1[28][2] = addclip20(this.ram1[28][2], m2 >> 1, m2 & 1);

                    this.ram1[28][1] = eram_unpack((this.ram2[28][9] & 0xffff) + this.tv_counter, 0);
                }
                {
                    // 9

                    int v1 = this.ram2[30][6] & 0xffff;
                    int m1 = multi(this.ram1[28][4], (byte) (v1 >> 8)) >> 5;

                    int v2 = addclip20(this.ram1[28][5], m1 >> 1, m1 & 1);
                    this.ram1[28][5] = v2;
                    int m2 = multi(v2, (byte) (v1 & 255)) >> 5;
                    this.ram1[28][4] = addclip20(this.ram1[28][4], m2 >> 1, m2 & 1);

                    this.ram1[29][4] = eram_unpack((this.ram2[29][5] & 0xffff) + this.tv_counter, 0);
                }
                {
                    // 10

                    int v1 = this.ram2[30][6] & 0xffff;
                    int v2 = this.ram1[28][1];
                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;
                    int s1 = eram_unpack((this.ram2[28][8] & 0xffff) + this.tv_counter, 0);
                    int v3 = addclip20(m1 >> 1, s1, m1 & 1);
                    this.ram1[28][1] = v3;
                    int m2 = multi(v3, (byte) (v1 & 255)) >> 5;
                    this.ram1[29][5] = addclip20(m2 >> 1, v2, m2 & 1);

                    eram_pack((this.ram2[28][4] & 0xffff) + this.tv_counter, this.ram1[28][3]);
                }
                {
                    // 11

                    int v1 = this.ram2[30][6] & 0xffff;
                    int v2 = this.ram1[29][4];
                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;
                    int s1 = eram_unpack((this.ram2[29][4] & 0xffff) + this.tv_counter, 0);
                    int v3 = addclip20(m1 >> 1, s1, m1 & 1);
                    this.ram1[29][4] = v3;
                    int m2 = multi(v3, (byte) (v1 & 255)) >> 5;
                    this.ram1[28][0] = addclip20(m2 >> 1, v2, m2 & 1);

                    eram_pack((this.ram2[28][5] & 0xffff) + this.tv_counter, this.ram1[28][2]);

                    eram_pack((this.ram2[29][0] & 0xffff) + this.tv_counter, this.ram1[28][5]);
                }
                {
                    // 12

                    this.ram1[28][5] = eram_unpack((this.ram2[28][6] & 0xffff) + this.tv_counter, 0);
                }

                {
                    // 13

                    int s1 = eram_unpack((this.ram2[28][10] & 0xffff) + this.tv_counter, 0);
                    this.ram1[28][5] = addclip20(this.ram1[28][5], s1, 0);

                    this.ram1[28][2] = eram_unpack((this.ram2[29][2] & 0xffff) + this.tv_counter, 0);
                }

                {
                    // 14

                    int s1 = eram_unpack((this.ram2[29][6] & 0xffff) + this.tv_counter, 0);
                    int t1 = addclip20(s1, this.ram1[28][2], 0); // 6

                    this.ram1[28][5] = addclip20(t1, this.ram1[28][5], 0);

                    this.ram1[28][2] = eram_unpack((this.ram2[28][7] & 0xffff) + this.tv_counter, 0);
                }

                {
                    // 15

                    int s1 = eram_unpack((this.ram2[28][11] & 0xffff) + this.tv_counter, 0);
                    this.ram1[28][2] = addclip20(this.ram1[28][2], s1, 0);

                    this.ram1[28][3] = eram_unpack((this.ram2[29][3] & 0xffff) + this.tv_counter, 0);
                }

                {
                    // 16

                    int s1 = eram_unpack((this.ram2[29][7] & 0xffff) + this.tv_counter, 0);
                    int t1 = addclip20(s1, this.ram1[28][2], 0);
                    this.ram1[28][2] = addclip20(t1, this.ram1[28][3], 0);

                    eram_pack((this.ram2[29][1] & 0xffff) + this.tv_counter, this.ram1[28][4]);

                    eram_pack((this.ram2[28][8] & 0xffff) + this.tv_counter, this.ram1[28][1]);
                }

                {
                    // 17
                    int v1 = this.ram2[30][2] & 0xffff;
                    int v2 = this.ram1[28][5];

                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;

                    rcadd[0] = m1;

                    rcadd2[0] = multi(v2, (byte) (v1 & 255)) >> 5;

                    int t1 = eram_unpack((this.ram2[29][10] & 0xffff) + this.tv_counter + 1, 0); //? 3a6e
                    eram_pack((this.ram2[28][9] & 0xffff) + this.tv_counter, this.ram1[29][5]);
                    this.ram1[29][5] = t1;
                }

                {
                    // 18
                    int v1 = this.ram2[30][3] & 0xffff;
                    int v2 = this.ram1[28][2];

                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;

                    rcadd[1] = m1;

                    rcadd2[1] = multi(v2, (byte) (v1 & 255)) >> 5;

                    this.ram1[28][1] = eram_unpack((this.ram2[29][11] & 0xffff) + this.tv_counter + 1, 0); //? 3a1e
                }
                {
                    // 19

                    int v1 = this.ram2[31][9] & 0xffff;

                    int s1 = eram_unpack((this.ram2[29][10] & 0xffff) + this.tv_counter, 0); //? 3a6d

                    eram_pack((this.ram2[29][4] & 0xffff) + this.tv_counter, this.ram1[29][4]);

                    int m1 = multi(s1, (byte) (v1 >> 8)) >> 5;
                    int m2 = multi(this.ram1[29][5], (byte) (v1 >> 8)) >> 5;

                    int t2 = addclip20(s1, (m1 >> 1) ^ 0xfffff, 1);

                    this.ram1[29][5] = addclip20(t2, m2 >> 1, m2 & 1);
                }
                {
                    // 20

                    int v1 = this.ram2[31][10] & 0xffff;

                    int s1 = eram_unpack((this.ram2[29][11] & 0xffff) + this.tv_counter, 0); //? 3a1d

                    eram_pack((this.ram2[29][5] & 0xffff) + this.tv_counter, this.ram1[28][0]);

                    int m1 = multi(s1, (byte) (v1 >> 8)) >> 5;
                    int m2 = multi(this.ram1[28][1], (byte) (v1 >> 8)) >> 5;

                    int t2 = addclip20(s1, (m1 >> 1) ^ 0xfffff, 1);

                    this.ram1[28][1] = addclip20(t2, m2 >> 1, m2 & 1);

                    eram_pack((this.ram2[29][9] & 0xffff) + this.tv_counter, this.ram1[29][1]);
                }
                {
                    // 21

                    int v1 = this.ram2[31][2] & 0xffff;
                    int v2 = this.ram1[29][5];

                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;
                    int m2 = multi(v2, (byte) (v1 & 255)) >> 5;

                    rcadd[2] = m1;
                    rcadd2[2] = m2;
                }
                {
                    // 22

                    int v1 = this.ram2[31][3] & 0xffff;
                    int v2 = this.ram1[29][5];

                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;
                    int m2 = multi(v2, (byte) (v1 & 255)) >> 5;

                    rcadd[3] = m1;
                    rcadd2[3] = m2;
                }
                {
                    // 23

                    int v1 = this.ram2[31][4] & 0xffff;
                    int v2 = this.ram1[28][1];

                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;
                    int m2 = multi(v2, (byte) (v1 & 255)) >> 5;

                    rcadd[4] = m1;
                    rcadd2[4] = m2;
                }
                {
                    // 31

                    int v1 = this.ram2[31][5] & 0xffff;
                    int v2 = this.ram1[28][1];

                    int m1 = multi(v2, (byte) (v1 >> 8)) >> 5;
                    int m2 = multi(v2, (byte) (v1 & 255)) >> 5;

                    rcadd[5] = m1;
                    rcadd2[5] = m2;

                    {
                        // address generator

                        int key = 1;
                        int okey = (this.ram2[31][7] & 0x20) != 0 ? 1 : 0;
                        int active = key != 0 && okey != 0 ? 1 : 0;
                        int kon = key != 0 && okey == 0 ? 1 : 0;

                        int b15 = (this.ram2[31][8] & 0x8000) != 0 ? 1 : 0; // 0
                        int b6 = (this.ram2[31][7] & 0x40) != 0 ? 1 : 0; // 1
                        int b7 = (this.ram2[31][7] & 0x80) != 0 ? 1 : 0; // 1
                        int old_nibble = (this.ram2[31][7] >> 12) & 15; // 1

                        int address = this.ram1[31][4]; // 0
                        int address_end = this.ram1[31][0]; // 1 or 2
                        int address_loop = this.ram1[31][2]; // 2 or 1

                        int sub_phase = (this.ram2[31][8] & 0x3fff); // 1
                        int interp_ratio = (sub_phase >> 7) & 127;
                        sub_phase += this.ram2[this.ram2[31][7] & 31][0]; // 5
                        int sub_phase_of = (sub_phase >> 14) & 7;
                        if (this.nfs != 0) {
                            this.ram2[31][8] &= ~0x3fff;
                            this.ram2[31][8] |= (short) (sub_phase & 0x3fff);
                        }

                        // address 0
                        int address_cnt = address;

                        int cmp1 = b15 != 0 ? address_loop : address_end;
                        int cmp2 = address_cnt;
                        int address_cmp = (cmp1 & 0xfffff) == (cmp2 & 0xfffff) ? 1 : 0; // 9
                        int next_b15 = b15;

                        int next_address = address_cnt; // 11

                        cmp1 = (b6 == 0 && address_cmp != 0) ? address_loop : address_cnt;
                        cmp2 = address_cnt;
                        int address_cnt2 = (kon != 0 || (b6 == 0 && address_cmp != 0)) ? cmp1 : cmp2;

                        int address_add = (address_cmp == 0 && b6 != 0 && b15 == 0) || (address_cmp == 0 && b6 == 0) ? 1 : 0;
                        int address_sub = address_cmp == 0 && b6 != 0 && b15 != 0 ? 1 : 0;
                        if (b7 != 0)
                            address_cnt2 -= address_add - address_sub;
                        else
                            address_cnt2 += address_add - address_sub;
                        address_cnt = address_cnt2 & 0xf_ffff; // 11
                        b15 = b6 != 0 && (b15 != 0 ^ address_cmp != 0) ? 1 : 0; // 11

                        cmp1 = b15 != 0 ? address_loop : address_end;
                        cmp2 = address_cnt;
                        address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff) ? 1 : 0; // 13

                        if (sub_phase_of >= 1) {
                            next_address = address_cnt; // 13
                            next_b15 = b15;
                        }

                        if (active != 0 && this.nfs != 0)
                            this.ram1[31][4] = next_address;

                        if (this.nfs != 0) {
                            this.ram2[31][8] &= (short) ~0x8000;
                            this.ram2[31][8] |= (short) (next_b15 << 15);
                        }

                        int t1 = address_loop; // 18
                        int t2 = this.ram1[31][4] - t1; // 19
                        int t3 = address_end - t2; // 20
                        int t4 = this.ram1[31][4]; // 23

                        this.ram2[29][10] = (short) t3;
                        this.ram2[29][11] = (short) t4;
                    }
                }
            }

            this.ram1[31][1] = 0;
            this.ram1[31][3] = 0;
            this.rcsum[0] = 0;
            this.rcsum[1] = 0;

            for (int slot = 0; slot < reg_slots; slot++) {
                int[] ram1 = this.ram1[slot];
                short[] ram2 = this.ram2[slot];
                boolean okey = (ram2[7] & 0x20) != 0;
                int key = (voice_active >> slot) & 1;

                boolean active = okey && key != 0;
                boolean kon = key != 0 && !okey;

                // address generator

                boolean b15 = (ram2[8] & 0x8000) != 0; // 0
                boolean b6 = (ram2[7] & 0x40) != 0; // 1
                boolean b7 = (ram2[7] & 0x80) != 0; // 1
                int hiaddr = (ram2[7] >> 8) & 15; // 1
                int old_nibble = (ram2[7] >> 12) & 15; // 1

                int address = ram1[4]; // 0
                int address_end = ram1[0]; // 1 or 2
                int address_loop = ram1[2]; // 2 or 1

                int cmp1 = b15 ? address_loop : address_end;
                int cmp2 = address;
                boolean nibble_cmp1 = (cmp1 & 0xffff0) == (cmp2 & 0xffff0); // 2
                boolean irq_flag = false;

                // fixme:
                if (kon)
                    irq_flag = ((cmp1 + address_loop) & 0x10_0000) != 0;
                else
                    irq_flag = ((address + ((-address_loop) & 0xf_ffff)) & 0x10_0000) != 0;
                irq_flag ^= b7;

                int nibble_address = (!b6 && nibble_cmp1) ? address_loop : address; // 3
                boolean address_b4 = (nibble_address & 0x10) != 0;
                int wave_address = nibble_address >> 5;
                boolean xor2 = (address_b4 ^ b7);
                boolean check1 = xor2 && active;
                boolean xor1 = (b15 ^ !nibble_cmp1);
                boolean nibble_add = b6 ? check1 && xor1 : (!nibble_cmp1 && check1);
                boolean nibble_subtract = b6 && !xor1 && active && !xor2;
                if (b7)
                    wave_address -= (nibble_add ? 1 : 0) - (nibble_subtract ? 1 : 0);
                else
                    wave_address += (nibble_add ? 1 : 0) - (nibble_subtract ? 1 : 0);
                wave_address &= 0xfffff;

                int newnibble = PCM_ReadROM((hiaddr << 20) | wave_address) & 0xff;
                boolean newnibble_sel = address_b4 ^ ((b6 || !nibble_cmp1) && okey);
                if (newnibble_sel)
                    newnibble = (newnibble >> 4) & 15;
                else
                    newnibble &= 15;

                int sub_phase = (ram2[8] & 0x3fff); // 1
                int interp_ratio = (sub_phase >> 7) & 127;
                sub_phase += this.ram2[ram2[7] & 31][0] & 0xffff; // 5
                int sub_phase_of = (sub_phase >> 14) & 7;
                if (this.nfs != 0) {
                    ram2[8] &= ~0x3fff;
                    ram2[8] |= (short) (sub_phase & 0x3fff);
                }

                // address 0
                int address_cnt = address;
                int samp0 = PCM_ReadROM((hiaddr << 20) | address_cnt) & 0xff; // 18

                cmp1 = address;
                cmp2 = address_cnt;
                boolean nibble_cmp2 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0); // 8
                cmp1 = b15 ? address_loop : address_end;
                cmp2 = address_cnt;
                boolean address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff); // 9

                int next_address = address_cnt; // 11
                boolean usenew = !nibble_cmp2;
                boolean next_b15 = b15;

                cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
                cmp2 = address_cnt;
                int address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : cmp2;

                boolean address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
                boolean address_sub = !address_cmp && b6 && b15;
                if (b7)
                    address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                else
                    address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                address_cnt = address_cnt2 & 0xf_ffff; // 11
                b15 = b6 && (b15 ^ address_cmp); // 11

                int samp1 = PCM_ReadROM((hiaddr << 20) | address_cnt) & 0xff; // 20

                cmp1 = address;
                cmp2 = address_cnt;
                boolean nibble_cmp3 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0); // 12
                cmp1 = b15 ? address_loop : address_end;
                cmp2 = address_cnt;
                address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff); // 13

                if (sub_phase_of >= 1) {
                    next_address = address_cnt; // 13
                    usenew = !nibble_cmp3;
                    next_b15 = b15;
                }

                cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
                cmp2 = address_cnt;
                address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : cmp2;

                address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
                address_sub = !address_cmp && b6 && b15;
                if (b7)
                    address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                else
                    address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                address_cnt = address_cnt2 & 0xf_ffff; // 15
                b15 = b6 && (b15 ^ address_cmp); // 15

                int samp2 = PCM_ReadROM((hiaddr << 20) | address_cnt) & 0xff; // 1

                cmp1 = address;
                cmp2 = address_cnt;
                boolean nibble_cmp4 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0); // 16
                cmp1 = b15 ? address_loop : address_end;
                cmp2 = address_cnt;
                address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff); // 17

                if (sub_phase_of >= 2) {
                    next_address = address_cnt; // 17
                    usenew = !nibble_cmp4;
                    next_b15 = b15;
                }

                cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
                cmp2 = address_cnt;
                address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : cmp2;

                address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
                address_sub = !address_cmp && b6 && b15;
                if (b7)
                    address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                else
                    address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                address_cnt = address_cnt2 & 0xf_ffff; // 19
                b15 = b6 && (b15 ^ address_cmp); // 19

                int samp3 = PCM_ReadROM((hiaddr << 20) | address_cnt) & 0xff; // 5

                cmp1 = address;
                cmp2 = address_cnt;
                boolean nibble_cmp5 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0); // 20
                cmp1 = b15 ? address_loop : address_end;
                cmp2 = address_cnt;
                address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff); // 21

                if (sub_phase_of >= 3) {
                    next_address = address_cnt; // 21
                    usenew = !nibble_cmp5;
                    next_b15 = b15;
                }

                cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
                cmp2 = address_cnt;
                address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : cmp2;

                address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
                address_sub = !address_cmp && b6 && b15;
                if (b7)
                    address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                else
                    address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
                address_cnt = address_cnt2 & 0xf_ffff; // 23
                // b15 = b6 && (b15 ^ address_cmp); // 23

                cmp1 = address;
                cmp2 = address_cnt;
                boolean nibble_cmp6 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0); // 24

                if (sub_phase_of >= 4) {
                    next_address = address_cnt; // 1
                    usenew = !nibble_cmp6;
                    // b15 is not updated?
                }

                if (active && this.nfs != 0)
                    ram1[4] = next_address;

                if (this.nfs != 0) {
                    ram2[8] &= (short) ~0x8000;
                    ram2[8] |= (short) ((next_b15 ? 1 : 0) << 15);
                }

                // dpcm

                // 18
                int reference = ram1[5];

                // 19
                int preshift = samp0 << 10;
                int select_nibble = nibble_cmp2 ? old_nibble : newnibble;
                int shift = (10 - select_nibble) & 15;

                int shifted = (preshift << 1) >> shift;

                if (sub_phase_of >= 1)
                    reference = addclip20(reference, shifted >> 1, shifted & 1);

                preshift = samp1 << 10;
                select_nibble = nibble_cmp3 ? old_nibble : newnibble;
                shift = (10 - select_nibble) & 15;

                shifted = (preshift << 1) >> shift;

                if (sub_phase_of >= 2)
                    reference = addclip20(reference, shifted >> 1, shifted & 1);

                preshift = samp2 << 10;
                select_nibble = nibble_cmp4 ? old_nibble : newnibble;
                shift = (10 - select_nibble) & 15;

                shifted = (preshift << 1) >> shift;

                if (sub_phase_of >= 3)
                    reference = addclip20(reference, shifted >> 1, shifted & 1);

                preshift = samp3 << 10;
                select_nibble = nibble_cmp5 ? old_nibble : newnibble;
                shift = (10 - select_nibble) & 15;

                shifted = (preshift << 1) >> shift;

                if (sub_phase_of >= 4)
                    reference = addclip20(reference, shifted >> 1, shifted & 1);

                // interpolation

                int test = ram1[5];

                int step0 = multi(interp_lut[0][interp_ratio] << 6, (byte) samp0) >> 8;
                select_nibble = nibble_cmp2 ? old_nibble : newnibble;
                shift = (10 - select_nibble) & 15;
                step0 = (step0 << 1) >> shift;

                test = addclip20(test, step0 >> 1, step0 & 1);

                int step1 = multi(interp_lut[1][interp_ratio] << 6, (byte) samp1) >> 8;
                select_nibble = nibble_cmp3 ? old_nibble : newnibble;
                shift = (10 - select_nibble) & 15;
                step1 = (step1 << 1) >> shift;

                test = addclip20(test, step1 >> 1, step1 & 1);

                int step2 = multi(interp_lut[2][interp_ratio] << 6, (byte) samp2) >> 8;
                select_nibble = nibble_cmp4 ? old_nibble : newnibble;
                shift = (10 - select_nibble) & 15;
                step2 = (step2 << 1) >> shift;

                int reg1 = ram1[1];
                int reg3 = ram1[3];
                int reg2_6 = (ram2[6] >> 8) & 127;

                test = addclip20(test, step2 >> 1, step2 & 1);

                int filter = ram2[11];
                int v3;

                if (mcu.mcu_mk1) {
                    int mult1 = multi(reg1, (byte) (filter >> 8)); // 8
                    int mult2 = multi(reg1, (byte) ((filter >> 1) & 127)); // 9
                    int mult3 = multi(reg1, (byte) reg2_6); // 10

                    int v2 = addclip20(reg3, mult1 >> 6, (mult1 >> 5) & 1); // 9
                    int v1 = addclip20(v2, mult2 >> 13, (mult2 >> 12) & 1); // 10
                    int subvar = addclip20(v1, (mult3 >> 6), (mult3 >> 5) & 1); // 11

                    ram1[3] = v1;

                    v3 = addclip20(test, subvar ^ 0xfffff, 1); // 12

                    int mult4 = multi(v3, (byte) (filter >> 8));
                    int mult5 = multi(v3, (byte) ((filter >> 1) & 127));
                    int v4 = addclip20(reg1, mult4 >> 6, (mult4 >> 5) & 1); // 14
                    int v5 = addclip20(v4, mult5 >> 13, (mult5 >> 12) & 1); // 15

                    ram1[1] = v5;
                } else {
                    // hack: use 32-bit math to avoid overflow
                    int mult1 = reg1 * (byte) (filter >> 8); // 8
                    int mult2 = reg1 * (byte) ((filter >> 1) & 127); // 9
                    int mult3 = reg1 * (byte) reg2_6; // 10

                    int v2 = reg3 + (mult1 >> 6) + ((mult1 >> 5) & 1); // 9
                    int v1 = v2 + (mult2 >> 13) + ((mult2 >> 12) & 1); // 10
                    int subvar = v1 + (mult3 >> 6) + ((mult3 >> 5) & 1); // 11

                    ram1[3] = v1;

                    int tests = test;
                    tests <<= 12;
                    tests >>= 12;

                    v3 = tests - subvar; // 12

                    int mult4 = v3 * (byte) (filter >> 8);
                    int mult5 = v3 * (byte) ((filter >> 1) & 127);
                    int v4 = reg1 + (mult4 >> 6) + ((mult4 >> 5) & 1); // 14
                    int v5 = v4 + (mult5 >> 13) + ((mult5 >> 12) & 1); // 15

                    ram1[1] = v5;
                }

                ram1[5] = reference;

                if (active && (ram2[6] & 1) != 0 && (ram2[8] & 0x4000) == 0 && this.irq_assert == 0 && irq_flag) {
                    //printf("irq voice %i\n", slot);
                    if (this.nfs != 0)
                        ram2[8] |= 0x4000;
                    this.irq_assert = 1;
                    this.irq_channel = slot;
                    if (mcu.mcu_jv880)
                        mcu.MCU_GA_SetGAInt(5, true);
                    else
                        mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ0.ordinal(), true);
                }

                int[] volmul1 = new int[1];
                int[] volmul2 = new int[1];

                calc_tv(0, ram2[3], ram2, 9, active, volmul1);
                calc_tv(1, ram2[4], ram2, 10, active, volmul2);
                calc_tv(2, ram2[5], ram2, 11, active, null);

                // if (volmul1 && volmul2)
                //     volmul1 += 0;

                int sample = (ram2[6] & 2) == 0 ? ram1[3] : v3;
                //sample = test;

                int multiv1 = multi(sample, (byte) (volmul1[0] >> 8));
                int multiv2 = multi(sample, (byte) ((volmul1[0] >> 1) & 127));

                int sample2 = addclip20(multiv1 >> 6, multiv2 >> 13, ((multiv2 >> 12) | (multiv1 >> 5)) & 1);

                int multiv3 = multi(sample2, (byte) (volmul2[0] >> 8));
                int multiv4 = multi(sample2, (byte) ((volmul2[0] >> 1) & 127));

                int sample3 = addclip20(multiv3 >> 6, multiv4 >> 13, ((multiv4 >> 12) | (multiv3 >> 5)) & 1);

                int pan = active ? ram2[1] : 0;
                int rc = active ? ram2[2] : 0;

                int sampl = multi(sample3, (byte) ((pan >> 8) & 255));
                int sampr = multi(sample3, (byte) ((pan >> 0) & 255));

                int rc0 = multi(sample3, (byte) ((rc >> 8) & 255)) >> 5; // reverb
                int rc1 = multi(sample3, (byte) ((rc >> 0) & 255)) >> 5; // chorus

                // mix reverb/chorus?
                int slot2 = (slot == reg_slots - 1) ? 31 : slot + 1;
                switch (slot2) {
                    // 17, 18 - reverb

                    case 17:
                        this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[0] >> 1, rcadd[0] & 1);
                        break;
                    case 18:
                        this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[1] >> 1, rcadd[1] & 1);
                        break;
                    case 21:
                        this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[2] >> 1, rcadd[2] & 1);
                        break;
                    case 22:
                        this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[3] >> 1, rcadd[3] & 1);
                        break;
                    case 23:
                        this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[4] >> 1, rcadd[4] & 1);
                        break;
                    case 31:
                        this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[5] >> 1, rcadd[5] & 1);
                        break;
                }

                int suml = addclip20(this.ram1[31][1], sampl >> 6, (sampl >> 5) & 1);
                int sumr = addclip20(this.ram1[31][3], sampr >> 6, (sampr >> 5) & 1);

                switch (slot2) {
                    case 17:
                        this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[0] >> 1, rcadd2[0] & 1);
                        break;
                    case 18:
                        this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[1] >> 1, rcadd2[1] & 1);
                        break;
                    case 21:
                        this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[2] >> 1, rcadd2[2] & 1);
                        break;
                    case 22:
                        this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[3] >> 1, rcadd2[3] & 1);
                        break;
                    case 23:
                        this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[4] >> 1, rcadd2[4] & 1);
                        break;
                    case 31:
                        this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[5] >> 1, rcadd2[5] & 1);
                        break;
                }

                this.rcsum[0] = addclip20(this.rcsum[0], rc0 >> 1, rc0 & 1);
                this.rcsum[1] = addclip20(this.rcsum[1], rc1 >> 1, rc1 & 1);

                if (slot != reg_slots - 1) {
                    this.ram1[31][1] = suml;
                    this.ram1[31][3] = sumr;
                } else {
                    this.accum_l = suml;
                    this.accum_r = sumr;
                }

                if (key != 0 && this.nfs != 0) {
                    ram2[7] &= (short) ~0xf020;
                    ram2[7] |= (short) (((usenew || kon) ? newnibble : old_nibble) << 12);

                    // update key
                    ram2[7] |= (short) (key << 5);
                }

                if (active) {
                    if (this.nfs != 0) {
                        ram1[1] = 0;
                        ram1[3] = 0;
                        ram1[5] = 0;
                    }

                    ram2[8] = 0;
                    ram2[9] = 0;
                    ram2[10] = 0;
                }
            }

            if (this.nfs != 0) {
                this.ram2[31][7] |= 0x20;
            }

            this.nfs = 1;

            int cycles_ = (reg_slots + 1) * 25;

            this.cycles += mcu.mcu_jv880 ? (cycles_ * 25) / 29 : cycles_;
        }
    }
}
