package com.veloster.vehicle;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads stored trouble codes from each ECU through the head unit's diagnostic service, the way the stock telematics
 * (com.hkmc.telematics.hscan DiagManager) does it. READ ONLY: it sends UDS 19 02 (or KWP 18 00 FF 00 when an ECU
 * refuses UDS) and nothing else; no clear (14), no session change (10), no security access (27).
 *
 *   ServiceManager "com.hkmc.micom.hscanmicomservice", IHscanMicomService (no permission check)
 *   request  sendPacket(0xC7, 0x40, 0x80, 0x40, 0x80|ECU, service bytes)            transact 3
 *   reply    IHscanMicomListener.onEventFromMicom(0xC7, -, 0x80|ECU, reply bytes)    broadcast to every listener;
 *            MICOM reassembles multi-frame replies, so one event carries the whole list.
 * ECU codes are MiComDefine TP_DEVICE_* (CAN 7E0 engine = 0x27 ...). Pacing follows the stock code: 1.75 s for a
 * reply, up to 6 s more while the ECU answers 7F xx 78 (pending).
 */
public final class DtcReader {
    private static final String SERVICE="com.hkmc.micom.hscanmicomservice";
    private static final String SERVICE_IFACE="com.hkmc.hscan.IHscanMicomService";
    private static final String LISTENER_IFACE="com.hkmc.hscan.IHscanMicomListener";
    private static final long REPLY_MS=1750,PENDING_MS=6000;

    /** ECUs an i30 PD can carry: name, MICOM code, UDS status mask (stock: 0D powertrain, 0C chassis/body). */
    private static final Object[][] ECUS={
        {"엔진 (EMS)",0x27,0x0D},{"변속기 (TCU)",0x26,0x0D},{"제동 (ABS/ESC)",0x28,0x0C},
        {"조향 (MDPS)",0x21,0x0C},{"에어백 (ACU)",0x23,0x0C},{"바디 (BCM)",0x30,0x0C}};
    private static final int ACU=0x23;

    public interface Progress { void step(String ecu); }
    /** ecu and error are UiText keys; detail is the raw negative reply when the ECU refused. */
    public static final class Result {
        public final String ecu;public final List<Dtc> codes;public final String error,detail;
        Result(String ecu,List<Dtc> codes,String error,String detail){this.ecu=ecu;this.codes=codes;this.error=error;this.detail=detail;}
    }

    private final Object lock=new Object();
    private final List<byte[]> replies=new ArrayList<byte[]>();
    private int expectEcu=-1;

    /** Reads every ECU in turn on the calling (background) thread. Throws only when the service itself is missing. */
    public static List<Result> readAll(Progress progress) throws Exception {
        IBinder svc=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,SERVICE);
        if(svc==null)throw new IllegalStateException(SERVICE+" not found");
        DtcReader r=new DtcReader();
        Binder listener=r.new Listener(),token=new Binder();
        call(svc,1,listener,token);   // registerHscanMicomListener
        List<Result> out=new ArrayList<Result>();
        try{
            for(Object[] e:ECUS){
                String name=(String)e[0];int ecu=(Integer)e[1];
                if(progress!=null)progress.step(name);
                out.add(r.read(svc,name,ecu,(Integer)e[2]));
            }
        }finally{call(svc,2,listener,token);}   // unregisterHscanMicomListener
        return out;
    }

    private Result read(IBinder svc,String name,int ecu,int mask) throws Exception {
        byte[] uds=ask(svc,ecu,new byte[]{0x19,0x02,(byte)mask});
        List<Dtc> codes=Dtc.parseUds(uds);
        if(codes==null){
            byte[] kwp=ask(svc,ecu,new byte[]{0x18,0x00,(byte)0xff,0x00});
            codes=Dtc.parseKwp(kwp);
            if(codes==null)return uds==null&&kwp==null?new Result(name,null,"응답 없음 (미장착일 수 있음)",null)
                                                      :new Result(name,null,"읽기 거부",hex(kwp!=null?kwp:uds));
        }
        if(ecu==ACU){
            // The airbag unit's status byte means the opposite on this car's stock code (0 = lamp on); show no status
            // rather than a wrong one.
            List<Dtc> plain=new ArrayList<Dtc>();
            for(Dtc d:codes)plain.add(new Dtc(d.code,d.failureType,-1));
            codes=plain;
        }
        return new Result(name,codes,null,null);
    }

    /** One request; the positive reply (SID+0x40) or the final negative one (7F SID NRC), null on timeout. */
    private byte[] ask(IBinder svc,int ecu,byte[] request) throws Exception {
        int sid=request[0]&255;
        synchronized(lock){replies.clear();expectEcu=ecu;}
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(SERVICE_IFACE);
            in.writeByte((byte)0xc7);in.writeByte((byte)0x40);in.writeByte((byte)0x80);
            in.writeByte((byte)0x40);in.writeByte((byte)(0x80|ecu));
            in.writeByteArray(request);
            svc.transact(3,in,out,0);   // sendPacket
            out.readException();
        }finally{in.recycle();out.recycle();}
        long end=System.currentTimeMillis()+REPLY_MS;
        synchronized(lock){
            try{
                while(true){
                    while(!replies.isEmpty()){
                        byte[] a=replies.remove(0);
                        if(a.length>=1&&(a[0]&255)==sid+0x40)return a;
                        if(a.length>=3&&(a[0]&255)==0x7f&&(a[1]&255)==sid){
                            if((a[2]&255)==0x78){end=System.currentTimeMillis()+PENDING_MS;continue;}
                            return a;
                        }
                    }
                    long left=end-System.currentTimeMillis();
                    if(left<=0)return null;
                    lock.wait(left);
                }
            }finally{expectEcu=-1;}
        }
    }

    /** IHscanMicomListener stub: 1 onSystemReadyChanged(Z), 2 onMicomReadyChanged(Z), 3 onEventFromMicom(BBB[B). */
    private final class Listener extends Binder {
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws android.os.RemoteException {
            if(code==INTERFACE_TRANSACTION){reply.writeString(LISTENER_IFACE);return true;}
            if(code<1||code>3)return super.onTransact(code,data,reply,flags);
            data.enforceInterface(LISTENER_IFACE);
            if(code==3){
                int rcv=data.readByte()&255;data.readByte();int fl=data.readByte()&255;
                byte[] d=data.createByteArray();
                // Only diagnostic replies (0xC7 with bit 7 of funcL) from the ECU being asked; 0x2F is not a reply.
                if(rcv==0xc7&&(fl&0x80)!=0&&d!=null)synchronized(lock){if((fl&0x7f)==expectEcu){replies.add(d);lock.notifyAll();}}
            }
            if(reply!=null)reply.writeNoException();
            return true;
        }
    }

    private static void call(IBinder svc,int code,IBinder listener,IBinder token) throws Exception {
        Parcel in=Parcel.obtain(),out=Parcel.obtain();
        try{
            in.writeInterfaceToken(SERVICE_IFACE);
            in.writeStrongBinder(listener);in.writeStrongBinder(token);
            svc.transact(code,in,out,0);
            out.readException();
        }finally{in.recycle();out.recycle();}
    }
    private static String hex(byte[] b){
        StringBuilder s=new StringBuilder();
        for(int i=0;i<b.length&&i<4;i++)s.append(String.format("%02X ",b[i]&255));
        return s.toString().trim();
    }
}
