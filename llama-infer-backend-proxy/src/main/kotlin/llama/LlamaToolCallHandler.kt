package org.sbx.llama

interface LlamaToolCallHandler {
    val toolCallPrefix: String
    val toolCallSuffix: String
    val toolUsePrompt: String

    fun findToolName(toolCallData: String): String

    fun handle(toolCallData: String, tools: LlamaToolCollection): String {
        //println(findToolName(toolCallData))
        val tool = tools[findToolName(toolCallData)] ?: return "Tool not found"
        return tool.execute(toolCallData)
    }
}