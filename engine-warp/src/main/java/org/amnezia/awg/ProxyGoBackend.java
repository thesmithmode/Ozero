package org.amnezia.awg;

import org.amnezia.awg.backend.SocketProtector;

public abstract class ProxyGoBackend {
    public static native String awgGetProxyConfig(int handle);

    public static native void awgResetJNIGlobals();

    public static native void awgSetSocketProtector(SocketProtector socketProtector);

    public static native int awgStartProxy(String name, String config, String uapiPath, int port);

    public static native void awgStopProxy();

    public static native int awgUpdateProxyTunnelPeers(int handle, String peers);
}
