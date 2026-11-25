package ru.ozero.enginegfvpn;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

/** Private proof-of-integration endpoint; not registered as a selectable VPN engine yet. */
public final class GfVpnProcessService extends Service {
    public static final int START = 1;
    public static final int STOP = 2;
    public static final int NETWORK_CHANGED = 3;
    public static final int RESULT = 4;
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
        try {
            switch (request.what) {
                case START:
                    String country = request.getData().getString("country", "RU");
                    String client = request.getData().getString("clientIdentifier", "");
                    response.putInt("port", sdk.start(country, client));
                    break;
                case STOP:
                    sdk.stop();
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
            try { sdk.stop(); } catch (Exception | LinkageError ignored) { }
            worker.quitSafely();
        });
        super.onDestroy();
    }
}
