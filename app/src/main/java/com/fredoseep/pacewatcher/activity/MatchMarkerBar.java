package com.fredoseep.pacewatcher.activity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import java.util.Collection;
import java.util.TreeSet;

public class MatchMarkerBar extends View {
    public interface OnSeekListener { void onSeek(long positionMs); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TreeSet<Long> markers = new TreeSet<>();
    private OnSeekListener listener;
    private long positionMs;
    private long durationMs;

    public MatchMarkerBar(Context context) { super(context); }
    public void setMarkers(Collection<Long> values) { markers.clear(); markers.addAll(values); invalidate(); }
    public void setOnSeekListener(OnSeekListener value) { listener = value; }
    public void update(long position, long duration) {
        positionMs = position;
        durationMs = duration;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = 20f * getResources().getDisplayMetrics().density;
        float right = getWidth() - left;
        float center = getHeight() / 2f;
        paint.setStrokeWidth(5f * getResources().getDisplayMetrics().density);
        paint.setColor(Color.DKGRAY);
        canvas.drawLine(left, center, right, center, paint);
        if (durationMs <= 0 || right <= left) return;
        paint.setColor(Color.WHITE);
        canvas.drawLine(left, center, left + (right-left) * Math.min(positionMs, durationMs) / durationMs, center, paint);
        paint.setColor(Color.rgb(255, 193, 58));
        for (long point : markers) {
            if (point <= durationMs) canvas.drawCircle(left + (right-left) * point / durationMs,
                    center, 5f * getResources().getDisplayMetrics().density, paint);
        }
        paint.setColor(Color.WHITE);
        canvas.drawCircle(left + (right-left) * Math.min(positionMs, durationMs) / durationMs,
                center, 7f * getResources().getDisplayMetrics().density, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (durationMs <= 0 || listener == null) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE
                || event.getAction() == MotionEvent.ACTION_UP) {
            float left = 20f * getResources().getDisplayMetrics().density;
            float usable = Math.max(1, getWidth() - 2 * left);
            float fraction = Math.max(0, Math.min(1, (event.getX() - left) / usable));
            listener.onSeek((long) (fraction * durationMs));
            return true;
        }
        return super.onTouchEvent(event);
    }
}
