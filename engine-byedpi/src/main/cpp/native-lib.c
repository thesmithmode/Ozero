#include <string.h>
#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdlib.h>
#include <unistd.h>
#include <android/log.h>

#include "byedpi/error.h"
#include "main.h"

#define BYEDPI_LOG_TAG "ByeDpiNative"
#define BYEDPI_LOGW(...) __android_log_print(ANDROID_LOG_WARN, BYEDPI_LOG_TAG, __VA_ARGS__)

#include "listener-control.h"

struct params default_params = {
        .await_int = 10,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 16384,
        .baddr = {
            .in6 = { .sin6_family = AF_INET6 }
        },
        .laddr = {
            .in = { .sin_family = AF_INET }
        },
        .debug = 0
};

void reset_params(void) {
    clear_params(NULL, NULL);
    params = default_params;
}

/* Освобождает argv[0..count) и сам массив. */
static void free_argv(char **argv, int count) {
    for (int i = 0; i < count; i++) {
        if (argv[i]) free(argv[i]);
    }
    free(argv);
}

/* Полный teardown при ошибке: освобождает argv и отпускает CAS guard. */
static jint jni_start_fail(char **argv, int filled, jint code) {
    if (argv) free_argv(argv, filled);
    ozero_listener_finish();
    return code;
}

#define JNI_GUARD_BUSY (-2)

JNIEXPORT jint JNICALL
Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStartProxy(JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    if (!ozero_listener_begin()) return JNI_GUARD_BUSY;

    int user_argc = (*env)->GetArrayLength(env, args);
    int argc = user_argc + 1;
    char **argv = calloc((size_t)argc + 1u, sizeof(char *));
    if (!argv) {
        ozero_listener_finish();
        return -1;
    }

    argv[0] = strdup("byedpi");
    if (!argv[0]) {
        return jni_start_fail(argv, 0, -1);
    }

    for (int i = 0; i < user_argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            return jni_start_fail(argv, i + 1, -1);
        }
        if (!arg) { argv[i + 1] = NULL; continue; }

        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            (*env)->DeleteLocalRef(env, arg);
            return jni_start_fail(argv, i + 1, -1);
        }
        if (!arg_str) {
            (*env)->DeleteLocalRef(env, arg);
            return jni_start_fail(argv, i + 1, -1);
        }

        char *copy = strdup(arg_str);
        (*env)->ReleaseStringUTFChars(env, arg, arg_str);
        (*env)->DeleteLocalRef(env, arg);
        if (!copy) {
            return jni_start_fail(argv, i + 1, -1);
        }
        argv[i + 1] = copy;
    }

    reset_params();
    optind = 1;

    BYEDPI_LOGW("jniStartProxy → main() entry argc=%d", argc);
    int result = main(argc, argv);
    BYEDPI_LOGW("jniStartProxy ← main() exit code=%d", result);

    free_argv(argv, argc);
    ozero_listener_finish();

    return result;
}

JNIEXPORT jint JNICALL
Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStopProxy(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    return ozero_listener_stop();
}

JNIEXPORT jint JNICALL
Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniForceClose(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    return ozero_listener_stop();
}
