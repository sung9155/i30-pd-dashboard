package com.veloster.vehicle;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.util.ArrayList;
import java.util.List;

/**
 * Active OBD-II mode 01 poller over the head unit's MICOM diagnostic channel (the same channel {@link DtcReader}
 * uses). READ ONLY: it asks the engine ECU for a few standard PIDs the hscan broadcast does not carry — ambient
 * temperature (0x46), fuel level (0x2F), catalyst temperature (0x3C) and absolute load (0x43) — a couple of times a
 * second and feeds them to a sink. Only on pd_kr, only while the gate is open (ignition on), and it steps aside while
 * a trouble-code read runs (the channel is shared). The engine ECU answered these in the 2026-11-02 probe.
 */
public final class ObdLive {
    public interface Sink { void value(int pid,int a,int b); }
    public interface Gate { boolean open(); }
    /** DtcReader raises this while it reads trouble codes; the poller pauses so the two never share the channel. */
    public static volatile boolean diagBusy;

    private static final String SERVICE="com.hkmc.micom.hscanmicomservice";
    private static final String SIFACE="com.hkmc.hscan.IHscanMicomService";
    private static final String LIFACE="com.hkmc.hscan.IHscanMicomListener";
    private static final int EMS=0x27;
    private static final int[] PIDS={0x46,0x2f,0x3c,0x43};
    private static final long REPLY_MS=300;

    private final Object lock=new Object();
    private final List<byte[]> replies=new ArrayList<byte[]>();
    private int expectEcu=-1;
    private volatile boolean running;
    private Thread thread;

    public void start(final Sink sink,final Gate gate){
        if(running||!"pd_kr".equals(android.os.Build.PRODUCT))return;
        running=true;
        thread=new Thread(new Runnable(){public void run(){loop(sink,gate);}},"n-obd-poll");
        thread.setDaemon(true);thread.start();
    }
    public void stop(){running=false;Thread t=thread;if(t!=null)t.interrupt();}

    private void loop(Sink sink,Gate gate){
        IBinder svc;
        try{svc=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,SERVICE);}
        catch(Throwable e){return;}
        if(svc==null)return;
        Binder listener=new Listener(),token=new Binder();
        try{call(svc,1,listener,token);}catch(Throwable e){return;}
        try{
            while(running){
                if(diagBusy||gate==null||!gate.open()){nap(500);continue;}
                for(int pid:PIDS){
                    if(!running)return;
                    if(diagBusy)break;
                    byte[] r=ask(svc,EMS,new byte[]{0x01,(byte)pid});
                    if(r!=null&&r.length>=3&&(r[0]&255)==0x41&&(r[1]&255)==pid)
                        sink.value(pid,r[2]&255,r.length>3?r[3]&255:0);
                    nap(220);
                }
                nap(500);
            }
        }finally{try{call(svc,2,listener,token);}catch(Throwable e){}}
    }
    private void nap(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){}}

    /** One request; the positive reply (41 PID ...) or null on a negative reply or timeout. */
    private byte[] ask(IBinder svc,int ecu,byte[] req){
        int sid=req[0]&255;
        synchronized(lock){replies.clear();expectEcu=ecu;}
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(SIFACE);
            in.writeByte((byte)0xc7);in.writeByte((byte)0x40);in.writeByte((byte)0x80);
            in.writeByte((byte)0x40);in.writeByte((byte)(0x80|ecu));
            in.writeByteArray(req);
            svc.transact(3,in,out,0);out.readException();
        }catch(Throwable e){return null;}
        finally{in.recycle();out.recycle();}
        long end=System.currentTimeMillis()+REPLY_MS;
        synchronized(lock){
            try{
                while(true){
                    while(!replies.isEmpty()){
                        byte[] a=replies.remove(0);
                        if(a.length>=1&&(a[0]&255)==sid+0x40)return a;
                        if(a.length>=3&&(a[0]&255)==0x7f&&(a[1]&255)==sid)return null;
                    }
                    long left=end-System.currentTimeMillis();
                    if(left<=0)return null;
                    lock.wait(left);
                }
            }catch(InterruptedException e){return null;}
            finally{expectEcu=-1;}
        }
    }

    private final class Listener extends Binder {
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws android.os.RemoteException {
            if(code==INTERFACE_TRANSACTION){reply.writeString(LIFACE);return true;}
            if(code<1||code>3)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(LIFACE);
            if(code==3){
                int rcv=data.readByte()&255;data.readByte();int fl=data.readByte()&255;
                byte[] d=data.createByteArray();
                if(rcv==0xc7&&(fl&0x80)!=0&&d!=null)synchronized(lock){if((fl&0x7f)==expectEcu){replies.add(d);lock.notifyAll();}}
            }
            if(reply!=null)reply.writeNoException();
            return true;
        }
    }
    private static void call(IBinder svc,int code,IBinder listener,IBinder token) throws Exception {
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(SIFACE);
            in.writeStrongBinder(listener);in.writeStrongBinder(token);
            svc.transact(code,in,out,0);out.readException();
        }finally{in.recycle();out.recycle();}
    }
}
