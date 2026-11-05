package dev.pocketpulse.monitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Fixed storage, no animation loop; redraws only with a sample or resize. */
final class Sparkline extends View {
    final MetricMath.History history = new MetricMath.History(90);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int color;
    Sparkline(Context context, int color) {
        super(context);
        this.color = color;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void add(float value) { history.add(value); invalidate(); }
    void clear() { history.clear(); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth(), height = getHeight() - 3;
        paint.setStrokeWidth(getResources().getDisplayMetrics().density);
        paint.setColor(0xFF283340);
        for (int i = 1; i < 4; i++) canvas.drawLine(0, height * i / 4, width, height * i / 4, paint);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        path.rewind();
        boolean connected = false;
        for (int i = 0; i < history.size(); i++) {
            float value = history.get(i);
            if (!Float.isFinite(value)) { connected = false; continue; }
            float x = width * (90 - history.size() + i) / 89f;
            float y = height * (1 - Math.max(0, Math.min(100, value)) / 100f) + 1;
            if (connected) path.lineTo(x, y); else path.moveTo(x, y);
            connected = true;
            if (i == history.size() - 1) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, 2.5f * getResources().getDisplayMetrics().density, paint);
                paint.setStyle(Paint.Style.STROKE);
            }
        }
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
