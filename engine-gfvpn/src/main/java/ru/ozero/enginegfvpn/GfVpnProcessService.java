package ru.ozero.enginegfvpn;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import ru.ozero.enginescore.settings.GfVpnOptions;

/** One owner, one SDK lifetime, one isolated process. No exported endpoint. */
public final class GfVpnProcessService extends Service {
    public static final int START = 1;
    public static final int STOP = 2;
    public static final int NETWORK_CHANGED = 3;
    public static final int RESULT = 4;
    public static final int PROBE = 5;
    public static final int ABORT = 6;
    public static final int ATTACH_TUN = 7;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closing = new AtomicBoolean();
    private HandlerThread worker;
    private Handler work;
    private Messenger messenger;
    private GfVpnSdk sdk;
    private GfVpnTun tun;
    private IBinder owner;
    private boolean pending;
    private final IBinder.DeathRecipient ownerDeath = this::terminate;

    @Override public void onCreate() {
        super.onCreate();
        sdk = new GfVpnSdk(this);
        tun = new GfVpnTun(this);
        worker = new HandlerThread("gfvpn-worker");
        worker.start();
        work = new Handler(worker.getLooper());
        // Control messages must remain responsive when native calls stall.
        messenger = new Messenger(new Handler(Looper.getMainLooper(), this::dispatch));
    }

    private void terminate() {
        if (closing.compareAndSet(false, true)) Process.killProcess(Process.myPid());
    }

    private boolean dispatch(Message request) {
        if (request.sendingUid != getApplicationInfo().uid || request.replyTo == null || closing.get()) return true;
        IBinder caller = request.replyTo.getBinder();
        if (owner == null) {
            if (request.what != START) {
                reply(request.replyTo, request.arg1, failure("No runtime owner"));
                return true;
            }
            try {
                caller.linkToDeath(ownerDeath, 0);
                owner = caller;
            } catch (RemoteException dead) {
                terminate();
                return true;
            }
        }
        if (!owner.equals(caller)) {
            reply(request.replyTo, request.arg1, failure("Runtime already owned"));
            return true;
        }
        if (request.what == ABORT) {
            terminate();
            return true;
        }
        if (pending) {
            reply(request.replyTo, request.arg1, failure("Runtime request in progress"));
            return true;
        }
        pending = true;
        int operation = request.what;
        int id = request.arg1;
        Bundle input = new Bundle(request.getData());
        Messenger target = request.replyTo;
        work.post(() -> execute(operation, id, input, target));
        return true;
    }

    private void execute(int operation, int id, Bundle input, Messenger target) {
        ScheduledFuture<?> deadline = watchdog.schedule(this::terminate,
            operation == START ? 120 : 12, TimeUnit.SECONDS);
        Bundle response = new Bundle();
        try {
            switch (operation) {
                case START:
                    GfVpnSdk.Session session = sdk.start(input.getString("country", "RU"),
                        input.getString("clientIdentifier", ""), GfVpnOptions.decode(input.getString("options")));
                    response.putInt("port", session.port);
                    response.putString("username", session.username);
                    response.putString("password", session.password);
                    break;
                case ATTACH_TUN:
                    ParcelFileDescriptor descriptor = input.getParcelable("tun");
                    if (descriptor == null) throw new java.io.IOException("Missing TUN descriptor");
                    try {
                        tun.start(descriptor, sdk.requireSession());
                    } catch (Exception | LinkageError error) {
                        try { descriptor.close(); } catch (java.io.IOException ignored) { }
                        throw error;
                    }
                    break;
                case STOP:
                    try { tun.stop(); } finally { sdk.stop(); }
                    break;
                case PROBE:
                    response.putLong("latencyMs", sdk.probe());
                    if (tun.isAttached()) {
                        long[] counters = tun.counters();
                        response.putLong("bytesOut", counters[1]);
                        response.putLong("bytesIn", counters[3]);
                    }
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
        new Handler(Looper.getMainLooper()).post(() -> {
            pending = false;
            reply(target, id, response);
            // Never reuse an unverified or stopped native runtime.
            if (operation == STOP || ((operation == START || operation == ATTACH_TUN) && !response.getBoolean("ok"))) terminate();
        });
    }

    private static Bundle failure(String reason) {
        Bundle response = new Bundle();
        response.putBoolean("ok", false);
        response.putString("error", reason);
        return response;
    }

    private void reply(Messenger target, int id, Bundle response) {
        if (closing.get()) return;
        response.putInt("requestId", id);
        Message result = Message.obtain(null, RESULT);
        result.setData(response);
        try { target.send(result); } catch (RemoteException dead) { terminate(); }
    }

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    @Override public boolean onUnbind(Intent intent) {
        terminate();
        return false;
    }

    @Override public void onDestroy() {
        if (owner != null) owner.unlinkToDeath(ownerDeath, 0);
        terminate();
        super.onDestroy();
    }
}
