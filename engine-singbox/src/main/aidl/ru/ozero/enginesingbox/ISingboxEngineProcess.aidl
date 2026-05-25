package ru.ozero.enginesingbox;

import ru.ozero.enginesingbox.ISingboxProtector;
import ru.ozero.enginesingbox.ISingboxStatusCallback;
import ru.ozero.enginesingbox.SingboxStats;

interface ISingboxEngineProcess {
    void startWithConfig(in ParcelFileDescriptor tunFd, String singboxJsonConfig, ISingboxProtector protector);
    void stop();
    SingboxStats getStats();
    void registerStatusCallback(ISingboxStatusCallback cb);
}
