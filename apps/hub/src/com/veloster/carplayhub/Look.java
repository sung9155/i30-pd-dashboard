package com.veloster.carplayhub;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** My Apps' look, shared by its pages: dark cards, one accent; sizes in px for the 800 x 480 head unit. */
final class Look {
    static final int BG=0xff0e1014,CARD=0xff1e2228,LINE=0xff343a42,TXT=0xffe8eaee,MUT=0xff8c929c,ACC=0xff00788f,ON=0xff46c86e,RED=0xffdc463c;
    private Look(){}

    static GradientDrawable shape(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);if(stroke!=0)d.setStroke(2,stroke);return d;}
    /** Card background with a pressed state, so a tap shows. */
    static StateListDrawable look(int fill,int stroke,int radius){
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed},shape(fill==0?0x33ffffff:0xff2c333c,stroke,radius));s.addState(new int[0],shape(fill,stroke,radius));return s;}
    static TextView label(Context context,String text,float size,int color,boolean bold){
        TextView v=new TextView(context);v.setText(text);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size);v.setTextColor(color);
        if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    /** A list button of the other pages (기본앱, 블루투스, 라디오): a card with its title centred. */
    static TextView button(Context context,String title){
        TextView v=label(context,title,17,TXT,false);v.setGravity(Gravity.CENTER);v.setBackgroundDrawable(look(CARD,LINE,8));v.setClickable(true);
        v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);return v;}
    /** A line of explanation or status under the buttons. */
    static TextView note(Context context,String text){
        TextView v=label(context,text,16,MUT,false);v.setPadding(6,8,6,8);v.setLineSpacing(4,1);return v;}
}
