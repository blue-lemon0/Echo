package com.lemon.echo.scanner.chain

class ChainResolver {

    private val segments = mutableMapOf<Int, String>()
    private var expectedTotal: Int? = null

    /** Feed a parsed packet, returns the current step result. */
    fun accept(packet: ChainPacket): Step {
        if (expectedTotal != null && packet.total != expectedTotal) {
            return Step.Rejected("段数不匹配: ${packet.total} ≠ $expectedTotal")
        }
        if (expectedTotal == null) {
            expectedTotal = packet.total
        }

        if (!segments.containsKey(packet.index)) {
            segments[packet.index] = packet.data
        }

        if (segments.size == expectedTotal) {
            val full = (0 until expectedTotal!!)
                .map { segments[it] ?: "" }
                .joinToString("")
            reset()
            return Step.Assembled(full)
        }

        return Step.Collecting(segments.size, expectedTotal!!)
    }

    /** Peek at collected segments without resetting. */
    fun peek(): List<Pair<Int, String>> {
        return segments.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    fun reset() {
        segments.clear()
        expectedTotal = null
    }

    sealed class Step {
        data class Collecting(val got: Int, val total: Int) : Step()
        data class Assembled(val text: String) : Step()
        data class Rejected(val reason: String) : Step()
    }
}
