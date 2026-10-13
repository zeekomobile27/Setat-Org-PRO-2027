package com.zeekoltd.setat.pro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class SmoothWaveView extends View {
    private Paint paint;
    private Path path;
    private Path path2;
    private float phase = 0f;
    private float amplitude = 10f;
    private float targetAmplitude = 2f; 
    private ValueAnimator animator;
    private boolean isPlaying = false;

    public SmoothWaveView(Context context) {
        super(context);
        init();
    }

    public SmoothWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f); // زيادة العرض قليلاً لإبراز التوهج بشكل أفضل
        
        // تأثير التوهج (Glow)
        paint.setShadowLayer(12f, 0f, 0f, Color.parseColor("#00FFFF"));
        setLayerType(LAYER_TYPE_SOFTWARE, paint);

        path = new Path();
        path2 = new Path();

        // محرك الحركة المستمرة
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            phase += 0.15f; 
            if (Math.abs(amplitude - targetAmplitude) > 0.5f) {
                amplitude += (targetAmplitude - amplitude) * 0.1f;
            }
            invalidate();
        });
        animator.start(); 
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            // هنا السر: إنشاء تدرج لوني يجعل الأطراف (اليمين واليسار) شفافة تماماً
            int cyan = Color.parseColor("#00FFFF");
            int transparent = Color.TRANSPARENT;
            
            LinearGradient shader = new LinearGradient(
                    0, 0, w, 0,
                    new int[]{transparent, cyan, cyan, transparent},
                    new float[]{0.0f, 0.2f, 0.8f, 1.0f}, // التلاشي يبدأ في أول 20% وآخر 20% من العرض
                    Shader.TileMode.CLAMP
            );
            paint.setShader(shader);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        int centerY = height / 2;

        path.reset();
        path.moveTo(0, centerY);

        float frequency = 1.5f; 

        // رسم الموجة الأولى
        for (int i = 0; i <= width; i += 5) {
            float x = (float) i;
            float y = (float) (centerY + amplitude * Math.sin(2 * Math.PI * frequency * (x / width) + phase));
            
            // تنعيم ارتفاع الموجة لتصبح مسطحة عند الأطراف
            float edgeMultiplier = (float) Math.sin(Math.PI * (x / width));
            y = centerY + (y - centerY) * edgeMultiplier;

            path.lineTo(x, y);
        }
        paint.setAlpha(255);
        canvas.drawPath(path, paint);
        
        // رسم موجة ثانية متداخلة لإعطاء عمق للتصميم
        path2.reset();
        path2.moveTo(0, centerY);
        for (int i = 0; i <= width; i += 5) {
            float x = (float) i;
            float y = (float) (centerY + (amplitude * 0.6f) * Math.sin(2 * Math.PI * (frequency * 1.2f) * (x / width) + (phase * 1.4f)));
            float edgeMultiplier = (float) Math.sin(Math.PI * (x / width));
            y = centerY + (y - centerY) * edgeMultiplier;
            path2.lineTo(x, y);
        }
        paint.setAlpha(120); 
        canvas.drawPath(path2, paint);
    }

    public void startAnimation() {
        isPlaying = true;
        targetAmplitude = getHeight() / 2.5f; // ارتفاع الموجة عند التشغيل
    }

    public void stopAnimation() {
        isPlaying = false;
        targetAmplitude = 2f; // وضع السكون (تتنفس ببطء)
    }
}