package com.lemon.echo.scanner.chain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages a continuous scan session lifecycle.
 * Exposes [state] flow for UI to observe.
 */
class ScanSession {

    private val resolver = ChainResolver()
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Start a new session — resets any previous state. */
    fun start() {
        resolver.reset()
        _state.value = State.Scanning
    }

    /** Accept raw scanned text. No-op if session not active. */
    fun accept(text: String) {
        val s = _state.value
        if (s !== State.Scanning && s !is State.Collecting) return

        val packet = ChainPacket.parse(text)
        if (packet != null) {
            when (val step = resolver.accept(packet)) {
                is ChainResolver.Step.Collecting -> {
                    _state.value = State.Collecting(step.got, step.total)
                }
                is ChainResolver.Step.Assembled -> {
                    _state.value = State.Done(step.text)
                }
                is ChainResolver.Step.Rejected -> {
                    _state.value = State.Rejected(step.reason)
                }
            }
        } else {
            _state.value = State.SoloScanned(text)
        }
    }

    /** Stop the session and return collected segments. */
    fun stop() {
        val peeked = resolver.peek()
        resolver.reset()
        _state.value = if (peeked.isEmpty()) State.Idle else State.Stopped(peeked)
    }

    /** Reset back to Idle. */
    fun reset() {
        resolver.reset()
        _state.value = State.Idle
    }

    sealed class State {
        data object Idle : State()
        data object Scanning : State()
        data class Collecting(val got: Int, val total: Int) : State()
        data class SoloScanned(val text: String) : State()
        data class Done(val text: String) : State()
        data class Stopped(val segments: List<Pair<Int, String>>) : State()
        data class Rejected(val reason: String) : State()
    }
}
