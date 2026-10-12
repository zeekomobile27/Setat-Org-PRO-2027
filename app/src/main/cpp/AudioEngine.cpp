#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <vector>
#include <atomic>
#include <cmath>
#include <algorithm>
#include <thread>
#include <chrono>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ZeekoAudio", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ZeekoAudio", __VA_ARGS__)

using namespace oboe;

struct Voice {
    std::atomic<bool> active{false};
    std::atomic<int> sampleIndex{-1};
    int bankIndex = 0; 
    float position = 0.0f;
    float pitchRate = 1.0f;
    float volume = 0.0f;
    
    std::atomic<bool> releasing{false};
    float envelope = 0.0f; 
    bool isAttacking = true;
};

struct MonoVoice {
    std::atomic<bool> active{false};
    int sampleIndex = -1;
    int bankIndex = 0; 
    float position = 0.0f;
    float currentPitchRate = 1.0f;
    float targetPitchRate = 1.0f;
    float volume = 0.0f;
    float envelope = 0.0f;
    bool isAttacking = false;
    std::atomic<bool> releasing{false};
    
    int prevSampleIndex = -1;
    int prevBankIndex = 0; 
    float prevPosition = 0.0f;
    bool isCrossfading = false;
    float crossfadeProgress = 1.0f;
};

// 🚀 توريث ErrorCallback لمعالجة فصل/شبك السماعة
class ZeekoAudioEngine : public AudioStreamDataCallback, public AudioStreamErrorCallback {
private:
    AudioStream *stream{};
    std::mutex streamLock; // حماية المسار أثناء إعادة التشغيل
    
    std::vector<std::vector<std::vector<float>>> soundBanks; 
    std::vector<std::vector<std::vector<float>>> rhythmBanks; 

    static const int MAX_VOICES = 32;
    Voice voices[MAX_VOICES];
    MonoVoice monoVoice; 
    
    std::atomic<int> currentSoundBank{0};
    std::atomic<int> currentRhythmBank{0};

    std::atomic<float> keysGlobalVolume{1.0f};
    std::atomic<float> globalPitchBend{1.0f};

    std::atomic<bool> isRhythmPlaying{false};
    std::atomic<int> currentRhythmIndex{0};
    std::atomic<int> previousRhythmIndex{0}; 
    std::atomic<int> pendingRhythmIndex{-1};
    std::atomic<bool> hasPendingRhythm{false};
    std::atomic<float> rhythmTempo{1.0f};    
    float rhythmPosition = 0.0f;
    
    std::atomic<float> rhythmVolume{0.85f};

    static const int SAMPLE_RATE = 44100;
    static const int DELAY_BUFFER_SIZE = SAMPLE_RATE * 2; 
    float delayBuffer[DELAY_BUFFER_SIZE] = {0};
    int delayWriteIdx = 0;
    float lastEcho1 = 0.0f;
    float lastEcho2 = 0.0f;

    // 🚀 متغير للتحكم بإعادة التشغيل
    std::atomic<bool> isRestarting{false};

public:
    ZeekoAudioEngine() {
        soundBanks.resize(2, std::vector<std::vector<float>>(24));
        rhythmBanks.resize(2, std::vector<std::vector<float>>(3)); 
    }

    bool start() {
        std::lock_guard<std::mutex> lock(streamLock);
        
        AudioStreamBuilder builder;
        builder.setDirection(Direction::Output);
        builder.setPerformanceMode(PerformanceMode::LowLatency); 
        builder.setSharingMode(SharingMode::Exclusive); 
        builder.setFormat(AudioFormat::Float);
        builder.setChannelCount(2); 
        builder.setSampleRate(SAMPLE_RATE);
        builder.setDataCallback(this);
        builder.setErrorCallback(this); // 🚀 تفعيل مراقب الأخطاء هنا

        Result result = builder.openStream(&stream);
        if (result != Result::OK) {
            LOGE("Failed to open stream. Error: %s", convertToText(result));
            return false;
        }
        
        stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2); 
        result = stream->requestStart();
        if (result != Result::OK) {
            LOGE("Failed to start stream. Error: %s", convertToText(result));
            return false;
        }
        
