package com.example.scamheat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class AdminDonutView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pending = 0;
    private int verified = 0;
    private int rejected = 0;

    private final int pendingColor = 0xFFFF9800;
    private final int verifiedColor = 0xFF16A34A;
    private final int rejectedColor = 0xFF9CA3AF;
    private final int trackColor = 0xFFE5E7EB;

    public AdminDonutView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCounts(int pending, int verified, int rejected) {
        this.pending = Math.max(0, pending);
        this.verified = Math.max(0, verified);
        this.rejected = Math.max(0, rejected);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float size = Math.min(getWidth(), getHeight());
        float stroke = Math.max(12f, size * 0.13f);
        float inset = stroke / 2f + 3f;

        RectF rect = new RectF(
                (getWidth() - size) / 2f + inset,
                (getHeight() - size) / 2f + inset,
                (getWidth() + size) / 2f - inset,
                (getHeight() + size) / 2f - inset
        );

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.BUTT);

        canvas.drawArc(rect, 0, 360, false, setColor(trackColor));

        int total = pending + verified + rejected;

        if (total > 0) {
            float start = -90f;
            float pendingSweep = 360f * pending / total;
            float verifiedSweep = 360f * verified / total;
            float rejectedSweep = 360f * rejected / total;

            if (pending > 0) {
                canvas.drawArc(rect, start, pendingSweep,
                        false, setColor(pendingColor));
                start += pendingSweep;
            }

            if (verified > 0) {
                canvas.drawArc(rect, start, verifiedSweep,
                        false, setColor(verifiedColor));
                start += verifiedSweep;
            }

            if (rejected > 0) {
                canvas.drawArc(rect, start, rejectedSweep,
                        false, setColor(rejectedColor));
            }
        }

        paint.setStyle(Paint.Style.FILL);
    }

    private Paint setColor(int color) {
        paint.setColor(color);
        return paint;
    }
}
