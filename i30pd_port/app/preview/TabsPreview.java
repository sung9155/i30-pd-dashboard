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
        Theme.select(System.getProperty("preview.theme","n"));
        {String r=System.getProperty("preview.root","");java.io.File m=new java.io.File(r,"apps/dashboard/res/raw/chakra_petch_medium.ttf");   // the 수동 screen's face, as the car loads it
         if(m.isFile()){LambdaCluster.FACE=android.graphics.Typeface.createFromFile(m);LambdaCluster.FACE_BOLD=android.graphics.Typeface.createFromFile(new java.io.File(r,"apps/dashboard/res/raw/chakra_petch_bold.ttf"));}}   // PREVIEW_THEME=lambo|bmw|ice|mono|amber|teal|night|rose|copper|red|green in preview.sh
        CarPaint.select(System.getProperty("preview.paint","blue"));   // PREVIEW_PAINT=white|red|black|grey|navy|brown|intense|orange|marina|silver|copper|purple|khaki
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
        PitRenderer perf=new PitRenderer();perf.setSymmetric(true);perf.setPage(PitRenderer.PAGE_PERFORMANCE);LapTimer lap=new LapTimer();lap.tap(now-310000);lap.tap(now-222000);lap.tap(now-137500);lap.tap(now-51800);perf.setLap(lap);   // lap 4 running; lap 2 is the best, lap 3 is 1.2 s off it
        save(out,"4b_performance",perf,model,now,false,false);
        lap.hold(now-20000);save(out,"4b_performance_lap_stopped",perf,model,now,false,false);   // stopped 20 s ago: frozen, hold clears
        perf.setLap(new LapTimer());save(out,"4b_performance_lap_idle",perf,model,now,false,false);
        PitRenderer show=new PitRenderer();show.setSymmetric(true);show.setPage(PitRenderer.PAGE_SHOW);
        save(out,"4e_show",show,model,now,false,false);
        rec.setPage(PitRenderer.PAGE_RACE);save(out,"4c_race",rec,model,now,false,false);
        save(out,"4c_race_music",rec,model,now,false,true);   // race tab with the music panel toggled on
        race(out,"4c_race_mt",rec,model,now,VehicleProfile.PETROL_MT,1);       // 6MT: clutch cell left of the brake, pressed
        race(out,"4c_race_mt_out",rec,model,now,VehicleProfile.PETROL_MT,0);   // and released
        race(out,"4c_race_mt_hint",rec,model,now,VehicleProfile.PETROL_MT,0,4); // the ECU's shift hint (▲4) on its red band
        race(out,"4c_race_diesel",rec,model,now,VehicleProfile.DIESEL_DCT,-1); // DPF slot instead of SPARK
        {Telemetry dz=new Telemetry();byte[] e=new byte[56];e[36]=35;e[38]=58;e[46]=1;e[47]=(byte)0xE2;e[48]=0x08;   // only the Hyundai ED03 block: 15.1 kPa, 372 C, 123.4 km
         for(int n=0;n<=last;n++)feed(dz,raw.get(n),1_000_000+n*step);dz.obd(Telemetry.DID_DPF,e,now);race(out,"4c_race_diesel_ed03",rec,dz,now,VehicleProfile.DIESEL_DCT,-1);
         dz.obd(Telemetry.DID_SOOT,new byte[]{0,0,0,0,0x0C,(byte)0xCD,0,0},now);race(out,"4c_race_diesel_soot",rec,dz,now,VehicleProfile.DIESEL_DCT,-1);}   // + 22 ED1D: 12.8 g leads the cell
        {byte[] ct=new byte[33];ct[30]=(byte)76;ct[32]=(byte)98;model.obd(Telemetry.DID_CLUTCH,ct,now);}   // 7DCT clutch temperatures odd 112 / even 156 C (TCU 22 01A4, x2-40)
        race(out,"4c_race_dct_clutch",rec,model,now,VehicleProfile.PETROL_DCT,-1); // odd / even clutch left of the brake, amber from 150 C
        model.obd(0x8b,new byte[]{0x11,0x01,(byte)0x9e,0,0,0,0},now);model.obd(0x7a,new byte[]{0x01,0x05,(byte)0xdc,0,0,0,0},now);model.obd(0x7c,new byte[]{0x01,0x19,0x64,0,0,0,0,0,0},now);   // standard DPF PIDs: regenerating, trigger 62 %, 15 kPa, 610 °C
        race(out,"4c_race_diesel_regen",rec,model,now,VehicleProfile.DIESEL_DCT,-1);
        // after it: trigger 38 %, 6.5 kPa, 310 °C, 123 km since the regeneration ended (ECU average 480 km, shown in turn)
        model.obd(0x8b,new byte[]{0x51,0x00,(byte)0x61,0,0,0x01,(byte)0xe0},now);model.dpfRegenEnd(model.sample(Telemetry.ODOMETER,now).value-123);
        model.obd(0x8b,new byte[]{0x51,0x00,(byte)0x61,0,0,0x01,(byte)0xe0},now);model.obd(0x7a,new byte[]{0x01,0x02,(byte)0x8a,0,0,0,0},now);model.obd(0x7c,new byte[]{0x01,0x0d,(byte)0xac,0,0,0,0,0,0},now);
        race(out,"4c_race_diesel_dpf",rec,model,now/6000*6000,VehicleProfile.DIESEL_DCT,-1);race(out,"4c_race_diesel_dpf_avg",rec,model,now/6000*6000+3000,VehicleProfile.DIESEL_DCT,-1);
        rec.setPage(PitRenderer.PAGE_RECORDS);save(out,"4d_records",rec,model,now,false,false);
        {DriveStats d=rec.stats();float[] keep={d.maxLat,d.maxDecel,d.maxBoost,d.maxRpm,d.maxSpeed,d.maxClutchOdd,d.maxClutchEven};   // the car photo 2026-11-04: six cells, 6242 rpm ran into the next one
         d.maxLat=1.12f;d.maxDecel=1.08f;d.maxBoost=1.52f;d.maxRpm=6742;d.maxSpeed=236;d.maxClutchOdd=196;d.maxClutchEven=102;save(out,"4d_records_full",rec,model,now,false,false);
         d.maxLat=keep[0];d.maxDecel=keep[1];d.maxBoost=keep[2];d.maxRpm=keep[3];d.maxSpeed=keep[4];d.maxClutchOdd=keep[5];d.maxClutchEven=keep[6];}
        model.obd(0x46,new byte[]{(byte)58},now);model.obd(0x2f,new byte[]{(byte)158},now);   // ambient 18 C, fuel 62 % (the replayed log has neither; the classic tiles show them)
        String[] layouts={"9a_cluster_classic","9b_cluster_retro","9c_cluster_rally","9d_cluster_taxi","9e_cluster_80s","9f_cluster_hud","9g_cluster_8bit","9g_cluster_8bit_chase",
            "9i_cluster_gcircle","9j_cluster_night","9k_cluster_drag","9l_cluster_engine","9m_cluster_cassette","9n_cluster_warp","9o_cluster_circuit"};
        for(int i=0;i<layouts.length;i++){rec.setPage(PitRenderer.PAGE_CLASSIC+i);save(out,layouts[i],rec,model,now,false,false);}
        rec.setPicker(true,false,0);save(out,"9_cluster_list",rec,model,now,false,false);rec.setPicker(false,false,0);
        Telemetry.Sample sel=model.sample(Telemetry.GEAR_SELECTOR,now);   // G circle in the DCT manual gate (S -> "M" + gear), then the lever back
        model.hscanPd(0xc7,0x1d,new byte[]{0,8,0,0,0,0,0,0},now);rec.setPage(PitRenderer.PAGE_CLASSIC+java.util.Arrays.asList(layouts).indexOf("9i_cluster_gcircle"));
        save(out,"9i_cluster_gcircle_m",rec,model,now,false,false);
        int back=!sel.valid?5:sel.value==0?0:sel.value==1?7:sel.value==2?6:sel.value==4?8:5;model.hscanPd(0xc7,0x1d,new byte[]{0,(byte)back,0,0,0,0,0,0},now);
        // Which layouts follow the theme: render a few under Copper. Night/Engine/Warp (and G-circle/Drag) recolour; 8-bit and HUD keep their own palette.
        rec.setPicker(false,false,0);Theme.select("copper");
        int[] tl={9,11,13,8,10,6,5};String[] tn={"night","engine","warp","gcircle","drag","8bit","hud"};
        for(int ti=0;ti<tl.length;ti++){rec.setPage(PitRenderer.PAGE_CLASSIC+tl[ti]);save(out,"z_theme_"+tn[ti]+"_copper",rec,model,now,false,false);}
        Theme.select(System.getProperty("preview.theme","n"));   // back to the theme this run renders in
        String[] cats={"screens","theme","paint","mode","rpm","sound","lang","diag","float"};   // settings (the gear), one shot per category
        rec.setPicker(true,true,(1<<2)|(1<<7)|(1<<12)|(1<<20));rec.setModeOptions(true,false);rec.setSound(true,0,0,0,SettingsLayout.SOUND_DEFAULT);
        int[] targets={ClusterPicker.F1+2,ClusterPicker.GAUGES,-1};   // SPORT set to the race page, ECO to the gauges
        for(int k=0;k<cats.length;k++){rec.setSettings(k,"auto",-1,targets);save(out,"9_settings_"+cats[k],rec,model,now,false,false);}
        {   // the floating window (FloatPanel): shapes, cell counts and sizes over the 개요 page standing in for another app
            int[][] combos={{1,4,1},{1,4,3},{1,2,1},{1,1,2},{2,4,1},{2,2,0},{3,4,1},{3,2,2}};
            String[] cn={"card4","card4_xl","card2","card1_l","strip4","strip2_s","side4","side2_l"};FloatPanel.cells[2]=-1;
            for(int ci=0;ci<combos.length;ci++){int sh=combos[ci][0],n=combos[ci][1],sz=combos[ci][2];
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(sh,n,sz),FloatPanel.defaultY(sh,n,sz));
                FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,sz,model.snapshot(now),false,now);ImageIO.write(img,"png",new File(out,"x_float_"+cn[ci]+".png"));}
            {   // the three graphs, the line filled from the last 30 s of the drive (a sample every 0.5 s)
                Telemetry hm=new Telemetry();long t0=now-30000;int from=Math.max(0,last-(int)(31000/step));
                for(int q2=0;q2<=last;q2++){long tq=1_000_000+q2*step;feed(hm,raw.get(q2),tq);if(q2>=from&&(q2-from)%Math.max(1,(int)(500/step))==0)FloatPanel.draw(new android.graphics.Canvas(new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB).createGraphics()),FloatPanel.CARD,4,2,hm.snapshot(tq),false,tq);}
                FloatPanel.graphs[0]=FloatPanel.LINE;FloatPanel.graphs[1]=FloatPanel.BAR;FloatPanel.graphs[2]=FloatPanel.LED;FloatPanel.graphs[3]=FloatPanel.LINE;FloatPanel.cells[3]=0;
                for(int[] cb:new int[][]{{1,4,2},{2,4,1}}){BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                    new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                    java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(cb[0],cb[1],cb[2]),FloatPanel.defaultY(cb[0],cb[1],cb[2]));
                    FloatPanel.draw(new android.graphics.Canvas(g2),cb[0],cb[1],cb[2],hm.snapshot(1_000_000+last*step),false,1_000_000+last*step);ImageIO.write(img,"png",new File(out,"x_float_graphs_"+(cb[0]==1?"card":"strip")+".png"));}
                {   // no values yet (the car just woke): every cell the value alone, large and centred
                    BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                    java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(1,4,2),FloatPanel.defaultY(1,4,2));
                    FloatPanel.draw(new android.graphics.Canvas(g2),1,4,2,new Telemetry().snapshot(now),false,now+1);ImageIO.write(img,"png",new File(out,"x_float_graphs_empty.png"));}
                FloatPanel.cells[3]=-1;
            }
            FloatPanel.shape=FloatPanel.CARD;FloatPanel.count=2;   // the settings shot below shows the window on, two cells
        }
        rec.setBeep(true,2,70,new int[]{0,2,-1},1);rec.setSettings(4,"auto",-1,targets);save(out,"9_settings_rpm_beep",rec,model,now,false,false);rec.setBeep(false,0,70,new int[]{-1,-1,-1},0);
        rec.setModeOptions(true,true);rec.setSettings(3,"auto",-1,targets);save(out,"9_settings_mode_on",rec,model,now,false,false);
        rec.setSettings(3,"auto",0,targets);save(out,"9_settings_mode_pick",rec,model,now,false,false);
        {Telemetry.Sample[] q=model.snapshot(now);q[7]=new Telemetry.Sample(103,true,false,true,false,0);   // GPS speed calibration off / on: the speedometer's 103 shown as a true 100
         for(int on=0;on<2;on++){Telemetry.speedScale=on==1?0.971f:1;BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
          new PitRenderer().draw(c,800,480,false,model,now,0,false,null);android.graphics.Paint bg=new android.graphics.Paint(1);bg.setColor(Theme.current().map(0xff040506));c.drawRect(0,53,800,480,bg);
          new RaceRenderer().draw(c,q,ShiftGuide.evaluate(q,ShiftGuide.target(0,VehicleProfile.PETROL_DCT),VehicleProfile.PETROL_DCT),now,rec.stats(),VehicleProfile.PETROL_DCT,false,null,null);
          ImageIO.write(img,"png",new File(out,on==1?"x_speedcal_on.png":"x_speedcal_off.png"));}Telemetry.speedScale=1;}
        rec.setSpeedCal(true,0.968f,1240);rec.setSettings(7,"auto",-1,targets);save(out,"9_settings_diag_speedcal",rec,model,now,false,false);rec.setSpeedCal(false,0.968f,1240);save(out,"9_settings_diag_speedcal_off",rec,model,now,false,false);rec.setSpeedCal(false,Float.NaN,35);save(out,"9_settings_diag_measuring",rec,model,now,false,false);rec.setSpeedCal(false,Float.NaN,0);   // GPS speed calibration learned, on
        rec.setPicker(false,false,0);rec.setModeOptions(false,false);
        // Missing signals (empty model) and a 6MT profile, drawn straight through ClusterRenderer.
        Telemetry none=new Telemetry();
        for(int i=0;i<layouts.length;i++)cluster(out,"x_"+layouts[i].substring(3)+"_empty",i,none,now,new DriveStats(),VehicleProfile.PETROL_DCT);
        cluster(out,"x_cluster_mt",0,model,now,rec.stats(),VehicleProfile.PETROL_MT);
        // Taxi right panel: the radio during a trip (sample track), the receipt after 운행 종료 (x_cluster_taxi_empty: 운행 시작).
        MediaState ms=new MediaState();ms.mode(0x36);
        ms.update(1,true,"한강 따라 달리는 밤의 드라이브 (Night Drive Remix)",true,"City Lights Band",true,83000,true,214000,false,now);ms.playback(1,1);
        taxi(out,"9d_cluster_taxi_music",model,now,ms.snapshot(now),false);
        {   // 8비트 with a track playing: the now-playing box on the right (a made-up cover; a Korean title and an English one)
            BufferedImage art=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D ag=art.createGraphics();
            ag.setPaint(new java.awt.GradientPaint(0,0,new java.awt.Color(0xff5f8a),64,64,new java.awt.Color(0x3a6ea5)));ag.fillRect(0,0,64,64);
            ag.setColor(new java.awt.Color(0xffe04a));ag.fillOval(18,14,28,28);ag.setColor(new java.awt.Color(0x1d2025));ag.fillRect(0,46,64,18);ag.dispose();
            for(int pass=0;pass<5;pass++){   // 3, 4: Arcade with and without a cover
                MediaState m8=new MediaState();m8.mode(0x36);
                if(pass!=1)m8.update(1,true,"한강 따라 달리는 밤의 드라이브 (Night Drive Remix)",true,"서울 시티 라이츠 밴드 & 한강 오케스트라",true,83000,true,214000,false,now);
                else m8.update(1,true,"Midnight Run",true,"Neon Riders",true,150000,true,200000,false,now);
                m8.playback(1,1);
                ClusterKit.artwork=pass!=1&&pass!=4?android.graphics.Bitmap.of(art):null;
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
                new PitRenderer().draw(c,800,480,false,model,now,0,false,null);
                PixelCluster pk=new PixelCluster(pass>=3);pk.frame(c,model.snapshot(now),model,new DriveStats(),VehicleProfile.PETROL_DCT,now,m8.snapshot(now));
                if(pass==2)pk.frame(c,model.snapshot(now),model,new DriveStats(),VehicleProfile.PETROL_DCT,now+4500,m8.snapshot(now));
                ImageIO.write(img,"png",new File(out,pass==0?"9g_cluster_8bit_music.png":pass==1?"9g_cluster_8bit_music_en.png":pass==2?"9g_cluster_8bit_music_scroll.png":pass==3?"9g_cluster_8bit_chase_music.png":"9g_cluster_8bit_chase_music_nocover.png"));}
            ClusterKit.artwork=null;
        }
        taxi(out,"9d_cluster_taxi_receipt",model,now,ms.snapshot(now),true);
        taxiArea(out,"9d_cluster_taxi_busan_out",model,now,ms.snapshot(now));
        for(int ci=0;ci<Circuits.count();ci++)circuit(out,"9o_circuit_"+Circuits.id(ci),ci,model,now);
        {   // in Seoul: 자동 shows the nearest circuit and how far; then the list, nearest first
            BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
            CircuitCluster k=new CircuitCluster();k.feed(37.5663,126.9779,0);
            GpsFix.lat=37.5663;GpsFix.lon=126.9779;GpsFix.accuracy=6;GpsFix.at=System.nanoTime()/1000000;
            k.frame(c,model.snapshot(now),model,new DriveStats(),VehicleProfile.PETROL_DCT,now,null);ImageIO.write(img,"png",new File(out,"9o_circuit_seoul.png"));
            k.tap(100,80);k.frame(c,model.snapshot(now),model,new DriveStats(),VehicleProfile.PETROL_DCT,now,null);ImageIO.write(img,"png",new File(out,"9o_circuit_list.png"));
            GpsFix.at=0;
        }
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
        // 6MT clutch on F1 and performance, diesel DPF on engine/night/classic/retro (synthetic values on top of the log).
        Telemetry mt=new Telemetry();
        for(int k=0;k<31;k++)mt.hscanPd(0xc7,0x1d,new byte[]{0,0,0,0,0,0,0,0},now-40+k);   // frozen lever slot: a 6MT
        mt.hscanPd(0xc7,0x0c,new byte[]{0,0,0,0,0,0,0x66,0},now);mt.hscanPd(0xc7,0x08,new byte[]{5,11,(byte)0xe0,0x2e,11,10,0,0x7f},now);
        mt.hscanPd(0xc7,0x09,new byte[]{0,(byte)0xaf,0,(byte)0x8c,0,0,0,0x10},now);   // EMS12 clutch IN
        save(out,"x_f1_mt_clutch",new PitRenderer(),mt,now,true,false);
        Telemetry.Sample[] ps=model.snapshot(now);ps[Telemetry.CLUTCH]=new Telemetry.Sample(1,true,false,true,false,0);
        {BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas pc=new android.graphics.Canvas(img.createGraphics());
         perf.draw(pc,800,480,false,model,now,0,false,null);android.graphics.Paint bg=new android.graphics.Paint(1);bg.setColor(Theme.current().map(0xff040506));pc.drawRect(0,53,800,480,bg);
         new PerformanceRenderer().draw(pc,ps,null,now,VehicleProfile.PETROL_MT);ImageIO.write(img,"png",new File(out,"x_perf_mt_clutch.png"));}
        model.obd(0x8b,new byte[]{0x11,0x01,(byte)0x9e,0,0,0,0},now);   // regenerating, trigger 62 %
        int[] dl={11,9,0,2};String[] dn={"engine","night","classic","rally"};   // classic and rally carry the warning-lamp row
        for(int k=0;k<dl.length;k++)cluster(out,"x_"+dn[k]+"_diesel_regen",dl[k],model,now,rec.stats(),VehicleProfile.DIESEL_DCT);
        // hold-to-edit tiles (Tiles): a few picks on each layout, then back to the defaults
        Tiles.picked[Tiles.CLASSIC]=new int[]{1,Telemetry.CAT_TEMP,Telemetry.BOOST,Telemetry.THROTTLE_POS,Tiles.TIRES,Telemetry.CLUTCH_ODD,Telemetry.BAT_VOLTAGE,Tiles.CORNER_MAX_LAT};
        Tiles.picked[Tiles.RACE]=new int[]{Tiles.TIRES,Tiles.CLUTCH,Telemetry.CAT_TEMP,Telemetry.BAT_VOLTAGE};
        Tiles.picked[Tiles.F1]=new int[]{Tiles.TIRES,Tiles.CLUTCH};
        Tiles.picked[Tiles.RETRO]=new int[]{Tiles.TIRES,Tiles.CLUTCH,Telemetry.AMBIENT,Telemetry.FUEL_LEVEL};
        rec.setPage(PitRenderer.PAGE_CLASSIC);save(out,"x_tiles_classic",rec,model,now,false,false);
        race(out,"x_tiles_race",rec,model,now,VehicleProfile.PETROL_DCT,-1);
        save(out,"x_tiles_f1",new PitRenderer(),model,now,true,false);
        rec.setPage(PitRenderer.PAGE_RETRO);save(out,"x_tiles_retro",rec,model,now,false,false);
        for(int[] t:Tiles.picked)java.util.Arrays.fill(t,-1);
        cluster(out,"x_tiles_classic_mt",0,model,now,rec.stats(),VehicleProfile.PETROL_MT);   // a 6MT: engine load instead of the DCT clutches
        {Telemetry.Sample[] q=model.snapshot(now);DriveStats dst=new DriveStats();   // 수동 on a 7DCT: learned ratios, M gate, clutch temperatures
         float[] perKmh={0,49.5f,29.4f,19.4f,14.6f,11.6f,9.5f,8.0f};
         for(int g=1;g<=7;g++){dst.ratios.update(0,g,perKmh[g]*150,150,false);dst.ratios.update(2000,g,perKmh[g]*150,150,false);}
         q[8]=new Telemetry.Sample(4,true,false,true,false,0);q[7]=new Telemetry.Sample(90,true,false,true,false,0);q[0]=new Telemetry.Sample(90*14.6f,true,false,true,false,0);
         q[Telemetry.CLUTCH_ODD]=new Telemetry.Sample(112,true,false,true,false,0);q[Telemetry.CLUTCH_EVEN]=new Telemetry.Sample(156,true,false,true,false,0);q[10]=new Telemetry.Sample(18,true,false,true,false,0);
         BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
         new PitRenderer().draw(c,800,480,false,model,now,0,false,null);new LambdaCluster().frame(c,q,model,dst,VehicleProfile.PETROL_DCT,now,null);
         ImageIO.write(img,"png",new File(out,"9p_manual_dct.png"));}
        for(int rv=0;rv<2;rv++){Telemetry.Sample[] q=model.snapshot(now);   // 수동, 6MT in R and in N: the one N/R/P circle shows it
         q[8]=new Telemetry.Sample(rv==0?14:0,true,false,true,false,0);q[7]=new Telemetry.Sample(rv==0?4:0,true,false,true,false,0);q[0]=new Telemetry.Sample(rv==0?1200:800,true,false,true,false,0);
         BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
         new PitRenderer().draw(c,800,480,false,model,now,0,false,null);new LambdaCluster().frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_MT,now,null);
         ImageIO.write(img,"png",new File(out,rv==0?"9p_manual_reverse.png":"9p_manual_neutral.png"));}
        {Telemetry.Sample[] q=model.snapshot(now);   // 수동 with 모드 색 on in SPORT: the accent turns red
         q[8]=new Telemetry.Sample(3,true,false,true,false,0);q[7]=new Telemetry.Sample(90,true,false,true,false,0);q[0]=new Telemetry.Sample(4200,true,false,true,false,0);
         q[Telemetry.DRIVE_MODE]=new Telemetry.Sample(1,true,false,true,false,0);q[Telemetry.CLUTCH]=new Telemetry.Sample(0,true,false,true,false,0);q[9]=new Telemetry.Sample(80,true,false,true,false,0);
         Theme.mode(1);BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
         new PitRenderer().draw(c,800,480,false,model,now,0,false,null);new LambdaCluster().frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_MT,now,null);
         ImageIO.write(img,"png",new File(out,"9p_manual_sport_color.png"));Theme.mode(-1);}
        {   // 에코: a scripted 3-minute trip fed through the trip counter, then frames in each state and view
         java.io.File rr=new java.io.File(System.getProperty("preview.root"),"apps/dashboard/res/raw");
         EcoCluster.CAR=android.graphics.Bitmap.of(ImageIO.read(new File(rr,"eco_car_rear.png")));EcoCluster.PAINT=android.graphics.Bitmap.of(ImageIO.read(new File(rr,"eco_car_paint.png")));
         EcoCluster k=new EcoCluster();EcoCluster.reset(0);EcoCluster.hintDone=true;
         float[][] K={{0,0,.65f,0},{4,0,.65f,0},{6,12,9,45},{9,35,11,50},{12,50,6.5f,38},{16,50,2.6f,22},{18,55,3.4f,26},{21,58,3,24},{22,58,0,0},{28,40,0,0},{29,40,2.2f,18},{33,42,2.4f,19},{35,46,16,70},{38,76,20,85},{40,80,9,45},{44,80,4.6f,28},{46,80,0,0},{50,58,0,0}};
         long t0=1000000;
         for(int i=0;i<3600*4;i++){float ts=(i*0.25f)%50;float[] r=ecoAt(K,ts);long t=t0+i*250L;   // an hour: a few bottles on the shelf
             k.background(ecoQ(model,now,r,ts>=11&&ts<=24),t);}
         String[] names={"9q_eco_cruise","9q_eco_idle","9q_eco_cut","9q_eco_coins","9q_eco_coins_hard","9q_eco_l100","9q_eco_price","9q_eco_diesel_regen","9q_eco_price_diesel"};
         float[] ecoT={14,2,25,25,37,14,14,14,14};
         for(int n=0;n<names.length;n++){
             EcoCluster.coins=n==3||n==4;EcoCluster.l100=n==5;
             VehicleProfile pf=n>=7?VehicleProfile.DIESEL_DCT:VehicleProfile.PETROL_DCT;
             long t=t0+3600*4*250L+n*100000L;BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
             for(int f=0;f<60;f++){float ts=ecoT[n]-3+f*0.05f;Telemetry.Sample[] q=ecoQ(model,now,ecoAt(K,ts),n==7);if(n==6&&f==0)EcoCluster.grade=1;
                 k.background(q,t+f*50L);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
                 if(f==59)new PitRenderer().draw(c,800,480,false,model,now,0,false,null);
                 if((n==6||n==8)&&f==58)k.tap(665,120);
                 k.frame(c,q,model,new DriveStats(),pf,t+f*50L,null);}
             ImageIO.write(img,"png",new File(out,names[n]+".png"));}
         EcoCluster.coins=false;EcoCluster.l100=false;EcoCluster.grade=0;
        }
        {   // 타코: {gear, km/h, rpm, clutch, brake bar, pedal %}; neutral after a frame in 5th, so the last gear is known
         float[][] tk={{5,75,2030,0,0,22},{3,68,2300,1,22,35},{0,73,900,1,0,0},{2,52,5950,0,0,100},{4,90,2970,0,0,30}};
         String[] tcn={"9r_tacho_cruise","9r_tacho_blip","9r_tacho_neutral","9r_tacho_shift","9r_tacho_dct"};
         TachoCluster k=new TachoCluster();k.shiftTarget=6000;
         for(int i=0;i<tk.length;i++){float[] r=tk[i];Telemetry.Sample[] q=model.snapshot(now);
             q[8]=new Telemetry.Sample(r[0],true,false,true,false,0);q[7]=new Telemetry.Sample(r[1],true,false,true,false,0);q[0]=new Telemetry.Sample(r[2],true,false,true,false,0);
             q[Telemetry.CLUTCH]=new Telemetry.Sample(r[3],true,false,true,false,0);q[10]=new Telemetry.Sample(r[4],true,false,true,false,0);q[9]=new Telemetry.Sample(r[5],true,false,true,false,0);
             VehicleProfile pf=i==4?VehicleProfile.PETROL_DCT:VehicleProfile.PETROL_MT;
             if(i==2){Telemetry.Sample[] w=model.snapshot(now);w[8]=new Telemetry.Sample(5,true,false,true,false,0);w[7]=new Telemetry.Sample(80,true,false,true,false,0);
                 BufferedImage tmp=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);k.frame(new android.graphics.Canvas(tmp.createGraphics()),w,model,new DriveStats(),pf,now,null);}
             BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
             new PitRenderer().draw(c,800,480,false,model,now,0,false,null);k.frame(c,q,model,new DriveStats(),pf,now+i*1000,null);
             ImageIO.write(img,"png",new File(out,tcn[i]+".png"));}
        }
        // 수동 screen (the Λ layout, 6MT): {gear, km/h, rpm, clutch, brake bar}; the race and performance pages turn the rpm green on a match
        float[][] ds={{4,80,1500,1,22},{5,100,2800,0,0},{3,60,2800,0,25},{6,96,2200,0,0},{4,80,3720,1,22},{3,122,5750,0,0}};String[] dsn={"a_4th80_clutch","b_5th100_cruise","c_3rd60_brake","d_6th96","e_4th80_match","f_3rd122_upshift"};
        for(int k=0;k<ds.length;k++){Telemetry.Sample[] q=model.snapshot(now);float[] d=ds[k];
            q[8]=new Telemetry.Sample(d[0],true,false,true,false,0);q[7]=new Telemetry.Sample(d[1],true,false,true,false,0);q[0]=new Telemetry.Sample(d[2],true,false,true,false,0);
            q[Telemetry.CLUTCH]=new Telemetry.Sample(d[3],true,false,true,false,0);q[10]=new Telemetry.Sample(d[4],true,false,true,false,0);q[Telemetry.SHIFT_HINT]=new Telemetry.Sample(0,true,false,true,false,0);
            ShiftGuide.Cue cue=ShiftGuide.evaluate(q,ShiftGuide.target(0,VehicleProfile.PETROL_MT),VehicleProfile.PETROL_MT);
            BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
            PitRenderer bgR=new PitRenderer();bgR.draw(c,800,480,false,model,now,0,false,null);android.graphics.Paint bg=new android.graphics.Paint(1);bg.setColor(Theme.current().map(0xff040506));c.drawRect(0,53,800,480,bg);
            if(k==0||k==4)q[9]=new Telemetry.Sample(28,true,false,true,false,0);if(k==5)q[9]=new Telemetry.Sample(100,true,false,true,false,0);   // heel-toe: throttle blipped with the brake on, clutch in
            DriveStats dst=new DriveStats();ShiftCoach co=dst.coach;co.count=11;
            co.last=k==1||k==5?new ShiftCoach.Shift(2,3,false,-60,0.31f,0.06f):k==2?new ShiftCoach.Shift(4,3,false,-320,0.55f,0.21f):k==3?null:new ShiftCoach.Shift(5,4,true,40,0.42f,0.08f);
            new LambdaCluster().frame(c,q,model,dst,VehicleProfile.PETROL_MT,now,null);
            ImageIO.write(img,"png",new File(out,"9p_manual_"+dsn[k]+".png"));
            if(k==4){img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);c=new android.graphics.Canvas(img.createGraphics());bgR.draw(c,800,480,false,model,now,0,false,null);c.drawRect(0,53,800,480,bg);
                new RaceRenderer().draw(c,q,cue,now,rec.stats(),VehicleProfile.PETROL_MT,false,null,null);ImageIO.write(img,"png",new File(out,"x_race_mt_match.png"));
                img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);c=new android.graphics.Canvas(img.createGraphics());bgR.draw(c,800,480,false,model,now,0,false,null);c.drawRect(0,53,800,480,bg);
                new PerformanceRenderer().draw(c,q,null,now,VehicleProfile.PETROL_MT);ImageIO.write(img,"png",new File(out,"x_perf_mt_match.png"));}}
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
    /** Race tab for a profile the 7DCT log cannot detect: header from PitRenderer, body redrawn with prof and the
     * clutch sample forced (-1 leaves it as logged). */
    private static void race(File dir,String name,PitRenderer r,Telemetry m,long now,VehicleProfile prof,int clutch) throws Exception {race(dir,name,r,m,now,prof,clutch,0);}
    private static void race(File dir,String name,PitRenderer r,Telemetry m,long now,VehicleProfile prof,int clutch,int hint) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
        r.draw(c,800,480,false,m,now,0,false,null);
        Telemetry.Sample[] s=m.snapshot(now);
        if(clutch>=0)s[Telemetry.CLUTCH]=new Telemetry.Sample(clutch,true,false,true,false,0);
        if(hint!=0)s[Telemetry.SHIFT_HINT]=new Telemetry.Sample(hint,true,false,true,false,0);
        android.graphics.Paint bg=new android.graphics.Paint(1);bg.setColor(Theme.current().map(0xff040506));c.drawRect(0,53,800,480,bg);
        new RaceRenderer().draw(c,s,ShiftGuide.evaluate(s,ShiftGuide.target(0,prof),prof),now,r.stats(),prof,false,null,null);
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
    private static void cluster(File dir,String name,int layout,Telemetry m,long now,DriveStats st,VehicleProfile prof) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
        new ClusterRenderer().draw(new android.graphics.Canvas(img.createGraphics()),layout,m.snapshot(now),now,st,prof,m,null);
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
    private static void taxi(File dir,String name,Telemetry m,long now,MediaState.Snapshot track,boolean endTrip) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);ClusterRenderer r=new ClusterRenderer();
        android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
        r.draw(c,3,m.snapshot(now),now,new DriveStats(),VehicleProfile.PETROL_DCT,m,track);   // the car moves: trip starts
        if(endTrip)r.tap(3,150,420);
        r.draw(c,3,m.snapshot(now),now+50,new DriveStats(),VehicleProfile.PETROL_DCT,m,track);
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
    /** A trip started in Busan (GpsFix), then four seconds in Yangsan: Busan's table with its 30 % out-of-area surcharge. */
    private static void taxiArea(File dir,String name,Telemetry m,long now,MediaState.Snapshot track) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);ClusterRenderer r=new ClusterRenderer();
        android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
        GpsFix.set(35.1798,129.0750,8);   // Busan City Hall
        r.draw(c,3,m.snapshot(now),now,new DriveStats(),VehicleProfile.PETROL_DCT,m,track);
        GpsFix.set(35.3350,129.0373,8);   // Yangsan City Hall
        for(int i=1;i<=4;i++)r.draw(c,3,m.snapshot(now),now+i*1001,new DriveStats(),VehicleProfile.PETROL_DCT,m,track);
        GpsFix.at=0;
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
    /** Circuit ci driven along its own track at about 120 km/h (one fix a second): a lap and a half, the second lap
     *  2 s quicker, drawn mid-lap. */
    private static void circuit(File dir,String name,int ci,Telemetry m,long now) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
        android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
        CircuitCluster k=new CircuitCluster();
        int n=Circuits.points(ci);double t=0,want=33.3,s=0,next=0;double[] at=Circuits.point(ci,n-3);
        k.feed(at[0],at[1],0);   // just before the line
        for(int lap=0;lap<3;lap++)for(int i=0;i<n;i++){
            if(lap==2&&i>n*0.45)break;
            double[] a=Circuits.point(ci,i),b=Circuits.point(ci,(i+1)%n);
            double kx=111320*Math.cos(Math.toRadians(a[0])),len=Math.hypot((b[1]-a[1])*kx,(b[0]-a[0])*110540),v=lap==1?want*1.03:want;
            for(double d=next;d<len;d+=v){double f=d/len;t+=1000;k.feed(a[0]+f*(b[0]-a[0]),a[1]+f*(b[1]-a[1]),(long)t);next=d+v-len;}
            if(next<0)next=0;
        }
        GpsFix.lat=at[0];GpsFix.lon=at[1];GpsFix.accuracy=4;GpsFix.at=System.nanoTime()/1000000;   // fresh, without a new fix (seq) for the cluster to take
        k.frame(c,m.snapshot(now),m,new DriveStats(),VehicleProfile.PETROL_DCT,now,null);
        GpsFix.at=0;
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
    private static void save(File dir,String name,PitRenderer r,Telemetry model,long now,boolean cluster,boolean media) throws Exception {
        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
        r.draw(new android.graphics.Canvas(img.createGraphics()),800,480,cluster,model,now,0,media,null);
        ImageIO.write(img,"png",new File(dir,name+".png"));
    }
    private static float[] ecoAt(float[][] K,float t){for(int i=0;i<K.length-1;i++)if(t<=K[i+1][0]){float[] a=K[i],b=K[i+1];float f=(t-a[0])/(b[0]-a[0]);return new float[]{a[1]+(b[1]-a[1])*f,a[2]+(b[2]-a[2])*f,a[3]+(b[3]-a[3])*f};}
        float[] e=K[K.length-1];return new float[]{e[1],e[2],e[3]};}
    private static Telemetry.Sample[] ecoQ(Telemetry model,long now,float[] r,boolean regen){Telemetry.Sample[] q=model.snapshot(now);
        q[7]=new Telemetry.Sample(r[0],true,false,true,false,0);q[Telemetry.FUEL_RATE]=new Telemetry.Sample(r[1]+(regen&&r[1]>0.05f?1.3f:0),true,false,true,false,0);
        q[9]=new Telemetry.Sample(r[2],true,false,true,false,0);q[0]=new Telemetry.Sample(r[0]<3?780:900+r[0]*28,true,false,true,false,0);
        q[8]=new Telemetry.Sample(r[0]<3?0:r[0]<18?1:r[0]<30?2:r[0]<45?3:r[0]<60?4:5,true,false,true,false,0);
        q[Telemetry.DPF_REGEN]=new Telemetry.Sample(regen?1:0,true,false,true,false,0);return q;}
}
