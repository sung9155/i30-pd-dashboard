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
        {EMS,0x22,0xED,0x03},{EMS,0x22,0xED,0x06},   // ED06: regeneration completed state (Kia Ceed 2018 in OBDb)
        // ED1D, the soot load the DPF tile leads with: on a v1.8.6 diesel it showed "—" and its one drop line can fall off the tail
        {EMS,0x22,0xED,0x1D},
        // exhaust gas temperature bank 1, the standard PID (01 7C, DPF temperature, got no answer on a diesel PD)
        {EMS,0x01,0x78}};
    private static final long PROBE_FIRST=20000,PROBE_EVERY=300000;private static final int PROBE_ROUNDS=24;
    private final List<String> coldProbe=new ArrayList<String>();
    private static final long REPLY_MS=300,SLOW_REPLY_MS=1200;   // a mode 01 PID is one frame; a mode 22 block (ED03 ~59 B, 01A4) is multi-frame and slower
    private final long[] dpfLogged=new long[256];
    /** Burst (sports drive): while the pedal is past half or the engine is past 4000 rpm, and 3 s after, ask the
     *  boost (E003) and knock (E00A) blocks every 0.5 s and the oil block (E001) every 5 s, each logged with rpm,
     *  pedal and computed boost so a pull lines up with the hscan record. Capped so the record's log tail keeps it. */
    private static final float BURST_PEDAL=50,BURST_RPM=4000;private static final long BURST_HOLD=3000,BURST_EVERY=500;
    private static final int BURST_MAX=2400;
    private static final byte[][] BURST={{0x22,(byte)0xE0,0x03},{0x22,(byte)0xE0,0x0A},{0x22,(byte)0xE0,0x01}};
    private long burstUntil,nextBurst;private int burstLines,burstStep;

    private final Object lock=new Object();
    private final List<byte[]> replies=new ArrayList<byte[]>();
    private int expectEcu=-1;
    private volatile boolean running;
    private static volatile ObdSchedule current;
    /** The live requests' tally for the diagnostics line; empty before the poller starts (or off the PD). */
    public static String summary(){ObdSchedule s=current;if(s==null)return "";StringBuilder b=new StringBuilder(" | obd ").append(s.summary());
        if(passive>0)b.append(" | passive ").append(passive);
        synchronized(STRAY){for(int i=0;i<128;i++)if(STRAY[i]>0)b.append(" | stray ").append(String.format("%02X",i)).append(' ').append(STRAY[i]).append(' ').append(strayHead[i]);}
        return b.toString();}
    /** Replies that came back from another device than the one asked (per TP_DEVICE code), with the last one's first bytes:
     *  shows an ECU answering under another code (a diesel engine, say), which the matching would otherwise drop unseen. */
    private static final int[] STRAY=new int[128];private static final String[] strayHead=new String[128];
    private Thread thread;
    private volatile Sink sink;
    /** Replies another tester asked for that we could use (the same request as one of ours), and the kinds we could not. */
    private static volatile int passive;
    private static final java.util.Set<String> OTHER=new java.util.HashSet<String>();
    /** A positive reply that is not the answer to our request in flight: if it answers one of our requests, use it; else
     *  log the first reply of each kind (ECU + first three bytes) once, up to 40 kinds, so a record shows who else asks. */
    private void passive(int ecu,byte[] r){
        ObdSchedule s=current;Sink k=sink;
        if(r==null||r.length<2||(r[0]&255)==0x7f)return;
        ObdSchedule.Poll p=s==null?null:s.match(ecu,r);
        if(p!=null&&k!=null){byte[] d=new byte[r.length-p.req.length];System.arraycopy(r,p.req.length,d,0,d.length);k.value(p.key,d);passive++;return;}
        StringBuilder head=new StringBuilder(String.format("%02X",ecu));for(int i=0;i<Math.min(3,r.length);i++)head.append(String.format(" %02X",r[i]&255));
        synchronized(OTHER){if(OTHER.size()>=40||!OTHER.add(head.toString()))return;}
        StringBuilder b=new StringBuilder();for(byte x:r)b.append(String.format("%02X ",x&255));
        android.util.Log.i("VehicleDash","OBD other ecu "+String.format("%02X",ecu)+": "+b.toString().trim()+" ("+r.length+" B)");
    }

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
        long nextProbe=-1;int rounds=0;ObdSchedule sched=ObdSchedule.standard();current=sched;this.sink=sink;
        try{
            while(running){
                if(diagBusy||gate==null||!gate.open()){nap(500);continue;}
                long now=System.currentTimeMillis();
                if(nextProbe<0)nextProbe=now+PROBE_FIRST;
                else if(now>=nextProbe&&rounds<PROBE_ROUNDS){probe(svc,++rounds);nextProbe=now+PROBE_EVERY;continue;}
                float rpm=VehicleDash.now(0),ped=VehicleDash.now(9);
                if(ped>=BURST_PEDAL||rpm>=BURST_RPM)burstUntil=now+BURST_HOLD;
                if(now<burstUntil&&now>=nextBurst&&burstLines<BURST_MAX){burst(svc);nextBurst=now+BURST_EVERY;continue;}
                ObdSchedule.Poll p=sched.next(now);
                if(p==null){long w=sched.untilNext(now);nap(w<0?1000:Math.min(w,500));continue;}
                byte[] r=askRaw(svc,p.ecu,p.req,p.req[0]==0x01?REPLY_MS:SLOW_REPLY_MS);
                boolean ok=positive(r,p.req);
                if(p.dropAfter()>0&&p.ecu==EMS)dpfLog(p,r,ok);   // the diesel-only PIDs
                if(!ok){StringBuilder x=new StringBuilder();if(r!=null)for(int i=0;i<Math.min(3,r.length);i++)x.append(i==0?"":" ").append(String.format("%02X",r[i]&255));p.bad=r==null?"none":x.toString();}
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
    static boolean positive(byte[] r,byte[] req){return ObdSchedule.positive(r,req);}
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
    private void burst(IBinder svc){
        int n=burstStep++%10==0?3:2;
        for(int i=0;i<n&&running&&!diagBusy;i++){
            byte[] r=askRaw(svc,EMS,BURST[i],SLOW_REPLY_MS);
            StringBuilder b=new StringBuilder();if(r!=null)for(byte x:r)b.append(String.format("%02X ",x&255));
            android.util.Log.i("VehicleDash",String.format("OBD burst rpm %.0f ped %.0f boost %.2f 22 %02X%02X: ",
                VehicleDash.now(0),VehicleDash.now(9),VehicleDash.now(Telemetry.BOOST),BURST[i][1]&255,BURST[i][2])+(r==null?"no reply":b.toString().trim()));
            burstLines++;
            nap(ObdSchedule.gapAfter(r==null?0:r.length));
        }
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
                        // a positive reply to another request (a dongle's, on the same ECU): use it, keep waiting for ours
                        if(a.length>=1&&(a[0]&255)==sid+0x40){if(positive(a,req))return a;passive(ecu,a);continue;}
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
                if(rcv==0xc7&&(fl&0x80)!=0&&d!=null){int k=fl&0x7f;boolean mine,asking;
                    synchronized(lock){mine=k==expectEcu;asking=expectEcu>=0;if(mine){replies.add(d);lock.notifyAll();}}
                    if(!mine){if(asking)synchronized(STRAY){STRAY[k]++;StringBuilder x=new StringBuilder();
                            for(int i=0;i<Math.min(3,d.length);i++)x.append(i==0?"":" ").append(String.format("%02X",d[i]&255));strayHead[k]=x.toString();}
                        passive(k,d);}}   // another ECU's reply, or one while we ask nothing: another tester's
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
