package com.veloster.vehicle;

/**
 * Ported from jihoon-seo/de22ev-ves (MIT, Copyright (c) 2026 Jihoon Seo): core/src/ves/core/PackVoice.java (mix,
 * layer, playAligned, play, gains) and EngineModel.java's drive gain (softened at idle, see render). Dropped: the EV engine
 * model, one-shot events and pops, the output stage's speaker filters and makeup gain (here a soft clip like
 * EngineSynth's), regen. Added: the loops are resampled once to the output rate (band-limited, around the loop so the
 * seam stays clean), and set()/render() mirror EngineSynth.
 *
 * Plays a SoundPack from the car's rpm and pedal: for each load layer the two loops nearest the rpm are crossfaded on
 * log rpm, each pitch-shifted to the rpm (linear interpolation). The two layers around the effective load
 * e = smoothstep(load) are blended. Aligned packs (rendered: every loop starts at the same crank angle) are played on
 * one shared engine-cycle phase, so neighbouring loops sum in phase: linear crossfades, no comb-filter dips; others
 * crossfade at constant power with free-running loops. Every loop is level-matched to RMS LOOP_RMS times the pack's
 * level trim. Parameters are interpolated across each render block; loop indices and weights are worked out every SEG
 * samples. Allocation-free after construction; one thread.
 */
public final class PackVoice {
    static final int SEG = 16;
    /** every loop brought to this RMS (gain at most 16x): the drive gain shapes loudness, the pack gives the timbre */
    static final double LOOP_RMS = 0.30, MAX_LOOP_GAIN = 16;
    /** into the soft clip, as upstream's output stage */
    static final double DRIVE = 1.6;
    /** 3+ layer packs: time constants of the effective layer load going up and down */
    static final double LAYER_UP_S = 0.4, LAYER_DOWN_S = 0.08;

    private final SoundPack pack;
    private final int sr;
    private final short[][][] pcm; // per layer, per loop: the loop at the output rate
    private final double[][] g, ph; // per layer, per loop: level-matching gain, free-running read position
    private double cyc; // engine cycles since start (aligned packs)
    private double tRpm = 800, tLoad, pRpm = 800, pLoad, master, lastE, eS;
    private boolean running, eStarted;
    private final float[] seg = new float[SEG];

    public PackVoice(int outRate, SoundPack pack) {
        this.pack = pack;
        sr = outRate > 0 ? outRate : SoundPack.RATE;
        int L = pack.layers.length;
        pcm = new short[L][][];
        g = new double[L][];
        ph = new double[L][];
        for (int k = 0; k < L; k++) {
            ph[k] = new double[pack.layers[k].length];
            if (k > 0 && pack.layers[k] == pack.layers[k - 1]) { pcm[k] = pcm[k - 1]; g[k] = g[k - 1]; continue; }
            SoundPack.Loop[] lp = pack.layers[k];
            pcm[k] = new short[lp.length][];
            g[k] = new double[lp.length];
            for (int i = 0; i < lp.length; i++) {
                pcm[k][i] = resample(lp[i].pcm, SoundPack.RATE, sr);
                double ss = 0;
                for (short v : lp[i].pcm) ss += (double) v * v;
                double rms = Math.sqrt(ss / Math.max(1, lp[i].pcm.length)) / 32768.0;
                g[k][i] = rms < 1e-6 ? 0 : Math.min(MAX_LOOP_GAIN, LOOP_RMS / rms) * pack.level;
            }
        }
    }

    public SoundPack pack() { return pack; }

    /** NaN rpm (engine off, stale data) fades to silence; load = pedal 0..1. Takes effect over the next render block. */
    public void set(double rpm, double load) {
        running = !Double.isNaN(rpm) && rpm > 300;
        if (running) tRpm = Math.min(12000, rpm);
        tLoad = Double.isNaN(load) ? 0 : Math.max(0, Math.min(1, load));
    }

    public boolean running() { return running; }

