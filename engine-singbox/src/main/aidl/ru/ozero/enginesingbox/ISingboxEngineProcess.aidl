package ru.ozero.enginesingbox;

import ru.ozero.enginesingbox.ISingboxProtector;
import ru.ozero.enginesingbox.ISingboxStatusCallback;
import ru.ozero.enginesingbox.SingboxStats;

interface ISingboxEngineProcess {
    void startWithConfig(in ParcelFileDescriptor tunFd, String singboxJsonConfig, ISingboxProtector protector);
    void startWithConfigFile(in ParcelFileDescriptor tunFd, String configFilePath, ISingboxProtector protector);
    void startProxyMode(String singboxJsonConfig, ISingboxProtector protector);
    void stop();
    boolean stopAndWait(long timeoutMs);
    boolean runtimeRunning();
    SingboxStats getStats();
    void registerStatusCallback(ISingboxStatusCallback cb);
    long urlTest(long profileId);
    oneway void setPerAppPackages(in String[] packages, boolean isAllowList);
}
