package com.veloster.carplayhub;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** The floating shortcut's face: a small speedometer (white arc, red redline, red needle) on a dark disc, no text.
 *  Plain Canvas drawing so the Java2D preview shim can render it too. */
final class DashBadge {
    private DashBadge(){}
    private static final Paint P=new Paint(3);
    private static final RectF ARC=new RectF();

    static void draw(Canvas c,float s,boolean pressed){
        float m=s/2;
        P.setStyle(Paint.Style.FILL);P.setColor(0xD0101318);c.drawCircle(m,m,m-1,P);
        P.setStyle(Paint.Style.STROKE);P.setStrokeCap(Paint.Cap.BUTT);
        P.setStrokeWidth(Math.max(1,s*0.025f));P.setColor(pressed?0xA0FFFFFF:0x38FFFFFF);c.drawCircle(m,m,m-1-s*0.0125f,P);
        float r=s*0.29f;ARC.set(m-r,m-r,m+r,m+r);
        P.setStrokeWidth(s*0.07f);
        P.setColor(0xF0FFFFFF);c.drawArc(ARC,135,212,false,P);   // 0..80 % of the dial
        P.setColor(0xFFE5322D);c.drawArc(ARC,351,54,false,P);    // redline, after a 4 degree gap
        double a=Math.toRadians(135+270*0.68);float n=r*0.86f;   // needle a bit past the middle
        P.setStrokeCap(Paint.Cap.ROUND);P.setStrokeWidth(s*0.055f);P.setColor(0xFFFF4A3D);
        c.drawLine(m,m,m+(float)Math.cos(a)*n,m+(float)Math.sin(a)*n,P);
        P.setStyle(Paint.Style.FILL);P.setColor(0xFFFFFFFF);c.drawCircle(m,m,s*0.065f,P);
    }
}
