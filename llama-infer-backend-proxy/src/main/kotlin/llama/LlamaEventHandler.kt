package org.sbx.llama

interface LlamaEventHandler {
    fun onNewToken(token: String)
    fun onEndThinking()
    fun onToolUsed(toolName: String)
    fun onBeginToolCall()
}