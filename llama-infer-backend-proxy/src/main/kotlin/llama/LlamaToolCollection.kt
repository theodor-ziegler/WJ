package org.sbx.llama

data class LlamaToolCollection(val tools: Map<String, LlamaTool>) {
    operator fun get(name: String): LlamaTool? = tools[name]
    operator fun iterator() = tools.iterator()
}