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
        int size = 160;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0x40FFFFFF);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setColor(0x50FFFFFF);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, borderPaint);

        int miniSize = 52;
        int[][] offsets = {
                {22, 22}, {86, 22},
                {22, 86}, {86, 86}
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
