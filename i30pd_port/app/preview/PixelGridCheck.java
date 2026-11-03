package com.veloster.vehicle;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preview-only check that the 8-bit views stay 8-bit: in the scene band (below the top HUD, above the bottom one)
 * every 4 x 4 scene pixel is one colour, and every colour is one the renderer names (no blending, no translucency).
 * Runs both views by day and night with lamps, brake, turn signals, boost, throttle and SPORT, over 30 frames each.
 * Usage: java ... com.veloster.vehicle.PixelGridCheck shared/src/com/veloster/vehicle/PixelCluster.java [png-dir]
 */
public final class PixelGridCheck {
    static Telemetry.Sample on(float v){return new Telemetry.Sample(v,true,false,true,false,0);}
    public static void main(String[] a) throws Exception {
        Set<Integer> palette=new HashSet<>();
        Matcher m=Pattern.compile("0xff([0-9a-fA-F]{6})").matcher(new String(Files.readAllBytes(Paths.get(a[0])),StandardCharsets.UTF_8));
        while(m.find())palette.add(Integer.parseInt(m.group(1),16));
        Theme.select("n");CarPaint.select("blue");for(int b:CarPaint.body())palette.add(b&0xffffff);
        int bad=0,frames=0;java.util.Map<String,int[]> where=new java.util.TreeMap<>();
        for(int hour:new int[]{13,23})for(int layout:new int[]{6,7})for(int state=0;state<4;state++){
            int off=((hour-(int)(System.currentTimeMillis()/3_600_000%24)+11)%24+24)%24-11;TimeZone.setDefault(TimeZone.getTimeZone("GMT"+(off>=0?"+":"")+off));
            ClusterRenderer r=new ClusterRenderer();Telemetry t=new Telemetry();
            BufferedImage img=new BufferedImage(800,480,BufferedImage.TYPE_INT_RGB);android.graphics.Canvas c=new android.graphics.Canvas(img.createGraphics());
            long now=5_000_000;
            for(int f=0;f<60;f++){
                Telemetry.Sample[] s=t.snapshot(now+f*33);
                s[7]=on(state==0?0:90);s[0]=on(state==2?6400:2600);s[Telemetry.STEERING]=on(state==3?-60:25);
                s[Telemetry.HEAD_LAMP]=on(state%2);s[Telemetry.TAIL_LAMP]=on(state%2);s[Telemetry.HIGH_BEAM]=on(state==3?1:0);
                s[10]=on(state==1?40:0);s[9]=on(state==2?90:state==3?30:0);s[Telemetry.BOOST]=on(state==2?1.2f:state==3?0.5f:0);
                s[Telemetry.TURN_LEFT]=on(state==1&&f%10<5?1:0);s[Telemetry.TURN_RIGHT]=on(state==3&&f%10<5?1:0);
                s[Telemetry.DRIVE_MODE]=on(state==2?1:state==3?3:0);
                r.draw(c,layout,s,now+f*33,new DriveStats(),VehicleProfile.PETROL_DCT,t,null);
                if(f==59&&a.length>1)javax.imageio.ImageIO.write(img,"png",new java.io.File(a[1],(layout==7?"chase":"side")+"_"+(hour==13?"day":"night")+"_"+state+".png"));
                if(f<30)continue;frames++;
                int y0=layout==7?200:220,y1=layout==7?420:400;
                for(int y=y0;y<y1;y+=4)for(int x=0;x<800;x+=4){
                    int first=img.getRGB(x,y)&0xffffff;boolean ok=palette.contains(first);
                    for(int dy=0;dy<4&&ok;dy++)for(int dx=0;dx<4&&ok;dx++)ok=(img.getRGB(x+dx,y+dy)&0xffffff)==first;
                    if(!ok){bad++;String k=(layout==7?"chase":"side")+(hour==13?" day":" night")+" state "+state+": "+(palette.contains(first)?"split cell":"colour not in the palette");
                        int[] e=where.get(k);if(e==null)where.put(k,e=new int[]{0,x,y});e[0]++;}
                }
            }
        }
        for(java.util.Map.Entry<String,int[]> e:where.entrySet())System.out.printf("%s  x%d (first at %d,%d)%n",e.getKey(),e.getValue()[0],e.getValue()[1],e.getValue()[2]);
        System.out.println(bad==0?"PASS 8-bit grid and palette, "+frames+" frames":"FAIL "+bad+" cells");
        if(bad>0)System.exit(1);
    }
}
