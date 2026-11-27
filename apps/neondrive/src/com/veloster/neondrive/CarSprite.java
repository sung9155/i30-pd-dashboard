package com.veloster.neondrive;

import com.veloster.vehicle.ArcadeCarFrames;

/**
 * The pixel car Neon Drive drives, seen from behind: a sheet of frames (columns, one per view angle) x ROWS layers.
 * Rows: 0 the car, 1 brake lamps lit, 2 left / 3 right turn lamp lit, WHEEL_ROW.. wheel phases, TAIL_ROW tail lamps on,
 * UNLIT_ROW lamps off. Swappable: upstream drew its Veloster N from a generated sheet (apps/neondrive/art/SpriteForge.java
 * there, not shipped here); this build makes the i30 N from the Arcade view's frames (ArcadeCarFrames), painted in the
 * chosen CarPaint. No Android: host-tested; the renderer turns the pixels into a bitmap.
 */
public final class CarSprite {
    public static final int ROWS = 9, WHEEL_ROW = 4, WHEEL_PHASES = 3, TAIL_ROW = 7, UNLIT_ROW = 8;
    public final int frames, cellW, cellH;
    /** Screen pixels per sprite pixel. */
    public final float scale;
    /** View angle per frame, degrees: negative shows the right flank (turning right), positive the left. */
    public final int[] angle;
    /** Per frame, cell pixels: left lamp x,y, right lamp x,y, left pipe x,y, right pipe x,y, car top, car bottom, stop lamp x,y (-1 if none),
     *  plate face left, right, top, bottom (-1 if none; drawn on by NeonRenderer with PlatePixels). */
    public final int[][] points;
    /** ARGB, (frames * cellW) x (ROWS * cellH). */
    public final int[] pixels;

    private CarSprite(int frames, int cellW, int cellH, float scale, int[] angle, int[][] points, int[] pixels) {
        this.frames = frames; this.cellW = cellW; this.cellH = cellH; this.scale = scale; this.angle = angle; this.points = points; this.pixels = pixels;
    }

    public int sheetW() { return frames * cellW; }
    public int sheetH() { return ROWS * cellH; }

    /** Frame closest to a view angle in degrees (negative shows the right flank), clamped to the outermost. */
    public int frameFor(float a) {
        int best = 0;
        for (int i = 1; i < frames; i++) if (Math.abs(angle[i] - a) < Math.abs(angle[best] - a)) best = i;
        return best;
    }

    /** Arcade frame code of the chrome; in the bottom quarter it is the exhaust tips. */
    static final int CHROME = 15;

