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

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JPanel;

import vavi.sound.sc55.Mcu.McuButton;

import static java.lang.System.getLogger;
import static vavi.sound.sc55.LcdFont.lcd_font;
import static vavi.sound.sc55.Mcu.McuButton.*;


class Lcd {

    private static final Logger logger = getLogger(Lcd.class.getName());

    private boolean LCD_DL, LCD_N, LCD_F, LCD_D, LCD_C, LCD_B, LCD_ID, LCD_S;
    private int LCD_DD_RAM, LCD_AC, LCD_CG_RAM;
    private boolean LCD_RAM_MODE = false;
    private final byte[] LCD_Data = new byte[80];
    private final byte[] LCD_CG = new byte[64];

    private boolean lcd_enable = true;
    private boolean lcd_quit_requested = false;

    private final Mcu mcu;

    Lcd(Mcu mcu) {
        this.mcu = mcu;
    }

    void LCD_Enable(boolean enable) {
        lcd_enable = enable;
    }

    boolean LCD_QuitRequested() {
        return lcd_quit_requested;
    }

    void LCD_Write(int address, byte data) {
//System.err.printf("%10d: %02x %02x%n", mcu.CC, address, data);
//if (mcu.CC++ > 6000) { System.exit(1); }
        if (address == 0) {
            if ((data & 0xe0) == 0x20) {
                LCD_DL = (data & 0x10) != 0;
                LCD_N = (data & 0x8) != 0;
                LCD_F = (data & 0x4) != 0;
            } else if ((data & 0xf8) == 0x8) {
                LCD_D = (data & 0x4) != 0;
                LCD_C = (data & 0x2) != 0;
                LCD_B = (data & 0x1) != 0;
            } else if ((data & 0xff) == 0x01) {
                LCD_DD_RAM = 0;
                LCD_ID = true;
                Arrays.fill(LCD_Data, (byte) 0x20);
            } else if ((data & 0xff) == 0x02) {
                LCD_DD_RAM = 0;
            } else if ((data & 0xfc) == 0x04) {
                LCD_ID = (data & 0x2) != 0;
                LCD_S = (data & 0x1) != 0;
            } else if ((data & 0xc0) == 0x40) {
                LCD_CG_RAM = (data & 0x3f);
                LCD_RAM_MODE = false;
            } else if ((data & 0x80) == 0x80) {
                LCD_DD_RAM = (data & 0x7f);
                LCD_RAM_MODE = true;
            } else {
                address += 0;
            }
        } else {
            if (!LCD_RAM_MODE) {
                LCD_CG[LCD_CG_RAM] = (byte) (data & 0x1f);
                if (LCD_ID) {
                    LCD_CG_RAM++;
                } else {
                    LCD_CG_RAM--;
                }
                LCD_CG_RAM &= 0x3f;
            } else {
                if (LCD_N) {
                    if ((LCD_DD_RAM & 0x40) != 0) {
                        if ((LCD_DD_RAM & 0x3f) < 40)
                            LCD_Data[(LCD_DD_RAM & 0x3f) + 40] = data;
                    } else {
                        if ((LCD_DD_RAM & 0x3f) < 40) {
                            LCD_Data[LCD_DD_RAM & 0x3f] = data;
//if (LCD_DD_RAM == 0 && data == 0x31) { new Exception("LCD_Write").printStackTrace(); }
                        }
                    }
                } else {
                    if (LCD_DD_RAM < 80)
                        LCD_Data[LCD_DD_RAM] = data;
                }
                if (LCD_ID) {
                    LCD_DD_RAM++;
                } else {
                    LCD_DD_RAM--;
                }
                LCD_DD_RAM &= 0x7f;
            }
        }
//        logger.log(Level.TRACE, "%d %2x ".formatted(address, data));
//        if (data >= 0x20 && data <= 'z')
//            logger.log(Level.TRACE, "%c".formatted(data));
//        else
//            logger.log(Level.TRACE, "");
    }

    int lcd_width = 741;
    int lcd_height = 268;
    private static final int lcd_width_max = 1024;
    private static final int lcd_height_max = 1024;
    private JFrame window;
    private BufferedImage texture;
    private JPanel renderer;

    private String m_back_path = "/data/back.data";

    private final int[][] lcd_buffer = new int[lcd_height_max][lcd_width_max];
    private final int[][] lcd_background = new int[268][741];