        isRestarting.store(false);
        LOGI("Stream started successfully.");
        return true;
    }

    // 🚀 الحل الجذري والآمن لفصل السماعة
    void onErrorAfterClose(AudioStream *oboeStream, Result error) override {
        if (error == Result::ErrorDisconnected) {
            LOGI("Stream disconnected. Initiating safe restart...");
            if (!isRestarting.exchange(true)) {
                std::thread([this]() {
                    // الانتظار قليلاً ليقوم نظام أندرويد بتفريغ مسارات الهاردوير
                    std::this_thread::sleep_for(std::chrono::milliseconds(50));
                    
                    std::lock_guard<std::mutex> lock(streamLock);
                    // لا نستدعي requestStop هنا لأن المسار مات بالفعل وهذا يسبب التعليق.
                    if (stream != nullptr) {
                        stream->close();
                        stream = nullptr;
                    }
                    
                    // إعادة بناء المسار الصوتي
                    AudioStreamBuilder builder;
                    builder.setDirection(Direction::Output);
                    builder.setPerformanceMode(PerformanceMode::LowLatency); 
                    builder.setSharingMode(SharingMode::Exclusive); 
                    builder.setFormat(AudioFormat::Float);
                    builder.setChannelCount(2); 
                    builder.setSampleRate(SAMPLE_RATE);
                    builder.setDataCallback(this);
                    builder.setErrorCallback(this); 

                    if (builder.openStream(&stream) == Result::OK) {
                        stream->setBufferSizeInFrames(stream->getFramesPerBurst() * 2);
                        stream->requestStart();
                    }
                    isRestarting.store(false);
                    LOGI("Stream restarted safely.");
                }).detach();
            }
        }
    }

    void destroy() {
        std::lock_guard<std::mutex> lock(streamLock);
        if (stream != nullptr) {
            stream->requestStop();
            stream->close();
            stream = nullptr;
        }
        stopAll();
        for (auto &bank : soundBanks) {
            for (auto &sample : bank) {
                sample.clear();
            }
        }
        for (auto &bank : rhythmBanks) {
            for (auto &sample : bank) {
                sample.clear();
            }
        }
    }

    void loadSample(int bank, int index, const float* data, int numFrames) {
        if (bank >= 0 && bank < 2 && index >= 0 && index < 24) 
            soundBanks[bank][index].assign(data, data + numFrames);
    }

    void loadRhythmSample(int bank, int index, const float* data, int numFrames) {
        if (bank >= 0 && bank < 2 && index >= 0 && index < 3) 
            rhythmBanks[bank][index].assign(data, data + numFrames);
    }

    void switchSoundBank(int bank) { currentSoundBank.store(bank); }
    void switchRhythmBank(int bank) { currentRhythmBank.store(bank); }

    void setKeysVolume(float vol) { keysGlobalVolume.store(vol); }
    void setRhythmVolume(float vol) { rhythmVolume.store(vol); }
    void setPitchBend(float mult) { globalPitchBend.store(mult); }

    void playPoly(int noteIndex, float volume, float pitchRate) {
        int activeBank = currentSoundBank.load();
        if (noteIndex < 0 || noteIndex >= 24 || soundBanks[activeBank][noteIndex].empty()) return;
        for (auto & voice : voices) {
            if (!voice.active.load()) {
                voice.bankIndex = activeBank;
                voice.sampleIndex.store(noteIndex);
                voice.position = 0.0f;
                voice.volume = volume;
                voice.pitchRate = pitchRate;
                voice.releasing.store(false);
                voice.envelope = 0.0f; 
                voice.isAttacking = true;
                voice.active.store(true);
                break;
            }
        }
    }

    void releasePoly(int noteIndex) {
        for (auto & voice : voices) {
            if (voice.active.load() && voice.sampleIndex.load() == noteIndex) {
                voice.releasing.store(true);
            }
        }
    }

    void playMono(int noteIndex, float volume, float pitchRate) {
        int activeBank = currentSoundBank.load();
        if (noteIndex < 0 || noteIndex >= 24 || soundBanks[activeBank][noteIndex].empty()) return;
        
        if (monoVoice.active.load()) {
            monoVoice.prevBankIndex = monoVoice.bankIndex;
            monoVoice.prevSampleIndex = monoVoice.sampleIndex;
            monoVoice.prevPosition = monoVoice.position;
            monoVoice.isCrossfading = true;
            monoVoice.crossfadeProgress = 0.0f;
            
            monoVoice.bankIndex = activeBank;
            monoVoice.sampleIndex = noteIndex;
            monoVoice.position = 0.0f; 
            monoVoice.targetPitchRate = pitchRate;
            monoVoice.volume = volume;
            monoVoice.releasing.store(false);
            monoVoice.isAttacking = true; 
        } else {
            monoVoice.bankIndex = activeBank;
            monoVoice.sampleIndex = noteIndex;
            monoVoice.position = 0.0f;
            monoVoice.currentPitchRate = pitchRate;
            monoVoice.targetPitchRate = pitchRate;
            monoVoice.volume = volume;
            monoVoice.envelope = 0.0f;
            monoVoice.isAttacking = true;
            monoVoice.releasing.store(false);
            monoVoice.isCrossfading = false;
            monoVoice.active.store(true);
        }
    }

    void releaseMono() {
        monoVoice.releasing.store(true);
    }

    void playRhythm(int index) {
        if (index >= 0 && index < 3) {
            if (index != 2) previousRhythmIndex.store(index); 
            if (isRhythmPlaying.load()) {
                pendingRhythmIndex.store(index);
                hasPendingRhythm.store(true);
            } else {
                currentRhythmIndex.store(index);
                rhythmPosition = 0.0f;
                isRhythmPlaying.store(true);
            }
        }
    }

    void stopRhythm() {
        isRhythmPlaying.store(false);
        hasPendingRhythm.store(false);
    }

    void setRhythmTempo(float tempo) { rhythmTempo.store(tempo); }
    int getActiveRhythmIndex() { return currentRhythmIndex.load(); }

    void stopAll() {
        for (auto & voice : voices) voice.active.store(false);
        monoVoice.active.store(false);
        isRhythmPlaying.store(false);
        hasPendingRhythm.store(false);
        std::fill(std::begin(delayBuffer), std::end(delayBuffer), 0.0f);
    }

    DataCallbackResult onAudioReady(AudioStream *oboeStream, void *audioData, int32_t numFrames) override {
        if (isRestarting.load()) return DataCallbackResult::Continue;

        auto *floatData = static_cast<float *>(audioData);

        for (int i = 0; i < numFrames; ++i) {
            float keysMix = 0.0f; 
            float rhythmMix = 0.0f; 

            for (auto & voice : voices) {
                if (voice.active.load()) keysMix += processVoice(voice);
            }
            if (monoVoice.active.load()) keysMix += processMonoVoice(); 

            keysMix *= keysGlobalVolume.load();

            int readIdx1 = (delayWriteIdx - (int)(SAMPLE_RATE * 0.55f) + DELAY_BUFFER_SIZE) % DELAY_BUFFER_SIZE;
            int readIdx2 = (delayWriteIdx - (int)(SAMPLE_RATE * 1.25f) + DELAY_BUFFER_SIZE) % DELAY_BUFFER_SIZE; 
            float rawEcho1 = delayBuffer[readIdx1] * 0.20f; 
            float rawEcho2 = delayBuffer[readIdx2] * 0.05f; 
            lastEcho1 = (lastEcho1 * 0.6f) + (rawEcho1 * 0.4f);
            lastEcho2 = (lastEcho2 * 0.6f) + (rawEcho2 * 0.4f);
            delayBuffer[delayWriteIdx] = keysMix + (lastEcho1 * 0.2f); 
            delayWriteIdx = (delayWriteIdx + 1) % DELAY_BUFFER_SIZE;
            float finalKeysOut = keysMix + lastEcho1 + lastEcho2;

            if (isRhythmPlaying.load()) {
                int rBank = currentRhythmBank.load();
                int rIdx = currentRhythmIndex.load();
                if (rIdx >= 0 && rIdx < 3 && !rhythmBanks[rBank][rIdx].empty()) {
                    const auto& rSample = rhythmBanks[rBank][rIdx];
                    int pos = static_cast<int>(rhythmPosition);
                    if (pos >= 0 && pos < rSample.size()) {
                        float frac = rhythmPosition - pos;
                        float s1 = rSample[pos];
                        float s2 = (pos + 1 < rSample.size()) ? rSample[pos + 1] : rSample[0];
                        rhythmMix = (s1 + frac * (s2 - s1)) * rhythmVolume.load();
                        rhythmPosition += rhythmTempo.load(); 
                    }
                    if (rhythmPosition >= rSample.size()) {
                        if (hasPendingRhythm.load()) {
                            currentRhythmIndex.store(pendingRhythmIndex.load());
                            hasPendingRhythm.store(false);
                            rhythmPosition = rhythmPosition - std::floor(rhythmPosition);
                        } else if (rIdx == 2) { 
                            currentRhythmIndex.store(previousRhythmIndex.load());
                            rhythmPosition = rhythmPosition - std::floor(rhythmPosition);
                        } else {
                            rhythmPosition -= rSample.size(); 
                        }
                    }
                }
            }

            float finalOut = finalKeysOut + rhythmMix;
            finalOut = std::max(-1.0f, std::min(1.0f, finalOut));
            
            floatData[i * 2] = finalOut;     
            floatData[i * 2 + 1] = finalOut; 
        }
        return DataCallbackResult::Continue;
    }

