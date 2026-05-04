package ru.ozero.enginewarp

import org.amnezia.awg.backend.Tunnel

interface AwgBackend {
    fun setState(tunnel: Tunnel, state: Tunnel.State, config: org.amnezia.awg.config.Config?): Tunnel.State
}
