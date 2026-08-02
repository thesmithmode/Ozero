package ru.ozero.singboxprocess

import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicReference

internal enum class TunFdOwnershipState {
    DETACHED,
    CLAIMED_BY_LIBBOX,
    CLOSED,
}

internal class DetachedTunFd(
    val fd: Int,
    private val closeFd: (Int) -> Unit = { ParcelFileDescriptor.adoptFd(it).close() },
) {
    private val ownership = AtomicReference(TunFdOwnershipState.DETACHED)

    val state: TunFdOwnershipState
        get() = ownership.get()

    fun claimByLibbox(): Int {
        check(ownership.compareAndSet(TunFdOwnershipState.DETACHED, TunFdOwnershipState.CLAIMED_BY_LIBBOX))
        return fd
    }

    fun closeIfDetached(): Boolean {
        if (!ownership.compareAndSet(TunFdOwnershipState.DETACHED, TunFdOwnershipState.CLOSED)) return false
        closeFd(fd)
        return true
    }
}
