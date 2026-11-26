package ru.ozero.enginegfvpn;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/** Single-session, asynchronous client. All state transitions run on the main looper. */
public final class GfVpnRuntime implements AutoCloseable {
    public static final class Session {
        public final int port;
        public final String username;
        public final String password;
        private Session(Bundle data) throws IOException {
            port = data.getInt("port");
            username = data.getString("username", "");
            password = data.getString("password", "");
            if (port < 1 || port > 65535 || username.isEmpty() || password.isEmpty()) {
                throw new IOException("Invalid SDK session");
            }
        }
        @Override public String toString() { return "gfVPN session (credentials redacted)"; }
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Messenger replies = new Messenger(new Handler(Looper.getMainLooper(), this::receive));
    private Messenger remote;
    private boolean bound;
    private boolean closed;
    private boolean started;
    private int sequence;
    private int pendingOperation;
    private CompletableFuture<Bundle> pending;
    private Bundle pendingInput;
    private Runnable deadline;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            remote = new Messenger(binder);
            sendPending();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            fail(new IOException("gfVPN process disconnected"));
        }
        @Override public void onBindingDied(ComponentName name) {
            fail(new IOException("gfVPN process binding died"));
        }
        @Override public void onNullBinding(ComponentName name) {
            fail(new IOException("gfVPN process returned no binder"));
        }
    };

    public GfVpnRuntime(Context context) { this.context = context.getApplicationContext(); }

    public CompletableFuture<Session> start(String country, String clientIdentifier) {
        Bundle data = new Bundle();
        data.putString("country", country);
        data.putString("clientIdentifier", clientIdentifier);
        CompletableFuture<Bundle> raw = request(GfVpnProcessService.START, data, 125000);
        CompletableFuture<Session> result = raw.thenApply(response -> {
            try { return new Session(response); }
            catch (IOException error) { close(); throw new java.util.concurrent.CompletionException(error); }
        });
        result.whenComplete((value, error) -> { if (result.isCancelled()) close(); });
        return result;
    }

    /** Duplicates the caller's TUN fd; ownership of the original stays with the caller. */
    public CompletableFuture<Void> attachTun(ParcelFileDescriptor descriptor) {
        Bundle input = new Bundle();
        try {
            input.putParcelable("tun", descriptor.dup());
        } catch (IOException failure) {
            CompletableFuture<Void> result = new CompletableFuture<>();
            result.completeExceptionally(failure);
            return result;
        }
        CompletableFuture<Void> result = request(GfVpnProcessService.ATTACH_TUN, input, 15000).thenApply(data -> null);
        result.whenComplete((value, error) -> { if (error != null) close(); });
        return result;
    }

    private static void closeInput(Bundle input) {
        if (input == null) return;
        ParcelFileDescriptor descriptor = input.getParcelable("tun");
        if (descriptor != null) {
            try { descriptor.close(); } catch (IOException ignored) { }
            input.remove("tun");
        }
    }

    public CompletableFuture<Long> probe() {
        CompletableFuture<Bundle> raw = request(GfVpnProcessService.PROBE, new Bundle(), 15000);
        CompletableFuture<Long> result = raw.thenApply(data -> data.getLong("latencyMs"));
        result.whenComplete((value, error) -> { if (result.isCancelled()) close(); });
        return result;
    }

    public CompletableFuture<Void> networkChanged() {
        return voidRequest(GfVpnProcessService.NETWORK_CHANGED, 15000);
    }

    public CompletableFuture<Void> stop() {
        return voidRequest(GfVpnProcessService.STOP, 15000);
    }

    private CompletableFuture<Void> voidRequest(int operation, long timeout) {
        CompletableFuture<Void> result = request(operation, new Bundle(), timeout).thenApply(data -> null);
        result.whenComplete((value, error) -> { if (result.isCancelled() || operation == GfVpnProcessService.STOP) close(); });
        return result;
    }

    private CompletableFuture<Bundle> request(int operation, Bundle input, long timeoutMs) {
        CompletableFuture<Bundle> result = new CompletableFuture<>();
        result.whenComplete((value, error) -> { if (result.isCancelled()) close(); });
        main.post(() -> {
            if (result.isCancelled()) { closeInput(input); return; }
            if (closed || pending != null || (operation == GfVpnProcessService.START ? started : !started)) {
                closeInput(input);
                result.completeExceptionally(new IOException("Invalid runtime state"));
                return;
            }
            pending = result;
            pendingOperation = operation;
            pendingInput = input;
            sequence++;
            deadline = () -> fail(new TimeoutException("gfVPN runtime timed out"));
            main.postDelayed(deadline, timeoutMs);
            if (remote != null) sendPending();
            else {
                try {
                    bound = context.bindService(new Intent(context, GfVpnProcessService.class),
                        connection, Context.BIND_AUTO_CREATE);
                    if (!bound) fail(new IOException("Cannot bind gfVPN process"));
                } catch (RuntimeException failure) { fail(failure); }
            }
        });
        return result;
    }

    private void sendPending() {
        if (pending == null || remote == null || closed) return;
        Message message = Message.obtain(null, pendingOperation);
        message.arg1 = sequence;
        message.replyTo = replies;
        message.setData(pendingInput);
        try { remote.send(message); } catch (RemoteException failure) { fail(failure); }
        finally { closeInput(pendingInput); }
    }

    private boolean receive(Message message) {
        if (message.sendingUid != context.getApplicationInfo().uid ||
            message.what != GfVpnProcessService.RESULT || pending == null || closed) return true;
        Bundle data = message.getData();
        if (data.getInt("requestId") != sequence) return true;
        int operation = pendingOperation;
        CompletableFuture<Bundle> result = clearPending();
        if (data.getBoolean("ok")) {
            if (operation == GfVpnProcessService.START) started = true;
            if (operation == GfVpnProcessService.STOP) started = false;
            result.complete(data);
        } else {
            result.completeExceptionally(new IOException("gfVPN: " + data.getString("error", "operation failed")));
            if (operation == GfVpnProcessService.START || operation == GfVpnProcessService.STOP) release();
        }
        return true;
    }

    private CompletableFuture<Bundle> clearPending() {
        CompletableFuture<Bundle> result = pending;
        pending = null;
        closeInput(pendingInput);
        pendingInput = null;
        if (deadline != null) main.removeCallbacks(deadline);
        deadline = null;
        return result;
    }

    private void fail(Throwable failure) {
        CompletableFuture<Bundle> result = clearPending();
        release();
        if (result != null) result.completeExceptionally(failure);
    }

    private void release() {
        if (closed) return;
        closed = true;
        started = false;
        if (remote != null) {
            Message abort = Message.obtain(null, GfVpnProcessService.ABORT);
            abort.replyTo = replies;
            try { remote.send(abort); } catch (RemoteException ignored) { }
            remote = null;
        }
        if (bound) {
            bound = false;
            try { context.unbindService(connection); } catch (IllegalArgumentException ignored) { }
        }
    }

    @Override public void close() {
        main.post(() -> fail(new IOException("gfVPN runtime closed")));
    }
}
