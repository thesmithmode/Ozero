import pathlib
import sys


def replace_once(path, old, new):
    source = path.read_text()
    if source.count(old) != 1:
        raise RuntimeError(f"Unexpected pinned HEV source: {path.name}")
    path.write_text(source.replace(old, new, 1))


root = pathlib.Path(sys.argv[1])
jni = root / "src/hev-jni.c"
main = root / "src/hev-main.c"
makefile = root / "Android.mk"
replace_once(jni, "#include <pthread.h>", "#include <pthread.h>\n#include <android/log.h>\n#include <fcntl.h>\n#include <errno.h>")
replace_once(
    jni,
    "    hev_socks5_tunnel_main (tdata->path, tdata->fd);",
    """    int flags = fcntl (tdata->fd, F_GETFL);
    int fd_error = flags < 0 ? errno : 0;
    __android_log_print (ANDROID_LOG_INFO, "HevSoak", "worker fd=%d flags=%d errno=%d",
                         tdata->fd, flags, fd_error);
    int result = hev_socks5_tunnel_main (tdata->path, tdata->fd);
    __android_log_print (ANDROID_LOG_INFO, "HevSoak", "worker exit=%d", result);""",
)
replace_once(
    jni,
    "    pthread_create (&work_thread, NULL, thread_handler, tdata);",
    """    int result = pthread_create (&work_thread, NULL, thread_handler, tdata);
    __android_log_print (ANDROID_LOG_INFO, "HevSoak", "pthread_create=%d", result);""",
)
replace_once(main, "#include <lwip/init.h>", "#include <lwip/init.h>\n#include <android/log.h>")
replace_once(
    main,
    "    hev_socks5_tunnel_run ();",
    """    __android_log_print (ANDROID_LOG_INFO, "HevSoak", "native init complete");
    hev_socks5_tunnel_run ();
    __android_log_print (ANDROID_LOG_INFO, "HevSoak", "native event loop exited");""",
)
replace_once(
    makefile,
    "include $(BUILD_SHARED_LIBRARY)",
    "LOCAL_LDLIBS += -llog\ninclude $(BUILD_SHARED_LIBRARY)",
)
