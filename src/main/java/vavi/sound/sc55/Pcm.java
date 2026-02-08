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

import java.util.Arrays;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

import static vavi.sound.sc55.McuInterrupt.Interrupt.INTERRUPT_SOURCE_IRQ0;


class Pcm {

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

        // IRQ queue: bitmask of voices that want to fire IRQs but were blocked
        int irq_pending_mask = 0;

        int nfs;

        int tv_counter;

        long cycles;

        short[] eram = new short[0x4000];

        int accum_l;
        int accum_r;
        int[] rcsum = new int[2];

        // Diagnostic counters
        long diagActiveVoices = 0;    // Sum of active voices per sample

        // Voice mask tracking (for diagnostic output)
        int voiceMaskSetCount = 0;
        int voiceMaskClearCount = 0;
        long diagEarlyExits = 0;      // Times early-exit was taken
        long diagFullProcess = 0;     // Times full voice processing ran
        long diagSamples = 0;         // Samples per diagnostic period (reset)

        // Envelope state diagnostics
        long diagEnvNotFrozen = 0;    // Times envelope was NOT frozen when checked
        long diagEnvFrozen = 0;       // Times envelope WAS frozen when checked
        long diagEnvSilent = 0;       // Times envelope level was very low (silent)
        // Track envelope state distribution when NOT frozen
        long diagEnvSpeedZeroLowTarget = 0;   // speed=0, target<=128
        long diagEnvSpeedNonZero = 0;          // speed>0

        // IRQ blocking diagnostic - counts when an IRQ would fire but was blocked
        long diagIrqBlocked = 0;  // IRQs blocked because irq_assert was already 1

        // ROM read skip optimization tracking
        long diagRomReadsSkipped = 0;  // Number of voices where ROM reads were skipped
        long diagRomReadsPerformed = 0;  // Number of voices where ROM reads were done

        // === SoA ARRAYS FOR TWO-PHASE VOICE PROCESSING ===
        // Phase 1 stores Stage 3 outputs here, Phase 2 uses them for vectorized processing

        // Enable two-phase SoA processing (4x faster than original loop)
        // Set -Dsc55.useSoA=false to disable
        private static final boolean USE_SOA_PROCESSING = !Boolean.getBoolean("sc55.noSoA");

        // Voice timing counter - NEVER reset, used for voice allocation/release timing
        long totalSamples = 0;

        /** Returns the total number of samples processed */
        public long getTotalSamples() {
            return totalSamples;
        }

        // Voice age tracking: timestamp of last key-on for each voice
        // When a voice has been active too long without new key-on, force deallocate
        private final long[] voiceLastKeyOn = new long[32];

        // Keep frozen counter as backup mechanism for truly stuck voices
        // With the IRQ queue fix, this should rarely be needed
        private final long[] voiceFrozenCounter = new long[32];
        // ~50ms threshold (3300 samples at 66207 Hz) - forces faster voice release
        // Per plan file: this value worked to keep voices balanced
        // TODO opus4.5 set 3300, it's not enough for normal midi
        //      but 10 causes muddy note on "passport.mid"
        private static final long FROZEN_VOICE_THRESHOLD = 3300 * 10;

        // Cached fast-path flag for ROM reads (set once at init)
        private boolean useFastRomRead = false;

        // Pre-allocated scratch arrays to avoid GC pressure in hot PCM_Update loop
        private final int[] scratch_tt = new int[2];
        private final int[] scratch_u = new int[1];
        private final int[] scratch_rcadd = new int[6];
        private final int[] scratch_rcadd2 = new int[6];
        private final int[] scratch_volmul1 = new int[1];
        private final int[] scratch_volmul2 = new int[1];

        // Vector API for SIMD processing - 8 lanes of 32-bit integers
        private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_256;
        private static final int VECTOR_LENGTH = SPECIES.length();  // 8

        // Decay constant vector (all lanes = 100)
        private static final IntVector ZERO_VECTOR = IntVector.zero(SPECIES);

        // Pre-computed addlow values for calc_tv optimization
        // These depend only on tv_counter, which is constant for all voices in a sample
        // Index: 0=type4, 1=type0, 2=type1, 3=type2, 4=type3
        private final int[] precompAddlow = new int[5];
        // Pre-computed write conditions (bitmask: bit N = write condition for type N)
        private int precompWriteMask = 0;

        // === SoA (Structure-of-Arrays) for vectorized processing ===
        // These flat arrays enable SIMD operations across all 32 voices

        // Envelope levels (ram2[slot][9], ram2[slot][10], ram2[slot][11])
        private final int[] soaEnvLevel0 = new int[32];  // Main envelope

        // Voice active flags (1 = active, 0 = inactive)
        private final int[] soaVoiceActive = new int[32];

        // Volume output from calc_tv (for vectorized processing)
        private final int[] soaVolmul0 = new int[32];

        // ---- TWO-PHASE SoA PROCESSING ARRAYS ----
        // Phase 1 outputs (address generator + ROM reads)
        private final int[] p1Samp0 = new int[32];
        private final int[] p1Samp1 = new int[32];
        private final int[] p1Samp2 = new int[32];
        private final int[] p1Samp3 = new int[32];
        private final int[] p1NewNibble = new int[32];
        private final int[] p1OldNibble = new int[32];
        private final int[] p1InterpRatio = new int[32];
        private final int[] p1SubPhaseOf = new int[32];
        private final int[] p1NextAddress = new int[32];
        private final boolean[] p1NextB15 = new boolean[32];
        private final boolean[] p1UseNew = new boolean[32];
        private final boolean[] p1NibbleCmp2 = new boolean[32];
        private final boolean[] p1NibbleCmp3 = new boolean[32];
        private final boolean[] p1NibbleCmp4 = new boolean[32];
        private final boolean[] p1NibbleCmp5 = new boolean[32];
        private final boolean[] p1IrqFlag = new boolean[32];
        private final boolean[] p1Active = new boolean[32];
        private final boolean[] p1Kon = new boolean[32];
        private final int[] p1Key = new int[32];
        // Phase 1 state copies
        private final int[] p1B7 = new int[32];  // 1=b7, 0=not b7
        // Indices of voices to process in Phase 2
        private final int[] p2VoiceList = new int[32];
        private int p2VoiceCount = 0;
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

    // Update fast path flag - call after MCU type and config are known
    void updateFastRomRead() {
        useFastRomRead = !mcu.mcu_mk1 && !mcu.mcu_jv880 && (this.config_reg_3d & 0x20) == 0;
    }

    // Fastest possible ROM read - use only when useFastRomRead is true
    // This is the hottest code path in the entire emulator
    private byte PCM_ReadROM_inline(int address) {
        int bank = (address >>> 19) & 7;
        return switch (bank) {
            case 0 -> waverom1[address & 0x1f_ffff];
            case 1 -> waverom2[address & 0xf_ffff];
            case 2 -> waverom3[address & 0xf_ffff];
            default -> 0;
        };
    }

    // Fast path for SC-55mk2 (most common case) - avoids MCU type checks and switch
    private byte PCM_ReadROM_mk2(int address) {
        int bank = (address >>> 19) & 7;
        return switch (bank) {
            case 0 -> waverom1[address & 0x1f_ffff];
            case 1 -> waverom2[address & 0xf_ffff];
            case 2 -> waverom3[address & 0xf_ffff];
            default -> 0;
        };
    }

