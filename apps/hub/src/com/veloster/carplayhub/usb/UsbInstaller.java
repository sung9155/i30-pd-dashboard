package com.veloster.carplayhub.usb;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * USB update on the head unit: finds i30-update/ on a mounted stick, checks the signed manifest against this unit and
 * the installed apps, copies each APK into this app's own storage and checks the copy (UsbBundle), then installs it.
 *
 * Installing: both apps carry the platform signature the head unit accepts, so My Apps may hold INSTALL_PACKAGES and
 * use the hidden PackageManager.installPackage (Android 4.2) - no installer screen. When that is refused, the copy
 * goes to the system installer screen instead (one tap per app). The dashboard goes first; My Apps last, since
 * replacing itself ends this process (MY_PACKAGE_REPLACED then reports it).
 */
public final class UsbInstaller {
    /** Where the head unit mounts USB sticks: /storage/usb0 on the PD (seen in the car's boot records). Not /storage/sdcard1,
     *  which is the navigation map card here. */
    public static final String[] ROOTS = {"/storage/usb0", "/storage/usb1", "/storage/usb2", "/storage/usb3", "/mnt/usb", "/mnt/usb0", "/mnt/usb_storage"};
    public static final String PREFS = "usb_update";
    private static final int INSTALL_REPLACE_EXISTING = 2;

    /** A stick worth offering: the bundle, its folder, and the apps in it that are newer than what is installed. */
    public static final class Offer {
        public final UsbBundle bundle; public final File folder; public final List<UsbBundle.App> apps = new ArrayList<UsbBundle.App>();
        public final List<Integer> installed = new ArrayList<Integer>();
        Offer(UsbBundle b, File f) { bundle = b; folder = f; }
    }

    /** Where one app of an install stands: waiting, being copied and checked, being installed, installed. */
    public static final int WAIT = 0, COPY = 1, INSTALL = 2, OK = 3;
    /** step: one app's phase; percent: the whole install, 0..100 (copying by bytes, each install by an estimate). */
    public interface Progress { void step(int app, int phase); void percent(int p); }

    /** Turns bytes done into percent of the whole install, telling the screen only when the number changes. */
    private static final class Meter {
        final Progress progress; final long total; int last = -1;
        Meter(Progress p, long total) { progress = p; this.total = Math.max(1, total); }
        void at(long done) { int p = (int) Math.min(100, done * 100 / total); if (p != last) { last = p; progress.percent(p); } }
    }

    private final Context context;
    public UsbInstaller(Context c) { context = c.getApplicationContext(); }

    /** The first mounted stick (the given root first) with a valid bundle newer than the installed apps; null when none. */
    public Offer find(String preferredRoot) {
        lastError = null; current = null;
        List<String> roots = new ArrayList<String>();
        if (preferredRoot != null) roots.add(preferredRoot);
        for (String r : ROOTS) if (!roots.contains(r)) roots.add(r);
        for (String r : roots) {
            File folder = new File(r, UsbBundle.FOLDER);
            if (!new File(folder, UsbBundle.MANIFEST).isFile()) continue;
            try { Offer o = check(folder); if (!o.apps.isEmpty()) return o; current = o.bundle.release; } catch (Exception e) { lastError = e.getMessage(); }
        }
        return null;
    }

    /** Why the last stick with an i30-update folder was not offered (null when none was seen). */
    public String lastError;
    /** The release of a valid bundle that holds nothing newer than what is installed (null when none was seen). */
    public String current;

    public Offer check(File folder) throws Exception {
        UsbBundle b = UsbBundle.verify(UsbBundle.read(new File(folder, UsbBundle.MANIFEST), UsbBundle.MAX_MANIFEST),
            UsbBundle.read(new File(folder, UsbBundle.SIGNATURE), 256), UsbBundle.key(UsbUpdateKey.DER));
        b.checkDevice(Build.PRODUCT, Build.VERSION.SDK_INT);
        Offer o = new Offer(b, folder);
        for (UsbBundle.App a : b.apps) {
            PackageInfo p = installedInfo(a.packageName);
            int have = p == null ? 0 : p.versionCode;
            if (!UsbBundle.newer(a, have, p == null ? null : p.versionName)) continue;
            UsbBundle.require(b.signer.equals(signer(p)), "the installed " + a.packageName + " is signed differently");
            o.apps.add(a); o.installed.add(have);
        }
        return o;
    }

    private PackageInfo installedInfo(String name) {
        try { return context.getPackageManager().getPackageInfo(name, PackageManager.GET_SIGNATURES); } catch (PackageManager.NameNotFoundException e) { return null; }
    }

    private static String signer(PackageInfo p) throws Exception {
        UsbBundle.require(p != null && p.signatures != null && p.signatures.length == 1, "installed signer");
        return UsbBundle.hex(MessageDigest.getInstance("SHA-256").digest(p.signatures[0].toByteArray()));
    }

