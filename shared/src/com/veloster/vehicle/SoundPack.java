package com.veloster.vehicle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Ported from jihoon-seo/de22ev-ves (MIT, Copyright (c) 2026 Jihoon Seo): core/src/ves/core/Pack.java,
 * PackLoader.java, Wav.java and PackError.java folded into one class. Dropped: one-shot events (event and pop_rpm lines
 * are accepted and skipped), the speaker-emphasis part of the level matching (it belongs to that project's output
 * stage), the USB import. Pure Java, no Android.
 *
 * A sound pack: loops recorded (or rendered) at fixed rpm in up to four load layers (on-load = pedal, off-load =
 * overrun, and loads between), PCM16 mono 22050 Hz, sorted by rpm. Immutable once loaded; PackVoice plays it.
 *
 * <pre>
 * # comment
 * name   v8 cross-plane
 * cylinders 8
 * idle   800
 * redline 6500
 * aligned yes          (optional: loops start at the same crank angle and hold whole cycles)
 * level -4.5           (optional: level trim in dB, -24..12, at most 2 decimals)
 * loop v8_0800_on.wav rpm=800 load=on [start=0] [end=17640]
 * loop v8_0800_off.wav rpm=800 load=off
 * loop v8_0800_mid.wav rpm=800 load=0.45   (a load layer between off (0) and on (1), up to 3 decimals)
 * </pre>
 * Rules: WAV PCM 16-bit mono 22050 Hz; file names [a-z0-9_-]+.wav (no folders); at most 4 load layers; the top one
 * holds at least 3 loops reaching from at most 1200 rpm to at least 4000; the others are optional, at least 2 loops
 * each; without a load-0 layer the lowest one stands in for it; each loop 0.2..2 s; at most 96 loops and 2,000,000
 * samples; loop points inside the file. Duplicate rpm per layer: the later line wins. Every byte is untrusted: only
 * IOException (with a short reason) leaves the loaders.
 */
public final class SoundPack {
    public static final class Loop {
        public final int rpm;
        /** the loop, start..end of its file: plays end to start without a seam */
        public final short[] pcm;
        Loop(int rpm, short[] pcm) { this.rpm = rpm; this.pcm = pcm; }
    }

    /** where the loader gets the pack's files: the named file (name already validated), at most maxBytes long */
    interface Source { byte[] read(String name, int maxBytes) throws IOException; }

    public static final int RATE = 22050;
    static final int MAX_LOOPS = 96, MAX_SAMPLES = 2000000;
    static final int MIN_LOOP = RATE / 5, MAX_LOOP = RATE * 2, MAX_LOADS = 4;
    static final int MAX_WAV_BYTES = MAX_LOOP * 2 + 64 * 1024, MAX_TXT_BYTES = 16 * 1024;
    static final int MAX_ZIP_BYTES = MAX_SAMPLES * 2 + 1024 * 1024;

    public final String name;
    public final int cylinders, idleRpm, redlineRpm;
    /** every loop starts at the same crank angle and holds whole engine cycles: PackVoice keeps them on one phase */
    public final boolean aligned;
    /** the pack's level trim as a gain (1 without a "level" line) */
    public final double level;
    /** the load of each layer, rising, the first 0; without a load-0 layer layers[0] is the same array as layers[1] */
    public final double[] loads;
    /** the loops of each layer by rising rpm */
    public final Loop[][] layers;

    private SoundPack(String name, int cylinders, int idleRpm, int redlineRpm, boolean aligned, double level, double[] loads, Loop[][] layers) {
        this.name = name; this.cylinders = cylinders; this.idleRpm = idleRpm; this.redlineRpm = redlineRpm;
        this.aligned = aligned; this.level = level; this.loads = loads; this.layers = layers;
    }

    /** a pack folder: pack.txt and its wavs */
    public static SoundPack fromDir(final File dir) throws IOException {
        return load(new Source() {
            public byte[] read(String name, int maxBytes) throws IOException {
                File f = new File(dir, name);
                if (!f.isFile()) throw new IOException("missing " + name);
                if (f.length() > maxBytes) throw new IOException(name + ": too large");
                InputStream in = new FileInputStream(f);
                try { return readAll(in, maxBytes, name); } finally { in.close(); }
            }
        });
    }