    /**
     * Writes out[off..off+n) with -1..1 samples at the given master volume (0 = silent, 1 = as designed, up to 2
     * louder: above 1 it is driven harder into the soft clip), as EngineSynth.render.
     */
    public void render(float[] out, int off, int n, double volume) {
        if (n <= 0) return;
        double ke = 1 - Math.exp(-1 / (.15 * sr)); // fade in/out over ~150 ms, as EngineSynth
        if (!running && master < 1e-5) {
            master = 0;
            for (int i = 0; i < n; i++) out[off + i] = 0;
            pRpm = tRpm;
            pLoad = tLoad;
            return;
        }
        double drive = DRIVE * (volume > 1 ? volume : 1), scale = volume < 1 ? Math.max(0, volume) : 1, tm = running ? 1 : 0;
        for (int pos = 0; pos < n; pos += SEG) {
            int m = Math.min(SEG, n - pos);
            double f = (pos + m * 0.5) / n, rpm = pRpm + (tRpm - pRpm) * f, load = pLoad + (tLoad - pLoad) * f;
            mix(seg, m, rpm, load);
            // upstream EngineModel's 0.45 + 0.55 load left idle ~10 dB above EngineSynth: a lower floor and EngineSynth's rpm factor
            double level = Math.min(1, rpm / 800) * (0.25 + 0.75 * load) * (.60 + .40 * Math.min(1, rpm / 6000));
            for (int i = 0; i < m; i++) {
                master += (tm - master) * ke;
                double y = seg[i] * level * master * drive;
                if (y > 3) y = 3; else if (y < -3) y = -3;
                double y2 = y * y;
                out[off + pos + i] = (float) (y * (27 + y2) / (27 + 9 * y2) * scale);
            }
        }
        pRpm = tRpm;
        pLoad = tLoad;
    }

    /** the effective layer load the last mix used (tests) */
    double layerLoad() { return lastE; }

    private void mix(float[] out, int n, double rpm, double load) {
        double l = load;
        double[] lam = pack.loads;
        int top = lam.length - 1, k = 0;
        double e = clamp(l * l * (3 - 2 * l), lam[0], lam[top]); // smoothstep, held to the layers' range
        if (top >= 2) {
            // 3+ layers: up slowly, down fast (on a pull-away the rpm rises first and the loaded timbre follows)
            if (!eStarted) { eS = e; eStarted = true; }
            else eS += (e - eS) * (1 - Math.exp(-n / (sr * (e > eS ? LAYER_UP_S : LAYER_DOWN_S))));
            e = eS;
        }
        lastE = e;
        while (k < top - 1 && e >= lam[k + 1]) k++;
        double w = (e - lam[k]) / (lam[k + 1] - lam[k]); // two layers: w = e exactly
        boolean al = pack.aligned;
        double aHi = al ? w : Math.sqrt(w), aLo = al ? 1 - w : Math.sqrt(1 - w);
        for (int i = 0; i < n; i++) out[i] = 0;
        double dc = rpm / 120.0 / sr; // engine cycles per sample (4-stroke)
        if (aHi > 1e-3) layer(k + 1, rpm, aHi, out, n, al, dc);
        if (aLo > 1e-3) layer(k, rpm, aLo, out, n, al, dc);
        cyc += n * dc;
    }

    /** adds amp x (the two loops nearest rpm) into out[0 .. n) */
    private void layer(int k, double rpm, double amp, float[] out, int n, boolean aligned, double dc) {
        SoundPack.Loop[] L = pack.layers[k];
        int last = L.length - 1, i = 0;
        while (i < last - 1 && rpm >= L[i + 1].rpm) i++;
        int j = Math.min(i + 1, last);
        double w;
        if (rpm <= L[0].rpm) w = 0;
        else if (rpm >= L[last].rpm) {
            i = last - 1 < 0 ? 0 : last - 1;
            j = last;
            w = 1;
        } else w = (Math.log(rpm) - Math.log(L[i].rpm)) / (Math.log(L[j].rpm) - Math.log(L[i].rpm));
        w = clamp(w, 0, 1);
        double a = (aligned ? 1 - w : Math.cos(w * Math.PI / 2)) * amp, b = (aligned ? w : Math.sin(w * Math.PI / 2)) * amp;
        if (aligned) {
            if (a > 1e-4) playAligned(pcm[k][i], L[i].rpm, a * g[k][i], out, n, dc);
            if (b > 1e-4 && j != i) playAligned(pcm[k][j], L[j].rpm, b * g[k][j], out, n, dc);
            return;
        }
        if (a > 1e-4) play(pcm[k][i], L[i].rpm, ph[k], i, rpm, a * g[k][i], out, n);
        if (b > 1e-4 && j != i) play(pcm[k][j], L[j].rpm, ph[k], j, rpm, b * g[k][j], out, n);
    }

