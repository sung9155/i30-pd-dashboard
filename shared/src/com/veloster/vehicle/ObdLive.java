package com.veloster.vehicle;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.util.ArrayList;
import java.util.List;

/**
 * Active OBD-II mode 01 poller over the head unit's MICOM diagnostic channel (the same channel {@link DtcReader}
 * uses). READ ONLY: it asks for values the hscan broadcast does not carry — ambient temperature (0x46), fuel level
 * (0x2F), catalyst temperature (0x3C) and absolute load (0x43) from the engine, the 7DCT clutch temperatures from the
 * transmission — each on its own period under one request budget (ObdSchedule), and feeds them to a sink. Only on pd_kr, only while the gate is open (ignition on), and it steps aside while
 * a trouble-code read runs (the channel is shared). The engine ECU answered these in the 2026-11-02 probe.
 * It also asks the diesel DPF PIDs 0x7A/0x7C/0x8B; a PID that gets no positive reply three times in a row (a petrol
 * engine) is dropped for the session, and their raw replies are logged at most every 10 s for a diesel owner's record.
 * Probe (2026-11-04, read only): 20 s after the gate opens and then every 5 minutes (up to 2 hours), it asks a few
 * Hyundai data identifiers (mode 22) once each and logs the raw reply: engine oil temperature, boost / wastegate and
 * per-cylinder knock retard from the engine, DCT clutch temperatures from the transmission. Which identifiers to try
 * came from the OBDb Hyundai-i30 signal set (CC BY-SA 4.0); nothing is decoded or shown until our own car's replies
 * confirm the layout. The car's log ring is small and the installers keep only its tail, so every later round logs the
 * first (cold start) round's replies again, marked "round 1 again".
 */
public final class ObdLive {
    public interface Sink { void value(int pid,byte[] data); }   // data: the reply bytes after the PID
    public interface Gate { boolean open(); }
    /** DtcReader raises this while it reads trouble codes; the poller pauses so the two never share the channel. */
    public static volatile boolean diagBusy;

