package com.custom.pixellauncher;

import android.graphics.Color;
import android.graphics.drawable.Drawable;

public class AppInfo implements Comparable<AppInfo> {
    public final String label;
    public final String packageName;
    public final String activityName;
    public final Drawable icon;
    public final int splashAccentColor;

    public AppInfo(String label, String packageName, String activityName, Drawable icon) {
        this(label, packageName, activityName, icon, Color.TRANSPARENT);
    }

    public AppInfo(String label, String packageName, String activityName, Drawable icon, int splashAccentColor) {
        this.label = label != null ? label : "";
        this.packageName = packageName != null ? packageName : "";
        this.activityName = activityName != null ? activityName : "";
        this.icon = icon;
        this.splashAccentColor = splashAccentColor;
    }

    @Override
    public int compareTo(AppInfo other) {
        if (other == null) return 1;
        return this.label.compareToIgnoreCase(other.label);
    }
}
