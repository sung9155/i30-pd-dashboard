import com.veloster.vehicle.ObdSchedule;
import com.veloster.vehicle.Telemetry;

public final class ObdScheduleTest {
    private static int checks;
    private static void check(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
    public static void main(String[] args){
        ObdSchedule s=ObdSchedule.standard();
        check(s.perSecond()<=ObdSchedule.BUDGET,"the live table fits the request budget ("+s.perSecond()+"/s)");
        for(ObdSchedule.Poll p:s.polls())check(p.every>=1000,"nothing is asked more than once a second");
        check(ObdSchedule.gapAfter(5)==ObdSchedule.GAP_MS&&ObdSchedule.gapAfter(37)>ObdSchedule.GAP_MS
            &&ObdSchedule.gapAfter(104)>ObdSchedule.gapAfter(37),"a longer reply waits longer");

        ObdSchedule.Poll fast=new ObdSchedule.Poll(0x27,new byte[]{1,0x43},0x43,1000,0);
        ObdSchedule.Poll slow=new ObdSchedule.Poll(0x27,new byte[]{1,0x46},0x46,30000,0);
        ObdSchedule.Poll dct=new ObdSchedule.Poll(0x26,new byte[]{0x22,1,(byte)0xA4},Telemetry.DID_CLUTCH,2000,3);
        ObdSchedule t=new ObdSchedule(fast,slow,dct);
        long now=0;int nf=0,ns=0;
        // two minutes at the budget: fast every second, slow every 30 s, nothing starves
        for(;now<120000;now+=ObdSchedule.GAP_MS){
            ObdSchedule.Poll p=t.next(now);if(p==null)continue;
            if(p==fast)nf++;if(p==slow)ns++;
            t.done(p,now,p!=dct);   // the TCU never answers here (a 6MT)
        }
        check(nf>=110&&nf<=121,"fast value about once a second ("+nf+")");
        check(ns==4,"slow value every 30 s ("+ns+")");
        check(!dct.active()&&dct.misses()==3,"three misses in a row drop a request");
        check(t.untilNext(now)>=0,"the others stay scheduled");

        // a late request is sent once, then waits a full period (no catch-up burst)
        ObdSchedule.Poll q=new ObdSchedule.Poll(0x27,new byte[]{1,0x3c},0x3c,5000,0);ObdSchedule u=new ObdSchedule(q);
        check(u.next(0)==q,"due at start");u.done(q,60000,true);
        check(u.next(60000)==null&&u.untilNext(60000)==5000,"next one a period after the late answer");
        ObdSchedule.Poll r=new ObdSchedule.Poll(0x26,new byte[]{0x22,1,(byte)0xA4},1,2000,3);ObdSchedule v=new ObdSchedule(r);
        v.done(r,0,false);v.done(r,2000,false);v.done(r,4000,true);check(r.active()&&r.misses()==0,"an answer resets the miss count");
        r.bad="7F 22 31";v.done(r,6000,false);v.done(r,8000,false);v.done(r,10000,false);
        check(v.summary().equals("01A4 1/6 7F 22 31 off"),"summary: answered/sent, last bad reply, dropped ("+v.summary()+")");
        // timeouts alone (a DAG3 holding the engine) drop it to a retry a minute on; an answer brings it back
        ObdSchedule.Poll w=new ObdSchedule.Poll(0x27,new byte[]{0x22,(byte)0xED,0x03},2,2000,3);ObdSchedule x=new ObdSchedule(w);
        w.bad="none";x.done(w,0,false);x.done(w,2000,false);x.done(w,4000,false);
        check(!w.active()&&w.retrying()&&x.next(5000)==null&&x.next(64000)==w&&x.summary().endsWith("none retry"),"timeouts: tried again a minute later ("+x.summary()+")");
        x.done(w,64000,true);check(w.active()&&x.untilNext(64000)==2000,"an answer brings it back");
        x.skip(w,70000);check(w.misses()==0&&x.untilNext(70000)==2000,"skipped while another tester asks: no miss");
        // a request that answered before (the TCU's clutch block) and then misses three: asked again in 3 s, so its values never go stale
        ObdSchedule.Poll tc=new ObdSchedule.Poll(0x26,new byte[]{0x22,1,(byte)0xA4},Telemetry.DID_CLUTCH,1000,3);ObdSchedule y=new ObdSchedule(tc);
        y.done(tc,0,true);tc.bad="none";y.done(tc,1000,false);y.done(tc,2000,false);y.done(tc,3000,false);
        check(!tc.active()&&y.untilNext(3000)==3000&&y.next(6000)==tc,"an ECU that answered before: retried in 3 s, not a minute");
        y.done(tc,6000,true);check(tc.active()&&y.untilNext(6000)==1000,"and back to every second on its answer");
        // replies another tester asked for (an OBD dongle on a diesel PD, 2026-11-06): used when they answer one of our requests
        ObdSchedule m=ObdSchedule.standard();
        ObdSchedule.Poll cl=m.match(0x26,new byte[]{0x62,0x01,(byte)0xA4,1,2,3});
        check(cl!=null&&cl.key==com.veloster.vehicle.Telemetry.DID_CLUTCH,"a dongle's 62 01 A4 from the TCU is our clutch request");
        check(m.match(0x27,new byte[]{0x41,0x0b,0x7d}).key==0x0b&&m.match(0x27,new byte[]{0x41,0x10,0x08})==null,"41 0B is ours (MAP), 41 10 (MAF) is not");
        check(m.match(0x26,new byte[]{0x41,0x0b,0x7d})==null&&m.match(0x27,new byte[]{0x62,(byte)0xED,0x01,0})==null&&m.match(0x27,new byte[]{0x7f,0x22,0x31})==null,"another ECU, an unknown identifier, a refusal: none");
        System.out.println("PASS "+checks+" OBD schedule checks");
    }
}