    /** a pack zipped flat (pack.txt and the wavs at the top; folders and other files ignored), e.g. a raw resource */
    public static SoundPack fromZip(InputStream zip) throws IOException {
        final Map<String, byte[]> files = new HashMap<String, byte[]>();
        int total = 0;
        try {
            ZipInputStream z = new ZipInputStream(zip);
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                String n = e.getName();
                if (e.isDirectory() || !(n.equals("pack.txt") || n.matches("[a-z0-9_-]{1,60}\\.wav"))) continue;
                byte[] b = readAll(z, Math.min(MAX_WAV_BYTES, MAX_ZIP_BYTES - total), n);
                total += b.length;
                files.put(n, b);
            }
        } catch (IllegalArgumentException e) {   // malformed entry names
            throw new IOException("broken zip: " + e.getMessage());
        }
        return load(new Source() {
            public byte[] read(String name, int maxBytes) throws IOException {
                byte[] b = files.get(name);
                if (b == null) throw new IOException("missing " + name);
                if (b.length > maxBytes) throw new IOException(name + ": too large");
                return b;
            }
        });
    }

    private static byte[] readAll(InputStream in, int maxBytes, String what) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int r; (r = in.read(buf)) > 0; ) {
            if (o.size() + r > maxBytes) throw new IOException(what + ": too large");
            o.write(buf, 0, r);
        }
        return o.toByteArray();
    }

    static SoundPack load(Source src) throws IOException {
        try {
            return parse(src);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("broken pack: " + e);
        }
    }

    private static SoundPack parse(Source src) throws IOException {
        String text = new String(src.read("pack.txt", MAX_TXT_BYTES), "UTF-8");
        String name = null;
        int cyl = 4, idle = 800, redline = 6500;
        boolean aligned = false;
        double level = 1;
        TreeMap<Integer, List<Loop>> byLoad = new TreeMap<Integer, List<Loop>>(); // load in thousandths
        int lineNo = 0, total = 0, loops = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String l = raw.trim();
            if (l.length() == 0 || l.startsWith("#")) continue;
            String[] f = l.split("\\s+");
            String k = f[0];
            if (k.equals("name")) name = l.substring(4).trim();
            else if (k.equals("cylinders")) cyl = num(f, 1, 1, 16, lineNo);
            else if (k.equals("idle")) idle = num(f, 1, 300, 2000, lineNo);
            else if (k.equals("redline")) redline = num(f, 1, 2000, 12000, lineNo);
            else if (k.equals("aligned")) aligned = f.length > 1 && f[1].equals("yes");
            else if (k.equals("level")) level = levelDb(f, lineNo);
            else if (k.equals("event") || k.equals("pop_rpm")) continue; // ponytail: one-shot events not played here
            else if (k.equals("loop")) {
                if (loops++ >= MAX_LOOPS) throw new IOException("more than " + MAX_LOOPS + " loops");
                if (f.length < 2 || !f[1].matches("[a-z0-9_-]{1,60}\\.wav")) throw new IOException("line " + lineNo + ": bad file name");
                int rpm = -1, start = 0, end = -1, load = -1;
                for (int i = 2; i < f.length; i++) {
                    int eq = f[i].indexOf('=');
                    if (eq <= 0) throw new IOException("line " + lineNo + ": " + f[i]);
                    String key = f[i].substring(0, eq), v = f[i].substring(eq + 1);
                    if (key.equals("rpm")) rpm = parseInt(v, 300, 12000, lineNo);
                    else if (key.equals("load") && v.matches("on|off|0(\\.[0-9]{1,3})?|1(\\.0{1,3})?"))
                        load = v.equals("on") ? 1000 : v.equals("off") ? 0 : (int) Math.round(Double.parseDouble(v) * 1000);
                    else if (key.equals("start")) start = parseInt(v, 0, Integer.MAX_VALUE, lineNo);
                    else if (key.equals("end")) end = parseInt(v, 1, Integer.MAX_VALUE, lineNo);
                    else throw new IOException("line " + lineNo + ": " + f[i]);
                }
                if (rpm < 0 || load < 0) throw new IOException("line " + lineNo + ": needs rpm= and load=on|off|0..1");
                short[] pcm = mono16(src.read(f[1], MAX_WAV_BYTES), RATE, f[1]);
                if (end < 0) end = pcm.length;
                if (start >= end || end > pcm.length) throw new IOException(f[1] + ": loop points outside the file");
                int n = end - start;
                if (n < MIN_LOOP || n > MAX_LOOP) throw new IOException(f[1] + ": loop must be 0.2 to 2 s");
                total += n;
                if (total > MAX_SAMPLES) throw new IOException("pack larger than " + MAX_SAMPLES + " samples");
                List<Loop> layer = byLoad.get(load);
                if (layer == null) {
                    if (byLoad.size() >= MAX_LOADS) throw new IOException("line " + lineNo + ": more than " + MAX_LOADS + " loads");
                    byLoad.put(load, layer = new ArrayList<Loop>());
                }
                layer.add(new Loop(rpm, Arrays.copyOfRange(pcm, start, end)));
            } else throw new IOException("line " + lineNo + ": unknown key " + k);
        }
        if (name == null || name.length() == 0 || name.length() > 40) throw new IOException("needs a name (1-40 characters)");
        if (idle >= redline) throw new IOException("idle must be below redline");
        if (byLoad.isEmpty() || byLoad.lastKey() == 0) throw new IOException("needs at least 3 on-load loops");
        // the layers by rising load; without a load-0 layer the lowest stands in for it
        int n = byLoad.size(), base = byLoad.firstKey() == 0 ? 0 : 1, i = base;
        double[] loads = new double[n + base];
        Loop[][] layers = new Loop[n + base][];
        for (Map.Entry<Integer, List<Loop>> e : byLoad.entrySet()) {
            loads[i] = e.getKey() / 1000.0;
            layers[i++] = sorted(e.getValue());
        }
        if (base == 1) layers[0] = layers[1];
        Loop[] a = layers[layers.length - 1];
        if (a.length < 3) throw new IOException("needs at least 3 on-load loops");
        if (a[0].rpm > 1200 || a[a.length - 1].rpm < 4000) throw new IOException("on-load loops must reach from <=1200 to >=4000 rpm");
        for (int k = base; k < layers.length - 1; k++)
            if (layers[k].length == 1) throw new IOException("needs 0 or at least 2 loops at load " + loads[k]);
        return new SoundPack(name, cyl, idle, redline, aligned, level, loads, layers);
    }

    private static Loop[] sorted(List<Loop> l) {
        List<Loop> u = new ArrayList<Loop>();
        for (Loop x : l) {
            for (int i = u.size() - 1; i >= 0; i--) if (u.get(i).rpm == x.rpm) u.remove(i);
            u.add(x);
        }
        Loop[] a = u.toArray(new Loop[0]);
        Arrays.sort(a, new Comparator<Loop>() {
            public int compare(Loop x, Loop y) { return x.rpm - y.rpm; }
        });
        return a;
    }

    private static int num(String[] f, int i, int lo, int hi, int line) throws IOException {
        if (f.length <= i) throw new IOException("line " + line + ": missing value");
        return parseInt(f[i], lo, hi, line);
    }

    /** "level <dB>": -24..12 dB, plain decimal (no exponent, NaN or infinity), as a gain */
    private static double levelDb(String[] f, int line) throws IOException {
        if (f.length != 2) throw new IOException("line " + line + ": level takes one value in dB");
        if (!f[1].matches("[-+]?[0-9]{1,2}(\\.[0-9]{1,2})?")) throw new IOException("line " + line + ": not a level in dB: " + f[1]);
        double db = Double.parseDouble(f[1]);
        if (db < -24 || db > 12) throw new IOException("line " + line + ": level " + f[1] + " out of range (-24..12 dB)");
        return Math.pow(10, db / 20);
    }

    private static int parseInt(String v, int lo, int hi, int line) throws IOException {
        if (!v.matches("[0-9]{1,9}")) throw new IOException("line " + line + ": not a number: " + v);
        int x = Integer.parseInt(v);
        if (x < lo || x > hi) throw new IOException("line " + line + ": " + v + " out of range");
        return x;
    }

    // ---- WAV: RIFF/WAVE PCM 16-bit mono at a given rate; anything else is refused

    static short[] mono16(byte[] b, int rate, String what) throws IOException {
        if (b.length < 12 || !tag(b, 0, "RIFF") || !tag(b, 8, "WAVE")) throw new IOException(what + ": not a WAV file");
        int pos = 12;
        boolean fmt = false;
        while (pos + 8 <= b.length) {
            int size = le32(b, pos + 4);
            if (size < 0) throw new IOException(what + ": bad chunk");
            int body = pos + 8;
            if (tag(b, pos, "fmt ")) {
                if (size < 16 || body + 16 > b.length) throw new IOException(what + ": bad fmt chunk");
                int format = le16(b, body), ch = le16(b, body + 2), sr = le32(b, body + 4), bits = le16(b, body + 14);
                if (format != 1) throw new IOException(what + ": not PCM");
                if (ch != 1) throw new IOException(what + ": must be mono");
                if (sr != rate) throw new IOException(what + ": must be " + rate + " Hz, is " + sr);
                if (bits != 16) throw new IOException(what + ": must be 16-bit");
                fmt = true;
            } else if (tag(b, pos, "data")) {
                if (!fmt) throw new IOException(what + ": data before fmt");
                long end = (long) body + size;
                if (end > b.length) end = b.length; // tolerate a truncated last chunk
                int n = (int) ((end - body) / 2);
                short[] s = new short[n];
                for (int i = 0; i < n; i++) s[i] = (short) le16(b, body + 2 * i);
                return s;
            }
            long next = (long) body + size + (size & 1);
            if (next > Integer.MAX_VALUE) break;
            pos = (int) next;
        }
        throw new IOException(what + ": no audio data");
    }

    private static boolean tag(byte[] b, int at, String t) {
        if (at + 4 > b.length) return false;
        for (int i = 0; i < 4; i++) if (b[at + i] != t.charAt(i)) return false;
        return true;
    }

    private static int le16(byte[] b, int at) { return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8; }

    private static int le32(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8 | (b[at + 2] & 0xff) << 16 | (b[at + 3] & 0xff) << 24;
    }
}
