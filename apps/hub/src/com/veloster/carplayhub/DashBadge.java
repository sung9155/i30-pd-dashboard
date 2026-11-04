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
        // At rest the face is low-contrast (grey arc, dull red) so it stays out of the way under the overlay's faint
        // alpha; a touch brings the real white dial and bright red back. The caller also raises the view alpha when pressed.
        int arc=pressed?0xF0FFFFFF:0xFF60666C,redline=pressed?0xFFE5322D:0xFF6E403C,needle=pressed?0xFFFF4A3D:0xFF7E4A44,hub=pressed?0xFFFFFFFF:0xFF60666C;
        float m=s/2;
        P.setStyle(Paint.Style.FILL);P.setColor(0xD0101318);c.drawCircle(m,m,m-1,P);
        P.setStyle(Paint.Style.STROKE);P.setStrokeCap(Paint.Cap.BUTT);
        P.setStrokeWidth(Math.max(1,s*0.025f));P.setColor(pressed?0xA0FFFFFF:0x28FFFFFF);c.drawCircle(m,m,m-1-s*0.0125f,P);
        float r=s*0.29f;ARC.set(m-r,m-r,m+r,m+r);
        P.setStrokeWidth(s*0.07f);
        P.setColor(arc);c.drawArc(ARC,135,212,false,P);   // 0..80 % of the dial
        P.setColor(redline);c.drawArc(ARC,351,54,false,P);   // redline, after a 4 degree gap
        double a=Math.toRadians(135+270*0.68);float n=r*0.86f;   // needle a bit past the middle
        P.setStrokeCap(Paint.Cap.ROUND);P.setStrokeWidth(s*0.055f);P.setColor(needle);
        c.drawLine(m,m,m+(float)Math.cos(a)*n,m+(float)Math.sin(a)*n,P);
        P.setStyle(Paint.Style.FILL);P.setColor(hub);c.drawCircle(m,m,s*0.065f,P);
    }
}
