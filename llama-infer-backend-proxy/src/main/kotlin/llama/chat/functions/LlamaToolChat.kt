package org.sbx.llama.chat.functions

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.sbx.llama.LlamaTool

interface LlamaToolChat : LlamaTool {
    val name: String
    val parameters: List<LlamaToolParameter>
    val requiredParameters: List<String>

    fun toJson(): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject{
            put("name", name)
            put("description", description)
            put("parameters", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    parameters.forEach { parameter ->
                        put(parameter.name, parameter.toJson().second)
                    }
                })
                if (requiredParameters.isNotEmpty()) {
                    put("required", buildJsonArray { requiredParameters.forEach { add(it) } })
                }
            })
        })
    }
}