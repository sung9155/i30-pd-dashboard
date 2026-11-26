package com.veloster.vehicle;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

/**
 * Reads the stock navigation's guidance (Tbt) every POLL_MS while the dashboard is up: the dump of the cluster service
 * `com.hkmc.micom.cluster`, which keeps the last values the navigation sent to the instrument cluster. The service checks
 * android.permission.DUMP; the platform-signed app declares it. A daemon thread of its own: a slow dump never holds a
 * frame. The text is a few KB, well under a pipe's buffer, so the dump writes it all before we read.
 */
final class TbtReader {
    static final String SERVICE="com.hkmc.micom.cluster";
    static final long POLL_MS=1500;
    private static volatile Thread thread;
    private TbtReader(){}

    static synchronized void start(){
        if(thread!=null)return;
        Thread t=new Thread("tbt"){public void run(){
            IBinder b=null;int fails=0;boolean logged=false;
            while(thread==this){
                try{
                    if(b==null)b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,SERVICE);
                    if(b!=null){String text=dump(b);Tbt.set(Tbt.parse(text),SystemClock.elapsedRealtime());
                        if(!logged){logged=true;android.util.Log.i("VehicleDash","TBT dump "+text.length()+" chars, guidance "+Tbt.parse(text).active);}}
                }catch(Throwable e){b=null;if(fails++<3)android.util.Log.w("VehicleDash","TBT dump: "+e);}
                SystemClock.sleep(POLL_MS);
            }
        }};
        t.setDaemon(true);thread=t;t.start();
    }
    static synchronized void stop(){thread=null;}

    private static String dump(IBinder b) throws Exception {
        ParcelFileDescriptor[] p=ParcelFileDescriptor.createPipe();
        try{b.dump(p[1].getFileDescriptor(),new String[0]);}finally{p[1].close();}
        FileInputStream in=new FileInputStream(p[0].getFileDescriptor());ByteArrayOutputStream o=new ByteArrayOutputStream();
        try{byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)o.write(buf,0,n);}finally{in.close();p[0].close();}
        return o.toString("UTF-8");
    }
}
