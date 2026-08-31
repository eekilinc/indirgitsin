/* SPDX-License-Identifier: GPL-3.0-only */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include "lame.h"

static void fail(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env, type, message);
}

JNIEXPORT jlong JNICALL Java_com_indirgitsin_app_data_downloader_LameEncoder_openNative(
        JNIEnv *env, jobject self, jint rate, jint channels, jint bitrate) {
    (void)self;
    if (rate < 8000 || rate > 192000 || (channels != 1 && channels != 2) ||
        (bitrate != 128 && bitrate != 192 && bitrate != 320)) {
        fail(env, "Unsupported MP3 encoder configuration"); return 0;
    }
    lame_t encoder = lame_init();
    if (!encoder) { fail(env, "MP3 encoder allocation failed"); return 0; }
    if (lame_set_in_samplerate(encoder, rate) < 0 ||
        lame_set_out_samplerate(encoder, rate == 48000 ? 48000 : 44100) < 0 ||
        lame_set_num_channels(encoder, channels) < 0 ||
        lame_set_mode(encoder, channels == 1 ? MONO : JOINT_STEREO) < 0 ||
        lame_set_brate(encoder, bitrate) < 0 || lame_set_quality(encoder, 2) < 0 ||
        lame_set_bWriteVbrTag(encoder, 0) < 0 || lame_init_params(encoder) < 0) {
        lame_close(encoder); fail(env, "MP3 encoder initialization failed"); return 0;
    }
    return (jlong)(intptr_t)encoder;
}

JNIEXPORT jbyteArray JNICALL Java_com_indirgitsin_app_data_downloader_LameEncoder_encodeNative(
        JNIEnv *env, jobject self, jlong handle, jshortArray pcm, jint count, jboolean flush) {
    (void)self;
    lame_t encoder = (lame_t)(intptr_t)handle;
    if (!encoder || !pcm || count < 0 || count > 32768 || count > (*env)->GetArrayLength(env, pcm)) {
        fail(env, "Invalid MP3 PCM buffer"); return NULL;
    }
    int channels = lame_get_num_channels(encoder);
    if (count % channels != 0) { fail(env, "Incomplete PCM frame"); return NULL; }
    unsigned char output[49152];
    int size;
    if (flush) size = lame_encode_flush(encoder, output, sizeof(output));
    else {
        jshort *samples = (*env)->GetShortArrayElements(env, pcm, NULL);
        if (!samples) return NULL;
        size = channels == 2 ? lame_encode_buffer_interleaved(encoder, samples, count / 2, output, sizeof(output))
                             : lame_encode_buffer(encoder, samples, samples, count, output, sizeof(output));
        (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
    }
    if (size < 0) { fail(env, "MP3 encoding failed"); return NULL; }
    jbyteArray result = (*env)->NewByteArray(env, size);
    if (result && size) (*env)->SetByteArrayRegion(env, result, 0, size, (const jbyte *)output);
    return result;
}

JNIEXPORT void JNICALL Java_com_indirgitsin_app_data_downloader_LameEncoder_closeNative(
        JNIEnv *env, jobject self, jlong handle) {
    (void)env; (void)self;
    if (handle) lame_close((lame_t)(intptr_t)handle);
}
