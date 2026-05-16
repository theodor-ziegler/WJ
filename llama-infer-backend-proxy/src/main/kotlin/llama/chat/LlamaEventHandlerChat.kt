package org.sbx.llama.chat

import org.sbx.llama.LlamaEventHandler

interface LlamaEventHandlerChat : LlamaEventHandler {
    fun onBeginReasoning()
    fun onReasoningToken(token: String)
    fun onFinish()
}