private:
    float processVoice(Voice &v) {
        int idx = v.sampleIndex.load();
        if (idx < 0 || idx >= 24) return 0.0f; 
        
        const auto& sample = soundBanks[v.bankIndex][idx];
        if (sample.size() < 4000) return 0.0f;

        float posF = v.position;
        int pos = static_cast<int>(posF);
        float frac = posF - pos;
        float s = 0.0f;

        int fadeLen = 1500; 
        int loopEnd = sample.size() - fadeLen - 2; 
        int loopStart = sample.size() * 0.15f; 

        if (pos >= loopEnd && pos < sample.size() - 1) {
            float t = (float)(posF - loopEnd) / fadeLen;
            if (t > 1.0f) t = 1.0f;
            float fadeOut = std::cos(t * 1.5707963f);
            float fadeIn = std::sin(t * 1.5707963f);
            float sOut = sample[pos] + frac * (sample[pos + 1] - sample[pos]);
            int inPos = loopStart + (pos - loopEnd);
            if (inPos >= sample.size() - 1) inPos = sample.size() - 2;
            float sIn = sample[inPos] + frac * (sample[inPos + 1] - sample[inPos]);
            s = (sOut * fadeOut) + (sIn * fadeIn);
            if (pos >= sample.size() - 2) {
                v.position = loopStart + fadeLen + frac; 
            }
        } else if (pos < sample.size() - 1) {
            s = sample[pos] + frac * (sample[pos + 1] - sample[pos]);
        }

        if (v.isAttacking) {
            v.envelope += 0.005f; 
            if (v.envelope >= 1.0f) {
                v.envelope = 1.0f;
                v.isAttacking = false;
            }
        } else if (v.releasing.load()) {
            v.envelope -= 0.0003f; 
            if (v.envelope <= 0.0f) {
                v.active.store(false);
                return 0.0f;
            }
        }

        v.position += (v.pitchRate * globalPitchBend.load());
        return s * v.volume * v.envelope;
    }

    float processMonoVoice() {
        if (!monoVoice.active.load()) return 0.0f;
        
        monoVoice.currentPitchRate += (monoVoice.targetPitchRate - monoVoice.currentPitchRate) * 0.015f;
        
        int idx = monoVoice.sampleIndex;
        if (idx < 0 || idx >= 24) return 0.0f;
        
        const auto& sample = soundBanks[monoVoice.bankIndex][idx];
        if (sample.size() < 4000) return 0.0f;

        int fadeLen = 1500;
        int loopEnd = sample.size() - fadeLen - 2;
        int loopStart = sample.size() * 0.15f;

        auto getSampleVal = [&](const std::vector<float>& smp, float &posF) -> float {
            int p = static_cast<int>(posF);
            if (p < 0) return 0.0f;
            if (p >= smp.size() - 1) {
                posF = loopStart + fadeLen;
                p = static_cast<int>(posF);
            }
            float frac = posF - p;
            
            if (p >= loopEnd && p < smp.size() - 1) {
                float t = (float)(posF - loopEnd) / fadeLen;
                if (t > 1.0f) t = 1.0f;
                float fadeOut = std::cos(t * 1.5707963f);
                float fadeIn = std::sin(t * 1.5707963f);
                float sOut = smp[p] + frac * (smp[p + 1] - smp[p]);
                int inPos = loopStart + (p - loopEnd);
                if (inPos >= smp.size() - 1) inPos = smp.size() - 2;
                float sIn = smp[inPos] + frac * (smp[inPos + 1] - smp[inPos]);
                
                if (p >= smp.size() - 2) {
                    posF = loopStart + fadeLen + frac;
                }
                return (sOut * fadeOut) + (sIn * fadeIn);
            } else {
                return smp[p] + frac * (smp[p + 1] - smp[p]);
            }
        };

        float mainVal = getSampleVal(sample, monoVoice.position);
        
        float sampleVal = mainVal;
        if (monoVoice.isCrossfading) {
            const auto& prevSample = soundBanks[monoVoice.prevBankIndex][monoVoice.prevSampleIndex];
            float prevVal = getSampleVal(prevSample, monoVoice.prevPosition);
            
            float alpha = monoVoice.crossfadeProgress; 
            sampleVal = (prevVal * (1.0f - alpha)) + (mainVal * alpha);
            
            monoVoice.prevPosition += (monoVoice.currentPitchRate * globalPitchBend.load());
            
            monoVoice.crossfadeProgress += 0.0025f; 
            if (monoVoice.crossfadeProgress >= 1.0f) {
                monoVoice.isCrossfading = false;
            }
        }
        
        monoVoice.position += (monoVoice.currentPitchRate * globalPitchBend.load());
        
        if (monoVoice.isAttacking) {
            monoVoice.envelope += 0.005f; 
            if (monoVoice.envelope >= 1.0f) {
                monoVoice.envelope = 1.0f;
                monoVoice.isAttacking = false;
            }
        } else if (monoVoice.releasing.load()) {
            monoVoice.envelope -= 0.0003f; 
            if (monoVoice.envelope <= 0.0f) {
                monoVoice.active.store(false);
                return 0.0f;
            }
        }
        
        return sampleVal * monoVoice.volume * monoVoice.envelope;
    }
};

