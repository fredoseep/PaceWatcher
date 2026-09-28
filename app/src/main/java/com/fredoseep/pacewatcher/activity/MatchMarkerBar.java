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
    private boolean dragging;

    public MatchMarkerBar(Context context) {
        super(context);
        setClickable(true);
        setContentDescription("回放进度条，可点击或拖动，黄色圆点表示比赛开始");
    }
    public void setMarkers(Collection<Long> values) { markers.clear(); markers.addAll(values); invalidate(); }
    public void setOnSeekListener(OnSeekListener value) { listener = value; }
    public void update(long position, long duration) {
        durationMs = duration;
        if (!dragging) positionMs = position;
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

    private long positionForTouch(float x, boolean snapToMarker) {
        float left = 20f * getResources().getDisplayMetrics().density;
        float usable = Math.max(1, getWidth() - 2 * left);
        float fraction = Math.max(0, Math.min(1, (x - left) / usable));
        long target = (long) (fraction * durationMs);
        if (snapToMarker) {
            long threshold = (long) (14f * getResources().getDisplayMetrics().density * durationMs / usable);
            Long before = markers.floor(target);
            Long after = markers.ceiling(target);
            Long nearest = before;
            if (nearest == null || after != null && after - target < target - nearest) nearest = after;
            if (nearest != null && Math.abs(nearest - target) <= threshold) return nearest;
        }
        return target;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (durationMs <= 0 || listener == null) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                positionMs = positionForTouch(event.getX(), true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                positionMs = positionForTouch(event.getX(), false);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) return false;
                positionMs = positionForTouch(event.getX(), true);
                dragging = false;
                listener.onSeek(positionMs);
                performClick();
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
