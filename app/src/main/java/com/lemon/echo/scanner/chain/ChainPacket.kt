package com.lemon.echo.scanner.chain

import org.json.JSONObject

data class ChainPacket(
    val version: Int,
    val total: Int,
    val index: Int,
    val data: String
) {
    companion object {
        /** Parse Helix chain-format JSON: {"v":1,"t":<total>,"i":<index>,"d":"<text>"} */
        fun parse(text: String): ChainPacket? {
            return try {
                val json = JSONObject(text)
                val v = json.optInt("v", 0)
                if (v != 1) return null
                ChainPacket(
                    version = v,
                    total = json.getInt("t"),
                    index = json.getInt("i"),
                    data = json.getString("d")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
