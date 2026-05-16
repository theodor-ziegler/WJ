package org.sbx.llama.chat

enum class FinishReason {
    None,
    Stop,
    ToolCall;

    companion object {
        fun fromString(string: String): FinishReason = when(string) {
            "none" -> None
            "null" -> None
            "stop" -> Stop
            "tool_calls" -> ToolCall
            else -> throw IllegalArgumentException("$string is not a valid FinishReason")
        }
    }
}