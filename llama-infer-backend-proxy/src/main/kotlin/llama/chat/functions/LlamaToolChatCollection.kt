package org.sbx.llama.chat.functions

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray

data class LlamaToolChatCollection(val tools: Map<String, LlamaToolChat>) {
    operator fun get(name: String): LlamaToolChat? = tools[name]
    operator fun iterator() = tools.iterator()

    fun toJson(): JsonArray = buildJsonArray {
        tools.forEach {
            add(it.value.toJson())
        }
    }
}