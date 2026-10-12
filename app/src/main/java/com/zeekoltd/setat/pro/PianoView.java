package com.zeekoltd.setat.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PianoView extends View {

    private Paint strokePaint, textGreenPaint, textRedPaint, dotPaint;
    
    private Bitmap whiteKeyNormal, whiteKeyPressed, blackKeyNormal, blackKeyPressed;
    private Bitmap scaledWhiteNormal, scaledWhitePressed, scaledBlackNormal, scaledBlackPressed;
    
    private List<Key> whiteKeys = new ArrayList<>();
    private List<Key> blackKeys = new ArrayList<>();
    
    private final int WHITE_KEYS_COUNT = 14; 
    private final String[] notesLatin = {"Do", "Re", "Mi", "Fa", "Sol", "La", "Si"};
    
    private Map<Integer, Key> activePointers = new HashMap<>();
    private boolean[] detunedNotes = new boolean[12];
    
    private PianoListener listener;
    private boolean touchEnabled = false;

    public interface PianoListener {
        void onKeyDown(int noteIndex);
        void onKeyUp(int noteIndex);
    }

    public PianoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaintsAndBitmaps();
        
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public void setPianoListener(PianoListener listener) {
        this.listener = listener;
    }

    public void setTouchEnabled(boolean enabled) {
        this.touchEnabled = enabled;
    }

    public void setDetunedNotes(boolean[] detuned) {
        this.detunedNotes = detuned;
        invalidate(); 
    }

    public void clearAllTouches() {
        activePointers.clear();
        for (Key k : whiteKeys) k.pressedCount = 0;
        for (Key k : blackKeys) k.pressedCount = 0;
        invalidate();
    }

    private void initPaintsAndBitmaps() {
        whiteKeyNormal = BitmapFactory.decodeResource(getResources(), R.drawable.zeeko10);
        whiteKeyPressed = BitmapFactory.decodeResource(getResources(), R.drawable.zeeko11);
        blackKeyNormal = BitmapFactory.decodeResource(getResources(), R.drawable.zeeko12);
        blackKeyPressed = BitmapFactory.decodeResource(getResources(), R.drawable.zeeko13);

        strokePaint = new Paint();
        strokePaint.setColor(Color.parseColor("#1A1A1A"));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);

        textGreenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textGreenPaint.setColor(Color.parseColor("#1DB954"));
        textGreenPaint.setTextSize(32f);
        textGreenPaint.setTextAlign(Paint.Align.CENTER);

        textRedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textRedPaint.setColor(Color.parseColor("#FF3333"));
        textRedPaint.setTextSize(32f);
        textRedPaint.setTextAlign(Paint.Align.CENTER);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.parseColor("#00FFFF"));
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateKeys(w, h);
    }

    private void calculateKeys(int width, int height) {
        whiteKeys.clear();
        blackKeys.clear();

        float whiteKeyWidth = (float) width / WHITE_KEYS_COUNT;
        float blackKeyWidth = whiteKeyWidth * 0.90f; 
        float blackKeyHeight = height * 0.50f; 

        if (width > 0 && height > 0) {
            int wWhite = (int) Math.ceil(whiteKeyWidth);
            int wBlack = (int) Math.ceil(blackKeyWidth);
            
            scaledWhiteNormal = Bitmap.createScaledBitmap(whiteKeyNormal, wWhite, height, true);
            scaledWhitePressed = Bitmap.createScaledBitmap(whiteKeyPressed, wWhite, height, true);
            scaledBlackNormal = Bitmap.createScaledBitmap(blackKeyNormal, wBlack, (int) blackKeyHeight, true);
            scaledBlackPressed = Bitmap.createScaledBitmap(blackKeyPressed, wBlack, (int) blackKeyHeight, true);
        }

        int noteIndex = 0; 
        int whiteNoteLabelIndex = 0;

        for (int i = 0; i < WHITE_KEYS_COUNT; i++) {
            float left = i * whiteKeyWidth;
            float right = left + whiteKeyWidth;
            RectF rect = new RectF(left, 0, right, height);
            
            whiteKeys.add(new Key(rect, noteIndex, false, notesLatin[whiteNoteLabelIndex % 7], whiteNoteLabelIndex < 7));
            whiteNoteLabelIndex++;
            
            int noteInOctave = i % 7;
            if (noteInOctave != 2 && noteInOctave != 6 && i < WHITE_KEYS_COUNT - 1) {
                noteIndex++;
                
                // 🚀 إزاحة المفتاح الأسود لليسار لتعويض مساحة الظل وجعله يتوسط المسافة بصرياً
                float leftShiftOffset = blackKeyWidth * 0.12f; 
                float bLeft = right - (blackKeyWidth / 2f) - leftShiftOffset;
                float bRight = bLeft + blackKeyWidth;
                RectF bRect = new RectF(bLeft, 0, bRight, blackKeyHeight);
                blackKeys.add(new Key(bRect, noteIndex, true, "", false));
            }
            noteIndex++;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Key key : whiteKeys) {
            Bitmap bmp = (key.pressedCount > 0) ? scaledWhitePressed : scaledWhiteNormal;
            if (bmp != null) {
                canvas.drawBitmap(bmp, key.rect.left, key.rect.top, null);
            }
            
            canvas.drawRect(key.rect, strokePaint);
            
            Paint textPaint = key.isFirstOctave ? textGreenPaint : textRedPaint;
            canvas.drawText(key.label, key.rect.centerX(), key.rect.bottom - 15f, textPaint);
            
            if (detunedNotes[key.noteIndex % 12]) {
                canvas.drawCircle(key.rect.centerX(), key.rect.bottom - 65f, 6f, dotPaint);
            }
        }

        for (Key key : blackKeys) {
            Bitmap bmp = (key.pressedCount > 0) ? scaledBlackPressed : scaledBlackNormal;
            if (bmp != null) {
                canvas.drawBitmap(bmp, key.rect.left, key.rect.top, null);
            }
            
            if (detunedNotes[key.noteIndex % 12]) {
                canvas.drawCircle(key.rect.centerX(), key.rect.bottom - 25f, 6f, dotPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!touchEnabled) return true; 

        int action = event.getActionMasked();
        boolean needsVisualUpdate = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int actionIndex = event.getActionIndex();
                int pointerId = event.getPointerId(actionIndex);
                Key key = getTouchedKey(event.getX(actionIndex), event.getY(actionIndex));
                if (key != null) {
                    activePointers.put(pointerId, key);
                    pressKey(key);
                    needsVisualUpdate = true;
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pId = event.getPointerId(i);
                    Key newKey = getTouchedKey(event.getX(i), event.getY(i));
                    Key oldKey = activePointers.get(pId);

                    if (newKey != oldKey) {
                        if (oldKey != null) {
                            releaseKey(oldKey); 
                        }
                        if (newKey != null) {
                            pressKey(newKey);   
                        }
                        activePointers.put(pId, newKey);
                        needsVisualUpdate = true;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int actionIndex = event.getActionIndex();
                int pointerId = event.getPointerId(actionIndex);
                Key key = activePointers.get(pointerId);
                if (key != null) {
                    releaseKey(key);
                    activePointers.remove(pointerId);
                    needsVisualUpdate = true;
                }
                break;
            }
        }
        
        if (needsVisualUpdate) {
            invalidate();
        }
        
        return true;
    }

    private void pressKey(Key key) {
        key.pressedCount++;
        if (key.pressedCount == 1 && listener != null) {
            listener.onKeyDown(key.noteIndex);
        }
    }

    private void releaseKey(Key key) {
        if (key.pressedCount > 0) {
            key.pressedCount--;
            if (key.pressedCount == 0 && listener != null) {
                listener.onKeyUp(key.noteIndex);
            }
        }
    }

    private Key getTouchedKey(float x, float y) {
        for (Key key : blackKeys) if (key.rect.contains(x, y)) return key;
        for (Key key : whiteKeys) if (key.rect.contains(x, y)) return key;
        return null;
    }

    private static class Key {
        RectF rect;
        int noteIndex;
        boolean isBlack;
        String label;
        boolean isFirstOctave;
        int pressedCount = 0;

        Key(RectF rect, int noteIndex, boolean isBlack, String label, boolean isFirstOctave) {
            this.rect = rect;
            this.noteIndex = noteIndex;
            this.isBlack = isBlack;
            this.label = label;
            this.isFirstOctave = isFirstOctave;
        }
    }
}
