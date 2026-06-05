#include <jni.h>
#include "libvpk.h"
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "VPK_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_zzh_bin_vpktool_VpkEngine_load(JNIEnv *env, jobject instance, jstring path_) {
    const char *path = (*env)->GetStringUTFChars(env, path_, 0);
    LOGI("Attempting to load VPK from: %s", path);
    VPKHandle handle = vpk_load(path);
    if (handle == VPK_NULL_HANDLE) {
        LOGE("Failed to load VPK from: %s", path);
    } else {
        LOGI("Successfully loaded VPK.");
    }
    (*env)->ReleaseStringUTFChars(env, path_, path);
    return (jlong)handle;
}

JNIEXPORT void JNICALL
Java_zzh_bin_vpktool_VpkEngine_close(JNIEnv *env, jobject instance, jlong handle) {
    vpk_close((VPKHandle)handle);
}

JNIEXPORT jboolean JNICALL
Java_zzh_bin_vpktool_VpkEngine_isValid(JNIEnv *env, jobject instance, jlong handle) {
    return vpk_valid_handle((VPKHandle)handle) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_zzh_bin_vpktool_VpkEngine_getFirstFile(JNIEnv *env, jobject instance, jlong handle) {
    VPKFile file = vpk_ffirst((VPKHandle)handle);
    if (!file) return NULL;
    return (*env)->NewStringUTF(env, vpk_fpath(file));
}

JNIEXPORT jstring JNICALL
Java_zzh_bin_vpktool_VpkEngine_getNextFile(JNIEnv *env, jobject instance, jlong handle) {
    VPKFile file = vpk_fnext((VPKHandle)handle);
    if (!file) return NULL;
    return (*env)->NewStringUTF(env, vpk_fpath(file));
}

JNIEXPORT jboolean JNICALL
Java_zzh_bin_vpktool_VpkEngine_extractFile(JNIEnv *env, jobject instance, jlong handle, jstring vpkFilePath_, jstring destPath_) {
    const char *vpkFilePath = (*env)->GetStringUTFChars(env, vpkFilePath_, 0);
    const char *destPath = (*env)->GetStringUTFChars(env, destPath_, 0);
    
    VPKFile file = vpk_fopen((VPKHandle)handle, vpkFilePath);
    if (!file) {
        LOGE("Failed to open file in VPK: %s", vpkFilePath);
        (*env)->ReleaseStringUTFChars(env, vpkFilePath_, vpkFilePath);
        (*env)->ReleaseStringUTFChars(env, destPath_, destPath);
        return JNI_FALSE;
    }
    
    size_t len = vpk_flen(file);
    char *buffer = (char*)malloc(len);
    if (!buffer) {
        LOGE("Failed to allocate memory for extraction");
        vpk_fclose(file);
        (*env)->ReleaseStringUTFChars(env, vpkFilePath_, vpkFilePath);
        (*env)->ReleaseStringUTFChars(env, destPath_, destPath);
        return JNI_FALSE;
    }
    
    vpk_fread(buffer, 1, len, file);
    
    FILE *destFile = fopen(destPath, "wb");
    if (!destFile) {
        LOGE("Failed to create destination file: %s", destPath);
        free(buffer);
        vpk_fclose(file);
        (*env)->ReleaseStringUTFChars(env, vpkFilePath_, vpkFilePath);
        (*env)->ReleaseStringUTFChars(env, destPath_, destPath);
        return JNI_FALSE;
    }
    
    fwrite(buffer, 1, len, destFile);
    fclose(destFile);
    free(buffer);
    vpk_fclose(file);
    
    LOGI("Extracted %s to %s", vpkFilePath, destPath);
    
    (*env)->ReleaseStringUTFChars(env, vpkFilePath_, vpkFilePath);
    (*env)->ReleaseStringUTFChars(env, destPath_, destPath);
    return JNI_TRUE;
}

#ifdef __cplusplus
}
#endif
