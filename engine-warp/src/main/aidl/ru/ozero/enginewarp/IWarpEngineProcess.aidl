package ru.ozero.enginewarp;

import ru.ozero.enginewarp.WarpTurnOnResult;

interface IWarpEngineProcess {
    int turnOn(in ParcelFileDescriptor tunFd, String name, String iniConfig, String uapiPath);
    void turnOff(int handle);
    ParcelFileDescriptor socketV4Fd(int handle);
    ParcelFileDescriptor socketV6Fd(int handle);
    String version();
    WarpTurnOnResult turnOnAndGetSockets(in ParcelFileDescriptor tunFd, String name, String iniConfig, String uapiPath);
    int startProxy(String name, String iniConfig, String uapiPath, int port);
    void stopProxy();
    void resetProxyGlobals();
}
