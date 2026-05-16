package org.sbx.llama

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class LlamaContext(val messages: List<Pair<LlamaRole, String>>) {
    fun toChatApiMessages(): JsonArray = buildJsonArray {
        messages.forEach {
            add(
                buildJsonObject {
                    put(
                        "role",
                        when (it.first) {
                            LlamaRole.User -> "user"
                            LlamaRole.System -> "system"
                            LlamaRole.Assistant -> "assistant"
                            LlamaRole.Tool -> "tool"
                        }
                    )
                    put("content", it.second)
                })
        }
    }
}
