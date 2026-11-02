package com.veloster.vehicle;

import com.veloster.i18n.UiText;
import android.content.Context;
import android.content.Intent;

/**
 * OEM USB debug switch. No shell, TCP ADB or settings writes.
 * Head units with the ium feature switch at once through MiscManager.setSystem(5/6). Without it (i30 PD) setSystem only
 * stores persist.sys.dipo.state, which rsd applies at the next boot (and keeps applying: CarPlay stays off). So there
 * both buttons send the Engineer Mode Factory Test lines (65 01 on, 65 00 off): the system's MicomFactoryService passes
 * each one to the micom, which flips the port at once, and nothing persistent changes, so a restart returns to CarPlay.
 * OFF also stores carplay through setSystem(6), undoing a debug state an older build of this button saved.
 */
public final class UsbDebugControl {
    /** The PD procedure's Factory Test lines: enter factory mode, USB debug on (65 01) or off (65 00), leave factory mode. */
    static byte[][] factoryLines(boolean enabled){return new byte[][]{{0x0A,0x01},{0x65,(byte)(enabled?1:0)},{0x0A,0x02}};}

    public static boolean request(Context context,boolean enabled)throws Exception {
        if(!immediate()){
            for(byte[] line:factoryLines(enabled)){
                context.sendBroadcast(new Intent("intent.action.FACTORY_TEST").putExtra("intent.extra.FACTORY_TEST",line));
                Thread.sleep(800);   // the engineer menu sends one typed line at a time
            }
            if(enabled)return true;
        }
        Class<?> type=Class.forName("com.hkmc.misc.MiscManager");
        Object manager=type.getConstructor(Context.class).newInstance(context);
        Object accepted=type.getMethod("setSystem",Integer.TYPE).invoke(manager,Integer.valueOf(enabled?5:6));
        return Boolean.TRUE.equals(accepted);
    }
    /** True when setSystem switches the port at once (ium); otherwise the Factory Test lines do it. */
    public static boolean immediate(){
        try{return Boolean.TRUE.equals(Class.forName("com.hkmc.Feature").getMethod("hasFeature",String.class).invoke(null,"com.hkmc.software.ium"));}
        catch(Exception e){return false;}
    }
    public static String status() {
        try {
            if(prop("sys.usb.config").contains("adb"))return UiText.t("설정: ON");   // what the port runs now, either path
            if(!immediate()&&"debug".equals(prop("persist.sys.dipo.state")))return UiText.t("다음 재시작 때 디버깅으로 켜짐 (OFF로 되돌리기)");
            if(prop("sys.usb.config").length()>0)return UiText.t("설정: OFF");
        } catch(Exception e) { /* Unknown is distinct from OFF. */ }
        return UiText.t("설정 상태 확인 불가");
    }
    private static String prop(String key)throws Exception{
        return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");
    }
}
