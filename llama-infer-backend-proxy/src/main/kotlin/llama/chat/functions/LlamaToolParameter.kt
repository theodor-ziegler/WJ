package org.sbx.llama.chat.functions

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class LlamaToolParameter(
    val name: String,
    val type: LlamaToolParameterType,
    val description: String
) {
    fun toJson(): Pair<String, JsonObject> = name to buildJsonObject {
        put("type", type.toString())
        put("description", description)
    }
}