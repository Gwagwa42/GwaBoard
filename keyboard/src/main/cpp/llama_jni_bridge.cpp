#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "GwaBoard_LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef LLAMA_CPP_NOT_VENDORED

#include "llama.h"
#include "common.h"

// ---------------------------------------------------------------------------
// JNI bridge for llama.cpp — called from LlamaCppBridge.kt
//
// Each function maps to a native method declared in
// dev.gwaboard.keyboard.llm.LlamaCppBridge
// ---------------------------------------------------------------------------

extern "C" {

/**
 * Load a GGUF model from the given file path.
 *
 * @param modelPath Absolute path to the .gguf model file on device storage.
 * @param nThreads  Number of CPU threads for inference (typically 4 for Tensor G3).
 * @return A non-zero context handle on success, or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeLoadModel(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath,
        jint nThreads) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("nativeLoadModel: failed to get model path string");
        return 0;
    }

    LOGI("nativeLoadModel: loading %s with %d threads", path, nThreads);

    // Initialize llama backend (safe to call multiple times)
    llama_backend_init();

    // Configure model parameters
    llama_model_params model_params = llama_model_default_params();

    llama_model *model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!model) {
        LOGE("nativeLoadModel: failed to load model");
        return 0;
    }

    // Create inference context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;       // Context window (sufficient for keyboard input)
    ctx_params.n_threads = nThreads;
    ctx_params.n_batch = 64;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("nativeLoadModel: failed to create context");
        llama_model_free(model);
        return 0;
    }

    // Pack model + context into a single handle via a heap-allocated struct
    struct LlamaSession {
        llama_model *model;
        llama_context *ctx;
        volatile bool cancel_flag;
    };

    auto *session = new LlamaSession{model, ctx, false};
    LOGI("nativeLoadModel: success, handle=%p", session);

    return reinterpret_cast<jlong>(session);
}

/**
 * Unload a previously loaded model and free all associated resources.
 *
 * @param contextHandle Handle returned by nativeLoadModel.
 */
JNIEXPORT void JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeUnloadModel(
        JNIEnv * /* env */,
        jobject /* this */,
        jlong contextHandle) {

    if (contextHandle == 0) return;

    struct LlamaSession {
        llama_model *model;
        llama_context *ctx;
        volatile bool cancel_flag;
    };

    auto *session = reinterpret_cast<LlamaSession *>(contextHandle);

    LOGI("nativeUnloadModel: freeing handle=%p", session);

    if (session->ctx) {
        llama_free(session->ctx);
    }
    if (session->model) {
        llama_model_free(session->model);
    }

    delete session;
}

/**
 * Run inference on the loaded model with the given prompt.
 *
 * @param contextHandle Handle returned by nativeLoadModel.
 * @param prompt        Input text to complete.
 * @param maxTokens     Maximum number of tokens to generate.
 * @return Generated text, or empty string on error/cancellation.
 */
JNIEXPORT jstring JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeInfer(
        JNIEnv *env,
        jobject /* this */,
        jlong contextHandle,
        jstring prompt,
        jint maxTokens) {

    if (contextHandle == 0) {
        return env->NewStringUTF("");
    }

    struct LlamaSession {
        llama_model *model;
        llama_context *ctx;
        volatile bool cancel_flag;
    };

    auto *session = reinterpret_cast<LlamaSession *>(contextHandle);
    session->cancel_flag = false;

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    if (!promptStr) {
        return env->NewStringUTF("");
    }

    LOGI("nativeInfer: prompt length=%zu, maxTokens=%d", strlen(promptStr), maxTokens);

    // Tokenize the prompt
    const llama_model *model = llama_get_model(session->ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    const int n_prompt_max = strlen(promptStr) + 32;
    std::vector<llama_token> tokens(n_prompt_max);
    const int n_tokens = llama_tokenize(
        vocab,
        promptStr,
        strlen(promptStr),
        tokens.data(),
        n_prompt_max,
        true,   // add_special (BOS)
        false   // parse_special
    );

    env->ReleaseStringUTFChars(prompt, promptStr);

    if (n_tokens < 0) {
        LOGE("nativeInfer: tokenization failed");
        return env->NewStringUTF("");
    }
    tokens.resize(n_tokens);

    // Clear previous KV cache
    llama_kv_cache_clear(session->ctx);

    // Process prompt tokens in batch
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, (i == n_tokens - 1));
    }

    if (llama_decode(session->ctx, batch) != 0) {
        LOGE("nativeInfer: prompt decoding failed");
        llama_batch_free(batch);
        return env->NewStringUTF("");
    }

    // Generate tokens
    std::string result;
    int n_generated = 0;

    while (n_generated < maxTokens && !session->cancel_flag) {
        // Sample next token using greedy decoding
        float *logits = llama_get_logits_ith(session->ctx, -1);
        llama_token new_token = 0;
        float max_logit = logits[0];
        int n_vocab_size = llama_vocab_n_tokens(vocab);

        for (int i = 1; i < n_vocab_size; i++) {
            if (logits[i] > max_logit) {
                max_logit = logits[i];
                new_token = i;
            }
        }

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        // Convert token to text
        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, false);
        if (n > 0) {
            result.append(buf, n);
        }

        // Prepare next batch with single token
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token, n_tokens + n_generated, {0}, true);

        if (llama_decode(session->ctx, batch) != 0) {
            LOGE("nativeInfer: decode failed at token %d", n_generated);
            break;
        }

        n_generated++;
    }

    llama_batch_free(batch);

    if (session->cancel_flag) {
        LOGI("nativeInfer: cancelled after %d tokens", n_generated);
    } else {
        LOGI("nativeInfer: completed, %d tokens generated", n_generated);
    }

    return env->NewStringUTF(result.c_str());
}

/**
 * Signal cancellation of an in-progress inference.
 * The nativeInfer call will return partial results at the next iteration.
 *
 * @param contextHandle Handle returned by nativeLoadModel.
 */
JNIEXPORT void JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeCancelInference(
        JNIEnv * /* env */,
        jobject /* this */,
        jlong contextHandle) {

    if (contextHandle == 0) return;

    struct LlamaSession {
        llama_model *model;
        llama_context *ctx;
        volatile bool cancel_flag;
    };

    auto *session = reinterpret_cast<LlamaSession *>(contextHandle);
    session->cancel_flag = true;
    LOGI("nativeCancelInference: flag set");
}

} // extern "C"

#else // LLAMA_CPP_NOT_VENDORED

// ---------------------------------------------------------------------------
// Stub implementations when llama.cpp source is not yet vendored.
// All functions return error/empty values and log a warning.
// ---------------------------------------------------------------------------

extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeLoadModel(
        JNIEnv *env, jobject, jstring, jint) {
    LOGE("nativeLoadModel: llama.cpp not vendored — stub returning 0");
    return 0;
}

JNIEXPORT void JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeUnloadModel(
        JNIEnv *, jobject, jlong) {
    LOGE("nativeUnloadModel: llama.cpp not vendored — stub");
}

JNIEXPORT jstring JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeInfer(
        JNIEnv *env, jobject, jlong, jstring, jint) {
    LOGE("nativeInfer: llama.cpp not vendored — stub returning empty");
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_dev_gwaboard_keyboard_llm_LlamaCppBridge_nativeCancelInference(
        JNIEnv *, jobject, jlong) {
    LOGE("nativeCancelInference: llama.cpp not vendored — stub");
}

} // extern "C"

#endif // LLAMA_CPP_NOT_VENDORED
