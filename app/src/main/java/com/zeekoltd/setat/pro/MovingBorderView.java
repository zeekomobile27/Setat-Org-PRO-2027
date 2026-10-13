package com.zeekoltd.setat.pro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class MovingBorderView extends View {

    private Paint paint;
    private RectF rectF;
    private Matrix matrix;
    private float rotation = 0f;
    private ValueAnimator animator;
    private float cornerRadius;

    private int colorTransparent;
    private int colorTail;
    private int colorHead;

    private boolean isVisible = true;
    private boolean isContinuous = false; // متغير للتحكم بوضع الدوران المستمر

    public MovingBorderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.5f * density); 
        paint.setStrokeCap(Paint.Cap.ROUND); 

        cornerRadius = 14f * density; 
        rectF = new RectF();
        matrix = new Matrix();

        setGlowColor("#FFFFFF");

        setupAnimator();
    }

    private void setupAnimator() {
        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        
        if (isContinuous) {
            // وضع الدوران المستمر البطيء لنافذة التشغيل
            animator.setDuration(4000); // إبطاء الحركة
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                isVisible = true;
                rotation = (float) animation.getAnimatedValue() * 360f;
                invalidate(); 
            });
        } else {
            // الوضع الافتراضي للشاشة الرئيسية (جريان ثم اختفاء)
            animator.setDuration(10000); 
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                
                if (progress <= 0.6f) {
                    isVisible = true;
                    float normalizedProgress = progress / 0.6f; 
                    rotation = normalizedProgress * 720f; 
                } else {
                    isVisible = false;
                }
                invalidate(); 
            });
        }
        
        animator.start();
    }

    // دالة لتفعيل وضع الدوران المستمر
    public void setContinuousMode(boolean continuous) {
        this.isContinuous = continuous;
        setupAnimator();
    }

    public void setCornerRadius(float radiusDp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        this.cornerRadius = radiusDp * density;
        invalidate();
    }

    public void setGlowColor(String hexCode) {
        int baseColor = Color.parseColor(hexCode);
        int r = Color.red(baseColor);
        int g = Color.green(baseColor);
        int b = Color.blue(baseColor);
        
        this.colorTransparent = Color.argb(0, r, g, b);
        this.colorTail = Color.argb(100, r, g, b);
        this.colorHead = Color.argb(255, r, g, b);
        
        if (getWidth() > 0 && getHeight() > 0) {
            updateGradient(getWidth(), getHeight());
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float strokePadding = paint.getStrokeWidth() / 2f;
        rectF.set(strokePadding, strokePadding, w - strokePadding, h - strokePadding);
        updateGradient(w, h);
    }

    private void updateGradient(int w, int h) {
        int[] colors = {
                colorTransparent,
                colorTransparent,
                colorTail,
                colorHead,
                colorTransparent
        };
        float[] positions = {0f, 0.4f, 0.8f, 0.98f, 1f};
        
        SweepGradient gradient = new SweepGradient(w / 2f, h / 2f, colors, positions);
        paint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isVisible) {
            matrix.setRotate(rotation, getWidth() / 2f, getHeight() / 2f);
            paint.getShader().setLocalMatrix(matrix);
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
        }
    }

    public void startAnimation() {
        if (!animator.isRunning()) animator.start();
    }

    public void stopAnimation() {
        if (animator.isRunning()) animator.cancel();
        isVisible = false;
        invalidate();
    }
}
