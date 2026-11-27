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
        {   // 길안내: the stock navigation's next turn as a band over the page, then opened big
         String d=String.join("\n","Navigation Cluster:","  TBT SubInfo:","    distance      = 12.4 METER","    minutes       = 21","",
             "  TBT Info:","    Display Type  = TURN","    Direction     = 270.0","    Distance      = 350.0 METER","","  DestName = 0xf4 0x00 0x8c 0xd6 0xac 0xc0 ","");
         Tbt.set(Tbt.parse(d),now);save(out,"4c_race_tbt",rec,model,now,false,false);
         PitRenderer tb=new PitRenderer();tb.setSymmetric(true);tb.setPage(PitRenderer.PAGE_RACE);tb.tbtBig=true;save(out,"4c_race_tbt_big",tb,model,now,false,false);
         Tbt.set(Tbt.NONE,now);}
        model.hscanPd(0xc7,0x1c,new byte[]{2,1,0,0},now);save(out,"4c_race_tcs_off",rec,model,now,false,false);   // stability switches: TCS off, then TCS and ESC off
        model.hscanPd(0xc7,0x1c,new byte[]{2,2,0,0},now);save(out,"4c_race_esc_off",rec,model,now,false,false);model.hscanPd(0xc7,0x1c,new byte[]{0,0,0,0},now);
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
         d.maxLat=1.12f;d.maxDecel=1.08f;d.maxBoost=1.52f;d.maxRpm=6742;d.maxSpeed=236;d.maxClutchOdd=196;d.maxClutchEven=102;
         d.brakeLastM=d.brakeBestM=36.4f;d.brakeLastS=2.71f;d.quarterLastS=d.quarterBestS=14.86f;d.quarterTrap=158;save(out,"4d_records_full",rec,model,now,false,false);
         d.quarterRunning=true;d.quarterStart=now-6200;d.quarterDist=168;save(out,"4d_records_quarter_live",rec,model,now,false,false);d.quarterRunning=false;
         d.maxLat=keep[0];d.maxDecel=keep[1];d.maxBoost=keep[2];d.maxRpm=keep[3];d.maxSpeed=keep[4];d.maxClutchOdd=keep[5];d.maxClutchEven=keep[6];}
        model.obd(0x46,new byte[]{(byte)58},now);model.obd(0x2f,new byte[]{(byte)158},now);   // ambient 18 C, fuel 62 % (the replayed log has neither; the classic tiles show them)
        String[] layouts={"9a_cluster_classic","9b_cluster_retro","9c_cluster_rally","9d_cluster_taxi","9e_cluster_80s","9f_cluster_hud","9g_cluster_8bit","9g_cluster_8bit_chase",
            "9i_cluster_gcircle","9j_cluster_night","9k_cluster_drag","9l_cluster_engine","9m_cluster_cassette","9n_cluster_warp","9o_cluster_circuit"};
        for(int i=0;i<layouts.length;i++){rec.setPage(PitRenderer.PAGE_CLASSIC+i);save(out,layouts[i],rec,model,now,false,false);}
        rec.setPicker(true,false,0);save(out,"9_cluster_list",rec,model,now,false,false);rec.setPicker(false,false,0);
        {   // trim (the dashboard out of sight): every screen with kept bitmaps draws the same after letting go of them
            int[] pages=new int[layouts.length+6];for(int i=0;i<layouts.length;i++)pages[i]=PitRenderer.PAGE_CLASSIC+i;pages[layouts.length]=PitRenderer.PAGE_ECO;pages[layouts.length+1]=PitRenderer.PAGE_TACHO;for(int i=0;i<3;i++)pages[layouts.length+2+i]=PitRenderer.PAGE_TACHO+1+i;pages[layouts.length+5]=PitRenderer.PAGE_CLASSIC;
            int bad=0;StringBuilder why=new StringBuilder();
            for(int pg:pages){BufferedImage[] im=new BufferedImage[2];
                for(int m=0;m<2;m++){if(m==1)rec.trim();rec.setPage(pg);BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                    rec.draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);im[m]=img;}
                int diff=0;for(int yy=0;yy<480;yy++)for(int xx=0;xx<800;xx++)if(im[0].getRGB(xx,yy)!=im[1].getRGB(xx,yy))diff++;
                if(diff>0){bad++;why.append(" page "+pg+": "+diff+" px");}}
            rec.setPage(PitRenderer.PAGE_CLASSIC);System.out.println("trim: "+pages.length+" screens redrawn, "+bad+" differ"+why);
            if(bad>0)throw new AssertionError("a screen draws differently after trim:"+why);}
        {   // 엔진 경고등(MIL) lit: the new lamp where each screen gives it a home — race status strip, EICAS stack, classic/retro/rally warnings row
            model.tm(Telemetry.MIL,1,true,now);
            rec.setPage(PitRenderer.PAGE_RACE);save(out,"x_mil_race",rec,model,now,false,false);
            rec.setPage(PitRenderer.PAGE_CLASSIC+11);save(out,"x_mil_engine",rec,model,now,false,false);
            rec.setPage(PitRenderer.PAGE_CLASSIC+0);save(out,"x_mil_classic",rec,model,now,false,false);
            rec.setPage(PitRenderer.PAGE_CLASSIC+1);save(out,"x_mil_retro",rec,model,now,false,false);
            rec.setPage(PitRenderer.PAGE_CLASSIC+2);save(out,"x_mil_rally",rec,model,now,false,false);
            rec.setPage(PitRenderer.PAGE_CLASSIC+4);save(out,"x_mil_80s",rec,model,now,false,false);
            rec.setPage(PitRenderer.PAGE_CLASSIC+9);save(out,"x_mil_night",rec,model,now,false,false);
            model.tm(Telemetry.MIL,0,true,now);rec.setPage(PitRenderer.PAGE_CLASSIC);
        }
        Telemetry.Sample sel=model.sample(Telemetry.GEAR_SELECTOR,now);   // G circle in the DCT manual gate (S -> "M" + gear), then the lever back
        model.hscanPd(0xc7,0x1d,new byte[]{0,8,0,0,0,0,0,0},now);rec.setPage(PitRenderer.PAGE_CLASSIC+java.util.Arrays.asList(layouts).indexOf("9i_cluster_gcircle"));
        save(out,"9i_cluster_gcircle_m",rec,model,now,false,false);
        for(int[] pv:new int[][]{{7,'R'},{6,'N'}}){model.hscanPd(0xc7,0x1d,new byte[]{0,(byte)pv[0],0,0,0,0,0,0},now);   // 80s: R / N lit in the gear segments (P needs no gear, the preview drives in 4th)
            rec.setPage(PitRenderer.PAGE_CLASSIC+java.util.Arrays.asList(layouts).indexOf("9e_cluster_80s"));save(out,"x_80s_gear_"+(char)pv[1],rec,model,now,false,false);}
        int back=!sel.valid?5:sel.value==0?0:sel.value==1?7:sel.value==2?6:sel.value==4?8:5;model.hscanPd(0xc7,0x1d,new byte[]{0,(byte)back,0,0,0,0,0,0},now);
        // Which layouts follow the theme: render a few under Copper. Night/Engine/Warp (and G-circle/Drag) recolour; 8-bit and HUD keep their own palette.
        rec.setPicker(false,false,0);Theme.select("copper");
        int[] tl={9,11,13,8,10,6,5};String[] tn={"night","engine","warp","gcircle","drag","8bit","hud"};
        for(int ti=0;ti<tl.length;ti++){rec.setPage(PitRenderer.PAGE_CLASSIC+tl[ti]);save(out,"z_theme_"+tn[ti]+"_copper",rec,model,now,false,false);}
        Theme.select(System.getProperty("preview.theme","n"));   // back to the theme this run renders in
        String[] cats={"screens","mode","float","theme","paint","lang","font","rpm","sound","fx","diag"};   // settings (the gear), one shot per category, in SettingsLayout order
        rec.setPicker(true,true,(1<<2)|(1<<7)|(1<<12)|(1<<20));rec.setModeOptions(true,false);rec.setSound(true,0,0,0,SettingsLayout.SOUND_DEFAULT);
        int[] targets={ClusterPicker.F1+2,ClusterPicker.GAUGES,-1};   // SPORT set to the race page, ECO to the gauges
        for(int k=0;k<cats.length;k++){rec.setSettings(k,"auto",-1,targets);save(out,"9_settings_"+cats[k],rec,model,now,false,false);}
        {   // 글꼴: the stock row, the ten fonts inside the app (each name in its own face), one picked; the next page; a refused head unit
            java.io.File fd=new java.io.File(System.getProperty("preview.root",""),"apps/dashboard/assets/fonts");
            int n=1+BundledFonts.FONTS.length;PitRenderer.FontView v=new PitRenderer.FontView();
            v.names=new String[n];v.details=new String[n];v.usable=new boolean[n];v.faces=new android.graphics.Typeface[n];
            v.names[0]="순정 글꼴 (Mobis · Roboto)";v.details[0]="공장 출고 설정";v.usable[0]=true;
            for(int i=0;i<BundledFonts.FONTS.length;i++){BundledFonts.Font b=BundledFonts.FONTS[i];v.names[i+1]=b.name;v.usable[i+1]=true;
                v.details[i+1]="앱에 포함 · "+(b.kind==BundledFonts.HANDWRITING?"손글씨":b.kind==BundledFonts.MYEONGJO?"명조":"고딕")+" · "+(b.bold!=null?"R·B":"R")+" · 한글 전체"+String.format(java.util.Locale.US," · %.1f MB",b.bytes/1048576.0);
                v.faces[i+1]=android.graphics.Typeface.createFromFile(new java.io.File(fd,b.regular));}
            v.scanned=true;v.current=0;v.pick=2;v.currentName=v.names[0];v.free=40265318L;v.root=true;
            v.regular=v.faces[2];v.bold=android.graphics.Typeface.createFromFile(new java.io.File(fd,BundledFonts.FONTS[1].bold));
            rec.setSettings(SettingsLayout.FONT,"auto",-1,targets);rec.setFonts(v);save(out,"9_settings_font",rec,model,now,false,false);
            v.top=6;v.pick=8;v.regular=v.faces[8];v.bold=null;v.pending=true;v.busy=true;save(out,"9_settings_font_page2",rec,model,now,false,false);
            v.top=0;v.pick=0;v.regular=null;v.loading=false;v.busy=false;v.pending=false;v.refusal="확인하지 않은 amossu입니다 (0123456789ab…)";save(out,"9_settings_font_refused",rec,model,now,false,false);
            v.refusal=null;v.pick=1;v.regular=v.faces[1];v.bold=android.graphics.Typeface.createFromFile(new java.io.File(fd,BundledFonts.FONTS[0].bold));
            com.veloster.i18n.UiText.configure("en","ko");save(out,"9_settings_font_en",rec,model,now,false,false);com.veloster.i18n.UiText.configure("ko","ko");
        }
        {   // 플로팅 전체 모양 시안: FORMS 18개를 개요 배경 위에 자동 칸으로 (design overhaul reference)
            int[] FG=FloatPanel.FORMS;
            String[] fs={"card","strip","side","tick","fills","round","dial","twin","bars","pill","gearbox","wedge","shift","vcap","vrpm","tower","hero","diag","music"};
            if(fs.length!=FG.length)throw new AssertionError("float gallery names out of step with FloatPanel.FORMS");
            MediaState fm=new MediaState();fm.mode(0x36);fm.update(1,true,"한강 따라 달리는 밤의 드라이브 (Night Drive Remix)",true,"City Lights Band",true,83000,true,214000,false,now);fm.playback(1,1);
            FloatPanel.music(fm.snapshot(now),null);
            int shape0=FloatPanel.shape;boolean glide0=FloatPanel.glideOn;FloatPanel.glideOn=false;FloatPanel.shape=FloatPanel.CARD;   // no glide: every shot here shares one clock, so a bar would never arrive   // 자동 cells: a DCT shows both clutches 4th, a 6MT moves intake up
            if(FloatPanel.resolve(-1,3,VehicleProfile.PETROL_DCT)!=FloatPanel.DCT2)throw new AssertionError("auto: DCT clutches 4th on a DCT");
            if(FloatPanel.resolve(-1,3,VehicleProfile.PETROL_MT)!=Telemetry.INTAKE)throw new AssertionError("auto: a 6MT skips the DCT cell");
            Telemetry.Sample[] gq=model.snapshot(now).clone();   // the log predates the BCM tyre temperatures: example ones, so the tyre cells show them
            {float[] tt={28,29,31,30};for(int w=0;w<4;w++)gq[Telemetry.TIRE_TEMP_FL+w]=new Telemetry.Sample(tt[w],true,false,true,false,0);}
            for(int i=0;i<FG.length;i++){int sh=FG[i],n=FloatPanel.fitCount(sh,4),sz=1;FloatPanel.shape=sh;   // each shape's own 자동 cells
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(sh,n,sz),FloatPanel.defaultY(sh,n,sz));
                FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,sz,gq,VehicleProfile.PETROL_DCT,now);
                ImageIO.write(img,"png",new File(out,"x_floatall_"+fs[i]+".png"));}
            {   // the three quiet looks on all 19 shapes; coolant 112 degrees (signals 1 and 2) so the warning colours show, the white body's included
                Telemetry.Sample[] wq=gq.clone();wq[1]=wq[2]=new Telemetry.Sample(112,true,false,true,false,0);
                int[] sk={FloatSkin.CARD,FloatSkin.WHITE,FloatSkin.OEM};String[] skn={"card","white","oem"};int skin0=FloatPanel.skin,night0=FloatPanel.skinNight;
                for(int si=0;si<sk.length;si++){FloatPanel.setActiveSkin(sk[si]);
                    for(int i=0;i<FG.length;i++){int sh=FG[i],n=FloatPanel.fitCount(sh,4);FloatPanel.shape=sh;
                        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                        new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                        java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(sh,n,1),FloatPanel.defaultY(sh,n,1));
                        FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,1,wq,VehicleProfile.PETROL_DCT,now);
                        ImageIO.write(img,"png",new File(out,"x_floatlook_"+skn[si]+"_"+fs[i]+".png"));}}
                FloatPanel.skin=skin0;FloatPanel.skinNight=night0;}
            {   // 세로 캡슐 with 음악 띠 on and a track playing: its last two rows give way to the track box
                int sh=FloatPanel.VCAP,n=FloatPanel.fitCount(sh,4);FloatPanel.shape=sh;FloatPanel.musicStrip=true;
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(sh,n,1),FloatPanel.defaultY(sh,n,1));
                FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,1,gq,VehicleProfile.PETROL_DCT,(now/100000)*100000);
                ImageIO.write(img,"png",new File(out,"x_floatall_vcap_music.png"));FloatPanel.musicStrip=false;}
            {   // 변속 알림 steps: 800, 400 and 100 rpm under the shift point (yellow warming to red), then at it (the flash on), side by side
                int tgt=ShiftGuide.target(0,VehicleProfile.PETROL_DCT);int[] rpms={tgt-800,tgt-400,tgt-100,tgt+100};
                if(!Float.isNaN(FloatPanel.shiftLevel(gq,VehicleProfile.PETROL_DCT)))throw new AssertionError("shift light: idle rpm shows nothing");
                String[] sn={"card","vcap","pill"};int[] ss={FloatPanel.CARD,FloatPanel.VCAP,FloatPanel.PILL};
                for(int m=0;m<ss.length;m++){int sh=ss[m],n=FloatPanel.fitCount(sh,4);FloatPanel.shape=sh;int w=FloatPanel.width(sh,n,1),h=FloatPanel.height(sh,n,1),x0=FloatPanel.defaultX(sh,n,1),y0=FloatPanel.defaultY(sh,n,1);
                    int pad=10,cw=w+2*pad,ch=Math.min(480,h+2*pad);BufferedImage strip=new BufferedImage(cw*rpms.length+8*(rpms.length-1),ch,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D sg=strip.createGraphics();sg.setColor(new java.awt.Color(0x282828));sg.fillRect(0,0,strip.getWidth(),ch);
                    for(int st=0;st<rpms.length;st++){Telemetry.Sample[] sq=gq.clone();sq[0]=new Telemetry.Sample(rpms[st],true,false,true,false,0);sq[8]=new Telemetry.Sample(3,true,false,true,false,0);
                        BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                        new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                        java.awt.Graphics2D g2=img.createGraphics();g2.translate(x0,y0);
                        FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,1,sq,VehicleProfile.PETROL_DCT,now-now%180);
                        int cx=Math.max(0,x0-pad),cy=Math.max(0,y0-pad);sg.drawImage(img.getSubimage(cx,cy,Math.min(cw,800-cx),Math.min(ch,480-cy)),st*(cw+8),0,null);}
                    ImageIO.write(strip,"png",new File(out,"x_floatstage_"+sn[m]+".png"));}}
            {   // 변속 번쩍임: 6,500 rpm in 3rd (over the default shift point), the flash's on phase
                Telemetry.Sample[] hq=gq.clone();hq[0]=new Telemetry.Sample(6500,true,false,true,false,0);hq[8]=new Telemetry.Sample(3,true,false,true,false,0);
                long on=now-now%180;
                for(int i=0;i<FG.length;i++){int sh=FG[i],n=FloatPanel.fitCount(sh,4),sz=1;FloatPanel.shape=sh;
                    BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                    new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                    java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(sh,n,sz),FloatPanel.defaultY(sh,n,sz));
                    FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,sz,hq,VehicleProfile.PETROL_DCT,on);
                    ImageIO.write(img,"png",new File(out,"x_floatflash_"+fs[i]+".png"));}}
            FloatPanel.shape=shape0;FloatPanel.glideOn=glide0;
            FloatPanel.music(null,null);
        }
        com.veloster.i18n.UiText.configure("en","ko");   // English: the longer words must still fit
        for(int k:new int[]{SettingsLayout.SCREENS,SettingsLayout.FLOAT,SettingsLayout.SOUND}){rec.setSettings(k,"auto",-1,targets);save(out,"9_settings_"+cats[k]+"_en",rec,model,now,false,false);}
        com.veloster.i18n.UiText.configure("ko","ko");
        rec.setSettings(SettingsLayout.SOUND,"auto",-1,targets);rec.setOrder(null,80);save(out,"9_settings_sound_scrolled",rec,model,now,false,false);   // hints above and below
        rec.setOrder(null,SettingsLayout.maxPaneScroll(SettingsLayout.SOUND));save(out,"9_settings_sound_end",rec,model,now,false,false);   // the last rows
        rec.setSettings(SettingsLayout.SCREENS,"auto",-1,targets);rec.setOrder(null,62);rec.setScreenDrag(2,250);save(out,"9_settings_screens_drag",rec,model,now,false,false);rec.setScreenDrag(-1,0);rec.setOrder(null,0);
        {   // the floating window (FloatPanel): shapes, cell counts and sizes over the 개요 page standing in for another app
            int[][] combos={{1,4,1},{1,4,3},{1,2,1},{1,1,2},{2,4,1},{2,2,0},{3,4,1},{3,2,2}};
            String[] cn={"card4","card4_xl","card2","card1_l","strip4","strip2_s","side4","side2_l"};FloatPanel.cells[2]=-1;
            for(int ci=0;ci<combos.length;ci++){int sh=combos[ci][0],n=combos[ci][1],sz=combos[ci][2];
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(sh,n,sz),FloatPanel.defaultY(sh,n,sz));
                FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,sz,model.snapshot(now),VehicleProfile.PETROL_DCT,now);ImageIO.write(img,"png",new File(out,"x_float_"+cn[ci]+".png"));}
            {   // music: the 음악 띠 under four shapes and the 음악 shape (1 and 2 cells, two sizes, nothing playing)
                MediaState mm=new MediaState();mm.mode(0x36);mm.update(1,true,"한강 따라 달리는 밤의 드라이브 (Night Drive Remix)",true,"City Lights Band",true,83000,true,214000,false,now);mm.playback(1,1);
                MediaState none=new MediaState();
                Object[][] mc={{FloatPanel.CARD,4,1,true,"strip_card"},{FloatPanel.STRIP,4,1,true,"strip_strip"},{FloatPanel.SIDE,2,1,true,"strip_side"},{FloatPanel.ROUND,4,1,true,"strip_round"},
                    {FloatPanel.MUSIC,2,1,true,"shape2"},{FloatPanel.MUSIC,1,1,true,"shape1"},{FloatPanel.MUSIC,2,2,true,"shape2_large"},{FloatPanel.MUSIC,2,1,false,"shape_none"},{FloatPanel.CARD,4,1,false,"strip_none"}};
                for(Object[] m:mc){int sh=(Integer)m[0],n=(Integer)m[1],sz=(Integer)m[2];FloatPanel.musicStrip=sh!=FloatPanel.MUSIC;FloatPanel.music(((Boolean)m[3]?mm:none).snapshot(now),null);
                    BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                    java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(sh,n,sz),FloatPanel.defaultY(sh,n,sz));
                    FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,sz,model.snapshot(now),VehicleProfile.PETROL_DCT,now);ImageIO.write(img,"png",new File(out,"x_float_music_"+m[4]+".png"));}
                {FloatPanel.musicStrip=false;FloatPanel.music(mm.snapshot(now),null);FloatPanel.cells[0]=Telemetry.BOOST;FloatPanel.cells[1]=0;   // vacuum beside the rpm: the large value gives way
                 Telemetry.Sample[] vq=model.snapshot(now);vq[Telemetry.BOOST]=new Telemetry.Sample(-0.65f,true,false,true,false,0);vq[0]=new Telemetry.Sample(1561,true,false,true,false,0);
                 BufferedImage img=new BufferedImage(300,140,BufferedImage.TYPE_INT_RGB);FloatPanel.draw(new android.graphics.Canvas(img.createGraphics()),FloatPanel.MUSIC,2,1,vq,VehicleProfile.PETROL_DCT,now);
                 ImageIO.write(img,"png",new File(out,"x_float_music_shape2_vacuum.png"));FloatPanel.cells[0]=-1;FloatPanel.cells[1]=-1;}
                {android.graphics.Bitmap cover=android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);{android.graphics.Canvas cc=new android.graphics.Canvas(cover);android.graphics.Paint cp=new android.graphics.Paint(1);
                    cp.setColor(0xff7a3cc8);cc.drawRect(0,0,64,64,cp);cp.setColor(0xffffc04a);cc.drawCircle(40,26,18,cp);cp.setColor(0xff1b1030);cc.drawRect(0,46,64,64,cp);}   // a stand-in album cover
                 FloatPanel.musicStrip=true;FloatPanel.music(mm.snapshot(now),cover);int sk0=FloatPanel.skin,night0=FloatPanel.skinNight;   // the 음악 띠 in every 색감, still and scrolled 2.5 s on
                 for(int sk=0;sk<FloatSkin.NAMES.length;sk++)for(long dt:new long[]{0,2500}){FloatPanel.setActiveSkin(sk);int w=FloatPanel.width(FloatPanel.CARD,4,1),h=FloatPanel.height(FloatPanel.CARD,4,1);
                     BufferedImage img=new BufferedImage(w+20,h+20,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();g2.setColor(new java.awt.Color(0x6a7d8a));g2.fillRect(0,0,w+20,h+20);g2.translate(10,10);
                     long t0=(now/100000)*100000;FloatPanel.draw(new android.graphics.Canvas(g2),FloatPanel.CARD,4,1,model.snapshot(now),VehicleProfile.PETROL_DCT,t0+dt);ImageIO.write(img,"png",new File(out,"x_float_music_strip_skin"+sk+"_"+dt+".png"));}
                 for(int sk=0;sk<FloatSkin.NAMES.length;sk++){FloatPanel.setActiveSkin(sk);FloatPanel.musicStrip=false;int w=FloatPanel.width(FloatPanel.MUSIC,2,1),h=FloatPanel.height(FloatPanel.MUSIC,2,1);   // the 음악 shape in every 색감
                     BufferedImage img=new BufferedImage(w+20,h+20,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();g2.setColor(new java.awt.Color(0x6a7d8a));g2.fillRect(0,0,w+20,h+20);g2.translate(10,10);
                     FloatPanel.draw(new android.graphics.Canvas(g2),FloatPanel.MUSIC,2,1,model.snapshot(now),VehicleProfile.PETROL_DCT,now);ImageIO.write(img,"png",new File(out,"x_float_music_shape_skin"+sk+".png"));}
                 for(int sh:new int[]{FloatPanel.SIDE,FloatPanel.TOWER}){FloatPanel.setActiveSkin(0);FloatPanel.musicStrip=true;FloatPanel.music(mm.snapshot(now),cover);int[] cs=FloatPanel.counts(sh);int n=cs[cs.length-1];   // narrow shapes: the strip is words only
                     int w=FloatPanel.width(sh,n,1),h=FloatPanel.height(sh,n,1);BufferedImage img=new BufferedImage(w+16,h+16,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();g2.setColor(new java.awt.Color(0x6a7d8a));g2.fillRect(0,0,w+16,h+16);g2.translate(8,8);
                     FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,1,model.snapshot(now),VehicleProfile.PETROL_DCT,(now/100000)*100000);ImageIO.write(img,"png",new File(out,"x_float_music_narrow_"+sh+".png"));}
                 FloatPanel.skin=sk0;FloatPanel.skinNight=night0;}
                FloatPanel.musicStrip=false;FloatPanel.music(null,null);}
            {   // the three graphs, the line filled from the last 30 s of the drive (a sample every 0.5 s)
                Telemetry hm=new Telemetry();long t0=now-30000;int from=Math.max(0,last-(int)(31000/step));
                for(int q2=0;q2<=last;q2++){long tq=1_000_000+q2*step;feed(hm,raw.get(q2),tq);if(q2>=from&&(q2-from)%Math.max(1,(int)(500/step))==0)FloatPanel.draw(new android.graphics.Canvas(new BufferedImage(400,300,BufferedImage.TYPE_INT_ARGB).createGraphics()),FloatPanel.CARD,4,2,hm.snapshot(tq),VehicleProfile.PETROL_DCT,tq);}
                FloatPanel.graphs[0]=FloatPanel.LINE;FloatPanel.graphs[1]=FloatPanel.BAR;FloatPanel.graphs[2]=FloatPanel.LED;FloatPanel.graphs[3]=FloatPanel.LINE;FloatPanel.cells[3]=0;
                for(int[] cb:new int[][]{{1,4,2},{2,4,1}}){BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);
                    new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                    java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(cb[0],cb[1],cb[2]),FloatPanel.defaultY(cb[0],cb[1],cb[2]));
                    FloatPanel.draw(new android.graphics.Canvas(g2),cb[0],cb[1],cb[2],hm.snapshot(1_000_000+last*step),VehicleProfile.PETROL_DCT,1_000_000+last*step);ImageIO.write(img,"png",new File(out,"x_float_graphs_"+(cb[0]==1?"card":"strip")+".png"));}
                {   // no values yet (the car just woke): every cell the value alone, large and centred
                    BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                    java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(1,4,2),FloatPanel.defaultY(1,4,2));
                    FloatPanel.draw(new android.graphics.Canvas(g2),1,4,2,new Telemetry().snapshot(now),VehicleProfile.PETROL_DCT,now+1);ImageIO.write(img,"png",new File(out,"x_float_graphs_empty.png"));}
                FloatPanel.cells[3]=-1;
                {   // FloatPanel.tick: a redraw only when the window would look different
                    Telemetry.Sample[] e=new Telemetry().snapshot(now);int[] keep=FloatPanel.graphs.clone();java.util.Arrays.fill(FloatPanel.graphs,FloatPanel.BAR);long ft0=now+100000;
                    FloatPanel.tick(4,1,e,VehicleProfile.PETROL_DCT,ft0);
                    if(FloatPanel.tick(4,1,e,VehicleProfile.PETROL_DCT,ft0+50)||FloatPanel.tick(4,1,e,VehicleProfile.PETROL_DCT,ft0+2000))throw new AssertionError("float tick: nothing changed, no draw");
                    FloatPanel.graphs[0]=FloatPanel.LINE;
                    if(!FloatPanel.tick(4,1,e,VehicleProfile.PETROL_DCT,ft0+2050))throw new AssertionError("float tick: a graph changed, draw");
                    if(FloatPanel.tick(4,1,e,VehicleProfile.PETROL_DCT,ft0+2100))throw new AssertionError("float tick: same line sample, no draw");
                    if(!FloatPanel.tick(4,1,e,VehicleProfile.PETROL_DCT,ft0+2700))throw new AssertionError("float tick: a new line sample, draw");
                    if(!FloatPanel.tick(4,1,model.snapshot(now),VehicleProfile.PETROL_DCT,ft0+2750))throw new AssertionError("float tick: values changed, draw");
                    FloatPanel.tick(4,1,new Telemetry.Sample[e.length],VehicleProfile.DIESEL_DCT,ft0+2800);   // no sample for a signal: like no value, no crash
                    FloatPanel.track(4,model.snapshot(now),VehicleProfile.PETROL_DCT,ft0+2850);
                    System.arraycopy(keep,0,FloatPanel.graphs,0,4);}
            }
            FloatPanel.shape=FloatPanel.CARD;FloatPanel.count=2;   // the settings shot below shows the window on, two cells
        }
        {   // the 플로팅 pane: on with four cells, then a cell's chooser open (cell 2, the line graph picked)
            FloatPanel.shape=FloatPanel.CARD;FloatPanel.count=4;FloatPanel.graphs[1]=FloatPanel.LINE;FloatPanel.graphs[2]=FloatPanel.LED;
            rec.setSettings(SettingsLayout.FLOAT,"auto",-1,targets);rec.setFloatEdit(-1,0,-1,0,0);save(out,"9_settings_float_on",rec,model,now,false,false);
            rec.setFloatEdit(1,1,2,FloatPanel.LINE,1);save(out,"9_settings_float_sheet",rec,model,now,false,false);rec.setFloatEdit(1,1,7,FloatPanel.BAR,4);save(out,"9_settings_float_sheet_drive",rec,model,now,false,false);rec.setFloatEdit(-1,0,-1,0,0);
            FloatPanel.shape=FloatPanel.STRIP;save(out,"9_settings_float_strip",rec,model,now,false,false);FloatPanel.shape=FloatPanel.CARD;
            FloatPanel.setActiveSkin(0);FloatPanel.shape=FloatPanel.ROUND;save(out,"9_settings_float_round",rec,model,now,false,false);
            rec.setFloatEdit(0,0,-1,0,2);save(out,"9_settings_float_round_sheet",rec,model,now,false,false);rec.setFloatEdit(-1,0,-1,0,0);
            for(int[] cb:new int[][]{{4,1},{2,2},{1,3}}){int n=cb[0],sz=cb[1];   // round gauges over the 개요 page
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);new PitRenderer().draw(new android.graphics.Canvas(img.createGraphics()),800,480,false,model,now);
                java.awt.Graphics2D g2=img.createGraphics();g2.translate(FloatPanel.defaultX(FloatPanel.ROUND,n,sz),FloatPanel.defaultY(FloatPanel.ROUND,n,sz));
                FloatPanel.draw(new android.graphics.Canvas(g2),FloatPanel.ROUND,n,sz,model.snapshot(now),VehicleProfile.PETROL_DCT,now);ImageIO.write(img,"png",new File(out,"x_float_round"+n+"_"+sz+".png"));}
            FloatPanel.shape=FloatPanel.CARD;FloatPanel.setActiveSkin(0);
            // corners x themes on the window itself
            String keepTheme=Theme.current().id;int keepCorner=FloatPanel.corner;
            for(String th:new String[]{"n","purple","amber","bmw"})for(int cn=0;cn<FloatPanel.CORNER_NAMES.length;cn++){Theme.select(th);FloatPanel.corner=cn;
                BufferedImage img=new BufferedImage(260,140,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();g2.setColor(new java.awt.Color(0x1a2226));g2.fillRect(0,0,260,140);g2.translate(6,6);
                FloatPanel.draw(new android.graphics.Canvas(g2),FloatPanel.CARD,4,1,model.snapshot(now),VehicleProfile.PETROL_DCT,now);ImageIO.write(img,"png",new File(out,"x_float_corner_"+th+"_"+cn+".png"));}
            Theme.select(keepTheme);FloatPanel.corner=keepCorner;
            for(int al=0;al<FloatPanel.ALPHA_NAMES.length;al++){FloatPanel.alpha=al;   // 투명도, over a bright striped ground so it shows
                BufferedImage img=new BufferedImage(260,140,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();
                for(int sx=0;sx<260;sx+=20){g2.setColor(new java.awt.Color(sx/20%2==0?0x6fa86a:0xd8d2b8));g2.fillRect(sx,0,20,140);}g2.translate(6,6);
                FloatPanel.draw(new android.graphics.Canvas(g2),FloatPanel.CARD,4,1,model.snapshot(now),VehicleProfile.PETROL_DCT,now);ImageIO.write(img,"png",new File(out,"x_float_alpha_"+al+".png"));}
            FloatPanel.alpha=1;
            // 스킨: every skin as a 4-cell card over a map-like ground (1 and 2 cells, strip, side, big size for two of them)
            for(int sk=0;sk<FloatPanel.skinCount();sk++){FloatPanel.setActiveSkin(sk);
                for(int[] v:new int[][]{{FloatPanel.CARD,4,1},{FloatPanel.STRIP,4,1},{FloatPanel.SIDE,2,0},{FloatPanel.CARD,1,3}}){int sh=v[0],n=v[1],sz=v[2];
                    int w=FloatPanel.width(sh,n,sz),hh=FloatPanel.height(sh,n,sz);
                    BufferedImage img=new BufferedImage(w+24,hh+24,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();
                    g2.setColor(new java.awt.Color(0xe2ded2));g2.fillRect(0,0,w+24,hh+24);g2.setColor(new java.awt.Color(0xffffff));for(int sx=-hh;sx<w+24;sx+=46)g2.fillPolygon(new int[]{sx,sx+8,sx+8+hh+24,sx+hh+24},new int[]{0,0,hh+24,hh+24},4);
                    g2.setColor(new java.awt.Color(0x2f7df0));g2.fillRect(0,(hh+24)/2,w+24,7);g2.translate(12,12);
                    FloatPanel.draw(new android.graphics.Canvas(g2),sh,n,sz,model.snapshot(now),VehicleProfile.PETROL_DCT,now);
                    ImageIO.write(img,"png",new File(out,"x_float_skin_"+sk+"_"+sh+n+sz+".png"));}}
            // the layouts: each with 4 (and the pill and the dial with 2 / 1) in the plain look and three skins
            for(int f:new int[]{FloatPanel.PILL,FloatPanel.HERO,FloatPanel.GEARBOX,FloatPanel.DIAG,FloatPanel.DIAL,FloatPanel.SHIFT,FloatPanel.TICK,FloatPanel.FILLS,FloatPanel.BARS,FloatPanel.TWIN,FloatPanel.WEDGE,FloatPanel.TOWER})for(int sk:new int[]{0,1,4,8,6,7})for(int n:new int[]{4,2,1}){
                if(n!=4&&sk!=0||!FloatPanel.fits(f,n))continue;FloatPanel.setActiveSkin(sk);FloatPanel.count=n;int w=FloatPanel.width(f,n,1),hh=FloatPanel.height(f,n,1);
                BufferedImage img=new BufferedImage(w+24,hh+24,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();
                g2.setColor(new java.awt.Color(0xe2ded2));g2.fillRect(0,0,w+24,hh+24);g2.setColor(new java.awt.Color(0xffffff));for(int sx=-hh;sx<w+24;sx+=46)g2.fillPolygon(new int[]{sx,sx+8,sx+8+hh+24,sx+hh+24},new int[]{0,0,hh+24,hh+24},4);
                g2.setColor(new java.awt.Color(0x2f7df0));g2.fillRect(0,(hh+24)/2,w+24,7);g2.translate(12,12);
                FloatPanel.draw(new android.graphics.Canvas(g2),f,n,1,model.snapshot(now),VehicleProfile.PETROL_DCT,now);
                ImageIO.write(img,"png",new File(out,"x_float_form_"+f+"_"+sk+"_"+n+".png"));}
            FloatPanel.count=4;FloatPanel.setActiveSkin(0);
            {   // partial redraw: the kept bitmap + the moving part must look like the whole drawing, every shape x look x count, two cell sets
                int[] keepC=FloatPanel.cells.clone(),keepG=FloatPanel.graphs.clone();int bad=0,checked=0;StringBuilder why=new StringBuilder();
                for(int variant=0;variant<2;variant++){
                    if(variant==1){FloatPanel.cells[0]=FloatPanel.QUAD_P;FloatPanel.graphs[1]=FloatPanel.LED;FloatPanel.graphs[2]=FloatPanel.NUM;FloatPanel.graphs[3]=FloatPanel.LINE;}
                    for(int f:FloatPanel.FORMS)for(int sk=0;sk<FloatPanel.skinCount();sk++)for(int n:FloatPanel.counts(f)){
                        FloatPanel.setActiveSkin(sk);FloatPanel.count=n;int w=FloatPanel.width(f,n,1),hh=FloatPanel.height(f,n,1);BufferedImage[] im=new BufferedImage[2];
                        for(int m=0;m<2;m++){FloatPanel.layerOn=m==1;
                            for(int rep=0;rep<2;rep++){BufferedImage img=new BufferedImage(w,hh,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();   // twice: the bitmap is built, the bars have landed
                                for(int sx=0;sx<w;sx+=10){g2.setColor(new java.awt.Color(sx/10%2==0?0x6fa86a:0xd8d2b8));g2.fillRect(sx,0,10,hh);}
                                FloatPanel.draw(new android.graphics.Canvas(g2),f,n,1,model.snapshot(now),VehicleProfile.PETROL_DCT,now);im[m]=img;}}
                        int diff=0,worst=0;for(int yy=0;yy<hh;yy++)for(int xx=0;xx<w;xx++){int c0=im[0].getRGB(xx,yy),c1=im[1].getRGB(xx,yy);
                            int dd=Math.max(Math.abs((c0>>16&255)-(c1>>16&255)),Math.max(Math.abs((c0>>8&255)-(c1>>8&255)),Math.abs((c0&255)-(c1&255))));worst=Math.max(worst,dd);if(dd>3)diff++;}
                        checked++;if(diff>0){if(bad++<400)why.append(" shape="+f+" look="+sk+" n="+n+" v="+variant+": "+diff+" px (worst "+worst+")");
                            if(bad<=3){ImageIO.write(im[0],"png",new File(out,"x_layer_whole_"+f+"_"+sk+"_"+n+".png"));ImageIO.write(im[1],"png",new File(out,"x_layer_split_"+f+"_"+sk+"_"+n+".png"));}}}}
                FloatPanel.layerOn=true;System.arraycopy(keepC,0,FloatPanel.cells,0,4);System.arraycopy(keepG,0,FloatPanel.graphs,0,4);FloatPanel.setActiveSkin(0);FloatPanel.count=4;
                System.out.println("partial redraw: "+checked+" windows, "+bad+" differ"+why);
                if(bad>0)throw new AssertionError("partial redraw differs from the whole drawing:"+why);}
            {   // where bars start: boost from the left end (0 bar idle, then +0.8), lateral G and steering from the middle (left turn)
                int[] keepC=FloatPanel.cells.clone();FloatPanel.cells[0]=Telemetry.BOOST;FloatPanel.cells[1]=11;FloatPanel.cells[2]=Telemetry.STEERING;FloatPanel.cells[3]=2;
                Telemetry.Sample[] ex=FloatPanel.example().clone();ex[11]=new Telemetry.Sample(-.4f,true,false,true,false,0);ex[Telemetry.STEERING]=new Telemetry.Sample(-90,true,false,true,false,0);
                for(float bv:new float[]{-.6f,0,.8f,1.8f}){ex[Telemetry.BOOST]=new Telemetry.Sample(bv,true,false,true,false,0);
                    for(int[] v:new int[][]{{FloatPanel.ROUND,0},{FloatPanel.DIAL,0},{FloatPanel.TWIN,0},{FloatPanel.CARD,0},{FloatPanel.CARD,1},{FloatPanel.CARD,2},{FloatPanel.FILLS,0}}){
                        int f=v[0];FloatPanel.setActiveSkin(v[1]);int w=FloatPanel.width(f,4,1),hh=FloatPanel.height(f,4,1);
                        BufferedImage img=new BufferedImage(w+24,hh+24,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();
                        g2.setColor(new java.awt.Color(0x2a3036));g2.fillRect(0,0,w+24,hh+24);g2.translate(12,12);
                        FloatPanel.drawStill(new android.graphics.Canvas(g2),f,4,1,ex,VehicleProfile.PETROL_DCT,now);
                        ImageIO.write(img,"png",new File(out,"x_float_zero_"+f+"_"+v[1]+"_"+(bv<0?"a_idle":bv==0?"b_zero":bv<1?"c_08":"d_13")+".png"));}}
                // text against needles and rings: every shape at idle after a +1.3 bar pull (so the peaks show), in the plain and 8-bit looks
                for(int f:FloatPanel.FORMS)for(int sk:new int[]{0,6}){FloatPanel.setActiveSkin(sk);int w=FloatPanel.width(f,4,1),hh=FloatPanel.height(f,4,1);
                    BufferedImage img=new BufferedImage(w+24,hh+24,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();
                    g2.setColor(new java.awt.Color(0x2a3036));g2.fillRect(0,0,w+24,hh+24);g2.translate(12,12);
                    ex[Telemetry.BOOST]=new Telemetry.Sample(1.3f,true,false,true,false,0);FloatPanel.draw(new android.graphics.Canvas(new BufferedImage(w,hh,BufferedImage.TYPE_INT_RGB).createGraphics()),f,4,1,ex,VehicleProfile.PETROL_DCT,now);
                    ex[Telemetry.BOOST]=new Telemetry.Sample(-.6f,true,false,true,false,0);FloatPanel.draw(new android.graphics.Canvas(new BufferedImage(w,hh,BufferedImage.TYPE_INT_RGB).createGraphics()),f,4,1,ex,VehicleProfile.PETROL_DCT,now+1000);
                    FloatPanel.draw(new android.graphics.Canvas(g2),f,4,1,ex,VehicleProfile.PETROL_DCT,now+1200);   // the needles have glided to idle
                    ImageIO.write(img,"png",new File(out,"x_float_occl_"+f+"_"+sk+".png"));}
                System.arraycopy(keepC,0,FloatPanel.cells,0,4);FloatPanel.setActiveSkin(0);}
            {   // 타이어 4개: pressure and temperature cells in the plain card, three skins, the round gauge and two layouts
                int[] keepC=FloatPanel.cells.clone();FloatPanel.cells[0]=FloatPanel.QUAD_P;FloatPanel.cells[1]=FloatPanel.QUAD_T;
                for(int[] v:new int[][]{{FloatPanel.CARD,0},{FloatPanel.CARD,1},{FloatPanel.CARD,6},{FloatPanel.CARD,4},{FloatPanel.ROUND,0},{FloatPanel.HERO,0},{FloatPanel.GEARBOX,1}}){
                    int f=v[0];FloatPanel.setActiveSkin(v[1]);int w=FloatPanel.width(f,4,1),hh=FloatPanel.height(f,4,1);
                    BufferedImage img=new BufferedImage(w+24,hh+24,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g2=img.createGraphics();
                    g2.setColor(new java.awt.Color(0xe2ded2));g2.fillRect(0,0,w+24,hh+24);g2.translate(12,12);
                    FloatPanel.drawStill(new android.graphics.Canvas(g2),f,4,1,model.snapshot(now),VehicleProfile.PETROL_DCT,now);
                    ImageIO.write(img,"png",new File(out,"x_float_quad_"+f+"_"+v[1]+".png"));}
                System.arraycopy(keepC,0,FloatPanel.cells,0,4);FloatPanel.setActiveSkin(0);}
            FloatPanel.shape=FloatPanel.HERO;rec.setFloatEdit(-1,0,-1,0,0);save(out,"9_settings_float_form",rec,model,now,false,false);
            rec.setFloatEdit(SettingsLayout.FORM_SHEET,0,-1,0,0);save(out,"9_settings_float_form_sheet",rec,model,now,false,false);rec.setFloatEdit(-1,0,-1,0,0);
            FloatPanel.shape=FloatPanel.GEARBOX;FloatPanel.count=2;FloatPanel.setActiveSkin(1);save(out,"9_settings_float_gear2",rec,model,now,false,false);   // 단수 중심: 2 or 4, the 1 dim
            rec.setFloatEdit(SettingsLayout.SKIN_SHEET,0,-1,0,0);save(out,"9_settings_float_look_gear2",rec,model,now,false,false);rec.setFloatEdit(-1,0,-1,0,0);FloatPanel.count=4;FloatPanel.shape=FloatPanel.CARD;
            FloatPanel.skin=FloatPanel.AUTO;FloatPanel.screen=5;rec.setFloatEdit(-1,0,-1,0,0);save(out,"9_settings_float_skin",rec,model,now,false,false);   // 계기판 따라 on the 레이스 screen
            FloatPanel.setActiveSkin(1);rec.setFloatEdit(SettingsLayout.SKIN_SHEET,0,-1,0,0);save(out,"9_settings_float_skin_sheet",rec,model,now,false,false);FloatPanel.skin=FloatPanel.AUTO;
            rec.setFloatEdit(0,0,-1,0,1);save(out,"9_settings_float_skin_cell",rec,model,now,false,false);rec.setFloatEdit(-1,0,-1,0,0);FloatPanel.setActiveSkin(0);FloatPanel.screen=0;
            {   // 색감 row: 자동 / 낮 / 밤 with a day look and a night look (night now: the night chip lit), 계기판 따라, round gauges, English
                FloatPanel.skin=0;FloatPanel.skinDay=FloatSkin.WHITE;FloatPanel.skinNight=FloatSkin.CARD;String[] dns={"auto","day","night"};
                for(int d=0;d<3;d++){FloatPanel.dn=d;save(out,"9_settings_float_dn_"+dns[d],rec,model,now,false,false);}
                FloatPanel.dn=DayNight.AUTO;FloatPanel.skin=FloatPanel.AUTO;FloatPanel.screen=5;save(out,"9_settings_float_dn_follow",rec,model,now,false,false);FloatPanel.skin=0;FloatPanel.screen=0;
                FloatPanel.shape=FloatPanel.ROUND;save(out,"9_settings_float_dn_round",rec,model,now,false,false);FloatPanel.shape=FloatPanel.CARD;
                FloatPanel.skinNight=7;com.veloster.i18n.UiText.configure("en","ko");save(out,"9_settings_float_dn_en",rec,model,now,false,false);
                rec.setFloatEdit(SettingsLayout.SKIN_SHEET,0,-1,0,0);save(out,"9_settings_float_skin_sheet_en",rec,model,now,false,false);rec.setFloatEdit(-1,0,-1,0,0);com.veloster.i18n.UiText.configure("ko","ko");   // the longest English look: Cassette
                FloatPanel.skinNight=FloatSkin.CARD;rec.setFloatEdit(SettingsLayout.SKIN_SHEET_DAY,0,-1,0,0);save(out,"9_settings_float_skin_sheet_day",rec,model,now,false,false);rec.setFloatEdit(-1,0,-1,0,0);
                FloatPanel.dn=DayNight.NIGHT;FloatPanel.skinDay=FloatSkin.WHITE;FloatPanel.setActiveSkin(0);}
            FloatPanel.skin=FloatPanel.AUTO;FloatPanel.screen=5;int fs5=FloatPanel.skinNow();FloatPanel.screen=0;int fs0=FloatPanel.skinNow();FloatPanel.setActiveSkin(0);if(fs5!=1||fs0!=0)throw new AssertionError("계기판 따라: 레이스 → 레이스 스킨, 전체 조망 → 기본");
            // this car's values only: a petrol DCT drops the DPF pair, a 6MT the DCT clutches, a diesel keeps all
            for(int gg:FloatPanel.groups(VehicleProfile.PETROL_DCT)){int[] vs=FloatPanel.group(gg,VehicleProfile.PETROL_DCT);
                if(vs.length>SettingsLayout.S_MAX_VALUES)throw new AssertionError("float chooser: group "+gg+" has more readings than the sheet shows");
                for(int sg:vs)if(sg==Telemetry.DPF_SOOT||sg==Telemetry.DPF_TEMP)throw new AssertionError("float chooser: no DPF on a petrol");}
            for(int gg:FloatPanel.groups(VehicleProfile.PETROL_MT))for(int sg:FloatPanel.group(gg,VehicleProfile.PETROL_MT))if(sg==Telemetry.CLUTCH_ODD)throw new AssertionError("float chooser: no DCT clutch on a 6MT");
            for(int gg:FloatPanel.groups(VehicleProfile.DIESEL_DCT))if(FloatPanel.group(gg,VehicleProfile.DIESEL_DCT).length>SettingsLayout.S_MAX_VALUES)throw new AssertionError("float chooser: diesel group too long");
            FloatPanel.cells[0]=Telemetry.DPF_SOOT;if(FloatPanel.signal(0,VehicleProfile.PETROL_DCT)!=Telemetry.BOOST)throw new AssertionError("float: a saved DPF cell falls back to 자동 on a petrol");FloatPanel.cells[0]=-1;
            FloatPanel.graphs[1]=FloatPanel.BAR;FloatPanel.graphs[2]=FloatPanel.BAR;FloatPanel.count=2;
        }
        rec.setBeep(true,2,70,new int[]{0,2,-1},1);rec.setSettings(SettingsLayout.RPM,"auto",-1,targets);SettingsLayout.floatBeep=true;save(out,"9_settings_rpm_beep",rec,model,now,false,false);SettingsLayout.floatBeep=false;rec.setBeep(false,0,70,new int[]{-1,-1,-1},0);
        rec.setModeOptions(true,true);rec.setSettings(SettingsLayout.MODE,"auto",-1,targets);save(out,"9_settings_mode_on",rec,model,now,false,false);
        rec.setSettings(SettingsLayout.MODE,"auto",0,targets);save(out,"9_settings_mode_pick",rec,model,now,false,false);
        {Telemetry.Sample[] q=model.snapshot(now);q[7]=new Telemetry.Sample(103,true,false,true,false,0);   // GPS speed calibration off / on: the speedometer's 103 shown as a true 100
         for(int on=0;on<2;on++){Telemetry.speedScale=on==1?0.971f:1;BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
          new PitRenderer().draw(c,800,480,false,model,now,0,false,null);android.graphics.Paint bg=new android.graphics.Paint(1);bg.setColor(Theme.current().map(0xff040506));c.drawRect(0,53,800,480,bg);
          new RaceRenderer().draw(c,q,ShiftGuide.evaluate(q,ShiftGuide.target(0,VehicleProfile.PETROL_DCT),VehicleProfile.PETROL_DCT),now,rec.stats(),VehicleProfile.PETROL_DCT,false,null,null);
          ImageIO.write(img,"png",new File(out,on==1?"x_speedcal_on.png":"x_speedcal_off.png"));}Telemetry.speedScale=1;}
        rec.setSpeedCal(true,0.968f,1240);rec.setSettings(SettingsLayout.DIAG,"auto",-1,targets);save(out,"9_settings_diag_speedcal",rec,model,now,false,false);rec.setSpeedCal(false,0.968f,1240);save(out,"9_settings_diag_speedcal_off",rec,model,now,false,false);rec.setSpeedCal(false,Float.NaN,35);save(out,"9_settings_diag_measuring",rec,model,now,false,false);rec.setSpeedCal(false,Float.NaN,0);   // GPS speed calibration learned, on
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
        {   // 앞차 피하기 on the Arcade view: parked in P, brake held -> countdown, then playing (wheel right), a crash, game over
            PixelCluster pk=new PixelCluster(true);long t=now;Telemetry.Sample[] q=model.snapshot(now);ClusterKit.artwork=null;
            q[7]=new Telemetry.Sample(0,true,false,true,false,0);q[8]=new Telemetry.Sample(0,false,false,true,false,0);q[Telemetry.GEAR_SELECTOR]=new Telemetry.Sample(0,true,false,true,false,0);
            q[Telemetry.BRAKE_SWITCH]=new Telemetry.Sample(1,true,false,true,false,0);q[10]=new Telemetry.Sample(30,true,false,true,false,0);q[Telemetry.STEERING]=new Telemetry.Sample(0,true,false,true,false,0);
            String[] shots={"count","go","play","steer_past","crash","over"};long[] gAt={1520,3320,9000,9600,0,0};int si=0;
            for(long e=0;e<=9600;e+=40){t=now+e;if(e>=3400){q[Telemetry.BRAKE_SWITCH]=new Telemetry.Sample(0,true,false,true,false,0);q[10]=new Telemetry.Sample(1,true,false,true,false,0);
                    q[Telemetry.STEERING]=new Telemetry.Sample(e<9200?-(float)(45*Math.sin(e/900.0)):-140,true,false,true,false,0);
                    q[9]=new Telemetry.Sample(e>=8800&&e<9200?40:0,true,false,true,false,0);}   // gas pressed at the play shot: the crossed-out pedal
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
                boolean shot=si<4&&e==gAt[si];if(shot)new PitRenderer().draw(c,800,480,false,model,now,0,false,null);
                pk.frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_DCT,t,null);
                if(shot)ImageIO.write(img,"png",new File(out,"x_arcade_game_"+shots[si++]+".png"));}
            if(pk.game.state==ArcadeGame.PLAY){   // the three obstacles side by side: a cone, a drum, a barricade
                for(int i=0;i<ArcadeGame.CARS;i++)pk.game.carM[i]=pk.game.pos+60+i*3;float[] obAt={3.4f,4.6f,6.2f};
                for(int i=0;i<3;i++){pk.game.carM[i]=pk.game.pos+obAt[i];pk.game.carLane[i]=i;pk.game.carCol[i]=i;}pk.game.x=0;pk.game.hitAt=Long.MIN_VALUE/2;
                BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());new PitRenderer().draw(c,800,480,false,model,now,0,false,null);
                pk.frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_DCT,t+20,null);ImageIO.write(img,"png",new File(out,"x_arcade_game_obstacles.png"));}
            for(int k=0;k<3&&pk.game.state==ArcadeGame.PLAY;k++){for(int i=0;i<ArcadeGame.CARS;i++)pk.game.carM[i]=pk.game.pos+30+i*3;pk.game.carM[0]=pk.game.pos+1.6f;pk.game.carLane[0]=1;pk.game.carV[0]=0;pk.game.x=0;pk.game.hitAt=Long.MIN_VALUE/2;
                t+=40;pk.frame(new android.graphics.Canvas(new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB).createGraphics()),q,model,new DriveStats(),VehicleProfile.PETROL_DCT,t,null);
                if(k==0){t+=40;BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());new PitRenderer().draw(c,800,480,false,model,now,0,false,null);
                    pk.game.carM[0]=pk.game.pos+0.95f;pk.frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_DCT,t,null);ImageIO.write(img,"png",new File(out,"x_arcade_game_crash.png"));}}
            {t+=40;BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());new PitRenderer().draw(c,800,480,false,model,now,0,false,null);
                pk.frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_DCT,t,null);ImageIO.write(img,"png",new File(out,"x_arcade_game_over.png"));}
            {Telemetry.Sample[] q2=q.clone();q2[Telemetry.BRAKE_SWITCH]=new Telemetry.Sample(0,true,false,true,false,0);q2[10]=new Telemetry.Sample(1,true,false,true,false,0);   // parked, brake off: the hint
                PixelCluster p2=new PixelCluster(true);BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());new PitRenderer().draw(c,800,480,false,model,now,0,false,null);
                p2.frame(c,q2,model,new DriveStats(),VehicleProfile.PETROL_DCT,now,null);ImageIO.write(img,"png",new File(out,"x_arcade_game_hint.png"));}
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
         {long t=10_000;for(int i=0;i<10;i++,t+=20)dst.coach.updateAuto(t,90*11.6f,90,5,0,dst.ratios);for(int i=0;i<12;i++,t+=20)dst.coach.updateAuto(t,1250+i*5,90,4,i<6?-0.25f:-0.1f,dst.ratios);for(int i=0;i<30;i++,t+=20)dst.coach.updateAuto(t,1314,90,4,-0.1f,dst.ratios);}   // the last shift: paddle 5>4
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
        {   // WRC, 아날로그, 네온, 동승자 (the screens after RPM): the replay through PitRenderer, set moments, and no data at all
         String[] mn={"9s_cluster_wrc","9t_cluster_analog","9u_cluster_neon"};
         for(int i=0;i<3;i++){rec.setPage(PitRenderer.PAGE_TACHO+1+i);for(int f=0;f<6;f++)save(out,mn[i],rec,model,now+f*50L,false,false);}   // a few frames: the needles ease in
         for(int i=0;i<3;i++){PitRenderer e=new PitRenderer();e.setSymmetric(true);e.setPage(PitRenderer.PAGE_TACHO+1+i);save(out,mn[i]+"_nodata",e,new Telemetry(),now,false,false);}
         int[] ix={7,0,8,9,10,11,12,Telemetry.STEERING,2,Telemetry.BOOST,Telemetry.OIL_ECU};
         // WRC: a stage four minutes in, three corners behind (3 R, 5 L, 2 R), now in a 4 L at 0.7 G
         {WrcCluster k=new WrcCluster();k.shiftTarget=6000;long t0=now-240000;float[][] seq={{-100,0},{40,0},{0,0},{-170,0},{0,0},{70,0.7f}};   // raw wheel angles: + is a left turn of the wheel
          BufferedImage img=null;
          k.background(with(model,now,ix,new float[]{40,3000,2,30,0,0,0,0,92,0.2f,98}),t0);   // moved off four minutes ago
          for(int f=0;f<=96;f++){long t=now-4800+f*50L;float[] st=seq[Math.min(seq.length-1,f/16)];
              Telemetry.Sample[] q=with(model,now,ix,new float[]{72,5200,3,64,0,st[1],0.05f,st[0],92,0.9f,98});k.background(q,t);
              img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
              new PitRenderer().draw(c,800,480,false,model,now,0,false,null);k.frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_DCT,t,null);}
          ImageIO.write(img,"png",new File(out,"9s_wrc_corner.png"));}
         // WRC while the stock navi guides (a domestic code, 600 m): the card shows the guidance
         {Tbt.set(Tbt.parse(String.join(String.valueOf((char)10),"Navigation Cluster:","  TBT SubInfo:","    distance      = 2.9 METER","    minutes       = 1","  TBT Info:","    Display Type  = Unknown display type(0x5b)","    Direction     = 0.0","    Distance      = 600.0 METER","")),now);
          rec.setPage(PitRenderer.PAGE_TACHO+1);save(out,"9s_wrc_navi",rec,model,now,false,false);Tbt.set(Tbt.parse(String.join(String.valueOf((char)10),"Navigation Cluster:","  TBT Info:","    Display Type  = U TURN","    Direction     = 270.0","    Distance      = 300.0 METER","")),now);save(out,"9s_wrc_navi_u",rec,model,now,false,false);Tbt.set(Tbt.NONE,now);}
         // 네온: 300 rpm before the shift target (the glitch), and overheating at the target
         for(int n=0;n<2;n++){NeonCluster k=new NeonCluster();k.shiftTarget=6000;
              Telemetry.Sample[] q=with(model,now,ix,new float[]{118,n==0?5700:6050,3,100,0,0.1f,0.4f,4,n==0?96:116,1.3f,104});
              BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
              new PitRenderer().draw(c,800,480,false,model,now,0,false,null);k.frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_DCT,now+n*70,null);
              ImageIO.write(img,"png",new File(out,n==0?"9u_neon_glitch.png":"9u_neon_hot.png"));}
         // 아날로그: every dial look (a tap steps through them), the needles eased in
         for(int s=0;s<AnalogCluster.STYLE_NAMES.length;s++){AnalogCluster.style=s;AnalogCluster k=new AnalogCluster();BufferedImage img=null;
              Telemetry.Sample[] q=with(model,now,ix,new float[]{96,4300,4,55,0,0,0,0,90,0.8f,98});
              for(int f=0;f<40;f++){img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
                  new PitRenderer().draw(c,800,480,false,model,now,0,false,null);k.frame(c,q,model,new DriveStats(),VehicleProfile.PETROL_DCT,now+f*50L,null);}
              ImageIO.write(img,"png",new File(out,"9t_analog_style"+s+".png"));}
         AnalogCluster.style=0;
         rec.setPage(PitRenderer.PAGE_CLASSIC);
        }
        // 수동 screen (the Λ layout, 6MT): {gear, km/h, rpm, clutch, brake bar}; the race and performance pages turn the rpm green on a match
        float[][] ds={{4,80,1500,1,22},{5,100,2800,0,0},{3,60,2800,0,25},{6,96,2200,0,0},{4,80,3720,1,22},{3,122,5750,0,0}};String[] dsn={"a_4th80_clutch","b_5th100_cruise","c_3rd60_brake","d_6th96","e_4th80_match","f_3rd122_upshift"};
        for(int k=0;k<ds.length;k++){Telemetry.Sample[] q=model.snapshot(now);float[] d=ds[k];
            q[8]=new Telemetry.Sample(d[0],true,false,true,false,0);q[7]=new Telemetry.Sample(d[1],true,false,true,false,0);q[0]=new Telemetry.Sample(d[2],true,false,true,false,0);
            q[Telemetry.CLUTCH]=new Telemetry.Sample(d[3],true,false,true,false,0);q[10]=new Telemetry.Sample(d[4],true,false,true,false,0);q[Telemetry.SHIFT_HINT]=new Telemetry.Sample(0,true,false,true,false,0);q[Telemetry.BRAKE_SWITCH]=new Telemetry.Sample(d[4]>5?1:0,true,false,true,false,0);
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
    /** The snapshot with some signals set (received, fresh). */
    private static Telemetry.Sample[] with(Telemetry m,long now,int[] idx,float[] val){Telemetry.Sample[] q=m.snapshot(now);for(int i=0;i<idx.length;i++)q[idx[i]]=new Telemetry.Sample(val[i],true,false,true,false,0);return q;}
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