    byte PCM_ReadROM(int address) {
        // Use cached fast path flag
        if (useFastRomRead) {
            return PCM_ReadROM_inline(address);
        }

        // Original path for other MCU types
        int bank;
        if ((this.config_reg_3d & 0x20) != 0)
            bank = (address >>> 21) & 7;
        else
            bank = (address >>> 19) & 7;
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
            int oldMask = this.voice_mask_pending;
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
            // Track voice mask changes (for diagnostic output)
            int newMask = this.voice_mask_pending;
            int setBits = newMask & ~oldMask;  // Bits that were 0 and are now 1
            int clearBits = oldMask & ~newMask; // Bits that were 1 and are now 0
            if (setBits != 0) {
                voiceMaskSetCount += Integer.bitCount(setBits);
            }
            if (clearBits != 0) voiceMaskClearCount += Integer.bitCount(clearBits);
            this.voice_mask_updating = 1;
        } else if (address >= 0x20 && address < 0x24) { // wave rom
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
            updateFastRomRead();  // Config changed, update fast path flag
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
                int ix = (address >>> 1) & 7;
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

                // Process queued IRQs: fire the next pending IRQ immediately
                if (this.irq_pending_mask != 0) {
                    // Find the lowest-numbered pending slot
                    int nextSlot = Integer.numberOfTrailingZeros(this.irq_pending_mask);
                    if (nextSlot < 28) {
                        // Clear this slot from pending mask
                        this.irq_pending_mask &= ~(1 << nextSlot);
                        // Mark this slot as having fired an IRQ
                        if (this.nfs != 0)
                            this.ram2[nextSlot][8] |= 0x4000;
                        // Fire the IRQ
                        this.irq_assert = 1;
                        this.irq_channel = nextSlot;
                        if (mcu.mcu_jv880)
                            mcu.MCU_GA_SetGAInt(5, true);
                        else
                            mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ0.ordinal(), true);
                    }
                }
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
                int ix = (address >>> 1) & 7;
                if ((address & 32) != 0)
                    ix |= 8;

                this.read_latch = this.ram2[this.select_channel][ix] & 0xffff;
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
        for (int i = 0; i < 32; i++) {
            Arrays.fill(this.ram1[i], 0);
            Arrays.fill(this.ram2[i], (short) 0);
        }
        Arrays.fill(this.eram, (short) 0);
        Arrays.fill(this.rcsum, 0);

