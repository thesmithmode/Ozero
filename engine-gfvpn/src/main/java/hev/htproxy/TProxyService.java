package hev.htproxy;

/** JNI registration contract of the pinned open-source HEV 2.20.0 native library. */
public final class TProxyService {
    private TProxyService() { }

    static { System.loadLibrary("hev-gfvpn-socks5-tunnel"); }

    public static native boolean TProxyStartService(String configPath, int fd);
    public static native boolean TProxyStopService();
    public static native boolean TProxyIsRunning();
    public static native long[] TProxyGetStats();
}
