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
    public static final int CAPTCHA_EVENT = 8;
    public static final int CAPTCHA_DISMISS = 9;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean closing = new AtomicBoolean();
    private HandlerThread worker;
    private Handler work;
    private Messenger messenger;
    private GfVpnSdk sdk;
    private GfVpnTun tun;
    private IBinder owner;
    private Messenger ownerTarget;
    private final AtomicBoolean dismissingCaptcha = new AtomicBoolean();
    private final AtomicBoolean manualVerificationSeen = new AtomicBoolean();
    private boolean pending;
    private final IBinder.DeathRecipient ownerDeath = this::terminate;

    @Override public void onCreate() {
        super.onCreate();
        sdk = new GfVpnSdk(this, this::captchaEvent);
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

    static String operationName(int operation) {
        switch (operation) {
            case START: return "start";
            case ATTACH_TUN: return "attach-tun";
            case STOP: return "stop";
            case PROBE: return "probe";
            case NETWORK_CHANGED: return "network-changed";
            default: return "unknown";
        }
    }

    private static void closeRejectedTun(Message request) {
        if (request.what != ATTACH_TUN) return;
        ParcelFileDescriptor descriptor = request.getData().getParcelable("tun");
        if (descriptor != null) {
            try { descriptor.close(); } catch (java.io.IOException ignored) { }
        }
    }

    private boolean dispatch(Message request) {
        if (request.sendingUid != getApplicationInfo().uid) return true;
        if (request.replyTo == null || closing.get()) {
            closeRejectedTun(request);
            return true;
        }
        IBinder caller = request.replyTo.getBinder();
        if (owner == null) {
            if (request.what != START) {
                closeRejectedTun(request);
                reply(request.replyTo, request.arg1, failure("No runtime owner"));
                return true;
            }
            try {
                caller.linkToDeath(ownerDeath, 0);
                owner = caller;
                ownerTarget = request.replyTo;
            } catch (RemoteException dead) {
                terminate();
                return true;
            }
        }
        if (!owner.equals(caller)) {
            closeRejectedTun(request);
            reply(request.replyTo, request.arg1, failure("Runtime already owned"));
            return true;
        }
        if (request.what == ABORT) {
            terminate();
            return true;
        }
        // CAPTCHA dismissal is an out-of-band control operation, not a second start request.
        if (request.what == CAPTCHA_DISMISS) {
            dismissCaptcha();
            return true;
        }
        if (pending) {
            closeRejectedTun(request);
            reply(request.replyTo, request.arg1, failure("Runtime request in progress"));
            return true;
        }
        pending = true;
        int operation = request.what;
        int id = request.arg1;
        Bundle input = new Bundle(request.getData());
        Messenger target = request.replyTo;
        if (!work.post(() -> execute(operation, id, input, target))) {
            pending = false;
            closeRejectedTun(request);
            reply(target, id, failure("Runtime worker unavailable"));
            terminate();
        }
        return true;
    }

    private void captchaEvent(String url) {
        if (url != null && !url.isEmpty()) manualVerificationSeen.set(true);
        new Handler(Looper.getMainLooper()).post(() -> {
            if (closing.get() || ownerTarget == null) return;
            Message event = Message.obtain(null, CAPTCHA_EVENT);
            Bundle data = new Bundle();
            data.putString("url", url);
            event.setData(data);
            try { ownerTarget.send(event); } catch (RemoteException dead) { terminate(); }
        });
    }

    private void dismissCaptcha() {
        if (!dismissingCaptcha.compareAndSet(false, true)) return;
        ScheduledFuture<?> deadline = watchdog.schedule(this::terminate, 12, TimeUnit.SECONDS);
        new Thread(() -> {
            try {
                sdk.dismissCaptcha();
            } catch (Exception | LinkageError failure) {
                android.util.Log.w("GfVpnProcess", "CAPTCHA dismissal failed: " +
                    failure.getClass().getSimpleName());
                terminate();
            } finally {
                deadline.cancel(false);
                dismissingCaptcha.set(false);
            }
        }, "gfvpn-captcha-dismiss").start();
    }

    private void execute(int operation, int id, Bundle input, Messenger target) {
        ScheduledFuture<?> hardDeadline = operation == START
            ? watchdog.schedule(this::terminate, 300, TimeUnit.SECONDS) : null;
        ScheduledFuture<?> deadline = watchdog.schedule(() -> {
            if (operation == START && manualVerificationSeen.get()) return;
            android.util.Log.w("GfVpnProcess", operationName(operation) + " watchdog expired");
            terminate();
        }, operation == START ? 120 : 12, TimeUnit.SECONDS);
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
            String detail = failure instanceof GfVpnSdk.StartupFailure
                ? failure.getMessage() : failure.getClass().getSimpleName();
            response.putString("error", operationName(operation) + ": " + detail);
        } finally {
            deadline.cancel(false);
            if (hardDeadline != null) hardDeadline.cancel(false);
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
