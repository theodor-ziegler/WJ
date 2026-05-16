package org.sbx.llama

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException

@Serializable
data class LlamaContextMetadata(val data: MutableMap<ULong, String>) {
    operator fun set(index: ULong, title: String) {
        data.put(index, title)
    }

    fun remove(index: ULong) {
        data.remove(index)
    }

    fun save(path: String) {
        val file = File(path)

        if (!File(file.parent).exists()) File(file.parent).mkdirs()
        if (!file.exists()) file.createNewFile()

        file.writeText(Json.encodeToString(this))
    }

    companion object {
        fun loadFrom(path: String): LlamaContextMetadata = try {
            Json.decodeFromString(File(path).readText())
        } catch (_: FileNotFoundException) {
            LlamaContextMetadata(mutableMapOf())
        }
    }
}