    private boolean lcd_init = false;

    final Map<Integer, McuButton> button_map_sc55 = new HashMap<>();

    {
        button_map_sc55.put(KeyEvent.VK_Q, MCU_BUTTON_POWER);
        button_map_sc55.put(KeyEvent.VK_W, MCU_BUTTON_INST_ALL);
        button_map_sc55.put(KeyEvent.VK_E, MCU_BUTTON_INST_MUTE);
        button_map_sc55.put(KeyEvent.VK_R, MCU_BUTTON_PART_L);
        button_map_sc55.put(KeyEvent.VK_T, MCU_BUTTON_PART_R);
        button_map_sc55.put(KeyEvent.VK_Y, MCU_BUTTON_INST_L);
        button_map_sc55.put(KeyEvent.VK_U, MCU_BUTTON_INST_R);
        button_map_sc55.put(KeyEvent.VK_I, MCU_BUTTON_KEY_SHIFT_L);
        button_map_sc55.put(KeyEvent.VK_O, MCU_BUTTON_KEY_SHIFT_R);
        button_map_sc55.put(KeyEvent.VK_P, MCU_BUTTON_LEVEL_L);
        button_map_sc55.put(KeyEvent.VK_OPEN_BRACKET, MCU_BUTTON_LEVEL_R);
        button_map_sc55.put(KeyEvent.VK_A, MCU_BUTTON_MIDI_CH_L);
        button_map_sc55.put(KeyEvent.VK_S, MCU_BUTTON_MIDI_CH_R);
        button_map_sc55.put(KeyEvent.VK_D, MCU_BUTTON_PAN_L);
        button_map_sc55.put(KeyEvent.VK_F, MCU_BUTTON_PAN_R);
        button_map_sc55.put(KeyEvent.VK_G, MCU_BUTTON_REVERB_L);
        button_map_sc55.put(KeyEvent.VK_H, MCU_BUTTON_REVERB_R);
        button_map_sc55.put(KeyEvent.VK_J, MCU_BUTTON_CHORUS_L);
        button_map_sc55.put(KeyEvent.VK_K, MCU_BUTTON_CHORUS_R);
        button_map_sc55.put(KeyEvent.VK_LEFT, MCU_BUTTON_PART_L);
        button_map_sc55.put(KeyEvent.VK_RIGHT, MCU_BUTTON_PART_R);
    }

    final Map<Integer, McuButton> button_map_jv880 = new HashMap<>();

    {
        button_map_jv880.put(KeyEvent.VK_P, MCU_BUTTON_PREVIEW);
        button_map_jv880.put(KeyEvent.VK_LEFT, MCU_BUTTON_CURSOR_L);
        button_map_jv880.put(KeyEvent.VK_RIGHT, MCU_BUTTON_CURSOR_R);
        button_map_jv880.put(KeyEvent.VK_TAB, MCU_BUTTON_DATA);
        button_map_jv880.put(KeyEvent.VK_Q, MCU_BUTTON_TONE_SELECT);
        button_map_jv880.put(KeyEvent.VK_A, MCU_BUTTON_PATCH_PERFORM);
        button_map_jv880.put(KeyEvent.VK_W, MCU_BUTTON_EDIT);
        button_map_jv880.put(KeyEvent.VK_E, MCU_BUTTON_SYSTEM);
        button_map_jv880.put(KeyEvent.VK_R, MCU_BUTTON_RHYTHM);
        button_map_jv880.put(KeyEvent.VK_T, MCU_BUTTON_UTILITY);
        button_map_jv880.put(KeyEvent.VK_S, MCU_BUTTON_MUTE);
        button_map_jv880.put(KeyEvent.VK_D, MCU_BUTTON_MONITOR);
        button_map_jv880.put(KeyEvent.VK_F, MCU_BUTTON_COMPARE);
        button_map_jv880.put(KeyEvent.VK_G, MCU_BUTTON_ENTER);
    }

    void LCD_SetBackPath(final String path) {
        m_back_path = path;
    }