    /** Copy and check every APK of the offer, then install them in order. Returns a line per app. Background thread. */
    public List<String> install(Offer o, Progress progress) throws Exception {
        List<File> staged = new ArrayList<File>();
        long all = 0; for (UsbBundle.App a : o.apps) all += a.bytes;
        Meter meter = new Meter(progress, all * 2); long base = 0;   // copying all of them is half, installing the other half
        meter.at(0);
        try {
            for (int i = 0; i < o.apps.size(); i++) {
                UsbBundle.App a = o.apps.get(i);
                progress.step(i, COPY);
                File f = stage(new File(o.folder, a.file), a, meter, base); base += a.bytes;
                o.bundle.checkApk(f, a);
                PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
                UsbBundle.require(archive != null && a.packageName.equals(archive.packageName) && archive.versionCode == a.version && a.name.equals(archive.versionName), "APK package / version");
                staged.add(f);
                progress.step(i, WAIT);   // checked: waits its turn
            }
            List<String> done = new ArrayList<String>();
            for (int i = 0; i < o.apps.size(); i++) {
                UsbBundle.App a = o.apps.get(i); File f = staged.get(i);
                progress.step(i, INSTALL);
                if (a.packageName.equals(context.getPackageName()))   // replacing ourselves: the report comes after the restart
                    context.getSharedPreferences(PREFS, 0).edit().putInt("pending_self", a.version).putString("release", o.bundle.release).commit();
                silent(f);
                if (!waitFor(a.packageName, a.version, a.name, 180000, meter, base, a.bytes)) throw new IOException("install of " + a.packageName + " did not finish");
                base += a.bytes; meter.at(base);
                progress.step(i, OK);
                done.add(a.packageName + " " + a.name);
            }
            return done;
        } finally {
            for (File f : staged) f.delete();
        }
    }

    /** True when the silent path is refused (no INSTALL_PACKAGES): the caller then hands each copy to installerIntent. */
    public static boolean refused(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) if (t instanceof SecurityException || t instanceof NoSuchMethodException) return true;
        return false;
    }

    private void silent(File f) throws Exception {
        Class<?> observer = Class.forName("android.content.pm.IPackageInstallObserver");
        PackageManager pm = context.getPackageManager();
        try {
            pm.getClass().getMethod("installPackage", Uri.class, observer, int.class, String.class)
                .invoke(pm, Uri.fromFile(f), null, INSTALL_REPLACE_EXISTING, context.getPackageName());
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause(); if (c instanceof Exception) throw (Exception) c; throw e;
        }
    }

    /** Installed = the asked versionCode under the asked name (the name is what changes between this build's releases). */
    private boolean waitFor(String name, int version, String versionName, long timeoutMs, Meter meter, long base, long bytes) {
        long start = SystemClock.elapsedRealtime(), until = start + timeoutMs;
        // ponytail: the package manager tells nothing until it is done; the bar creeps on an estimate (5 s + 1 s per 2 MB)
        // and stops at 90 % of this app's share, so it never claims done early.
        double expect = 5000 + bytes / 2000.0;
        while (SystemClock.elapsedRealtime() < until) {
            meter.at(base + (long) (bytes * Math.min(0.9, (SystemClock.elapsedRealtime() - start) / expect)));
            PackageInfo p = installedInfo(name);
            if (p != null && p.versionCode == version && versionName.equals(p.versionName)) return true;
            SystemClock.sleep(500);
        }
        return false;
    }

    /** Fallback: the checked copy of one app for the system installer screen (API 17 takes file:// URIs). */
    public Intent installerIntent(Offer o, UsbBundle.App a) throws Exception {
        File f = stage(new File(o.folder, a.file), a, null, 0);
        o.bundle.checkApk(f, a);
        UsbBundle.require(f.setReadable(true, false), "installer file access");
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.fromFile(f), "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Copies an APK from the stick into files/ (readable by the package manager), checking its size on the way. */
    private File stage(File source, UsbBundle.App a, Meter meter, long base) throws Exception {
        UsbBundle.require(source.isFile() && source.length() == a.bytes, a.file + " missing or a different size");
        File dir = context.getFilesDir();
        dir.setExecutable(true, false);   // the package manager's copier reads the file by path
        File out = new File(dir, "usb-" + a.packageName + ".apk");
        out.delete();
        InputStream in = new FileInputStream(source);
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] b = new byte[65536]; int n; long total = 0;
            while ((n = in.read(b)) != -1) { total += n; UsbBundle.require(total <= a.bytes, a.file + " grew"); os.write(b, 0, n); if (meter != null) meter.at(base + total); }
        } finally { in.close(); os.close(); }
        out.setReadable(true, false);
        return out;
    }

    /** True the first time this bundle is offered by itself: a stick left plugged in must not open the screen at every
     *  start (it would, after [닫기] or with a test build installed). 내 앱 → USB 업데이트 always shows it. */
    public static boolean firstOffer(Context c, Offer o) {
        StringBuilder id = new StringBuilder(o.bundle.release);
        for (UsbBundle.App a : o.bundle.apps) id.append(':').append(a.sha256);
        android.content.SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        if (id.toString().equals(p.getString("offered", null))) return false;
        p.edit().putString("offered", id.toString()).apply();
        return true;
    }

    /** Removes the staged APK copies. install() does it itself, except when My Apps was one of them: replacing it ends
     *  the process first (seen on the API 17 emulator: 20 MB left in files/), so UsbUpdateDone calls this after the restart. */
    public static void clean(Context c) {
        File[] left = c.getFilesDir().listFiles();
        if (left != null) for (File f : left) if (f.getName().startsWith("usb-") && f.getName().endsWith(".apk")) f.delete();
    }

    /** After My Apps replaced itself: the version it asked for, or 0. Cleared once read. */
    public static int takePendingSelf(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        int v = p.getInt("pending_self", 0);
        if (v != 0) p.edit().remove("pending_self").apply();
        return v;
    }
}
