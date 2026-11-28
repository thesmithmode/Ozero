#ifndef OZERO_LISTENER_CONTROL_H
#define OZERO_LISTENER_CONTROL_H

int ozero_listener_begin(void);
int ozero_listener_publish(int fd);
int ozero_listener_stop(void);
void ozero_listener_finish(void);

#endif