    /**
     * The i30 N from the Arcade view's 13 frames (-24..24 deg; + = turning left, the same sign as {@link #angle}),
     * cropped to the frames' union box, in paint body (CarPaint.body(): body, highlight, dark side, shadow line).
     * There is no wheel animation: the Arcade's rear view hides the spokes, so the wheel rows stay empty.
     */
    public static CarSprite i30(int[] body) {
        int n = ArcadeCarFrames.FRAMES.length, x0 = ArcadeCarFrames.W, y0 = ArcadeCarFrames.H, x1 = -1, y1 = -1;
        for (int f = 0; f < n; f++) for (int y = 0; y < ArcadeCarFrames.H; y++) for (int x = 0; x < ArcadeCarFrames.W; x++)
            if (ArcadeCarFrames.code(f, x, y) != 0) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
        int cw = x1 - x0 + 1, ch = y1 - y0 + 1, sw = n * cw;
        int[] px = new int[sw * ROWS * ch];
        int[][] pts = new int[n][];
        int b = body[0], bh = body[1], bd = body[2], bs = body[3];
        for (int f = 0; f < n; f++) {
            long[] sum = new long[6];   // left lamp x,y,n, right lamp x,y,n
            long sx = 0, sy = 0, sn = 0;
            int top = ch, bottom = -1, mid = 0, cnt = 0, px0 = cw, px1 = -1, py0 = ch, py1 = -1;
            for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) if (ArcadeCarFrames.code(f, x + x0, y + y0) != 0) { mid += x; cnt++; }
            mid = cnt == 0 ? cw / 2 : mid / cnt;
            for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
                int k = ArcadeCarFrames.code(f, x + x0, y + y0);
                if (k == 0) continue;
                top = Math.min(top, y); bottom = Math.max(bottom, y);
                if (k == 20 || k == 21) { px0 = Math.min(px0, x); px1 = Math.max(px1, x); py0 = Math.min(py0, y); py1 = Math.max(py1, y); }
                int[] e = ArcadeCarFrames.CODES[k - 1];
                int at = f * cw + x;
                px[at + y * sw] = ArcadeCarFrames.colour(k, b, bh, bd, bs, false, false, false, false);
                boolean turn = e[0] == 4, lamp = e[0] == 3 || turn && ArcadeCarFrames.CODES[e[2] - 1][0] == 3;
                if (lamp) {
                    px[at + (y + ch) * sw] = ArcadeCarFrames.colour(k, b, bh, bd, bs, true, false, false, false);
                    px[at + (y + TAIL_ROW * ch) * sw] = ArcadeCarFrames.colour(k, b, bh, bd, bs, false, false, false, true);
                    px[at + (y + UNLIT_ROW * ch) * sw] = px[at + y * sw];
                    int lampKind = (turn ? ArcadeCarFrames.CODES[e[2] - 1] : e)[1];
                    if (lampKind == 2) { sx += x; sy += y; sn++; }   // the high-mount stop lamp
                    else if (lampKind <= 1 || turn) { int s = x < mid ? 0 : 3; sum[s] += x; sum[s + 1] += y; sum[s + 2]++; }
                }
                if (turn) px[at + (y + (e[1] < 0 ? 2 : 3) * ch) * sw] = ArcadeCarFrames.colour(k, b, bh, bd, bs, false, true, true, false);
            }
            int[] p = new int[16];
            for (int s = 0; s < 2; s++) {
                long nx = sum[s * 3 + 2];
                p[s * 2] = nx > 0 ? (int) (sum[s * 3] / nx) : -1; p[s * 2 + 1] = nx > 0 ? (int) (sum[s * 3 + 1] / nx) : -1;
            }
            // Exhaust tips: the chrome in the bottom quarter only (the badge and the bumper strip are chrome too).
            int lowFrom = top + (bottom - top) * 3 / 4;
            for (int side = 0; side < 2; side++) {
                long qx = 0, qy = 0, qn = 0;
                for (int y = Math.max(0, lowFrom); y <= bottom; y++) for (int x = 0; x < cw; x++)
                    if (ArcadeCarFrames.code(f, x + x0, y + y0) == CHROME && (x < mid) == (side == 0)) { qx += x; qy += y; qn++; }
                int lx = p[side * 2] < 0 ? (side == 0 ? mid - cw / 4 : mid + cw / 4) : p[side * 2];
                p[4 + side * 2] = qn > 0 ? (int) (qx / qn) : lx;
                p[5 + side * 2] = qn > 0 ? (int) (qy / qn) : bottom - 3;   // ponytail: no chrome found -> under the lamp
                if (p[side * 2] < 0) { p[side * 2] = lx; p[side * 2 + 1] = top + (bottom - top) / 3; }
            }
            p[8] = top; p[9] = bottom;
            p[10] = sn > 0 ? (int) (sx / sn) : -1; p[11] = sn > 0 ? (int) (sy / sn) : -1;
            p[12] = px1 < 0 ? -1 : px0; p[13] = px1; p[14] = px1 < 0 ? -1 : py0; p[15] = py1;
            pts[f] = p;
        }
        return new CarSprite(n, cw, ch, 3f, ArcadeCarFrames.YAWS.clone(), pts, px);
    }
}
