package org.sbx.llama.chat.functions

enum class LlamaToolParameterType {
    Object,
    String,
    Number,
    Integer,
    Boolean,
    Array;

    override fun toString(): String = when (this) {
        LlamaToolParameterType.Object -> "object"
        LlamaToolParameterType.String -> "string"
        LlamaToolParameterType.Number -> "number"
        LlamaToolParameterType.Integer -> "integer"
        LlamaToolParameterType.Boolean -> "boolean"
        LlamaToolParameterType.Array -> "array"
    }
}