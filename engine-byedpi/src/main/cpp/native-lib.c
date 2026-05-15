#include <string.h>
#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdlib.h>
#include <unistd.h>
#include <stdatomic.h>

#include "byedpi/error.h"
#include "main.h"

/* Known limitation: server_fd объявлен в upstream byedpi как обычный int (не _Atomic).
 * Race window между jniStopProxy и jniForceClose: оба читают server_fd без синхронизации;
 * между read и close()/shutdown() kernel может переиспользовать слот fd → close/shutdown
 * чужого socket. Локальный mutex = полу-фикс (upstream main() в submodule тоже пишет fd
 * без наших локов). Полный fix требует форка byedpi submodule для смены типа на
 * _Atomic int. За прод race не воспроизведён, акцептовано как known limitation. */
extern int server_fd;
/* Атомарный CAS-guard от двойного запуска: раньше int-флаг читался/писался
 * без синхронизации, две параллельные jniStartProxy могли пройти guard
 * одновременно и обе вызвать main() с гонкой на server_fd. */
static atomic_int g_proxy_running = 0;

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
    atomic_store(&g_proxy_running, 0);
    return code;
}

#define JNI_GUARD_BUSY (-2)

JNIEXPORT jint JNICALL
Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStartProxy(JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    /* CAS 0→1: один caller проходит. Guard busy = JNI_GUARD_BUSY (-2), отличимо
     * от -1 (OOM/JNI/upstream). Kotlin layer должен делать retry через cooperative
     * delay() — НЕ блокировать здесь, иначе сериализуется single-thread dispatcher. */
    int expected = 0;
    if (!atomic_compare_exchange_strong(&g_proxy_running, &expected, 1)) {
        return JNI_GUARD_BUSY;
    }

    int user_argc = (*env)->GetArrayLength(env, args);
    int argc = user_argc + 1;
    char **argv = calloc((size_t)argc + 1u, sizeof(char *));
    if (!argv) {
        atomic_store(&g_proxy_running, 0);
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

    int result = main(argc, argv);

    free_argv(argv, argc);
    atomic_store(&g_proxy_running, 0);

    return result;
}

JNIEXPORT jint JNICALL
Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniStopProxy(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    if (!atomic_load(&g_proxy_running)) return -1;
    if (server_fd < 0) return -1;
    return shutdown(server_fd, SHUT_RDWR);
}

JNIEXPORT jint JNICALL
Java_ru_ozero_enginebyedpi_ByeDpiProxy_jniForceClose(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    if (server_fd < 0) {
        return -1;
    }
    int rc = close(server_fd);
    server_fd = -1;
    /* Guard g_proxy_running НЕ сбрасываем здесь — отпустит jniStartProxy после
     * возврата main(). Premature release провоцировал race: вторая jniStartProxy
     * проходила CAS пока старая main() ещё в cleanup → concurrent main() с
     * shared upstream globals (server_fd, params) = memory corruption.
     * Trade-off: если upstream main() зависнет после close(fd), guard залипнет
     * до restart процесса — считаем upstream bug. */
    return rc;
}
