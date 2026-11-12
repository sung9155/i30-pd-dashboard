package com.veloster.vehicle;

/**
 * When ObdLive asks what: every live diagnostic request has its own period, set by how fast the value moves and
 * where it shows (engine load on a gauge and clutch temperature every second, DPF every 2 s, catalyst every 5 s,
 * ambient and fuel level every 30 s), and all of them share one budget so the request traffic stays a small
 * fraction of the bus: one request at a time, at least GAP_MS apart, a longer pause after a multi-frame reply, and
 * the table itself must fit in BUDGET requests a second (ObdScheduleTest). A late request is sent once, never
 * caught up in a burst. Pure Java so the host tests can run it; ObdLive does the talking.
 */
public final class ObdSchedule {
    /** At most this many requests a second, everything included: a ceiling with room for more values, ~0.3 % of a
     *  500 kbit/s bus even with multi-frame replies. The head unit's MICOM channel is the tighter limit (it also
     *  carries the OEM telematics and our trouble-code reads), so if the car lags, lower this first. */
    public static final int BUDGET=10;
    public static final long GAP_MS=1000/BUDGET;
    /** Pause after a reply: the gap, plus a little for each extra CAN frame a long (ISO-TP multi-frame) reply took. */
    public static long gapAfter(int replyBytes){return GAP_MS+(replyBytes<=7?0:(replyBytes-7+6)/7*15);}

