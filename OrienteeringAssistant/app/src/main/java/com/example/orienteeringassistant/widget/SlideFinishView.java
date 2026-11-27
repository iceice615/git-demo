package com.example.orienteeringassistant.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 滑动结束控件（仿外卖 App 的「滑动送达」）
 * 用法：
 *   SlideFinishView view = findViewById(R.id.slideFinish);
 *   view.setOnSlideCompleteListener(() -> { ... });
 *
 * 防误触原理：只有按住圆钮一路滑到最右侧才触发，单击、误碰、滑一半都不生效。
 */
public class SlideFinishView extends View {

    public interface OnSlideCompleteListener {
        void onComplete();
    }

    private static final int COLOR_TRACK = 0xFFE4E7EC;
    private static final int COLOR_FILL = 0xFFD92D20;   // 结束比赛用红色，视觉上更"危险"
    private static final int COLOR_TEXT = 0xFF667085;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path arrowPath = new Path();

    private float progress = 0f;   // 0~1
    private boolean dragging = false;
    private boolean completed = false;
    private OnSlideCompleteListener listener;
    private String label = "滑动结束比赛";

    public SlideFinishView(Context context) {
        this(context, null);
    }

    public SlideFinishView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setColor(COLOR_TRACK);
        fillPaint.setColor(COLOR_FILL);
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setShadowLayer(dp(3), 0, dp(1), 0x33000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // 为了阴影生效
        arrowPaint.setColor(COLOR_FILL);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(dp(2.5f));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(dp(15));
        textPaint.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
    }

    public void setOnSlideCompleteListener(OnSlideCompleteListener l) {
        this.listener = l;
    }

    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    /** 重置滑块位置（结束比赛被取消时可再次滑动） */
    public void reset() {
        progress = 0f;
        completed = false;
        dragging = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float radius = h / 2f;
        float thumbRadius = radius - dp(4);
        float minCx = radius;
        float maxCx = w - radius;
        float cx = minCx + (maxCx - minCx) * progress;

        // 底槽
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        // 已滑过部分的填充
        if (progress > 0) {
            rect.set(0, 0, cx, h);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
        }

        // 滑块圆钮
        canvas.drawCircle(cx, h / 2f, thumbRadius, thumbPaint);

        // 圆钮内的箭头（>）
        float a = dp(6);
        arrowPath.reset();
        arrowPath.moveTo(cx - a * 0.5f, h / 2f - a);
        arrowPath.lineTo(cx + a * 0.5f, h / 2f);
        arrowPath.lineTo(cx - a * 0.5f, h / 2f + a);
        canvas.drawPath(arrowPath, arrowPaint);

        // 提示文字，滑动时渐隐
        int alpha = (int) (255 * (1 - Math.min(1f, progress * 1.6f)));
        if (alpha > 10) {
            textPaint.setAlpha(alpha);
            canvas.drawText(label, w / 2f + dp(16), h / 2f + dp(5), textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;
        float usable = w - 2 * radius;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                completed = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!dragging || usable <= 0) {
                    return true;
                }
                progress = (event.getX() - radius) / usable;
                progress = Math.max(0f, Math.min(1f, progress));
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                if (progress >= 0.9f) {
                    // 滑到底：触发
                    progress = 1f;
                    invalidate();
                    completed = true;
                    if (listener != null) {
                        listener.onComplete();
                    }
                    postDelayed(this::reset, 600);
                } else {
                    progress = 0f;
                    invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.45f);
        if (!enabled) {
            reset();
        }
    }

    public boolean isCompleted() {
        return completed;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