    private static final String SERVICE="com.hkmc.micom.hscanmicomservice";
    private static final String SIFACE="com.hkmc.hscan.IHscanMicomService";
    private static final String LIFACE="com.hkmc.hscan.IHscanMicomListener";
    private static final int EMS=0x27,TCU=0x26,TPMS=0x24,BCM=0x30;   // MiComDefine TP_DEVICE_* (TPMS 0x24 read from the firmware's <clinit>)
    /** ECU, then the request: 22 + data identifier. */
    private static final int[][] PROBE={{EMS,0x22,0xE0,0x01},{EMS,0x22,0xED,0x02},{EMS,0x22,0xE0,0x11},{EMS,0x22,0xE0,0x21},
        {EMS,0x22,0xE0,0x0A},{EMS,0x22,0xE0,0x03},{TCU,0x22,0x01,0xA4},{TCU,0x22,0x01,0xA5},
        // tyre temperatures: Kia Ceed CD (the PD's sibling) and other Hyundai / Kia answer 22 C00B on 7A0, older ones 21 06
        // on 7D6 (OBDb); which TP_DEVICE the micom maps to 7A0 is unknown, so ask the TPMS and the BCM
        {TPMS,0x22,0xC0,0x0B},{BCM,0x22,0xC0,0x0B},{TPMS,0x21,0x06},
        // diesel (OBDb Hyundai-i30): differential pressure E0F1, successful regenerations E0F2, KWP 21 06 regeneration state, 21 03 block
        {EMS,0x22,0xE0,0xF1},{EMS,0x22,0xE0,0xF2},{EMS,0x21,0x06},{EMS,0x21,0x03},
        // the live ED03 poll logs its drop only once, early, and the record keeps only the log tail: ask it here too
        {EMS,0x22,0xED,0x03}};
    private static final long PROBE_FIRST=20000,PROBE_EVERY=300000;private static final int PROBE_ROUNDS=24;
    private final List<String> coldProbe=new ArrayList<String>();
    private static final long REPLY_MS=300,SLOW_REPLY_MS=1200;   // a mode 01 PID is one frame; a mode 22 block (ED03 ~59 B, 01A4) is multi-frame and slower
    private final long[] dpfLogged=new long[256];

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
        long nextProbe=-1;int rounds=0;ObdSchedule sched=ObdSchedule.standard();
        try{
            while(running){
                if(diagBusy||gate==null||!gate.open()){nap(500);continue;}
                long now=System.currentTimeMillis();
                if(nextProbe<0)nextProbe=now+PROBE_FIRST;
                else if(now>=nextProbe&&rounds<PROBE_ROUNDS){probe(svc,++rounds);nextProbe=now+PROBE_EVERY;continue;}
                ObdSchedule.Poll p=sched.next(now);
                if(p==null){long w=sched.untilNext(now);nap(w<0?1000:Math.min(w,500));continue;}
                byte[] r=askRaw(svc,p.ecu,p.req,p.req[0]==0x01?REPLY_MS:SLOW_REPLY_MS);
                boolean ok=positive(r,p.req);
                if(p.dropAfter()>0&&p.ecu==EMS)dpfLog(p,r,ok);   // the diesel-only PIDs
                sched.done(p,System.currentTimeMillis(),ok);
                if(ok){byte[] d=new byte[r.length-p.req.length];System.arraycopy(r,p.req.length,d,0,d.length);sink.value(p.key,d);}
                nap(ObdSchedule.gapAfter(r==null?0:r.length));
            }
        }finally{try{call(svc,2,listener,token);}catch(Throwable e){}}
    }
    /** Raw DPF replies (and the drop) into the log, so a diesel owner's diagnostics record shows what the ECU sends. */
    private void dpfLog(ObdSchedule.Poll p,byte[] r,boolean ok){
        long now=System.currentTimeMillis();int k=p.key&255;
        if(ok&&now-dpfLogged[k]<10000)return;
        if(!ok&&p.misses()!=p.dropAfter()-1)return;   // log only the miss that drops it, with its 7F code if any
        dpfLogged[k]=now;StringBuilder b=new StringBuilder();
        if(r!=null)for(byte x:r)b.append(String.format("%02X ",x&255));
        android.util.Log.i("VehicleDash","OBD "+String.format("%02X",p.key)+(ok?": "+b.toString().trim():": "+(r==null?"no reply":b.toString().trim())+" "+p.dropAfter()+"x, dropped"));
    }
    /** A positive reply to req: its service + 0x40, then the same PID / data identifier. */
    static boolean positive(byte[] r,byte[] req){
        if(r==null||r.length<=req.length||(r[0]&255)!=(req[0]&255)+0x40)return false;
        for(int i=1;i<req.length;i++)if(r[i]!=req[i])return false;
        return true;
    }
    /** One round of the mode 22 probe into the log: the whole reply in hex, a negative response code, or no reply. */
    private void probe(IBinder svc,int round){
        for(int[] p:PROBE){
            if(!running||diagBusy)return;
            byte[] req=new byte[p.length-1];for(int i=1;i<p.length;i++)req[i-1]=(byte)p[i];
            byte[] r=askRaw(svc,p[0],req,1200);
            StringBuilder b=new StringBuilder();
            if(r!=null)for(byte x:r)b.append(String.format("%02X ",x&255));
            StringBuilder q=new StringBuilder();for(int i=1;i<p.length;i++)q.append(String.format(i==2?" %02X":"%02X",p[i]));
            String what=String.format("ecu %02X ",p[0])+q+": "+(r==null?"no reply":b.toString().trim()+" ("+r.length+" B)");
            android.util.Log.i("VehicleDash","OBD probe round "+round+" "+what);
            if(round==1)coldProbe.add(what);
            nap(ObdSchedule.gapAfter(r==null?0:r.length));   // the probe keeps to the same budget (its replies run to 104 B)
        }
        if(round>1)for(String s:coldProbe)android.util.Log.i("VehicleDash","OBD probe round 1 again "+s);
    }
    private void nap(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){}}

    /** One request; the positive reply, the negative one (7F sid code), or null on a timeout. */
    private byte[] askRaw(IBinder svc,int ecu,byte[] req,long waitMs){
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
        long end=System.currentTimeMillis()+waitMs;
        synchronized(lock){
            try{
                while(true){
                    while(!replies.isEmpty()){
                        byte[] a=replies.remove(0);
                        if(a.length>=1&&(a[0]&255)==sid+0x40)return a;
                        if(a.length>=3&&(a[0]&255)==0x7f&&(a[1]&255)==sid){if((a[2]&255)==0x78)continue;return a;}   // 78: still working, keep waiting
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
