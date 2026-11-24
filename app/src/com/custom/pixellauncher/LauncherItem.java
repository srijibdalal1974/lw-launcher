package com.custom.pixellauncher;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class LauncherItem {
    public static final int TYPE_APP = 1;
    public static final int TYPE_FOLDER = 2;

    public int type;
    public AppInfo appInfo;
    public FolderInfo folderInfo;

    public LauncherItem(AppInfo appInfo) {
        this.type = TYPE_APP;
        this.appInfo = appInfo;
    }

    public LauncherItem(FolderInfo folderInfo) {
        this.type = TYPE_FOLDER;
        this.folderInfo = folderInfo;
    }

    public String getLabel() {
        if (type == TYPE_FOLDER && folderInfo != null) {
            return folderInfo.name;
        }
        return appInfo != null ? appInfo.label : "";
    }

    public Drawable getIcon(Context context) {
        if (type == TYPE_FOLDER && folderInfo != null) {
            return folderInfo.createIcon(context);
        }
        return appInfo != null ? appInfo.icon : null;
    }
}
