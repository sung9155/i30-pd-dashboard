import com.veloster.vehicle.PackVoice;
import com.veloster.vehicle.SoundPack;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Sample-pack engine sound: loads a synthetic pack from a folder and a zip alike, refuses broken ones, is silent
 *  without rpm, bounded with it, and sweeps through the loops without clicks. */
public final class PackVoiceTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    static final int SR=11025,PR=SoundPack.RATE;

    /** A loop of whole engine cycles at rpm: the 2nd order (a four's firing) and a little 4th. */
    static byte[] loopWav(int rpm,int cycles,double amp,int rate){
        double spc=120.0*PR/rpm;int n=(int)Math.round(cycles*spc);short[] s=new short[n];
        for(int i=0;i<n;i++){double ph=2*Math.PI*i/spc;s[i]=(short)(amp*32767*(.8*Math.sin(2*ph)+.2*Math.sin(4*ph)));}
        return wav(s,rate);
    }
    static byte[] wav(short[] s,int rate){
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        b.write('R');b.write('I');b.write('F');b.write('F');le32(b,36+2*s.length);
        for(char c:"WAVEfmt ".toCharArray())b.write(c);
        le32(b,16);le16(b,1);le16(b,1);le32(b,rate);le32(b,rate*2);le16(b,2);le16(b,16);
        for(char c:"data".toCharArray())b.write(c);le32(b,2*s.length);
        for(short v:s)le16(b,v);
        return b.toByteArray();
    }
    static void le16(ByteArrayOutputStream b,int v){b.write(v&0xff);b.write((v>>8)&0xff);}
    static void le32(ByteArrayOutputStream b,int v){le16(b,v&0xffff);le16(b,(v>>>16)&0xffff);}

    static final int[] RPMS={800,2000,5000},CYCLES={6,15,37};
    static void writePack(File dir,String txtExtra,int rate,boolean skipName) throws IOException{
        dir.mkdirs();StringBuilder t=new StringBuilder("# synthetic\n"+(skipName?"":"name test four\n")+"cylinders 4\nidle 800\nredline 6500\naligned yes\n"+txtExtra);
        for(int k=0;k<RPMS.length;k++){
            put(dir,"on"+RPMS[k]+".wav",loopWav(RPMS[k],CYCLES[k],.7,rate));t.append("loop on"+RPMS[k]+".wav rpm="+RPMS[k]+" load=on\n");
            put(dir,"off"+RPMS[k]+".wav",loopWav(RPMS[k],CYCLES[k],.3,rate));t.append("loop off"+RPMS[k]+".wav rpm="+RPMS[k]+" load=off\n");
        }
        put(dir,"pack.txt",t.toString().getBytes("UTF-8"));
    }
    static void put(File dir,String name,byte[] b) throws IOException{FileOutputStream o=new FileOutputStream(new File(dir,name));try{o.write(b);}finally{o.close();}}
    static byte[] zip(File dir) throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();ZipOutputStream z=new ZipOutputStream(b);
        for(File f:dir.listFiles()){z.putNextEntry(new ZipEntry(f.getName()));z.write(java.nio.file.Files.readAllBytes(f.toPath()));z.closeEntry();}
        z.close();return b.toByteArray();
    }
    static String refusal(File dir){try{SoundPack.fromDir(dir);return null;}catch(IOException e){return e.getMessage();}}

    /** seconds of steady input in 512-sample blocks like the audio thread */
    static float[] run(PackVoice v,double rpm,double load,double seconds){
        int n=(int)(seconds*SR);float[] all=new float[n];
        for(int i=0;i<n;i+=512){v.set(rpm,load);v.render(all,i,Math.min(512,n-i),1);}
        return all;
    }
    static double rms(float[] x,int from){double a=0;for(int i=from;i<x.length;i++)a+=x[i]*x[i];return Math.sqrt(a/(x.length-from));}
    static double peak(float[] x){double a=0;for(float v:x)a=Math.max(a,Math.abs(v));return a;}
    static double maxJump(float[] x,int from){double a=0;for(int i=Math.max(1,from);i<x.length;i++)a=Math.max(a,Math.abs(x[i]-x[i-1]));return a;}

    public static void main(String[] args) throws Exception{
        File tmp=java.nio.file.Files.createTempDirectory("packvoice").toFile();
        try{
            File good=new File(tmp,"good");writePack(good,"",PR,false);
            SoundPack p=SoundPack.fromDir(good);
            check(p.name.equals("test four")&&p.cylinders==4&&p.aligned,"header parsed");
            check(p.layers.length==2&&p.layers[1].length==3&&p.layers[0].length==3&&p.layers[1][2].rpm==5000,"two layers of three loops, by rpm");

            File wrongRate=new File(tmp,"rate");writePack(wrongRate,"",44100,false);
            String r=refusal(wrongRate);check(r!=null&&r.contains("22050"),"a 44.1 kHz loop is refused: "+r);
            File noName=new File(tmp,"noname");writePack(noName,"",PR,true);
            check(refusal(noName)!=null,"a pack without a name is refused");
            File junk=new File(tmp,"junk");writePack(junk,"wobble 3\n",PR,false);
            r=refusal(junk);check(r!=null&&r.contains("unknown key"),"an unknown line is refused: "+r);
            File missing=new File(tmp,"missing");writePack(missing,"loop gone.wav rpm=3000 load=on\n",PR,false);
            r=refusal(missing);check(r!=null&&r.contains("gone.wav"),"a missing loop is refused: "+r);
            File empty=new File(tmp,"empty");empty.mkdirs();check(refusal(empty)!=null,"an empty folder is refused");
            try{SoundPack.fromZip(new ByteArrayInputStream(new byte[]{1,2,3,4}));check(false,"junk bytes are not a pack");}catch(IOException e){checks++;}

            PackVoice v=new PackVoice(SR,p);
            float[] x=run(v,Double.NaN,.5,.5);
            check(peak(x)==0,"no rpm: silence");
            x=run(v,2000,.5,2);
            double level=rms(x,SR);
            check(level>.05&&peak(x)<=1,"2000 rpm half load: sound, bounded (rms "+level+", peak "+peak(x)+")");
            check(run(v,2000,1,1.5)[SR]!=0&&rms(run(v,2000,1,1.5),SR)>rms(run(v,2000,0,1.5),SR),"load is louder than overrun");
            x=run(v,Double.NaN,.5,1.5);
            check(peak(java.util.Arrays.copyOfRange(x,SR,x.length))<1e-3,"rpm lost: fades to silence");
            check(peak(java.util.Arrays.copyOfRange(run(v,2000,.5,1),0,64))<.05,"after silence it fades in, no jump");

            // sweep 800 -> 6000 -> 800 over 6 s with the pedal moving: every loop pair change and both layer blends
            PackVoice s=new PackVoice(SR,p);int n=6*SR;float[] sw=new float[n];
            for(int i=0;i<n;i+=512){double t=(double)i/n,rpm=800+5200*(t<.5?2*t:2-2*t);s.set(rpm,t<.5?1:t<.6?.3:0);s.render(sw,i,Math.min(512,n-i),1);}
            double steady=maxJump(run(new PackVoice(SR,p),6000,1,1),SR/2);
            double jump=maxJump(sw,SR/4);
            check(steady<.1&&jump<=steady*1.3+.01,"no clicks in the sweep (max step "+jump+", steady 6000 rpm "+steady+")");
            check(peak(sw)<=1,"sweep bounded");

            // the same pack from a zip plays the same samples
            PackVoice a=new PackVoice(SR,p),b=new PackVoice(SR,SoundPack.fromZip(new ByteArrayInputStream(zip(good))));
            float[] xa=run(a,3100,.4,1),xb=run(b,3100,.4,1);
            check(java.util.Arrays.equals(xa,xb),"zip and folder load the same pack");

            // free-running (not aligned) packs play too
            File free=new File(tmp,"free");writePack(free,"",PR,false);
            String txt=new String(java.nio.file.Files.readAllBytes(new File(free,"pack.txt").toPath()),"UTF-8").replace("aligned yes\n","level -6\n");
            put(free,"pack.txt",txt.getBytes("UTF-8"));
            SoundPack fp=SoundPack.fromDir(free);check(!fp.aligned&&Math.abs(fp.level-.501)<.001,"level trim parsed");
            x=run(new PackVoice(SR,fp),3500,.5,1.5);check(rms(x,SR)>.02&&peak(x)<=1,"free-running pack plays, bounded");
        }finally{
            for(File d:tmp.listFiles()){for(File f:d.listFiles())f.delete();d.delete();}tmp.delete();
        }
        // every built-in pack (res/raw/enginepack_<id>.zip, the 소리 원본 row's list) loads and plays a sweep, bounded
        for(String id:com.veloster.vehicle.SettingsLayout.PACK_IDS){
            java.io.InputStream in=new java.io.FileInputStream("apps/dashboard/res/raw/enginepack_"+id+".zip");SoundPack bp;try{bp=SoundPack.fromZip(in);}finally{in.close();}
            check(bp.name.equals(id)&&bp.aligned,"built-in pack "+id+" loads");
            PackVoice v=new PackVoice(SR,bp);double lo=rms(run(v,900,0,2),SR),hi=rms(run(v,5500,1,2),SR);
            check(lo>.005&&hi>lo&&peak(run(v,6500,1,.5))<=1,"built-in pack "+id+" plays, louder on load at high rpm");
        }
        System.out.println("PASS "+checks+" sound pack checks");
    }
}
