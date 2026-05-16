package org.sbx.llama

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LlamaContextStore(
    val contextMap: MutableMap<ULong, LlamaContext>,
    val metadata: LlamaContextMetadata = LlamaContextMetadata.loadFrom(CONTEXT_PATH)
) {
    init {
        if (metadata.data.size < contextMap.size) {
            contextMap.forEach {
                metadata[it.key] = "New chat"
            }

            metadata.save(CONTEXT_PATH)
        }
    }

    fun setTitle(index: ULong, title: String) {
        metadata[index] = title
    }

    fun saveContext(path: String) {
        val file = File(path)

        if (!File(file.parent).exists()) {
            File(file.parent).mkdirs()
        }

        if (!file.exists()) {
            file.createNewFile()
        }

        file.writeText(Json.encodeToString(this))
        metadata.save(CONTEXT_PATH)
    }

    operator fun get(index: ULong): LlamaContext? = contextMap[index]

    operator fun set(index: ULong, element: LlamaContext) {
        contextMap[index] = element
    }

    fun delete(index: ULong) {
        contextMap.remove(index)
        metadata.remove(index)
    }

    companion object {
        const val CONTEXT_PATH = "./store/context-meta.json"

        fun loadContext(path: String): LlamaContextStore {
            val file = File(path)

            if (!file.exists()) {
                return LlamaContextStore(mutableMapOf())
            }

            return Json.decodeFromString(file.readText())
        }
    }
}