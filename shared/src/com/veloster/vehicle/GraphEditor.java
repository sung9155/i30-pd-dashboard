package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;

/** Local graph selection and time window only; no vehicle setting commands. */
public final class GraphEditor {
    private final Activity activity;
    private final GraphLayout layout;
    private final SharedPreferences prefs;
    private final Runnable changed;
    private AlertDialog dialog;
    public GraphEditor(Activity a,GraphLayout l,Runnable invalidate){
        activity=a;layout=l;changed=invalidate;prefs=a.getSharedPreferences("nmode_vehicle_graphs",0);list=new TilePicker(a);
        try{layout.window(prefs.getInt("window",60000));}catch(IllegalArgumentException e){}catch(ClassCastException e){}
        for(int i=0;i<GraphLayout.SLOTS;i++)try{layout.signal(i,prefs.getInt("signal_"+i,layout.signal(i)));}catch(IllegalArgumentException e){}catch(ClassCastException e){}
    }
    private final TilePicker list;
    public void dismiss(){AlertDialog old=dialog;dialog=null;if(old!=null)old.dismiss();list.dismiss();}
    private void save(){SharedPreferences.Editor e=prefs.edit().putInt("window",(int)layout.window());for(int i=0;i<GraphLayout.SLOTS;i++)e.putInt("signal_"+i,layout.signal(i));e.apply();changed.run();}
    public void window(long value){dismiss();layout.window(value);save();}
    public void edit(final int slot,VehicleProfile p){   // the grouped list (TilePicker), single signals only
        if(slot<0||slot>=GraphLayout.SLOTS||activity.isFinishing())return;
        dismiss();list.open(UiText.t("그래프 ")+(slot+1)+UiText.t(" · 표시 항목"),p==null?VehicleProfile.PETROL_DCT:p,false,false,layout.signal(slot),
            new TilePicker.Pick(){public void pick(int s){layout.signal(slot,s);save();}});
    }
}
