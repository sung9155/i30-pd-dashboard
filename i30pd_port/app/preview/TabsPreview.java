package com.veloster.vehicle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Replays an hscan raw log through the app's own Telemetry.hscanPd() and renders every dashboard tab
 * with the real PitRenderer through the Java2D android.graphics shim.
 * Frame times are spread evenly over the capture (the dump has no per-frame clock).
 */
public final class TabsPreview {
    public static void main(String[] a) throws Exception {
        List<String> raw=new ArrayList<>();
        for(String line:Files.readAllLines(new File(a[0]).toPath(),StandardCharsets.UTF_8))if(line.startsWith("RAW "))raw.add(line.substring(4).replace(" ","").trim());
        File out=new File(a[1]);out.mkdirs();
        Theme.select(System.getProperty("preview.theme","n"));   // PREVIEW_THEME=lambo|bmw|redblack in preview.sh
        float at=a.length>2?Float.parseFloat(a[2]):1f;   // 0..1 position in the log
        long secs=600_000;for(String line:Files.readAllLines(new File(a[0]).toPath(),StandardCharsets.UTF_8))
            if(line.startsWith("# PC_START")&&line.contains("SECS="))secs=Long.parseLong(line.replaceAll(".*SECS=(\\d+).*","$1"))*1000;
        int last=Math.max(0,Math.min(raw.size()-1,(int)(at*(raw.size()-1))));
        long step=Math.max(1,secs/Math.max(1,raw.size()));
        Telemetry model=new Telemetry();
        PitRenderer rec=new PitRenderer();rec.setSymmetric(true);  // keeps DriveStats for the whole replay
        for(int n=0;n<=last;n++){
            long now=1_000_000+n*step;feed(model,raw.get(n),now);rec.stats().update(model.snapshot(now),now);
        }
        long now=1_000_000+last*step;
        PitRenderer p=new PitRenderer();
        save(out,"1_overview",p,model,now,false,false);
        BufferedImage intro=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
        PitRenderer ip=new PitRenderer();android.graphics.Canvas ic=new android.graphics.Canvas(intro.createGraphics());
        ip.draw(ic,800,480,false,model,now);ip.drawIntro(ic,0.42f);
        ImageIO.write(intro,"png",new File(out,"0_intro.png"));
        save(out,"2_f1",p,model,now,true,false);
        save(out,"3_f1_media",p,model,now,true,true);
        PitRenderer perf=new PitRenderer();perf.setSymmetric(true);perf.setPage(PitRenderer.PAGE_PERFORMANCE);perf.setLap(83456,false);
        save(out,"4b_performance",perf,model,now,false,false);
        PitRenderer show=new PitRenderer();show.setSymmetric(true);show.setPage(PitRenderer.PAGE_SHOW);
        save(out,"4e_show",show,model,now,false,false);
        rec.setPage(PitRenderer.PAGE_RACE);save(out,"4c_race",rec,model,now,false,false);
        save(out,"4c_race_music",rec,model,now,false,true);   // race tab with the music panel toggled on
        rec.setPage(PitRenderer.PAGE_RECORDS);save(out,"4d_records",rec,model,now,false,false);
        String[] layouts={"9a_cluster_classic","9b_cluster_retro","9c_cluster_rally"};
        for(int i=0;i<3;i++){rec.setPage(PitRenderer.PAGE_CLASSIC+i);save(out,layouts[i],rec,model,now,false,false);}
        // Missing signals (empty model) and a 6MT profile, drawn straight through ClusterRenderer.
        String[] empty={"x_cluster_empty","x_cluster_empty_retro","x_cluster_empty_rally"};
        Telemetry none=new Telemetry();
        for(int i=0;i<3;i++)cluster(out,empty[i],i,none.snapshot(now),now,new DriveStats(),VehicleProfile.PETROL_DCT);
        cluster(out,"x_cluster_mt",0,model.snapshot(now),now,rec.stats(),VehicleProfile.PETROL_MT);
        // Theme switch on a live renderer: draw once in the selected theme, then switch to lambo and draw again.
        String was=Theme.current().id;PitRenderer sw=new PitRenderer();sw.setSymmetric(true);sw.setPage(PitRenderer.PAGE_RACE);
        save(out,"x_theme_before",sw,model,now,false,false);Theme.select("lambo");save(out,"x_theme_switch",sw,model,now,false,false);Theme.select(was);
        p=new PitRenderer();p.setGaugeLayout(new GaugeLayout());save(out,"5_gauge",p,model,now,false,false);
        GaugeLayout dense=new GaugeLayout();dense.dense(true);p.setGaugeLayout(dense);save(out,"6_gauge_4x3",p,model,now,false,false);
        GaugeLayout pd=new GaugeLayout();pd.dense(true);
        int[] pick={Telemetry.FUEL_RATE,Telemetry.AVG_FUEL,Telemetry.RANGE,Telemetry.THROTTLE_POS,Telemetry.TORQUE_CORR,Telemetry.TORQUE_FRICTION,
            Telemetry.SPARK,Telemetry.BRAKE_SWITCH,Telemetry.STEER_TORQUE,Telemetry.DRIVE_MODE,Telemetry.PARK_BRAKE,Telemetry.ENGINE_LOAD};
        for(int i=0;i<pick.length;i++)pd.set(i,pick[i],i==0||i==1||i==6||i==8?0:i==4||i==5||i==11?1:2);
        p.setGaugeLayout(pd);save(out,"6b_gauge_pd_signals",p,model,now,false,false);
        p=new PitRenderer();p.setGraphLayout(new GraphLayout());save(out,"7_graph",p,model,now,false,false);
        GraphLayout picked=new GraphLayout();picked.signal(0,0);picked.signal(1,7);picked.signal(2,Telemetry.STEERING);picked.window(300000);
        p.setGraphLayout(picked);save(out,"8_graph_rpm_speed_steer_5min",p,model,now,false,false);
    }
    private static void feed(Telemetry model,String hex,long now){
        byte[] b=new byte[hex.length()/2];
        for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        if(b.length<12)return;
        int n=b[9]&255,i=11;
        for(int k=0;k<n&&i+2<=b.length;k++){
            int f=b[i]&255,l=b[i+1]&255;if(i+2+l>b.length)break;
            byte[] p=new byte[l];System.arraycopy(b,i+2,p,0,l);model.hscanPd(0xc7,f,p,now);i+=2+l;
        }
    }
    private static void cluster(File dir,String name,int layout,Telemetry.Sample[] s,long now,DriveStats st,VehicleProfile prof) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
        new ClusterRenderer().draw(new android.graphics.Canvas(img.createGraphics()),layout,s,now,st,prof);
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
    private static void save(File dir,String name,PitRenderer r,Telemetry model,long now,boolean cluster,boolean media) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
        r.draw(new android.graphics.Canvas(img.createGraphics()),800,480,cluster,model,now,0,media,null);
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
}
