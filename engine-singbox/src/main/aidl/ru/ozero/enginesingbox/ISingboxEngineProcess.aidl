package ru.ozero.enginesingbox;

import ru.ozero.enginesingbox.ISingboxProtector;
import ru.ozero.enginesingbox.SingboxStats;

interface ISingboxEngineProcess {
    void startWithConfig(long ownerId, in ParcelFileDescriptor tunFd, String singboxJsonConfig, ISingboxProtector protector);
    void startWithConfigFile(long ownerId, in ParcelFileDescriptor tunFd, String configFilePath, ISingboxProtector protector);
    void startProxyMode(long ownerId, String singboxJsonConfig, ISingboxProtector protector);
    boolean startProxyModeIfIdle(long ownerId, String singboxJsonConfig, ISingboxProtector protector);
    void stop(long ownerId);
    boolean stopAndWait(long ownerId, long timeoutMs);
    boolean runtimeRunning();
    int processId();
    SingboxStats getStats();
}
