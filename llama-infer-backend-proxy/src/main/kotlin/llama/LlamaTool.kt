package org.sbx.llama

interface LlamaTool {
    val description: String
    fun execute(toolCallData: String): String
}