        // Clear Primitives
        this.select_channel = 0;
        this.voice_mask = 0;
        this.voice_mask_pending = 0;
        this.voice_mask_updating = 0;
        this.write_latch = 0;
        this.wave_read_address = 0;
        this.wave_byte_latch = 0;
        this.read_latch = 0;
        this.config_reg_3c = 0;
        this.config_reg_3d = 0;
        this.irq_channel = 0;
        this.irq_assert = 0;
        this.irq_pending_mask = 0;
        this.nfs = 0;
        this.tv_counter = 0;
        this.cycles = 0;
        this.accum_l = 0;
        this.accum_r = 0;
        updateFastRomRead();  // Update cached fast path flag
    }

    private final int addclip20(int add1, int add2, int cin) {
        int sum = (add1 + add2 + cin) & 0xf_ffff;
        if ((add1 & 0x8_0000) != 0 && (add2 & 0x8_0000) != 0 && (sum & 0x8_0000) == 0)
            sum = 0x8_0000;
        else if ((add1 & 0x8_0000) == 0 && (add2 & 0x8_0000) == 0 && (sum & 0x8_0000) != 0)
            sum = 0x7_ffff;
        return sum;
    }

    private final int multi(int val1, byte val2) { // signed
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

    // Pre-shifted interpolation LUT (shifted left by 6) to eliminate shift in hot loop
    private static final int[][] interp_lut_shifted;

    static {
        interp_lut_shifted = new int[3][128];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 128; j++) {
                interp_lut_shifted[i][j] = interp_lut[i][j] << 6;
            }
        }
    }

    private void calc_tv(int e, int adjust, short[] levelcur, int lp, boolean active, int[] volmul) {
        // int adjust = ram2[3+e];
        // int levelcur = ram2[9+e] & 0x7fff;
        levelcur[lp] &= 0x7fff;
        int speed = adjust & 0xff;
        int target = (adjust >>> 8) & 0xff;


        int w1 = (speed & 0xf0) == 0 ? 1 : 0;
        int w2 = w1 != 0 || (speed & 0x10) != 0 ? 1 : 0;
        int w3 = this.nfs != 0 &&
                ((speed & 0x80) == 0 || ((speed & 0x40) == 0 && (w2 == 0 || (speed & 0x20) == 0))) ? 1 : 0;

        int type = w2 | (w3 << 3);
        if ((speed & 0x20) != 0)
            type |= 2;
        if ((speed & 0x80) == 0 || (speed & 0x40) == 0)
            type |= 4;

        // Use pre-computed addlow and write values (optimization)
        // precompAddlow[0]=type4, [1]=type0, [2]=type1, [3]=type2, [4]=type3
        // precompWriteMask: bit0=type4, bit1=type0, bit2=type1, bit3=type2, bit4=type3
        boolean write = !active;
        int addlow;
        if ((type & 4) != 0) {
            addlow = precompAddlow[0];
            write = true;
        } else {
            int typeIdx = (type & 3) + 1;  // 1,2,3,4 for type0,1,2,3
            addlow = precompAddlow[typeIdx];
            write |= (precompWriteMask & (1 << typeIdx)) != 0;
        }

        if ((type & 8) == 0) {
            int shift = speed & 15;
            shift = (10 - shift) & 15;

            int sum1 = (target << 11); // 5
            if (e != 2 || active)
                sum1 -= ((levelcur[lp] & 0xffff) << 4); // 6
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
                sum1 -= ((levelcur[lp] & 0xffff) << 4); // 6
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

    private int eram_unpack(int addr, int type /* = 0 */) {
        addr &= 0x3fff;
        int data = this.eram[addr] & 0xffff;
        int val = data & 0x3fff;
        int sh = (data >> 14) & 3;

        val <<= 18;
        return val >> (18 - sh * 2 + type);
    }

    private void eram_pack(int addr, int val) {
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

    /**
     * Two-phase voice processing using Structure-of-Arrays (SoA) layout.
     * Phase 1: Address generator + ROM reads for ALL voices (stores in SoA arrays)
     * Phase 2: DPCM + interpolation + filter + envelope + mix (uses SoA data)
     *
     * Benefits:
     * - Better cache locality (SoA arrays are contiguous)
     * - Reduced branch mispredictions (conditions checked once per batch)
     * - Enables future SIMD vectorization in Phase 2
     */
    private void processVoicesTwoPhase(int reg_slots, int voice_active, int frozenProcessedMask,
                                        int[] rcadd, int[] rcadd2) {
        // === PHASE 1: Address Generator + ROM Reads ===
        // Process ALL voices and store results in SoA arrays
        p2VoiceCount = 0;

        for (int slot = 0; slot < reg_slots; slot++) {
            int[] ram1_ = this.ram1[slot];
            short[] ram2_ = this.ram2[slot];
            boolean okey = (ram2_[7] & 0x20) != 0;
            int key = (voice_active >> slot) & 1;

            boolean active = okey && key != 0;
            boolean kon = key != 0 && !okey;

            // Voice deallocation check
            if (kon) {
                voiceLastKeyOn[slot] = totalSamples;
                voiceFrozenCounter[slot] = 0;
            }

            if (key != 0 && !kon) {
                long voiceAge = totalSamples - voiceLastKeyOn[slot];
                int envCtrl = ram2_[3] & 0xffff;
                int envSpeed = envCtrl & 0xff;
                int envTarget = (envCtrl >> 8) & 0xff;
                int envLevel = ram2_[9] & 0x7fff;
                boolean envFrozen = (envSpeed == 0 && envTarget > 128);
                boolean envSilent = (envLevel < 3000);

                boolean shouldDeallocate = false;
                boolean alreadyProcessed = (frozenProcessedMask & (1 << slot)) != 0;

                if (envFrozen && voiceAge > 1000 && !alreadyProcessed) {
                    if (envLevel > 20) ram2_[9] = (short)(envLevel - 20);
                    else ram2_[9] = 0;
                    voiceFrozenCounter[slot]++;
                    if (voiceFrozenCounter[slot] > FROZEN_VOICE_THRESHOLD) shouldDeallocate = true;
                } else if (alreadyProcessed) {
                    voiceFrozenCounter[slot]++;
                    if (voiceFrozenCounter[slot] > FROZEN_VOICE_THRESHOLD) shouldDeallocate = true;
                } else if (envFrozen) {
                    voiceFrozenCounter[slot]++;
                } else {
                    voiceFrozenCounter[slot] = 0;
                }

                if (envSilent && voiceAge > 1000) shouldDeallocate = true;

                if (shouldDeallocate) {
                    int slotBit = 1 << slot;
                    this.voice_mask_pending &= ~slotBit;
                    this.voice_mask &= ~slotBit;
                    voiceMaskClearCount++;
                    voiceFrozenCounter[slot] = 0;
                    voiceLastKeyOn[slot] = 0;
                    key = 0;
                    active = false;
                    kon = false;
                }
            }

            // Inactive voice - just clear state, don't do rcadd (will be handled in Phase 2)
            if (key == 0) {
                // Mark as inactive for Phase 2
                p1Key[slot] = 0;
                if (this.nfs != 0) { ram1_[1] = 0; ram1_[3] = 0; ram1_[5] = 0; }
                ram2_[8] = 0; ram2_[9] = 0; ram2_[10] = 0; ram2_[11] = 0;
                continue;
            }

            // Store active voice for Phase 2
            p2VoiceList[p2VoiceCount] = slot;
            p1Active[slot] = active;
            p1Kon[slot] = kon;
            p1Key[slot] = key;

            // === Address Generator ===
            boolean b15 = (ram2_[8] & 0x8000) != 0;
            boolean b6 = (ram2_[7] & 0x40) != 0;
            boolean b7 = (ram2_[7] & 0x80) != 0;
            int hiaddr = ((ram2_[7] & 0xffff) >> 8) & 15;
            int old_nibble = ((ram2_[7] & 0xffff) >> 12) & 15;
            int waveAddrBase = hiaddr << 20;

            int address = ram1_[4];
            int address_end = ram1_[0];
            int address_loop = ram1_[2];

            int cmp1 = b15 ? address_loop : address_end;
            int cmp2 = address;
            boolean nibble_cmp1 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0);
            boolean irq_flag = kon ? ((cmp1 + address_loop) & 0x10_0000) != 0
                                   : ((address + ((-address_loop) & 0xf_ffff)) & 0x10_0000) != 0;
            irq_flag ^= b7;

            int nibble_address = (!b6 && nibble_cmp1) ? address_loop : address;
            boolean address_b4 = (nibble_address & 0x10) != 0;
            int wave_address = nibble_address >> 5;
            boolean xor2 = address_b4 ^ b7;
            boolean check1 = xor2 && active;
            boolean xor1 = b15 ^ !nibble_cmp1;
            boolean nibble_add = b6 ? check1 && xor1 : (!nibble_cmp1 && check1);
            boolean nibble_subtract = b6 && !xor1 && active && !xor2;
            if (b7) wave_address -= (nibble_add ? 1 : 0) - (nibble_subtract ? 1 : 0);
            else wave_address += (nibble_add ? 1 : 0) - (nibble_subtract ? 1 : 0);
            wave_address &= 0xf_ffff;

            // ROM read for new nibble
            int newnibble;
            if (useFastRomRead) {
                int addr = waveAddrBase | wave_address;
                int bank = (addr >>> 19) & 7;
                newnibble = (bank == 0 ? waverom1[addr & 0x1f_ffff] & 0xff :
                             bank == 1 ? waverom2[addr & 0xf_ffff] & 0xff :
                             bank == 2 ? waverom3[addr & 0xf_ffff] & 0xff : 0);
            } else {
                newnibble = PCM_ReadROM(waveAddrBase | wave_address) & 0xff;
            }
            boolean newnibble_sel = address_b4 ^ ((b6 || !nibble_cmp1) && okey);
            if (newnibble_sel) newnibble = (newnibble >> 4) & 15;
            else newnibble &= 15;

            int sub_phase = ram2_[8] & 0x3fff;
            int interp_ratio = (sub_phase >> 7) & 127;
            sub_phase += this.ram2[ram2_[7] & 31][0] & 0xffff;
            int sub_phase_of = (sub_phase >> 14) & 7;
            if (this.nfs != 0) {
                ram2_[8] &= ~0x3fff;
                ram2_[8] |= (short)(sub_phase & 0x3fff);
            }

            // Read 4 samples for interpolation
            int address_cnt = address;
            int samp0 = readRomFast(waveAddrBase | address_cnt);
            cmp1 = address; cmp2 = address_cnt;
            boolean nibble_cmp2 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0);
            cmp1 = b15 ? address_loop : address_end; cmp2 = address_cnt;
            boolean address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff);

            int next_address = address_cnt;
            boolean usenew = !nibble_cmp2;
            boolean next_b15 = b15;

            // Advance to sample 1
            cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
            int address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : address_cnt;
            boolean address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
            boolean address_sub = !address_cmp && b6 && b15;
            if (b7) address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            else address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            address_cnt = address_cnt2 & 0xf_ffff;
            b15 = b6 && (b15 ^ address_cmp);

            int samp1 = readRomFast(waveAddrBase | address_cnt);
            cmp1 = address; cmp2 = address_cnt;
            boolean nibble_cmp3 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0);
            cmp1 = b15 ? address_loop : address_end; cmp2 = address_cnt;
            address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff);
            if (sub_phase_of >= 1) { next_address = address_cnt; usenew = !nibble_cmp3; next_b15 = b15; }

            // Advance to sample 2
            cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
            address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : address_cnt;
            address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
            address_sub = !address_cmp && b6 && b15;
            if (b7) address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            else address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            address_cnt = address_cnt2 & 0xf_ffff;
            b15 = b6 && (b15 ^ address_cmp);

            int samp2 = readRomFast(waveAddrBase | address_cnt);
            cmp1 = address; cmp2 = address_cnt;
            boolean nibble_cmp4 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0);
            cmp1 = b15 ? address_loop : address_end; cmp2 = address_cnt;
            address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff);
            if (sub_phase_of >= 2) { next_address = address_cnt; usenew = !nibble_cmp4; next_b15 = b15; }

            // Advance to sample 3
            cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
            address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : address_cnt;
            address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
            address_sub = !address_cmp && b6 && b15;
            if (b7) address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            else address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            address_cnt = address_cnt2 & 0xf_ffff;
            b15 = b6 && (b15 ^ address_cmp);

            int samp3 = readRomFast(waveAddrBase | address_cnt);
            cmp1 = address; cmp2 = address_cnt;
            boolean nibble_cmp5 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0);
            cmp1 = b15 ? address_loop : address_end; cmp2 = address_cnt;
            address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff);
            if (sub_phase_of >= 3) { next_address = address_cnt; usenew = !nibble_cmp5; next_b15 = b15; }

            // Check for 4th advance
            cmp1 = (!b6 && address_cmp) ? address_loop : address_cnt;
            address_cnt2 = (kon || (!b6 && address_cmp)) ? cmp1 : address_cnt;
            address_add = (!address_cmp && b6 && !b15) || (!address_cmp && !b6);
            address_sub = !address_cmp && b6 && b15;
            if (b7) address_cnt2 -= (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            else address_cnt2 += (address_add ? 1 : 0) - (address_sub ? 1 : 0);
            address_cnt = address_cnt2 & 0xf_ffff;
            cmp1 = address; cmp2 = address_cnt;
            boolean nibble_cmp6 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0);
            if (sub_phase_of >= 4) { next_address = address_cnt; usenew = !nibble_cmp6; }

            if (active && this.nfs != 0) ram1_[4] = next_address;
            if (this.nfs != 0) {
                ram2_[8] &= (short)~0x8000;
                ram2_[8] |= (short)((next_b15 ? 1 : 0) << 15);
            }

            // Store Phase 1 outputs in SoA arrays
            p1Samp0[slot] = samp0;
            p1Samp1[slot] = samp1;
            p1Samp2[slot] = samp2;
            p1Samp3[slot] = samp3;
            p1NewNibble[slot] = newnibble;
            p1OldNibble[slot] = old_nibble;
            p1InterpRatio[slot] = interp_ratio;
            p1SubPhaseOf[slot] = sub_phase_of;
            p1NextAddress[slot] = next_address;
            p1NextB15[slot] = next_b15;
            p1UseNew[slot] = usenew;
            p1NibbleCmp2[slot] = nibble_cmp2;
            p1NibbleCmp3[slot] = nibble_cmp3;
            p1NibbleCmp4[slot] = nibble_cmp4;
            p1NibbleCmp5[slot] = nibble_cmp5;
            p1IrqFlag[slot] = irq_flag;
            p1B7[slot] = b7 ? 1 : 0;

            p2VoiceCount++;
        }

        // === PHASE 2: Process ALL slots in order for correct rcadd timing ===
        for (int slot = 0; slot < reg_slots; slot++) {
            int[] ram1_ = this.ram1[slot];
            short[] ram2_ = this.ram2[slot];
            int key = p1Key[slot];
            int slot2 = (slot == reg_slots - 1) ? 31 : slot + 1;

            // Add rcadd at correct slot position (for ALL slots, active or not)
            switch (slot2) {
                case 17: this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[0] >> 1, rcadd[0] & 1); break;
                case 18: this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[1] >> 1, rcadd[1] & 1); break;
                case 21: this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[2] >> 1, rcadd[2] & 1); break;
                case 22: this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[3] >> 1, rcadd[3] & 1); break;
                case 23: this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[4] >> 1, rcadd[4] & 1); break;
                case 31: this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[5] >> 1, rcadd[5] & 1); break;
            }

            // Inactive voice - just do rcadd2 and update accumulators
            if (key == 0) {
                switch (slot2) {
                    case 17: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[0] >> 1, rcadd2[0] & 1); break;
                    case 18: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[1] >> 1, rcadd2[1] & 1); break;
                    case 21: this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[2] >> 1, rcadd2[2] & 1); break;
                    case 22: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[3] >> 1, rcadd2[3] & 1); break;
                    case 23: this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[4] >> 1, rcadd2[4] & 1); break;
                    case 31: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[5] >> 1, rcadd2[5] & 1); break;
                }
                if (slot == reg_slots - 1) {
                    this.accum_l = this.ram1[31][1];
                    this.accum_r = this.ram1[31][3];
                }
                continue;
            }

            // Active voice - full processing
            boolean active = p1Active[slot];
            boolean kon = p1Kon[slot];

            int samp0 = p1Samp0[slot];
            int samp1 = p1Samp1[slot];
            int samp2 = p1Samp2[slot];
            int samp3 = p1Samp3[slot];
            int newnibble = p1NewNibble[slot];
            int old_nibble = p1OldNibble[slot];
            int interp_ratio = p1InterpRatio[slot];
            int sub_phase_of = p1SubPhaseOf[slot];
            boolean usenew = p1UseNew[slot];
            boolean nibble_cmp2 = p1NibbleCmp2[slot];
            boolean nibble_cmp3 = p1NibbleCmp3[slot];
            boolean nibble_cmp4 = p1NibbleCmp4[slot];
            boolean nibble_cmp5 = p1NibbleCmp5[slot];
            boolean irq_flag = p1IrqFlag[slot];

            // DPCM decoding
            int reference = ram1_[5];
            int preshift = samp0 << 10;
            int select_nibble = nibble_cmp2 ? old_nibble : newnibble;
            int shift = (10 - select_nibble) & 15;
            int shifted = (preshift << 1) >> shift;
            if (sub_phase_of >= 1) reference = addclip20(reference, shifted >> 1, shifted & 1);

            preshift = samp1 << 10;
            select_nibble = nibble_cmp3 ? old_nibble : newnibble;
            shift = (10 - select_nibble) & 15;
            shifted = (preshift << 1) >> shift;
            if (sub_phase_of >= 2) reference = addclip20(reference, shifted >> 1, shifted & 1);

            preshift = samp2 << 10;
            select_nibble = nibble_cmp4 ? old_nibble : newnibble;
            shift = (10 - select_nibble) & 15;
            shifted = (preshift << 1) >> shift;
            if (sub_phase_of >= 3) reference = addclip20(reference, shifted >> 1, shifted & 1);

            preshift = samp3 << 10;
            select_nibble = nibble_cmp5 ? old_nibble : newnibble;
            shift = (10 - select_nibble) & 15;
            shifted = (preshift << 1) >> shift;
            if (sub_phase_of >= 4) reference = addclip20(reference, shifted >> 1, shifted & 1);

            // Interpolation
            int test = ram1_[5];
            int step0 = multi(interp_lut_shifted[0][interp_ratio], (byte) samp0) >> 8;
            select_nibble = nibble_cmp2 ? old_nibble : newnibble;
            shift = (10 - select_nibble) & 15;
            step0 = (step0 << 1) >> shift;
            test = addclip20(test, step0 >> 1, step0 & 1);

            int step1 = multi(interp_lut_shifted[1][interp_ratio], (byte) samp1) >> 8;
            select_nibble = nibble_cmp3 ? old_nibble : newnibble;
            shift = (10 - select_nibble) & 15;
            step1 = (step1 << 1) >> shift;
            test = addclip20(test, step1 >> 1, step1 & 1);

            int step2 = multi(interp_lut_shifted[2][interp_ratio], (byte) samp2) >> 8;
            select_nibble = nibble_cmp4 ? old_nibble : newnibble;
            shift = (10 - select_nibble) & 15;
            step2 = (step2 << 1) >> shift;

            int reg1 = ram1_[1];
            int reg3 = ram1_[3];
            int reg2_6 = ((ram2_[6] & 0xffff) >> 8) & 127;
            test = addclip20(test, step2 >> 1, step2 & 1);

            // Filter
            int filter = ram2_[11] & 0xffff;
            int v3;
            if (mcu.mcu_mk1) {
                int mult1 = multi(reg1, (byte)(filter >> 8));
                int mult2 = multi(reg1, (byte)((filter >> 1) & 127));
                int mult3 = multi(reg1, (byte)reg2_6);
                int v2 = addclip20(reg3, mult1 >> 6, (mult1 >> 5) & 1);
                int v1 = addclip20(v2, mult2 >> 13, (mult2 >> 12) & 1);
                int subvar = addclip20(v1, mult3 >> 6, (mult3 >> 5) & 1);
                ram1_[3] = v1;
                v3 = addclip20(test, subvar ^ 0xfffff, 1);
                int mult4 = multi(v3, (byte)(filter >> 8));
                int mult5 = multi(v3, (byte)((filter >> 1) & 127));
                int v4 = addclip20(reg1, mult4 >> 6, (mult4 >> 5) & 1);
                int v5 = addclip20(v4, mult5 >> 13, (mult5 >> 12) & 1);
                ram1_[1] = v5;
            } else {
                int mult1 = reg1 * (byte)(filter >> 8);
                int mult2 = reg1 * (byte)((filter >> 1) & 127);
                int mult3 = reg1 * (byte)reg2_6;
                int v2 = reg3 + (mult1 >> 6) + ((mult1 >> 5) & 1);
                int v1 = v2 + (mult2 >> 13) + ((mult2 >> 12) & 1);
                int subvar = v1 + (mult3 >> 6) + ((mult3 >> 5) & 1);
                ram1_[3] = v1;
                int tests = test;
                tests <<= 12;
                tests >>= 12;
                v3 = tests - subvar;
                int mult4 = v3 * (byte)(filter >> 8);
                int mult5 = v3 * (byte)((filter >> 1) & 127);
                int v4 = reg1 + (mult4 >> 6) + ((mult4 >> 5) & 1);
                int v5 = v4 + (mult5 >> 13) + ((mult5 >> 12) & 1);
                ram1_[1] = v5;
            }
            ram1_[5] = reference;

            // IRQ handling
            boolean wantsIrq = active && (ram2_[6] & 1) != 0 && (ram2_[8] & 0x4000) == 0 && irq_flag;
            if (wantsIrq) {
                if (this.irq_assert == 0) {
                    if (this.nfs != 0) ram2_[8] |= 0x4000;
                    this.irq_assert = 1;
                    this.irq_channel = slot;
                    if (mcu.mcu_jv880) mcu.MCU_GA_SetGAInt(5, true);
                    else mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ0.ordinal(), true);
                } else {
                    if (this.nfs != 0) ram2_[8] |= 0x4000;
                    this.irq_pending_mask |= (1 << slot);
                }
            }

            // Envelope
            int[] volmul1 = scratch_volmul1;
            int[] volmul2 = scratch_volmul2;
            volmul1[0] = 0; volmul2[0] = 0;
            calc_tv(0, ram2_[3] & 0xffff, ram2_, 9, active, volmul1);
            calc_tv(1, ram2_[4] & 0xffff, ram2_, 10, active, volmul2);
            calc_tv(2, ram2_[5] & 0xffff, ram2_, 11, active, null);

            // Volume/pan
            int sample = (ram2_[6] & 2) == 0 ? ram1_[3] : v3;
            int multiv1 = multi(sample, (byte)(volmul1[0] >> 8));
            int multiv2 = multi(sample, (byte)((volmul1[0] >> 1) & 127));
            int sample2 = addclip20(multiv1 >> 6, multiv2 >> 13, ((multiv2 >> 12) | (multiv1 >> 5)) & 1);
            int multiv3 = multi(sample2, (byte)(volmul2[0] >> 8));
            int multiv4 = multi(sample2, (byte)((volmul2[0] >> 1) & 127));
            int sample3 = addclip20(multiv3 >> 6, multiv4 >> 13, ((multiv4 >> 12) | (multiv3 >> 5)) & 1);

            int pan = active ? ram2_[1] & 0xffff : 0;
            int rc = active ? ram2_[2] & 0xffff : 0;
            int sampl = multi(sample3, (byte)((pan >> 8) & 255));
            int sampr = multi(sample3, (byte)((pan >> 0) & 255));
            int rc0 = multi(sample3, (byte)((rc >> 8) & 255)) >> 5;
            int rc1 = multi(sample3, (byte)((rc >> 0) & 255)) >> 5;

            // Mix - rcadd already added at start of slot iteration
            int suml = addclip20(this.ram1[31][1], sampl >> 6, (sampl >> 5) & 1);
            int sumr = addclip20(this.ram1[31][3], sampr >> 6, (sampr >> 5) & 1);

            switch (slot2) {
                case 17: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[0] >> 1, rcadd2[0] & 1); break;
                case 18: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[1] >> 1, rcadd2[1] & 1); break;
                case 21: this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[2] >> 1, rcadd2[2] & 1); break;
                case 22: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[3] >> 1, rcadd2[3] & 1); break;
                case 23: this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[4] >> 1, rcadd2[4] & 1); break;
                case 31: this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[5] >> 1, rcadd2[5] & 1); break;
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

            // Update nibble
            if (key != 0 && this.nfs != 0) {
                ram2_[7] &= (short)~0xf020;
                ram2_[7] |= (short)(((usenew || kon) ? newnibble : old_nibble) << 12);
                ram2_[7] |= (short)(key << 5);
            }

            if (!active) {
                if (this.nfs != 0) { ram1_[1] = 0; ram1_[3] = 0; ram1_[5] = 0; }
                ram2_[8] = 0; ram2_[9] = 0; ram2_[10] = 0;
            }
        }

        // Note: accum_l/r are already set correctly by the last slot processing
        // (either in the inactive slot handling at line 1248-1251, or in the
        // active slot handling at line 1418-1421). Do NOT overwrite here.
    }

    // Fast ROM read helper for two-phase processing
    private int readRomFast(int address) {
        if (useFastRomRead) {
            int bank = (address >>> 19) & 7;
            return switch (bank) {
                case 0 -> waverom1[address & 0x1f_ffff];
                case 1 -> waverom2[address & 0xf_ffff];
                case 2 -> waverom3[address & 0xf_ffff];
                default -> 0;
            };
        } else {
            return PCM_ReadROM(address);
        }
    }

    void PCM_Update(long cycles) {
        // Early exit if nothing to do (common case)
        if (this.cycles >= cycles) return;

        long profStart = System.nanoTime();  // Profiling: start time

        int reg_slots = (this.config_reg_3d & 31) + 1;
        int voice_active = this.voice_mask & this.voice_mask_pending;
        while (this.cycles < cycles) {
            int[] tt = scratch_tt;
            tt[0] = 0; tt[1] = 0;  // Reset

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

                // Pre-compute addlow values for calc_tv (optimization)
                // These depend only on tv_counter, constant for all 84 calc_tv calls per sample
                int tc = this.tv_counter;
                // type4: bits 3,2,1,0 reversed
                precompAddlow[0] = ((tc & 8) != 0 ? 1 : 0) | ((tc & 4) != 0 ? 2 : 0) |
                                   ((tc & 2) != 0 ? 4 : 0) | ((tc & 1) != 0 ? 8 : 0);
                // type0: bits 5,4,3,2 reversed
                precompAddlow[1] = ((tc & 0x20) != 0 ? 1 : 0) | ((tc & 0x10) != 0 ? 2 : 0) |
                                   ((tc & 8) != 0 ? 4 : 0) | ((tc & 4) != 0 ? 8 : 0);
                // type1: bits 7,6,5,4 reversed
                precompAddlow[2] = ((tc & 0x80) != 0 ? 1 : 0) | ((tc & 0x40) != 0 ? 2 : 0) |
                                   ((tc & 0x20) != 0 ? 4 : 0) | ((tc & 0x10) != 0 ? 8 : 0);
                // type2: bits 9,8,7,6 reversed
                precompAddlow[3] = ((tc & 0x200) != 0 ? 1 : 0) | ((tc & 0x100) != 0 ? 2 : 0) |
                                   ((tc & 0x80) != 0 ? 4 : 0) | ((tc & 0x40) != 0 ? 8 : 0);
                // type3: bits 11,10,9,8 reversed
                precompAddlow[4] = ((tc & 0x800) != 0 ? 1 : 0) | ((tc & 0x400) != 0 ? 2 : 0) |
                                   ((tc & 0x200) != 0 ? 4 : 0) | ((tc & 0x100) != 0 ? 8 : 0);
                // Pre-compute write conditions
                precompWriteMask = 0x1; // type4 always writes
                if ((tc & 3) == 0) precompWriteMask |= 0x2;   // type0
                if ((tc & 15) == 0) precompWriteMask |= 0x4;  // type1
                if ((tc & 63) == 0) precompWriteMask |= 0x8;  // type2
                if ((tc & 127) == 0) precompWriteMask |= 0x10; // type3
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
                scratch_u[0] = 0;
                calc_tv(1, this.ram2[30][0] & 0xffff, this.ram2[30], 9, active, scratch_u);
            }

            {
                int v1 = this.ram2[30][1] & 0xffff;
                int m1 = multi(this.ram1[29][0], (byte) (v1 >> 8)) >> 5; // 17
                int m2 = multi(this.rcsum[0], (byte) (v1 & 255)) >> 5; // 18

                this.ram1[29][0] = addclip20(m1 >> 1, m2 >> 1, (m1 | m2) & 1); // 19
            }

            int[] rcadd = scratch_rcadd;
            int[] rcadd2 = scratch_rcadd2;
            rcadd[0] = rcadd[1] = rcadd[2] = rcadd[3] = rcadd[4] = rcadd[5] = 0;
            rcadd2[0] = rcadd2[1] = rcadd2[2] = rcadd2[3] = rcadd2[4] = rcadd2[5] = 0;

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
                        boolean okey = (this.ram2[31][7] & 0x20) != 0;
                        boolean active = key != 0 && okey;
                        boolean kon = key != 0 && !okey;

                        boolean b15 = (this.ram2[31][8] & 0x8000) != 0; // 0
                        boolean b6 = (this.ram2[31][7] & 0x40) != 0; // 1
                        boolean b7 = (this.ram2[31][7] & 0x80) != 0; // 1
                        int old_nibble = ((this.ram2[31][7] & 0xffff) >> 12) & 15; // 1

                        int address = this.ram1[31][4]; // 0
                        int address_end = this.ram1[31][0]; // 1 or 2
                        int address_loop = this.ram1[31][2]; // 2 or 1

                        int sub_phase = (this.ram2[31][8] & 0x3fff); // 1
                        int interp_ratio = (sub_phase >> 7) & 127;
                        sub_phase += this.ram2[this.ram2[31][7] & 31][0] & 0xffff; // 5
                        int sub_phase_of = (sub_phase >> 14) & 7;
                        if (this.nfs != 0) {
                            this.ram2[31][8] &= ~0x3fff;
                            this.ram2[31][8] |= (short) (sub_phase & 0x3fff);
                        }

                        // address 0
                        int address_cnt = address;

                        int cmp1 = b15 ? address_loop : address_end;
                        int cmp2 = address_cnt;
                        int address_cmp = (cmp1 & 0xfffff) == (cmp2 & 0xfffff) ? 1 : 0; // 9
                        boolean next_b15 = b15;

                        int next_address = address_cnt; // 11

                        cmp1 = (!b6 && address_cmp != 0) ? address_loop : address_cnt;
                        cmp2 = address_cnt;
                        int address_cnt2 = (!kon || (!b6 && address_cmp != 0)) ? cmp1 : cmp2;

                        int address_add = (address_cmp == 0 && b6 && !b15) || (address_cmp == 0 && !b6) ? 1 : 0;
                        int address_sub = address_cmp == 0 && b6 && b15 ? 1 : 0;
                        if (b7)
                            address_cnt2 -= address_add - address_sub;
                        else
                            address_cnt2 += address_add - address_sub;
                        address_cnt = address_cnt2 & 0xf_ffff; // 11
                        b15 = b6 && (b15 ^ address_cmp != 0); // 11

                        cmp1 = b15 ? address_loop : address_end;
                        cmp2 = address_cnt;
                        address_cmp = (cmp1 & 0xf_ffff) == (cmp2 & 0xf_ffff) ? 1 : 0; // 13

                        if (sub_phase_of >= 1) {
                            next_address = address_cnt; // 13
                            next_b15 = b15;
                        }

                        if (active && this.nfs != 0)
                            this.ram1[31][4] = next_address;

                        if (this.nfs != 0) {
                            this.ram2[31][8] &= (short) ~0x8000;
                            this.ram2[31][8] |= (short) ((next_b15 ? 1 : 0) << 15);
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

            // Count active voices for this sample
            int activeCount = Integer.bitCount(voice_active & ((1 << reg_slots) - 1));
            diagActiveVoices += activeCount;
            diagSamples++;
            totalSamples++;  // Never-reset counter for voice timing

            // === VECTORIZED FROZEN ENVELOPE BATCH PROCESSING ===
            // Process all frozen envelopes using SIMD before the voice loop
            // This is faster than processing them one at a time in the loop
            int frozenProcessedMask = 0;
            {
                // Gather envelope data for frozen voices
                int frozenCount = 0;
                for (int s = 0; s < reg_slots && frozenCount < 32; s++) {
                    if (((voice_active >> s) & 1) == 0) continue;
                    short[] r2 = this.ram2[s];
                    boolean okey = (r2[7] & 0x20) != 0;
                    if (!okey) continue; // kon phase, not frozen

                    int envCtrl = r2[3] & 0xffff;
                    int envSpeed = envCtrl & 0xff;
                    int envTarget = (envCtrl >> 8) & 0xff;
                    boolean envFrozen = (envSpeed == 0 && envTarget > 128);

                    long voiceAge = totalSamples - voiceLastKeyOn[s];
                    if (envFrozen && voiceAge > 1000) {
                        // Mark for vectorized processing
                        soaEnvLevel0[frozenCount] = r2[9] & 0x7fff;
                        soaVoiceActive[frozenCount] = s; // Store slot index
                        frozenCount++;
                        frozenProcessedMask |= (1 << s);
                    }
                }

                // Vectorized decay for frozen envelopes (8 at a time)
                int i = 0;
                for (; i + VECTOR_LENGTH <= frozenCount; i += VECTOR_LENGTH) {
                    IntVector env = IntVector.fromArray(SPECIES, soaEnvLevel0, i);
                    IntVector decayed = env.sub(IntVector.broadcast(SPECIES, 20)).max(ZERO_VECTOR);
                    decayed.intoArray(soaEnvLevel0, i);
                }
                // Scalar remainder
                for (; i < frozenCount; i++) {
                    int env = soaEnvLevel0[i];
                    soaEnvLevel0[i] = Math.max(0, env - 20);
                }

                // Scatter results back
                for (i = 0; i < frozenCount; i++) {
                    int slot = soaVoiceActive[i];
                    this.ram2[slot][9] = (short) soaEnvLevel0[i];
                    diagEnvFrozen++;
                }
            }

            int vectorProcessedMask = 0; // Disabled - process all voices fully

            // NOTE: calc_tv_batch_env0 was tried but didn't help because
            // Java Vector API doesn't support efficient per-lane variable shifts.
            // The scalar fallback negated any SIMD benefit.
            int env0ProcessedMask = 0;  // Disabled - fall back to per-voice calc_tv

            // Track how many voices have done ROM reads this sample
            int romReadVoiceCount = 0;

            // Use two-phase SoA processing if enabled
            if (USE_SOA_PROCESSING) {
                processVoicesTwoPhase(reg_slots, voice_active, frozenProcessedMask, rcadd, rcadd2);
            } else
                    for (int slot = 0; slot < reg_slots; slot++) {
                    int[] ram1 = this.ram1[slot];
                    short[] ram2 = this.ram2[slot];
                    boolean okey = (ram2[7] & 0x20) != 0;
                    int key = (voice_active >> slot) & 1;

                    boolean active = okey && key != 0;
                    boolean kon = key != 0 && !okey;

                    // === VOICE AGE-BASED DEALLOCATION ===
                    // Track when each voice last had key-on. If voice active too long
                    // without new key-on, it's stuck - force deallocate.
                    // IMPORTANT: This must run for ALL voices including vectorized ones!

                    if (kon) {
                        // Key-on: record timestamp for this voice
                        voiceLastKeyOn[slot] = totalSamples;
                        voiceFrozenCounter[slot] = 0;
                    }

                    if (key != 0 && !kon) {
                        // Voice is active but not in key-on phase
                        long voiceAge = totalSamples - voiceLastKeyOn[slot];

                        // Check envelope state for deallocation
                        int envCtrl = ram2[3] & 0xffff;
                        int envSpeed = envCtrl & 0xff;
                        int envTarget = (envCtrl >> 8) & 0xff;
                        int envLevel = ram2[9] & 0x7fff;  // Current envelope level (0-32767)
                        boolean envFrozen = (envSpeed == 0 && envTarget > 128);
                        // Aggressive silent threshold: 3000 out of 32767 (~9% of max level)
                        boolean envSilent = (envLevel < 3000);

                        boolean shouldDeallocate = false;

                        // Frozen envelope: speed=0 and high target indicates release phase
                        // ROM sets this after Note Off, expecting voice to be held then released.
                        // In C++ this works correctly, but Java timing differs.
                        // FIX: Force the envelope to actually decay by modifying ram2[9] directly.
                        // This mimics what would happen if the ROM's release logic was working.
                        // Skip if already processed by vectorized batch
                        boolean alreadyProcessed = (frozenProcessedMask & (1 << slot)) != 0;
                        if (envFrozen && voiceAge > 1000 && !alreadyProcessed) {
                            diagEnvFrozen++;
                            // Force envelope decay: ~20 units per sample = ~100ms full decay
                            if (envLevel > 20) {
                                ram2[9] = (short)(envLevel - 20);
                            } else {
                                ram2[9] = 0;
                            }
                            voiceFrozenCounter[slot]++;
                            if (voiceFrozenCounter[slot] > FROZEN_VOICE_THRESHOLD) {
                                shouldDeallocate = true;
                            }
                        } else if (alreadyProcessed) {
                            // Already processed by vectorized batch
                            voiceFrozenCounter[slot]++;
                            if (voiceFrozenCounter[slot] > FROZEN_VOICE_THRESHOLD) {
                                shouldDeallocate = true;
                            }
                        } else if (envFrozen) {
                            // Still in early frozen phase - count but don't decay yet
                            diagEnvFrozen++;
                            voiceFrozenCounter[slot]++;
                        } else {
                            diagEnvNotFrozen++;
                            if (envSpeed == 0) {
                                diagEnvSpeedZeroLowTarget++;
                            } else {
                                diagEnvSpeedNonZero++;
                            }
                            voiceFrozenCounter[slot] = 0;
                        }

                        // Silent voices can be deallocated (after brief minimum age)
                        if (envSilent && voiceAge > 1000) {
                            diagEnvSilent++;
                            shouldDeallocate = true;
                        }

                        if (shouldDeallocate) {
                            // Force release this voice
                            int slotBit = 1 << slot;
                            this.voice_mask_pending &= ~slotBit;
                            this.voice_mask &= ~slotBit;
                            voiceMaskClearCount++;
                            voiceFrozenCounter[slot] = 0;
                            voiceLastKeyOn[slot] = 0;
                            key = 0;
                            active = false;
                            kon = false;
                        }
                    }

                    // Skip voices already processed by vectorized path (after deallocation check)
                    if ((vectorProcessedMask & (1 << slot)) != 0) {
                        // Still need slot2 mixing for reverb/chorus
                        int slot2 = (slot == reg_slots - 1) ? 31 : slot + 1;
                        switch (slot2) {
                            case 17: this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[0] >> 1, rcadd[0] & 1); break;
                            case 18: this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[1] >> 1, rcadd[1] & 1); break;
                            case 21: this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[2] >> 1, rcadd[2] & 1); break;
                            case 22: this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[3] >> 1, rcadd[3] & 1); break;
                            case 23: this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[4] >> 1, rcadd[4] & 1); break;
                            case 31: this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[5] >> 1, rcadd[5] & 1); break;
                        }
                        if (slot == reg_slots - 1) {
                            this.accum_l = this.ram1[31][1];
                            this.accum_r = this.ram1[31][3];
                        }
                        continue;
                    }

                    // Early exit for truly idle voices (key=0)
                    if (key == 0) {
                        diagEarlyExits++;
                        // Still need slot2 mixing for reverb/chorus
                        int slot2 = (slot == reg_slots - 1) ? 31 : slot + 1;
                        switch (slot2) {
                            case 17:
                                this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[0] >> 1, rcadd[0] & 1);
                                this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[0] >> 1, rcadd2[0] & 1);
                                break;
                            case 18:
                                this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[1] >> 1, rcadd[1] & 1);
                                this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[1] >> 1, rcadd2[1] & 1);
                                break;
                            case 21:
                                this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[2] >> 1, rcadd[2] & 1);
                                this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[2] >> 1, rcadd2[2] & 1);
                                break;
                            case 22:
                                this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[3] >> 1, rcadd[3] & 1);
                                this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[3] >> 1, rcadd2[3] & 1);
                                break;
                            case 23:
                                this.ram1[31][1] = addclip20(this.ram1[31][1], rcadd[4] >> 1, rcadd[4] & 1);
                                this.rcsum[0] = addclip20(this.rcsum[0], rcadd2[4] >> 1, rcadd2[4] & 1);
                                break;
                            case 31:
                                this.ram1[31][3] = addclip20(this.ram1[31][3], rcadd[5] >> 1, rcadd[5] & 1);
                                this.rcsum[1] = addclip20(this.rcsum[1], rcadd2[5] >> 1, rcadd2[5] & 1);
                                break;
                        }
                        // For last slot, set output accumulators
                        if (slot == reg_slots - 1) {
                            this.accum_l = this.ram1[31][1];
                            this.accum_r = this.ram1[31][3];
                        }
                        // Clear ALL voice state (including ram2[11] filter state)
                        if (this.nfs != 0) {
                            ram1[1] = 0;
                            ram1[3] = 0;
                            ram1[5] = 0;
                        }
                        ram2[8] = 0;
                        ram2[9] = 0;
                        ram2[10] = 0;
                        ram2[11] = 0;  // Also clear filter state
                        continue;
                    }

                    // Full voice processing (key != 0)
                    diagFullProcess++;

                    // Ultra-fast-path voices are now handled by vectorized processing above
                    // Here we only handle skipForEnvelope (nearly silent voices)
                    int currentEnvLevel = ram2[9] & 0x7fff;
                    boolean skipForEnvelope = (currentEnvLevel < 200) && !kon;
                    boolean skipRomReads = skipForEnvelope;
                    if (skipRomReads) {
                        diagRomReadsSkipped++;
                    } else {
                        diagRomReadsPerformed++;
                        romReadVoiceCount++;  // Count this voice as having done ROM reads
                    }

                    // address generator

                    boolean b15 = (ram2[8] & 0x8000) != 0; // 0
                    boolean b6 = (ram2[7] & 0x40) != 0; // 1
                    boolean b7 = (ram2[7] & 0x80) != 0; // 1
                    int hiaddr = ((ram2[7] & 0xffff) >> 8) & 15; // 1
                    int old_nibble = ((ram2[7] & 0xffff) >> 12) & 15; // 1
                    // Pre-compute ROM address base once per voice (optimization)
                    int waveAddrBase = hiaddr << 20;

                    int address = ram1[4]; // 0
                    int address_end = ram1[0]; // 1 or 2
                    int address_loop = ram1[2]; // 2 or 1

                    int cmp1 = b15 ? address_loop : address_end;
                    int cmp2 = address;
                    boolean nibble_cmp1 = (cmp1 & 0xf_fff0) == (cmp2 & 0xf_fff0); // 2
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
                    wave_address &= 0xf_ffff;

                    // Inline ROM read for performance (eliminates method call overhead)
                    int newnibble;
                    if (skipRomReads) {
                        newnibble = 0;
                    } else if (useFastRomRead) {
                        int addr = waveAddrBase | wave_address;
                        int bank = (addr >>> 19) & 7;
                        newnibble = (bank == 0 ? waverom1[addr & 0x1f_ffff] & 0xff :
                                     bank == 1 ? waverom2[addr & 0xf_ffff] & 0xff :
                                     bank == 2 ? waverom3[addr & 0xf_ffff] & 0xff : 0);
                    } else {
                        newnibble = PCM_ReadROM(waveAddrBase | wave_address) & 0xff;
                    }
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
                    // Skip ROM read for effectively silent voices - major performance optimization
                    // Inline ROM read for performance
                    int samp0;
                    if (skipRomReads) {
                        samp0 = 0;
                    } else if (useFastRomRead) {
                        int addr0 = waveAddrBase | address_cnt;
                        int bank0 = (addr0 >>> 19) & 7;
                        samp0 = bank0 == 0 ? waverom1[addr0 & 0x1f_ffff] :
                                bank0 == 1 ? waverom2[addr0 & 0xf_ffff] :
                                bank0 == 2 ? waverom3[addr0 & 0xf_ffff] : 0; // 18 signed
                    } else {
                        samp0 = PCM_ReadROM(waveAddrBase | address_cnt); // 18 signed
                    }

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

                    // Inline ROM read for performance
                    int samp1;
                    if (skipRomReads) {
                        samp1 = 0;
                    } else if (useFastRomRead) {
                        int addr1 = waveAddrBase | address_cnt;
                        int bank1 = (addr1 >>> 19) & 7;
                        samp1 = bank1 == 0 ? waverom1[addr1 & 0x1f_ffff] :
                                bank1 == 1 ? waverom2[addr1 & 0xf_ffff] :
                                bank1 == 2 ? waverom3[addr1 & 0xf_ffff] : 0; // 20 signed
                    } else {
                        samp1 = PCM_ReadROM(waveAddrBase | address_cnt); // 20 signed
                    }

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

                    // Inline ROM read for performance
                    int samp2;
                    if (skipRomReads) {
                        samp2 = 0;
                    } else if (useFastRomRead) {
                        int addr2 = waveAddrBase | address_cnt;
                        int bank2 = (addr2 >>> 19) & 7;
                        samp2 = bank2 == 0 ? waverom1[addr2 & 0x1f_ffff] :
                                bank2 == 1 ? waverom2[addr2 & 0xf_ffff] :
                                bank2 == 2 ? waverom3[addr2 & 0xf_ffff] : 0; // 1 signed
                    } else {
                        samp2 = PCM_ReadROM(waveAddrBase | address_cnt); // 1 signed
                    }

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

                    // Inline ROM read for performance
                    int samp3;
                    if (skipRomReads) {
                        samp3 = 0;
                    } else if (useFastRomRead) {
                        int addr3 = waveAddrBase | address_cnt;
                        int bank3 = (addr3 >>> 19) & 7;
                        samp3 = bank3 == 0 ? waverom1[addr3 & 0x1f_ffff] :
                                bank3 == 1 ? waverom2[addr3 & 0xf_ffff] :
                                bank3 == 2 ? waverom3[addr3 & 0xf_ffff] : 0; // 5 signed
                    } else {
                        samp3 = PCM_ReadROM(waveAddrBase | address_cnt); // 5 signed
                    }

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

                    int step0 = multi(interp_lut_shifted[0][interp_ratio], (byte) samp0) >> 8;
                    select_nibble = nibble_cmp2 ? old_nibble : newnibble;
                    shift = (10 - select_nibble) & 15;
                    step0 = (step0 << 1) >> shift;

                    test = addclip20(test, step0 >> 1, step0 & 1);

                    int step1 = multi(interp_lut_shifted[1][interp_ratio], (byte) samp1) >> 8;
                    select_nibble = nibble_cmp3 ? old_nibble : newnibble;
                    shift = (10 - select_nibble) & 15;
                    step1 = (step1 << 1) >> shift;

                    test = addclip20(test, step1 >> 1, step1 & 1);

                    int step2 = multi(interp_lut_shifted[2][interp_ratio], (byte) samp2) >> 8;
                    select_nibble = nibble_cmp4 ? old_nibble : newnibble;
                    shift = (10 - select_nibble) & 15;
                    step2 = (step2 << 1) >> shift;

                    int reg1 = ram1[1];
                    int reg3 = ram1[3];
                    int reg2_6 = ((ram2[6] & 0xffff) >> 8) & 127;

                    test = addclip20(test, step2 >> 1, step2 & 1);

                    int filter = ram2[11] & 0xffff;
                    int v3;

                    if (mcu.mcu_mk1) {
                        int mult1 = multi(reg1, (byte) (filter >> 8)); // 8
                        int mult2 = multi(reg1, (byte) ((filter >> 1) & 127)); // 9
                        int mult3 = multi(reg1, (byte) reg2_6); // 10

                        int v2 = addclip20(reg3, mult1 >> 6, (mult1 >> 5) & 1); // 9
                        int v1 = addclip20(v2, mult2 >> 13, (mult2 >> 12) & 1); // 10
                        int subvar = addclip20(v1, mult3 >> 6, (mult3 >> 5) & 1); // 11

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

                    // Check if this voice wants to fire an IRQ
                    boolean wantsIrq = active && (ram2[6] & 1) != 0 && (ram2[8] & 0x4000) == 0 && irq_flag;
                    if (wantsIrq) {
                        if (this.irq_assert == 0) {
                            // IRQ can fire immediately
                            if (this.nfs != 0)
                                ram2[8] |= 0x4000;
                            this.irq_assert = 1;
                            this.irq_channel = slot;
                            if (mcu.mcu_jv880)
                                mcu.MCU_GA_SetGAInt(5, true);
                            else
                                mcu.interrupt.MCU_Interrupt_SetRequest(INTERRUPT_SOURCE_IRQ0.ordinal(), true);
                        } else {
                            // IRQ is BLOCKED - queue it for later processing
                            // This prevents lost IRQs that cause voice accumulation
                            // Also set the 0x4000 bit to prevent re-queueing on subsequent loops
                            if (this.nfs != 0)
                                ram2[8] |= 0x4000;
                            this.irq_pending_mask |= (1 << slot);
                            diagIrqBlocked++;
                        }
                    }

                    int[] volmul1 = scratch_volmul1;
                    int[] volmul2 = scratch_volmul2;
                    volmul1[0] = 0;
                    volmul2[0] = 0;

                    // Use pre-computed envelope 0 value if available, otherwise compute
                    if ((env0ProcessedMask & (1 << slot)) != 0) {
                        volmul1[0] = soaVolmul0[slot];
                    } else {
                        calc_tv(0, ram2[3] & 0xffff, ram2, 9, active, volmul1);
                    }
                    calc_tv(1, ram2[4] & 0xffff, ram2, 10, active, volmul2);
                    calc_tv(2, ram2[5] & 0xffff, ram2, 11, active, null);

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

                    int pan = active ? ram2[1] & 0xffff : 0;
                    int rc = active ? ram2[2] & 0xffff : 0;

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

                    if (!active) {
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
