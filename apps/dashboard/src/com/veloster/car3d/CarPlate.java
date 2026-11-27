package com.veloster.car3d;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * The 3D car's number plates: the owner's plate text (설정 in the 내 차 screen, "12가 3456") drawn as a white Korean
 * long plate with black letters, one small texture for both plates. Empty text = "i30 PD". Only android.graphics:
 * host-tested with the preview's Java2D stand-ins.
 */
public final class CarPlate {
    /** Texture size (power of two for GLES 2.0 mipmaps), the longest text, the preference (pit_vehicle_ui). */
    public static final int W = 256, H = 64, MAX = 10;
    public static final String PREF = "car3d_plate";
    /** The mesh stretches the 4:1 texture over a 520 x 110 mm plate: letters are drawn this much narrower to look right. */
    static final float SQUEEZE = (W / (float) H) / (520f / 110f);
    static final int WHITE = 0xfff4f5f2, INK = 0xff111214, EDGE = 0xff5a5e63;

    private CarPlate() {}

    /** What is drawn: trimmed, inner spaces as one, no control characters, at most MAX characters. */
    public static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX; i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') b.append(' ');
            } else b.append(c);
        }
        return b.toString().trim();
    }

    /** The plate texture for this text (cleaned; blank = "i30 PD"): white plate, thin grey edge, black letters as large as fit. */
    public static Bitmap draw(String text) {
        String t = clean(text);
        if (t.isEmpty()) t = "i30 PD";   // PlatePixels.DEFAULT (shared; this file is host-tested on its own)
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(EDGE);
        c.drawRoundRect(new RectF(2, 2, W - 2, H - 2), 5, 5, p);
        p.setStyle(Paint.Style.FILL); p.setColor(INK);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextScaleX(SQUEEZE);
        p.setTextSize(100);
        float size = Math.min(H * 0.82f, 100 * (W - 20) / Math.max(1f, p.measureText(t)));
        p.setTextSize(size);
        c.drawText(t, W / 2f, H / 2f + size * 0.36f, p);   // digits' middle on the plate's middle
        return bmp;
    }
}
