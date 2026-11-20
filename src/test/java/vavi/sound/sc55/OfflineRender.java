/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sc55;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


/**
 * Renders the emulator output deterministically (no audio device, no threads)
 * for bit-exact comparison with the original C++ implementation.
 * <p>
 * usage: OfflineRender events.txt out.raw totalCycles
 * <p>
 * events.txt: each line is "{mcu cycle} {hex byte} {hex byte} ...",
 * bytes are posted to the UART when mcu cycles reach the value.
 * out.raw: 16bit little endian stereo.
 * <p>
 * system property "sc55.dir" ... rom directory
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-11-21 nsano initial version <br>
 */
public class OfflineRender {

    record Event(long cycle, byte[] data) {}

    /**
     * converts a smf into the events file.
     * usage: OfflineRender --convert in.mid out.txt startCycle
     */
    static void convert(String[] args) throws Exception {
        javax.sound.midi.Sequence seq = javax.sound.midi.MidiSystem.getSequence(new java.io.File(args[1]));
        if (seq.getDivisionType() != javax.sound.midi.Sequence.PPQ) throw new IllegalArgumentException("only PPQ supported");
        List<javax.sound.midi.MidiEvent> all = new ArrayList<>();
        for (javax.sound.midi.Track t : seq.getTracks()) for (int i = 0; i < t.size(); i++) all.add(t.get(i));
        all.sort(java.util.Comparator.comparingLong(javax.sound.midi.MidiEvent::getTick)); // stable
        long start = Long.parseLong(args[3]);
        double usPerTick = 500000.0 / seq.getResolution(), us = 0;
        long lastTick = 0;
        try (java.io.PrintWriter pw = new java.io.PrintWriter(args[2])) {
            for (javax.sound.midi.MidiEvent e : all) {
                us += (e.getTick() - lastTick) * usPerTick;
                lastTick = e.getTick();
                javax.sound.midi.MidiMessage m = e.getMessage();
                if (m instanceof javax.sound.midi.MetaMessage mm) {
                    if (mm.getType() == 0x51) {
                        byte[] d = mm.getData();
                        usPerTick = (((d[0] & 0xff) << 16) | ((d[1] & 0xff) << 8) | (d[2] & 0xff)) / (double) seq.getResolution();
                    }
                    continue;
                }
                byte[] b = m.getMessage();
                if (m instanceof javax.sound.midi.SysexMessage sx) {
                    byte[] d = sx.getData();
                    b = new byte[d.length + 1];
                    b[0] = (byte) sx.getStatus();
                    System.arraycopy(d, 0, b, 1, d.length);
                }
                StringBuilder sb = new StringBuilder();
                sb.append(start + (long) (us * 24));
                for (byte x : b) sb.append(String.format(" %02X", x & 0xff));
                pw.println(sb);
            }
        }
        System.err.printf("converted, length %.1f s%n", us / 1e6);
    }

    public static void main(String[] args) throws Exception {
        if (args[0].equals("--convert")) {
            convert(args);
            return;
        }
        List<Event> events = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] t = line.trim().split("\\s+");
            if (t.length == 0 || t[0].isEmpty()) continue;
            byte[] d = new byte[t.length - 1];
            for (int i = 1; i < t.length; i++) d[i - 1] = (byte) Integer.parseInt(t[i], 16);
            events.add(new Event(Long.parseLong(t[0]), d));
        }
        long total = Long.parseLong(args[2]);

        Mcu mcu = new Mcu();
        if (!mcu.loadRoms(new Mcu.Config()))
            throw new IllegalStateException("rom load failed");
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(args[1]), 1 << 20)) {
            byte[] buf = new byte[4];
            mcu.offlineSink = (l, r) -> {
                buf[0] = (byte) l; buf[1] = (byte) (l >> 8);
                buf[2] = (byte) r; buf[3] = (byte) (r >> 8);
                try { os.write(buf); } catch (Exception e) { throw new RuntimeException(e); }
            };
            mcu.reset();
            int ei = 0;
            long t0 = System.nanoTime();
            while (mcu.cycles < total) {
                while (ei < events.size() && mcu.cycles >= events.get(ei).cycle) {
                    for (byte b : events.get(ei).data) mcu.MCU_PostUART(b);
                    ei++;
                }
                mcu.step();
            }
            System.err.printf("rendered %d cycles in %.2f s%n", total, (System.nanoTime() - t0) / 1e9);
        }
    }
}
