#include "listener-control.h"
#include <fcntl.h>
#include <pthread.h>
#include <sys/socket.h>
#include <unistd.h>

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static int running = 0;
static int stop_requested = 0;
static int control_fd = -1;

int ozero_listener_begin(void) {
    pthread_mutex_lock(&lock);
    int accepted = !running;
    if (accepted) {
        running = 1;
        stop_requested = 0;
    }
    pthread_mutex_unlock(&lock);
    return accepted;
}

int ozero_listener_publish(int fd) {
    pthread_mutex_lock(&lock);
    int result = -1;
    if (running && !stop_requested && control_fd < 0) {
        // Own a distinct descriptor; upstream alone closes the original.
        control_fd = fcntl(fd, F_DUPFD_CLOEXEC, 0);
        if (control_fd >= 0) result = 0;
    }
    pthread_mutex_unlock(&lock);
    return result;
}

int ozero_listener_stop(void) {
    pthread_mutex_lock(&lock);
    int result = -1;
    if (running) {
        stop_requested = 1;
        result = control_fd < 0 ? 0 : shutdown(control_fd, SHUT_RDWR);
    }
    pthread_mutex_unlock(&lock);
    return result;
}

void ozero_listener_finish(void) {
    pthread_mutex_lock(&lock);
    if (control_fd >= 0) close(control_fd);
    control_fd = -1;
    stop_requested = 0;
    running = 0;
    pthread_mutex_unlock(&lock);
}
