package com.veloster.menu;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import com.hkmc.system.app.downloadmenu.define.Item;
import java.util.ArrayList;

/** Called only from the OEM menu's existing main-thread initialization. Adds the dashboard and My Apps, each only
 *  when it is installed, enabled and launchable; otherwise the original OEM list is kept. */
public final class HubEntryHelper {
    private static final String[][] ENTRIES = {
        {"com.veloster.dashboard", "com.veloster.dashboard.DashboardActivity"},
        {"com.veloster.carplayhub", "com.veloster.carplayhub.HubActivity"},
    };
    private static final ArrayList<Item> cached = new ArrayList<Item>();
    private static int oemCount;

    private HubEntryHelper() {}

    public static void prepare(Context context) {
        cached.clear();
        oemCount = 0;
        PackageManager pm = context.getPackageManager();
        for (int i = 0; i < ENTRIES.length; i++) {
            try {
                ActivityInfo info = pm.getActivityInfo(new ComponentName(ENTRIES[i][0], ENTRIES[i][1]), 0);
                if (!info.enabled || !info.exported || !info.applicationInfo.enabled) continue;
                if (info.permission != null && pm.checkPermission(info.permission, context.getPackageName())
                        != PackageManager.PERMISSION_GRANTED) continue;
                Drawable drawable = info.loadIcon(pm);
                if (drawable == null) continue;
                Bitmap icon = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, 128, 128);
                drawable.draw(new Canvas(icon));
                cached.add(new Item(0x10000 + i, ENTRIES[i][0], ENTRIES[i][1], info.loadLabel(pm).toString(), icon, icon));
            } catch (Exception unavailable) {
                // Missing, disabled or inaccessible: that entry is left out.
            }
        }
    }

    public static int adjustDisplayCount(int original) {
        oemCount = original;
        return original + cached.size();
    }

    public static int originalCount() { return oemCount; }

    public static void append(ArrayList<Item> items) {
        for (Item add : cached) {
            boolean present = false;
            for (Item item : items)
                if (add.getPackageName().equals(item.getPackageName()) && add.getClassName().equals(item.getClassName())) present = true;
            if (!present) items.add(add);
        }
    }
}