static ZeekoAudioEngine engine;

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_initEngine(JNIEnv *env, jobject thiz) { 
    return engine.start() ? JNI_TRUE : JNI_FALSE; 
}

JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_destroyEngineNative(JNIEnv *env, jobject thiz) { 
    engine.destroy(); 
}

JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_loadSampleNative(JNIEnv *env, jobject thiz, jint bank, jint index, jfloatArray data) {
    jsize len = env->GetArrayLength(data); 
    jfloat *body = env->GetFloatArrayElements(data, nullptr);
    engine.loadSample(bank, index, body, len); 
    env->ReleaseFloatArrayElements(data, body, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_loadRhythmSampleNative(JNIEnv *env, jobject thiz, jint bank, jint index, jfloatArray data) {
    jsize len = env->GetArrayLength(data); 
    jfloat *body = env->GetFloatArrayElements(data, nullptr);
    engine.loadRhythmSample(bank, index, body, len); 
    env->ReleaseFloatArrayElements(data, body, JNI_ABORT);
}

JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_switchSoundBank(JNIEnv *env, jobject thiz, jint bank) { engine.switchSoundBank(bank); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_switchRhythmBank(JNIEnv *env, jobject thiz, jint bank) { engine.switchRhythmBank(bank); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_playRhythm(JNIEnv *env, jobject thiz, jint index) { engine.playRhythm(index); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_stopRhythm(JNIEnv *env, jobject thiz) { engine.stopRhythm(); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_setRhythmTempo(JNIEnv *env, jobject thiz, jfloat tempo) { engine.setRhythmTempo(tempo); }
JNIEXPORT jint JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_getActiveRhythmIndex(JNIEnv *env, jobject thiz) { return engine.getActiveRhythmIndex(); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_setKeysVolume(JNIEnv *env, jobject thiz, jfloat vol) { engine.setKeysVolume(vol); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_setRhythmVolume(JNIEnv *env, jobject thiz, jfloat vol) { engine.setRhythmVolume(vol); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_setPitchBend(JNIEnv *env, jobject thiz, jfloat mult) { engine.setPitchBend(mult); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_playPoly(JNIEnv *env, jobject thiz, jint index, jfloat volume, jfloat pitch) { engine.playPoly(index, volume, pitch); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_releasePoly(JNIEnv *env, jobject thiz, jint index) { engine.releasePoly(index); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_playMono(JNIEnv *env, jobject thiz, jint index, jfloat volume, jfloat pitch) { engine.playMono(index, volume, pitch); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_releaseMono(JNIEnv *env, jobject thiz) { engine.releaseMono(); }
JNIEXPORT void JNICALL Java_com_zeekoltd_setat_pro_NativeAudioEngine_stopAll(JNIEnv *env, jobject thiz) { engine.stopAll(); }

} // إغلاق كتلة extern "C"
