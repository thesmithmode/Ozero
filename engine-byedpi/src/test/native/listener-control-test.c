#define _GNU_SOURCE
#include "listener-control.h"
#include <assert.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <pthread.h>
#include <sys/socket.h>
#include <unistd.h>

static int listener(void) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    assert(fd >= 0);
    struct sockaddr_in addr = {.sin_family = AF_INET, .sin_addr.s_addr = htonl(INADDR_LOOPBACK)};
    assert(bind(fd, (struct sockaddr *)&addr, sizeof(addr)) == 0);
    assert(listen(fd, 1) == 0);
    return fd;
}

static void *stop_many(void *unused) {
    (void)unused;
    for (int i = 0; i < 1000; i++) ozero_listener_stop();
    return NULL;
}

int main(void) {
    for (int i = 0; i < 100; i++) {
        assert(ozero_listener_begin());
        assert(!ozero_listener_begin());
        int original = listener();
        assert(ozero_listener_publish(original) == 0);
        assert(close(original) == 0);
        int unrelated = open("/dev/null", O_RDONLY);
        assert(unrelated >= 0);
        if (unrelated != original) {
            assert(dup2(unrelated, original) == original);
            close(unrelated);
            unrelated = original;
        }
        pthread_t stopper;
        assert(pthread_create(&stopper, NULL, stop_many, NULL) == 0);
        ozero_listener_finish();
        assert(pthread_join(stopper, NULL) == 0);
        assert(fcntl(unrelated, F_GETFD) != -1);
        assert(close(unrelated) == 0);
        assert(ozero_listener_stop() == -1);

        assert(ozero_listener_begin());
        assert(ozero_listener_stop() == 0);
        original = listener();
        assert(ozero_listener_publish(original) == -1);
        assert(close(original) == 0);
        ozero_listener_finish();
    }
    return 0;
}
