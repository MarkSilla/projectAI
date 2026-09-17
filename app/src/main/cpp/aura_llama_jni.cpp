#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

std::mutex g_llama_mutex;
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;

std::string toStdString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const char* utf = env->GetStringUTFChars(value, nullptr);
    if (utf == nullptr) {
        return {};
    }

    std::string result(utf);
    env->ReleaseStringUTFChars(value, utf);
    return result;
}

std::string renderGeneratedPayload(const std::vector<llama_token>& tokens, const llama_vocab* vocab) {
    std::string output;
    output.reserve(tokens.size() * 4);

    for (llama_token token : tokens) {
        char buffer[256];
        const int pieceSize = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, true);
        if (pieceSize < 0) {
            continue;
        }
        output.append(buffer, static_cast<size_t>(pieceSize));
    }

    return output;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_marksilla_auraagent_LocalLLMNativeBridge_loadModel(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring modelPath,
    jint contextSize,
    jint threads,
    jboolean useGpu) {
    const std::string model_file = toStdString(env, modelPath);
    if (model_file.empty()) {
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_llama_mutex);

    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = useGpu ? 99 : 0;

    g_model = llama_model_load_from_file(model_file.c_str(), model_params);
    if (g_model == nullptr) {
        return JNI_FALSE;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = std::max(512, static_cast<int>(contextSize));
    context_params.n_batch = std::max(64, std::min(512, static_cast<int>(contextSize / 2)));
    context_params.n_threads = std::max(1, static_cast<int>(threads));
    context_params.n_threads_batch = std::max(1, static_cast<int>(threads));

    g_context = llama_init_from_model(g_model, context_params);
    if (g_context == nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_marksilla_auraagent_LocalLLMNativeBridge_unloadModel(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_marksilla_auraagent_LocalLLMNativeBridge_generate(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring prompt,
    jint maxTokens) {
    const std::string prompt_text = toStdString(env, prompt);
    if (prompt_text.empty() || g_context == nullptr || g_model == nullptr) {
        return nullptr;
    }

    std::lock_guard<std::mutex> lock(g_llama_mutex);

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    const int n_prompt = -llama_tokenize(vocab, prompt_text.c_str(), static_cast<int>(prompt_text.size()), nullptr, 0, true, true);
    std::vector<llama_token> tokens(static_cast<size_t>(n_prompt));
    const int tokenized = llama_tokenize(vocab, prompt_text.c_str(), static_cast<int>(prompt_text.size()), tokens.data(), static_cast<int>(tokens.size()), true, true);
    if (tokenized < 0) {
        return nullptr;
    }

    std::vector<llama_token> generation;
    generation.reserve(static_cast<size_t>(std::max(1, maxTokens)));

    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int>(tokens.size()));
    if (llama_decode(g_context, batch) != 0) {
        return nullptr;
    }

    auto sampler_params = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    for (int i = 0; i < std::max(1, static_cast<int>(maxTokens)); ++i) {
        llama_token next_token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, next_token)) {
            break;
        }
        generation.push_back(next_token);
        batch = llama_batch_get_one(&next_token, 1);
        if (llama_decode(g_context, batch) != 0) {
            break;
        }
    }

    llama_sampler_free(sampler);
    const std::string text = renderGeneratedPayload(generation, vocab);
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_marksilla_auraagent_LocalLLMNativeBridge_streamGenerate(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring prompt,
    jint maxTokens) {
    return Java_com_marksilla_auraagent_LocalLLMNativeBridge_generate(env, nullptr, prompt, maxTokens);
}

extern "C" JNIEXPORT void JNICALL
Java_com_marksilla_auraagent_LocalLLMNativeBridge_cancelGeneration(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    // The app uses a cancellation flag in Kotlin; the native path simply no-ops here.
}
