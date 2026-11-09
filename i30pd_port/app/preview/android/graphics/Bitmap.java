package android.graphics;

import java.awt.image.BufferedImage;

/** Preview-only Bitmap over a BufferedImage: enough for the layouts' pixel-art caches (album art stays empty). */
public class Bitmap {
    public enum Config{ARGB_8888}
    final BufferedImage img;
    Bitmap(BufferedImage img){this.img=img;}
    public Bitmap(){img=null;}
    public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
    public void eraseColor(int c){if(img==null)return;for(int y=0;y<img.getHeight();y++)for(int x=0;x<img.getWidth();x++)img.setRGB(x,y,c);}
    public int getWidth(){return img==null?0:img.getWidth();}
    public int getHeight(){return img==null?0:img.getHeight();}
    public void getPixels(int[] px,int off,int stride,int x,int y,int w,int h){img.getRGB(x,y,w,h,px,off,stride);}
    public void setPixels(int[] px,int off,int stride,int x,int y,int w,int h){img.setRGB(x,y,w,h,px,off,stride);}
    /** Preview only: a PNG the renderer reads as an app resource. */
    public static Bitmap of(BufferedImage i){BufferedImage a=new BufferedImage(i.getWidth(),i.getHeight(),BufferedImage.TYPE_INT_ARGB);a.getGraphics().drawImage(i,0,0,null);return new Bitmap(a);}
}
