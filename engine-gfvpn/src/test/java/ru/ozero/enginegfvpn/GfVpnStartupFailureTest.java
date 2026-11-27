package ru.ozero.enginegfvpn;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GfVpnStartupFailureTest {
    @Test void reflectionFailureIsClassifiedWithoutLeakingProviderMessage() {
        GfVpnSdk.StartupFailure failure = new GfVpnSdk.StartupFailure(
            GfVpnSdk.StartupStage.NATIVE_START,
            new InvocationTargetException(new IOException("secret provider response")));
        assertEquals("NATIVE_START: IOException", failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test void linkingAndConfigurationFailuresRetainDistinctStages() {
        assertEquals("LOAD_SDK: UnsatisfiedLinkError", new GfVpnSdk.StartupFailure(
            GfVpnSdk.StartupStage.LOAD_SDK, new UnsatisfiedLinkError("private path")).getMessage());
        assertEquals("CONFIGURE: NoSuchMethodException", new GfVpnSdk.StartupFailure(
            GfVpnSdk.StartupStage.CONFIGURE, new NoSuchMethodException("private signature")).getMessage());
        assertEquals("VALIDATE_SESSION: IOException", new GfVpnSdk.StartupFailure(
            GfVpnSdk.StartupStage.VALIDATE_SESSION, new IOException("secret credential")).getMessage());
    }
}
