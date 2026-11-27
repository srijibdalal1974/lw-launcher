package com.custom.pixellauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

public class FolderInfo {
    public String id;
    public String name;
    public final List<AppInfo> apps = new ArrayList<>();

    public FolderInfo(String id, String name) {
        this.id = id;
        this.name = name != null ? name : "Folder";
    }

    public Drawable createIcon(Context context) {
        int size = 192;
        int pad = 2;
        float center = size * 0.5f;
        float radius = center - pad;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        boolean isNight = LauncherTheme.isNightMode(context);
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(isNight ? 0xB824282E : 0xD8ECF1F8);
        canvas.drawCircle(center, center, radius, bgPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.2f);
        borderPaint.setColor(isNight ? 0x33FFFFFF : 0x26000000);
        canvas.drawCircle(center, center, radius - 1.1f, borderPaint);

        int miniSize = 62;
        int[][] offsets = {
                {26, 26}, {104, 26},
                {26, 104}, {104, 104}
        };

        int count = Math.min(apps.size(), 4);
        for (int i = 0; i < count; i++) {
            Drawable d = apps.get(i).icon;
            if (d != null) {
                d.setBounds(offsets[i][0], offsets[i][1], offsets[i][0] + miniSize, offsets[i][1] + miniSize);
                d.draw(canvas);
            }
        }

        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
