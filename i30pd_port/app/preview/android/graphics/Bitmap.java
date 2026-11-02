package android.graphics;

import java.awt.image.BufferedImage;

/** Preview-only Bitmap over a BufferedImage: enough for the layouts' pixel-art caches (album art stays empty). */
public class Bitmap {
    public enum Config{ARGB_8888}
    final BufferedImage img;
    Bitmap(BufferedImage img){this.img=img;}
    public Bitmap(){img=null;}
    public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
    public int getWidth(){return img==null?0:img.getWidth();}
    public int getHeight(){return img==null?0:img.getHeight();}
}
