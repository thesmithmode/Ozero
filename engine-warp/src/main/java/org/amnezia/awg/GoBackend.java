package org.amnezia.awg;

public abstract class GoBackend {
    public static native String awgGetConfig(int handle);

    public static native int awgGetSocketV4(int handle);

    public static native int awgGetSocketV6(int handle);

    public static native void awgTurnOff(int handle);

    public static native int awgTurnOn(String name, int tunFd, String iniConfig, String uapiPath);

    public static native int awgUpdateTunnelPeers(int handle, String peers);

    public static native String awgVersion();
}
