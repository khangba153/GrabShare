package com.example.grabshare.presentation;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Vẽ số chưa đọc trên chuông, không thay kích thước hay vị trí nút trong header. */
final class UnreadNotificationBadge extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int count;

    UnreadNotificationBadge(float density) {
        this.density = density;
    }

    /** Chỉ nhận số lượng từ snapshot đã được máy chủ xác nhận. */
    void setCount(int count) {
        this.count = count;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (count <= 0) {
            return;
        }
        String label = String.valueOf(count);
        if (count > 99) {
            label = "99+";
        }
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(10 * density);
        float width = Math.max(18 * density, paint.measureText(label) + 8 * density);
        float right = getBounds().right - 2 * density;
        float top = getBounds().top + 2 * density;
        paint.setColor(Color.rgb(220, 38, 38));
        canvas.drawRoundRect(right - width, top, right, top + 18 * density,
                9 * density, 9 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float baseline = top + 9 * density - (metrics.ascent + metrics.descent) / 2;
        canvas.drawText(label, right - width / 2, baseline, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
