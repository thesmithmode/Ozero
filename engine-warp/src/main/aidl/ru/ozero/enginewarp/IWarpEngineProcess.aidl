package ru.ozero.enginewarp;

interface IWarpEngineProcess {
    int turnOn(in ParcelFileDescriptor tunFd, String name, String iniConfig, String uapiPath);
    void turnOff(int handle);
    ParcelFileDescriptor socketV4Fd(int handle);
    ParcelFileDescriptor socketV6Fd(int handle);
    String version();
}
