package ru.ozero.enginegfvpn;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Private proof-of-integration endpoint; not registered as a selectable VPN engine yet. */
public final class GfVpnProcessService extends Service {
    public static final int START = 1;
    public static final int STOP = 2;
    public static final int NETWORK_CHANGED = 3;
    public static final int RESULT = 4;
    public static final int PROBE = 5;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private HandlerThread worker;
    private Messenger messenger;
    private GfVpnSdk sdk;

    @Override public void onCreate() {
        super.onCreate();
        sdk = new GfVpnSdk(this);
        worker = new HandlerThread("gfvpn-worker");
        worker.start();
        messenger = new Messenger(new Handler(worker.getLooper(), this::handle));
    }

    private boolean handle(Message request) {
        Bundle response = new Bundle();
        response.putInt("requestId", request.arg1);
        if (request.sendingUid != getApplicationInfo().uid) return true;
        ScheduledFuture<?> deadline = watchdog.schedule(() -> android.os.Process.killProcess(android.os.Process.myPid()),
            request.what == START ? 120 : 20, TimeUnit.SECONDS);
        try {
            switch (request.what) {
                case START:
                    String country = request.getData().getString("country", "RU");
                    String client = request.getData().getString("clientIdentifier", "");
                    GfVpnSdk.Session session = sdk.start(country, client);
                    response.putInt("port", session.port);
                    response.putString("username", session.username);
                    response.putString("password", session.password);
                    break;
                case STOP:
                    sdk.stop();
                    break;
                case PROBE:
                    response.putLong("latencyMs", sdk.probe());
                    break;
                case NETWORK_CHANGED:
                    sdk.networkChanged();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation");
            }
            response.putBoolean("ok", true);
        } catch (Exception | LinkageError failure) {
            response.putBoolean("ok", false);
            response.putString("error", failure.getClass().getSimpleName());
        } finally {
            deadline.cancel(false);
        }
        if (request.replyTo != null) {
            Message result = Message.obtain(null, RESULT);
            result.setData(response);
            try { request.replyTo.send(result); } catch (RemoteException ignored) { }
        }
        return true;
    }

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    @Override public void onDestroy() {
        new Handler(worker.getLooper()).post(() -> {
            ScheduledFuture<?> deadline = watchdog.schedule(() -> android.os.Process.killProcess(android.os.Process.myPid()), 20, TimeUnit.SECONDS);
            try { sdk.stop(); } catch (Exception | LinkageError ignored) { }
            finally {
                deadline.cancel(false);
                watchdog.shutdownNow();
                worker.quitSafely();
            }
        });
        super.onDestroy();
    }
}
