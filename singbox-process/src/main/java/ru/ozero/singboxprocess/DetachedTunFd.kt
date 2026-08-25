package ru.ozero.singboxprocess

import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicReference

internal enum class TunFdOwnershipState {
    DETACHED,
    PROVIDED_TO_LIBBOX,
    CLOSED,
}

internal class DetachedTunFd(
    val fd: Int,
    private val closeFd: (Int) -> Unit = { ParcelFileDescriptor.adoptFd(it).close() },
) {
    private val ownership = AtomicReference(TunFdOwnershipState.DETACHED)

    val state: TunFdOwnershipState
        get() = ownership.get()

    fun provideToLibbox(): Int {
        check(ownership.compareAndSet(TunFdOwnershipState.DETACHED, TunFdOwnershipState.PROVIDED_TO_LIBBOX))
        return fd
    }

    fun closeIfDetached(): Boolean {
        if (!ownership.compareAndSet(TunFdOwnershipState.DETACHED, TunFdOwnershipState.CLOSED)) return false
        closeFd(fd)
        return true
    }

    fun closeOwnedByHost(): Boolean {
        while (true) {
            when (val current = ownership.get()) {
                TunFdOwnershipState.CLOSED -> return false
                TunFdOwnershipState.DETACHED,
                TunFdOwnershipState.PROVIDED_TO_LIBBOX,
                -> if (ownership.compareAndSet(current, TunFdOwnershipState.CLOSED)) {
                    closeFd(fd)
                    return true
                }
            }
        }
    }
}