    public static final class Poll {
        public final int ecu,key;public final byte[] req;public final long every;final int dropAfter;
        long due;int misses;
        /** For the diagnostics line: requests sent, positive answers, the last bad reply ("none" = timeout, else its first bytes). */
        int sent,answered;public String bad="";
        /** key is what the sink gets (a mode 01 PID, or Telemetry.DID_*); dropAfter = misses in a row that stop it, 0 = never. */
        public Poll(int ecu,byte[] req,int key,long every,int dropAfter){this.ecu=ecu;this.req=req;this.key=key;this.every=every;this.dropAfter=dropAfter;}
        public boolean active(){return dropAfter==0||misses<dropAfter;}
        /** Dropped on timeouts alone (no reply, "none"): another tester (a DAG3) may hold the ECU, so it is tried again
         *  once a RETRY_MS; a refusal (7F) means the ECU lacks it and it stays dropped. */
        public boolean retrying(){return !active()&&"none".equals(bad);}
        public int dropAfter(){return dropAfter;}
        public int misses(){return misses;}
    }
    static final int EMS=0x27,TCU=0x26,BCM=0x30;   // MiComDefine TP_DEVICE_*
    /** The live table. Fast: what a gauge shows moving; slow: what drifts over minutes. */
    public static ObdSchedule standard(){
        return new ObdSchedule(
            pid(0x43,1000,0),                                                              // absolute load (gauges)
            pid(0x00,10000,0),                                                             // supported PIDs: every OBD engine answers it, so its tally says whether the engine answers at all (a diesel's 43 / 3C / 2F may simply be unsupported)
            new Poll(TCU,new byte[]{0x22,0x01,(byte)0xA4},Telemetry.DID_CLUTCH,1000,3),    // 7DCT clutch temperatures (a launch heats them in seconds); a 6MT has no TCU
            pid(0x3c,5000,0),                                                              // catalyst temperature
            pid(0x7a,2000,3),pid(0x7c,2000,3),pid(0x8b,2000,3),                            // diesel DPF (pressure moves during a regeneration); a petrol engine drops them
            new Poll(EMS,new byte[]{0x22,(byte)0xED,0x03},Telemetry.DID_DPF,2000,3),
            new Poll(EMS,new byte[]{0x21,0x03},Telemetry.LID_DPF,2000,3),                   // the same block over KWP (exhaust temperature); whichever answers
            new Poll(EMS,new byte[]{0x22,(byte)0xE0,(byte)0xF1},Telemetry.DID_DPF2,5000,3),   // distance since regeneration; a DAG3 asks it, so it also arrives while we listen
            new Poll(EMS,new byte[]{0x22,(byte)0xED,0x1D},Telemetry.DID_SOOT,2000,3),         // soot load g (Kia Ceed 2018 in OBDb); a petrol drops it       // Hyundai diesel DPF block (the PD diesel ignores the standard PIDs); a petrol EMS says 7F 31
            new Poll(TCU,new byte[]{0x22,0x01,(byte)0xA6},Telemetry.DID_TCU_LIFE,60000,3),     // DCT clutch life candidates (a slow value; a 6MT has no TCU)
            new Poll(BCM,new byte[]{0x22,(byte)0xC0,0x0B},Telemetry.DID_TIRE,30000,3),         // tyre temperatures every 30 s (was 10 s; the sensors report every few tens of seconds, and STALE_MS is 60 s); an ECU that lacks it drops it
            pid(0x46,30000,0),pid(0x2f,30000,0),                                           // ambient, fuel level
            pid(0x0b,2000,3));                                                             // intake manifold pressure: checks the diesel boost scale (VehicleDash logs it beside the hscan byte)
    }
    private static Poll pid(int pid,long every,int dropAfter){return new Poll(EMS,new byte[]{0x01,(byte)pid},pid,every,dropAfter);}
    private final Poll[] polls;
    public static final long RETRY_MS=60000;
    public ObdSchedule(Poll... polls){this.polls=polls;}
    public Poll[] polls(){return polls;}
    /** The active request that has waited longest past its due time, or null when nothing is due yet. */
    public Poll next(long now){
        Poll best=null;
        for(Poll p:polls)if((p.active()||p.retrying())&&p.due<=now&&(best==null||p.due<best.due))best=p;
        return best;
    }
    /** Milliseconds until the next active request is due (0 when one is due, -1 when none is left). */
    public long untilNext(long now){
        long w=-1;for(Poll p:polls)if(p.active()||p.retrying()){long d=Math.max(0,p.due-now);w=w<0?d:Math.min(w,d);}
        return w;
    }
    /** After an answer (ok) or a miss: next due one period from now, so a late request does not burst. */
    /** A dropped request that has answered before is asked again after three of its periods (at least 3 s), not a minute:
     *  the ECU is there and was only busy. At a minute the 7DCT clutch temperatures went stale (60 s) and showed "-" after
     *  three missed replies in a row (an owner's record: 01A4 802/819, "none retry"). One that never answered (a 6MT has no
     *  TCU, a DAG3 holding the engine) still waits RETRY_MS. */
    public void done(Poll p,long now,boolean ok){p.misses=ok?0:p.misses+1;p.sent++;if(ok)p.answered++;
        p.due=now+(p.active()?(ok?p.every:missedEvery(p)):p.answered>0?Math.max(3000,p.every*3):RETRY_MS);}
    /** A slow value (fuel level, asked every 30 s) whose reply was missed is asked again in FAST_MS, not a period on: two
     *  misses at 30 s let it pass STALE_MS (60 s) and show "-" (owners of 1.6T and 1.4T petrol cars, 2026-11-12), and at start
     *  it would wait a period for a first value. Back to its period on an answer. One that has never answered gets FAST_TRIES
     *  quick tries, then its period: an engine without it (the diesel's 01 2F) is not asked every 5 s for the whole drive. */
    static final long SLOW_MS=30000,FAST_MS=5000;static final int FAST_TRIES=10;
    private static long missedEvery(Poll p){return p.every>=SLOW_MS&&(p.answered>0||p.misses<=FAST_TRIES)?FAST_MS:p.every;}
    /** Not sent this time (another tester holds the ECU): due again a period on, no miss counted. */
    public void skip(Poll p,long now){p.due=now+p.every;}
    /** Every request's tally on one short line ("ED1D 0/3 7F 22 31 off, 43 40/40, ..."): the 5 s diagnostics line
     *  carries it, so a record whose log tail lost the early drop lines still shows how each request went. */
    /** The request a positive reply answers (its service + 0x40, then the same PID / identifier from ecu), or null. ObdLive
     *  uses it on replies another tester asked for: an OBD dongle on the same engine (a diesel PD, 2026-11-06) gets the
     *  answers while ours go unanswered, and the head unit hears them all. */
    public Poll match(int ecu,byte[] reply){
        for(Poll p:polls)if(p.ecu==ecu&&positive(reply,p.req))return p;
        return null;
    }
    /** A positive reply to req: its service + 0x40, then the same PID / data identifier. */
    public static boolean positive(byte[] r,byte[] req){
        if(r==null||r.length<=req.length||(r[0]&255)!=(req[0]&255)+0x40)return false;
        for(int i=1;i<req.length;i++)if(r[i]!=req[i])return false;
        return true;
    }
    public String summary(){
        StringBuilder b=new StringBuilder();
        for(Poll p:polls){if(b.length()>0)b.append(", ");
            for(int i=1;i<p.req.length;i++)b.append(String.format("%02X",p.req[i]&255));
            b.append(' ').append(p.answered).append('/').append(p.sent);
            if(p.answered<p.sent&&p.bad.length()>0)b.append(' ').append(p.bad);
            if(!p.active())b.append(p.retrying()?" retry":" off");}
        return b.toString();
    }
    /** Requests a second if every entry stays active. */
    public double perSecond(){double s=0;for(Poll p:polls)s+=1000.0/p.every;return s;}
}
