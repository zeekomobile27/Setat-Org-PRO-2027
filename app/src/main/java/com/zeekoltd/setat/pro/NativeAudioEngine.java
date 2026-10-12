package com.zeekoltd.setat.pro;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NativeAudioEngine {

    private static NativeAudioEngine instance;
    private boolean isInitialized = false;

    // 🚀 مسار ديناميكي للذاكرة الآمنة للتطبيق (يتم تحديثه عند الاستدعاء ليطابق ZEEKOORG/Setat)
    private String SETS_FOLDER_PATH = "";

    static {
        System.loadLibrary("goldenorg");
    }

    public static NativeAudioEngine getInstance() {
        if (instance == null) {
            instance = new NativeAudioEngine();
        }
        return instance;
    }

    public native boolean initEngine();
    // 🚀 دالة الـ Native للإنهاء 
    private native void destroyEngineNative();
    private native void loadSampleNative(int bank, int index, float[] pcmData);
    private native void loadRhythmSampleNative(int bank, int index, float[] pcmData);
    
    public native void switchSoundBank(int bank);
    public native void switchRhythmBank(int bank);
    
    public native void playPoly(int index, float volume, float pitch);
    public native void releasePoly(int index);
    public native void playMono(int index, float volume, float pitch);
    public native void releaseMono();
    public native void stopAll();
    
    public native void setKeysVolume(float volume);
    // 🚀 دالة الجسر للتحكم بحجم الإيقاع من الجافا
    public native void setRhythmVolume(float volume);
    
    public native void setPitchBend(float multiplier);

    public native void playRhythm(int index);
    public native void stopRhythm();
    public native void setRhythmTempo(float tempo);
    public native int getActiveRhythmIndex();

    public interface LoadListener {
        void onAllLoaded();
    }

    // 🚀 دالة التدمير اليدوية لقتل العملية وإفراغ الذاكرة للفتح النظيف التالي
    public void destroy() {
        if (isInitialized) {
            stopAll();
            destroyEngineNative();
            isInitialized = false;
            instance = null;
        }
    }

    // 🚀 الدالة الشاملة الجبارة: تضخ 54 ملف صوتي لـ C++ بضغطة واحدة
    public void loadFullSetAsync(Context context, String s1, String s2, String r1, String r2, LoadListener listener) {
        if (!isInitialized) isInitialized = initEngine();
        
        // 🚀 جلب المسار الآمن المتوافق مع جوجل بلاي وتحديثه ديناميكياً
        File safeFolder = new File(context.getExternalFilesDir(null), "ZEEKOORG/Setat");
        SETS_FOLDER_PATH = safeFolder.getAbsolutePath();
        
        ExecutorService executor = Executors.newFixedThreadPool(8); // 8 مسارات متوازية للسرعة
        AtomicInteger loadedCount = new AtomicInteger(0);
        int totalFilesToLoad = (24 * 2) + (3 * 2); 

        // 1. تحميل بنوك الأصوات
        for (int bank = 0; bank < 2; bank++) {
            final String prefix = (bank == 0) ? s1 : s2;
            for (int i = 0; i < 24; i++) {
                final int b = bank;
                final int idx = i;
                executor.execute(() -> {
                    String fileNameNoExt = prefix + "_" + String.format(Locale.US, "%02d", (idx + 1));
                    File extFile = new File(SETS_FOLDER_PATH, fileNameNoExt + ".ogg");
                    float[] pcm = null;
                    if (extFile.exists()) {
                        pcm = decodeOggToFloatArrayFromFile(extFile.getAbsolutePath());
                    } else {
                        int resId = context.getResources().getIdentifier(fileNameNoExt, "raw", context.getPackageName());
                        if (resId != 0) pcm = decodeOggToFloatArray(context, resId);
                    }
                    if (pcm != null) loadSampleNative(b, idx, pcm);
                    
                    if (loadedCount.incrementAndGet() == totalFilesToLoad && listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(listener::onAllLoaded);
                    }
                });
            }
        }

        // 2. تحميل بنوك الإيقاعات
        for (int bank = 0; bank < 2; bank++) {
            final String prefix = (bank == 0) ? r1 : r2;
            String[] suffixes = {"_a", "_b", "_intro"};
            for (int i = 0; i < 3; i++) {
                final int b = bank;
                final int idx = i;
                executor.execute(() -> {
                    String fileNameNoExt = prefix + suffixes[idx];
                    File extFile = new File(SETS_FOLDER_PATH, fileNameNoExt + ".ogg");
                    float[] pcm = null;
                    if (extFile.exists()) {
                        pcm = decodeOggToFloatArrayFromFile(extFile.getAbsolutePath());
                    } else {
                        int resId = context.getResources().getIdentifier(fileNameNoExt, "raw", context.getPackageName());
                        if (resId != 0) pcm = decodeOggToFloatArray(context, resId);
                    }
                    if (pcm != null) loadRhythmSampleNative(b, idx, pcm);
                    
                    if (loadedCount.incrementAndGet() == totalFilesToLoad && listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(listener::onAllLoaded);
                    }
                });
            }
        }
    }

    private float[] decodeOggToFloatArray(Context context, int resId) {
        try {
            MediaExtractor extractor = new MediaExtractor();
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(resId);
            extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            MediaFormat format = extractor.getTrackFormat(0);
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            extractor.selectTrack(0);

            MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcmStream = new ByteArrayOutputStream();
            ByteBuffer[] inputBuffers = codec.getInputBuffers();
            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            boolean isEOS = false;
            while (!isEOS) {
                int inIndex = codec.dequeueInputBuffer(5000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 5000);
                while (outIndex >= 0) {
                    ByteBuffer outBuffer = outputBuffers[outIndex];
                    byte[] chunk = new byte[info.size];
                    outBuffer.get(chunk);
                    outBuffer.clear();
                    pcmStream.write(chunk, 0, chunk.length);
                    codec.releaseOutputBuffer(outIndex, false);
                    outIndex = codec.dequeueOutputBuffer(info, 5000);
                }
            }
            codec.stop();
            codec.release();
            extractor.release();

            byte[] pcmBytes = pcmStream.toByteArray();
            ByteBuffer bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
            short[] shortArray = new short[pcmBytes.length / 2];
            for (int i = 0; i < shortArray.length; i++) shortArray[i] = bb.getShort();

            if (channels == 2) {
                float[] monoData = new float[shortArray.length / 2];
                for (int i = 0; i < monoData.length; i++) {
                    monoData[i] = ((shortArray[i * 2] + shortArray[i * 2 + 1]) / 2.0f) / 32768.0f;
                }
                return monoData;
            } else {
                float[] floatData = new float[shortArray.length];
                for (int i = 0; i < floatData.length; i++) floatData[i] = shortArray[i] / 32768.0f;
                return floatData;
            }
        } catch (Exception e) {
            Log.e("ZeekoAudio", "Failed to decode", e);
            return null;
        }
    }

    private float[] decodeOggToFloatArrayFromFile(String filePath) {
        FileInputStream fis = null;
        try {
            MediaExtractor extractor = new MediaExtractor();
            fis = new FileInputStream(filePath);
            extractor.setDataSource(fis.getFD());

            int audioTrackIndex = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrackIndex = i;
                    break;
                }
            }
            if (audioTrackIndex == -1 && extractor.getTrackCount() > 0) audioTrackIndex = 0;
            if (audioTrackIndex == -1) return null;

            extractor.selectTrack(audioTrackIndex);
            MediaFormat format = extractor.getTrackFormat(audioTrackIndex);
            int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;

            MediaCodec codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format, null, null, 0);
            codec.start();

            ByteArrayOutputStream pcmStream = new ByteArrayOutputStream();
            ByteBuffer[] inputBuffers = codec.getInputBuffers();
            ByteBuffer[] outputBuffers = codec.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            boolean isEOS = false;
            while (!isEOS) {
                int inIndex = codec.dequeueInputBuffer(5000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }

                int outIndex = codec.dequeueOutputBuffer(info, 5000);
                while (outIndex >= 0) {
                    ByteBuffer outBuffer = outputBuffers[outIndex];
                    byte[] chunk = new byte[info.size];
                    outBuffer.get(chunk);
                    outBuffer.clear();
                    pcmStream.write(chunk, 0, chunk.length);
                    codec.releaseOutputBuffer(outIndex, false);
                    outIndex = codec.dequeueOutputBuffer(info, 5000);
                }
            }
            codec.stop();
            codec.release();
            extractor.release();
            fis.close();

            byte[] pcmBytes = pcmStream.toByteArray();
            ByteBuffer bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
            short[] shortArray = new short[pcmBytes.length / 2];
            for (int i = 0; i < shortArray.length; i++) shortArray[i] = bb.getShort();

            if (channels == 2) {
                float[] monoData = new float[shortArray.length / 2];
                for (int i = 0; i < monoData.length; i++) {
                    monoData[i] = ((shortArray[i * 2] + shortArray[i * 2 + 1]) / 2.0f) / 32768.0f;
                }
                return monoData;
            } else {
                float[] floatData = new float[shortArray.length];
                for (int i = 0; i < floatData.length; i++) floatData[i] = shortArray[i] / 32768.0f;
                return floatData;
            }
        } catch (Exception e) {
            Log.e("ZeekoAudio", "Failed to decode external file: " + filePath, e);
            try { if (fis != null) fis.close(); } catch(Exception ignored){}
            return null;
        }
    }
}
