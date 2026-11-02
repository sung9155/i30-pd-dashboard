import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Compares same-named PNGs in two folders below y0 (default 52, the header's bottom). Exit 1 on any difference. */
public final class BodyDiff {
    public static void main(String[] a) throws Exception {
        File da=new File(a[0]),db=new File(a[1]);int y0=a.length>2?Integer.parseInt(a[2]):52;
        int same=0,diff=0;
        for(String name:da.list()){
            if(!name.endsWith(".png")||!new File(db,name).exists())continue;   // ponytail: one-sided files are ignored
            BufferedImage x=ImageIO.read(new File(da,name)),y=ImageIO.read(new File(db,name));
            int n=0;
            if(x.getWidth()!=y.getWidth()||x.getHeight()!=y.getHeight())n=-1;
            else for(int j=y0;j<x.getHeight();j++)for(int i=0;i<x.getWidth();i++)if(x.getRGB(i,j)!=y.getRGB(i,j))n++;
            if(n==0)same++;else{diff++;System.out.println("DIFF "+name+" "+(n<0?"size":n+" px"));}
        }
        System.out.println(diff==0?"SAME "+same+" files":diff+" differ, "+same+" same");
        System.exit(diff==0?0:1);
    }
}
