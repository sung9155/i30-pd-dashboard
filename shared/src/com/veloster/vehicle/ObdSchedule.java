package com.veloster.vehicle;

/**
 * When ObdLive asks what: every live diagnostic request has its own period, set by how fast the value moves and
 * where it shows (engine load on a gauge every second, clutch temperature every 2 s, catalyst / DPF every 5 s,
 * ambient and fuel level every 30 s), and all of them share one budget so the request traffic stays a small
 * fraction of the bus: one request at a time, at least GAP_MS apart, a longer pause after a multi-frame reply, and
 * the table itself must fit in BUDGET requests a second (ObdScheduleTest). A late request is sent once, never
 * caught up in a burst. Pure Java so the host tests can run it; ObdLive does the talking.
 */
public final class ObdSchedule {
    /** At most this many requests a second, everything included. */
    public static final int BUDGET=4;
    public static final long GAP_MS=1000/BUDGET;
    /** Pause after a reply: the gap, plus a little for each extra CAN frame a long (ISO-TP multi-frame) reply took. */
    public static long gapAfter(int replyBytes){return GAP_MS+(replyBytes<=7?0:(replyBytes-7+6)/7*15);}

    public static final class Poll {
        public final int ecu,key;public final byte[] req;public final long every;final int dropAfter;
        long due;int misses;
        /** key is what the sink gets (a mode 01 PID, or Telemetry.DID_*); dropAfter = misses in a row that stop it, 0 = never. */
        public Poll(int ecu,byte[] req,int key,long every,int dropAfter){this.ecu=ecu;this.req=req;this.key=key;this.every=every;this.dropAfter=dropAfter;}
        public boolean active(){return dropAfter==0||misses<dropAfter;}
        public int dropAfter(){return dropAfter;}
        public int misses(){return misses;}
    }
    static final int EMS=0x27,TCU=0x26;   // MiComDefine TP_DEVICE_*
    /** The live table. Fast: what a gauge shows moving; slow: what drifts over minutes. */
    public static ObdSchedule standard(){
        return new ObdSchedule(
            pid(0x43,1000,0),                                                              // absolute load (gauges)
            new Poll(TCU,new byte[]{0x22,0x01,(byte)0xA4},Telemetry.DID_CLUTCH,2000,3),    // 7DCT clutch temperatures; a 6MT has no TCU
            pid(0x3c,5000,0),                                                              // catalyst temperature
            pid(0x7a,5000,3),pid(0x7c,5000,3),pid(0x8b,5000,3),                            // diesel DPF; a petrol engine drops them
            pid(0x46,30000,0),pid(0x2f,30000,0));                                          // ambient, fuel level
    }
    private static Poll pid(int pid,long every,int dropAfter){return new Poll(EMS,new byte[]{0x01,(byte)pid},pid,every,dropAfter);}
    private final Poll[] polls;
    public ObdSchedule(Poll... polls){this.polls=polls;}
    public Poll[] polls(){return polls;}
    /** The active request that has waited longest past its due time, or null when nothing is due yet. */
    public Poll next(long now){
        Poll best=null;
        for(Poll p:polls)if(p.active()&&p.due<=now&&(best==null||p.due<best.due))best=p;
        return best;
    }
    /** Milliseconds until the next active request is due (0 when one is due, -1 when none is left). */
    public long untilNext(long now){
        long w=-1;for(Poll p:polls)if(p.active()){long d=Math.max(0,p.due-now);w=w<0?d:Math.min(w,d);}
        return w;
    }
    /** After an answer (ok) or a miss: next due one period from now, so a late request does not burst. */
    public void done(Poll p,long now,boolean ok){p.due=now+p.every;p.misses=ok?0:p.misses+1;}
    /** Requests a second if every entry stays active. */
    public double perSecond(){double s=0;for(Poll p:polls)s+=1000.0/p.every;return s;}
}