    void LCD_Init() {
        if (lcd_init)
            return;

        lcd_quit_requested = false;

        String title = "Nuked SC-55: ";

        title += mcu.rs_name[mcu.romset];

        window = new JFrame(title);

        texture = new BufferedImage(lcd_width, lcd_height, BufferedImage.TYPE_INT_BGR);

        renderer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(texture, 0, 0, lcd_width, lcd_height, this);
            }
        };
        renderer.setPreferredSize(new Dimension(lcd_width, lcd_height));

        try {
            byte[] raw = Lcd.class.getResourceAsStream(m_back_path).readAllBytes();
logger.log(Level.DEBUG, "%d x %d x %d = %d, %d".formatted(lcd_background[0].length, lcd_background.length, Integer.BYTES, lcd_background[0].length * lcd_background.length * Integer.BYTES, raw.length));
            int p = 0;
            ByteBuffer bb = ByteBuffer.allocate(lcd_background[0].length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (var b : lcd_background) {
                System.arraycopy(raw, p, bb.array(), 0, bb.capacity());
                bb.asIntBuffer().get(b);
                p += b.length * Integer.BYTES;
            }
        } catch (NullPointerException e) {
            throw new IllegalStateException(m_back_path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        init();

        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.getContentPane().add(renderer);
        window.pack();
        window.setVisible(true);

        lcd_init = true;
    }

    void LCD_UnInit() {
        if (!lcd_init)
            return;
    }

    int lcd_col1 = 0x000000;
    int lcd_col2 = 0x0050c8;

    void LCD_FontRenderStandard(int x, int y, byte ch, boolean overlay /* = false */) {
        byte[] f;
        if ((ch & 0xff) >= 16)
            f = lcd_font[(ch & 0xff) - 16];
        else
            f = Arrays.copyOfRange(LCD_CG, (ch & 7) * 8, (ch & 7) * 8 + 8);
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 5; j++) {
                int col;
                if ((f[i] & (1 << (4 - j))) != 0) {
                    col = lcd_col1;
                } else {
                    col = lcd_col2;
                }
                int xx = x + i * 6;
                int yy = y + j * 6;
                for (int ii = 0; ii < 5; ii++) {
                    for (int jj = 0; jj < 5; jj++) {
                        if (overlay)
                            lcd_buffer[xx + ii][yy + jj] &= col;
                        else
                            lcd_buffer[xx + ii][yy + jj] = col;
                    }
                }
            }
        }
    }

    void LCD_FontRenderLevel(int x, int y, byte ch, byte width /* = 5 */) {
        byte[] f;
        if ((ch & 0xff) >= 16)
            f = lcd_font[(ch & 0xff) - 16];
        else
            f = Arrays.copyOfRange(LCD_CG, (ch & 7) * 8, (ch & 7) * 8 + 8);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < width; j++) {
                int col;
                if ((f[i] & (1 << (4 - j))) != 0) {
                    col = lcd_col1;
                } else {
                    col = lcd_col2;
                }
                int xx = x + i * 11;
                int yy = y + j * 26;
                for (int ii = 0; ii < 9; ii++) {
                    for (int jj = 0; jj < 24; jj++) {
                        lcd_buffer[xx + ii][yy + jj] = col;
                    }
                }
            }
        }
    }

    private static final boolean[][][] LR = {
            {
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, false, false, false, false, false, false, false, false, false},
                    {true, true, true, true, true, true, true, true, true, true, true},
                    {true, true, true, true, true, true, true, true, true, true, true},
            },
            {
                    {true, true, true, true, true, true, true, true, true, false, false},
                    {true, true, true, true, true, true, true, true, true, true, false},
                    {true, true, false, false, false, false, false, false, true, true, false},
                    {true, true, false, false, false, false, false, false, true, true, false},
                    {true, true, false, false, false, false, false, false, true, true, false},
                    {true, true, true, true, true, true, true, true, true, true, false},
                    {true, true, true, true, true, true, true, true, true, false, false},
                    {true, true, false, false, false, false, false, true, true, false, false},
                    {true, true, false, false, false, false, false, false, true, true, false},
                    {true, true, false, false, false, false, false, false, true, true, false},
                    {true, true, false, false, false, false, false, false, false, true, true},
                    {true, true, false, false, false, false, false, false, false, true, true},
            }
    };

    private static final int[][] LR_xy = {
            {70, 264},
            {232, 264}
    };

    void LCD_FontRenderLR(byte ch) {
        byte[] f;
        if ((ch & 0xff) >= 16)
            f = lcd_font[(ch & 0xff) - 16];
        else
            f = Arrays.copyOfRange(LCD_CG, (ch & 7) * 8, (ch & 7) * 8 + 8);
        int col;
        if ((f[0] & 1) != 0) {
            col = lcd_col1;
        } else {
            col = lcd_col2;
        }
        for (int f_ = 0; f_ < 2; f_++) {
            for (int i = 0; i < 12; i++) {
                for (int j = 0; j < 11; j++) {
                    if (LR[f_][i][j])
                        lcd_buffer[i + LR_xy[f_][0]][j + LR_xy[f_][1]] = col;
                }
            }
        }
    }

    void LCD_Update() {
        if (!lcd_init)
            return;

        if (!mcu.mcu_cm300 && !mcu.mcu_st && !mcu.mcu_scb55) {
            synchronized (mcu.work_thread_lock) {

                if (!lcd_enable && !mcu.mcu_jv880) {
                    for (int[] x : lcd_buffer)
                        Arrays.fill(x, 0);
                } else {
                    if (mcu.mcu_jv880) {
                        for (int i = 0; i < lcd_height; i++) {
                            for (int j = 0; j < lcd_width; j++) {
                                lcd_buffer[i][j] = 0xff03be51;
                            }
                        }
                    } else {
                        for (int i = 0; i < lcd_height; i++) {
                            for (int j = 0; j < lcd_width; j++) {
                                lcd_buffer[i][j] = lcd_background[i][j];
                            }
                        }
                    }

                    if (mcu.mcu_jv880) {
                        for (int i = 0; i < 2; i++) {
                            for (int j = 0; j < 24; j++) {
                                byte ch = LCD_Data[i * 40 + j];
                                LCD_FontRenderStandard(4 + i * 50, 4 + j * 34, ch, false);
                            }
                        }

                        // cursor
                        int j = LCD_DD_RAM % 0x40;
                        int i = LCD_DD_RAM / 0x40;
                        if (i < 2 && j < 24 && LCD_C)
                            LCD_FontRenderStandard(4 + i * 50, 4 + j * 34, (byte) '_', true);
                    } else {
                        for (int i = 0; i < 3; i++) { // left top
                            byte ch = LCD_Data[0 + i];
                            LCD_FontRenderStandard(11, 34 + i * 35, ch, false);
                        }
                        for (int i = 0; i < 16; i++) { // top: inst number, name
                            byte ch = LCD_Data[3 + i];
                            LCD_FontRenderStandard(11, 153 + i * 35, ch, false);
                        }
                        for (int i = 0; i < 3; i++) { // level
                            byte ch = LCD_Data[40 + i];
                            LCD_FontRenderStandard(75, 34 + i * 35, ch, false);
                        }
                        for (int i = 0; i < 3; i++) { // pan
                            byte ch = LCD_Data[43 + i];
                            LCD_FontRenderStandard(75, 153 + i * 35, ch, false);
                        }
                        for (int i = 0; i < 3; i++) { // reverb
                            byte ch = LCD_Data[49 + i];
                            LCD_FontRenderStandard(139, 34 + i * 35, ch, false);
                        }
                        for (int i = 0; i < 3; i++) { // chorus
                            byte ch = LCD_Data[46 + i];
                            LCD_FontRenderStandard(139, 153 + i * 35, ch, false);
                        }
                        for (int i = 0; i < 3; i++) { // k shift
                            byte ch = LCD_Data[52 + i];
                            LCD_FontRenderStandard(203, 34 + i * 35, ch, false);
                        }
                        for (int i = 0; i < 3; i++) { // midi ch
                            byte ch = LCD_Data[55 + i];
                            LCD_FontRenderStandard(203, 153 + i * 35, ch, false);
                        }

                        LCD_FontRenderLR(LCD_Data[58]);

                        for (int i = 0; i < 2; i++) {
                            for (int j = 0; j < 4; j++) {
                                byte ch = LCD_Data[20 + j + i * 40];
                                LCD_FontRenderLevel(71 + i * 88, 293 + j * 130, ch, (byte) (j == 3 ? 1 : 5));
                            }
                        }
                    }
                }
            }

            for (int y = 0; y < lcd_height; y++) {
                texture.getRaster().setDataElements(0, y, lcd_width, 1, lcd_buffer[y]);
            }
//logger.log(Level.DEBUG, "LCD_Update");
            renderer.repaint();
        }
    }

    void init() {
        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                lcd_quit_requested = true;
logger.log(Level.DEBUG, "windowClosing");
            }
        });
        window.addKeyListener(new KeyAdapter() {
            boolean held;
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_COMMA)
                    mcu.MCU_EncoderTrigger(0);
                if (e.getKeyCode() == KeyEvent.VK_PERIOD)
                    mcu.MCU_EncoderTrigger(1);

                process(e, true);
                held = true;
            }

            @Override
            public void keyReleased(KeyEvent e) {
                held = false;
                process(e, false);
            }

            void process(KeyEvent e, boolean isDown) {
                if (held)
                    return;

                int mask = 0;
                int button_pressed = mcu.mcu_button_pressed.get();

                var button_map = mcu.mcu_jv880 ? button_map_jv880 : button_map_sc55;
                var button_size = (mcu.mcu_jv880 ? button_map_jv880.size() : button_map_sc55.size());
                int i = e.getKeyCode();
                McuButton b = button_map.get(i);
                if (b == null) return;
logger.log(Level.DEBUG, "key: " + b + ", " + isDown);
                mask |= 1 << b.v;

                if (isDown)
                    button_pressed |= mask;
                else
                    button_pressed &= ~mask;

                mcu.mcu_button_pressed.set(button_pressed);

//#if 0
//                    if (sdl_event.key.keysym.scancode >= KeyEvent.VK_1 && sdl_event.key.keysym.scancode < KeyEvent.VK_0) {
//#if 0
//                        int kk = sdl_event.key.keysym.scancode - KeyEvent.VK_1;
//                        if (sdl_event.type == SDL_KEYDOWN) {
//                            MCU_PostUART(0xc0);
//                            MCU_PostUART(118);
//                            MCU_PostUART(0x90);
//                            MCU_PostUART(0x30 + kk);
//                            MCU_PostUART(0x7f);
//                        } else {
//                            MCU_PostUART(0x90);
//                            MCU_PostUART(0x30 + kk);
//                            MCU_PostUART(0);
//                        }
//#endif
//                        int kk = sdl_event.key.keysym.scancode - KeyEvent.VK_1;
//                        final int patch = 47;
//                        if (sdl_event.type == SDL_KEYDOWN) {
//                            private int bend = 0x2000;
//                            if (kk == 4) {
//                                MCU_PostUART(0x99);
//                                MCU_PostUART(0x32);
//                                MCU_PostUART(0x7f);
//                            } else if (kk == 3) {
//                                bend += 0x100;
//                                if (bend > 0x3fff)
//                                    bend = 0x3fff;
//                                MCU_PostUART(0xe1);
//                                MCU_PostUART(bend & 127);
//                                MCU_PostUART((bend >>> 7) & 127);
//                            } else if (kk == 2) {
//                                bend -= 0x100;
//                                if (bend < 0)
//                                    bend = 0;
//                                MCU_PostUART(0xe1);
//                                MCU_PostUART(bend & 127);
//                                MCU_PostUART((bend >>> 7) & 127);
//                            } else if (kk) {
//                                MCU_PostUART(0xc1);
//                                MCU_PostUART(patch);
//                                MCU_PostUART(0xe1);
//                                MCU_PostUART(bend & 127);
//                                MCU_PostUART((bend >>> 7) & 127);
//                                MCU_PostUART(0x91);
//                                MCU_PostUART(0x32);
//                                MCU_PostUART(0x7f);
//                            } else if (kk == 0) {
//                                //MCU_PostUART(0xc0);
//                                //MCU_PostUART(patch);
//                                MCU_PostUART(0xe0);
//                                MCU_PostUART(0x00);
//                                MCU_PostUART(0x40);
//                                MCU_PostUART(0x99);
//                                MCU_PostUART(0x37);
//                                MCU_PostUART(0x7f);
//                            }
//                        } else {
//                            if (kk == 1) {
//                                MCU_PostUART(0x91);
//                                MCU_PostUART(0x32);
//                                MCU_PostUART(0);
//                            } else if (kk == 0) {
//                                MCU_PostUART(0x99);
//                                MCU_PostUART(0x37);
//                                MCU_PostUART(0);
//                            } else if (kk == 4) {
//                                MCU_PostUART(0x99);
//                                MCU_PostUART(0x32);
//                                MCU_PostUART(0);
//                            }
//                        }
//                    }
//#endif
            }
        });
    }
}