package org.sbx.llama.chat.functions

data class ToolCallAccumulator(val name: String, var arguments: String)