    /** position = the shared crank phase within the loop's whole cycles */
    private void playAligned(short[] s, int loopRpm, double amp, float[] out, int n, double dc) {
        int len = s.length;
        double spc = 120.0 * sr / loopRpm; // samples per engine cycle in this loop
        // whole cycles in the loop; a length rounded down by under a sample still holds its last cycle
        double k = len / spc;
        long kr = Math.round(k);
        int cycles = Math.max(1, (int) ((kr - k) * spc < 1.0 && kr > k - 1e-9 ? kr : Math.floor(k + 1e-6)));
        double scale = amp / 32768.0, c = cyc % cycles;
        for (int i = 0; i < n; i++) {
            double p = c * spc;
            int i0 = (int) p;
            if (i0 >= len) i0 = len - 1;
            double fr = p - i0;
            int i1 = i0 + 1 >= len ? 0 : i0 + 1;
            out[i] += (float) ((s[i0] + (s[i1] - s[i0]) * fr) * scale);
            c += dc;
            if (c >= cycles) c -= cycles;
        }
    }

    private static void play(short[] s, int loopRpm, double[] ph, int k, double rpm, double amp, float[] out, int n) {
        int len = s.length;
        double step = clamp(rpm / loopRpm, 0.5, 2.0), p = ph[k], scale = amp / 32768.0;
        for (int i = 0; i < n; i++) {
            int i0 = (int) p;
            double fr = p - i0;
            int i1 = i0 + 1 == len ? 0 : i0 + 1;
            out[i] += (float) ((s[i0] + (s[i1] - s[i0]) * fr) * scale);
            p += step;
            if (p >= len) p -= len;
        }
        ph[k] = p;
    }

    /**
     * A loop at rate from, resampled to rate to as a loop: round(len x to / from) samples spanning exactly the same
     * stretch, windowed-sinc low-pass at 0.45 x the lower rate, taps read around the end so the seam stays continuous.
     */
    static short[] resample(short[] s, int from, int to) {
        if (from == to) return s;
        int len = s.length, m = Math.max(1, (int) Math.round((double) len * to / from));
        double step = (double) len / m, fc = 0.45 * Math.min(from, to) / from; // cycles per input sample
        int half = (int) Math.ceil(8 / fc / 2) + 1; // about 8 cutoff wavelengths of taps
        int res = 256; // kernel table: 1/256 sample steps (no sin per tap; the head unit builds this at load)
        double[] h = new double[half * res + 1];
        for (int q = 0; q < h.length; q++) {
            double d = (double) q / res, a = 2 * Math.PI * fc * d;
            h[q] = (q == 0 ? 1 : Math.sin(a) / a) * (0.42 + 0.5 * Math.cos(Math.PI * d / half) + 0.08 * Math.cos(2 * Math.PI * d / half)); // Blackman
        }
        short[] o = new short[m];
        for (int j = 0; j < m; j++) {
            double x = j * step, acc = 0, wsum = 0;
            int c = (int) Math.floor(x);
            for (int t = c - half + 1; t <= c + half; t++) {
                int q = (int) Math.round(Math.abs(x - t) * res);
                if (q >= h.length) continue;
                int idx = ((t % len) + len) % len;
                acc += s[idx] * h[q];
                wsum += h[q];
            }
            double v = wsum != 0 ? acc / wsum : 0;
            o[j] = (short) Math.max(-32768, Math.min(32767, Math.round(v)));
        }
        return o;
    }

    private static double clamp(double x, double lo, double hi) { return x < lo ? lo : x > hi ? hi : x; }
}
