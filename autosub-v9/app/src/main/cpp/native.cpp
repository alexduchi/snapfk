#include <jni.h>
#include <string>
#include <sstream>
#include <vector>
#include <algorithm>
#include <thread>
#include "whisper.h"

static std::string esc(const char *s) {
    std::string in = s ? s : "";
    std::string out;
    out.reserve(in.size()+16);
    for (char c : in) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out += c;
        }
    }
    return out;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_neo_autosub_localfull_MainActivity_nativeTranscribe(JNIEnv *env, jclass, jstring modelPath, jfloatArray samples) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!ctx) return env->NewStringUTF("{\"error\":\"Impossible de charger le modèle Whisper\"}");
    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm((size_t)n);
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());
    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_realtime = false; p.print_progress = false; p.print_timestamps = false;
    p.translate = false; p.language = "auto"; p.detect_language = true;
    p.n_threads = std::max(2u, std::thread::hardware_concurrency());
    if (whisper_full(ctx, p, pcm.data(), (int)pcm.size()) != 0) {
        whisper_free(ctx); return env->NewStringUTF("{\"error\":\"Échec de la transcription locale\"}");
    }
    int langId = whisper_full_lang_id(ctx);
    const char *lang = whisper_lang_str(langId);
    int count = whisper_full_n_segments(ctx);
    std::ostringstream os; os << "{\"language\":\"" << esc(lang) << "\",\"segments\":[";
    for (int i=0;i<count;i++) {
        if (i) os << ',';
        int64_t t0 = whisper_full_get_segment_t0(ctx,i), t1 = whisper_full_get_segment_t1(ctx,i);
        const char *txt = whisper_full_get_segment_text(ctx,i);
        os << "{\"start\":" << (t0/100.0) << ",\"end\":" << (t1/100.0) << ",\"text\":\"" << esc(txt) << "\"}";
    }
    os << "]}"; whisper_free(ctx); return env->NewStringUTF(os.str().c_str());
}
