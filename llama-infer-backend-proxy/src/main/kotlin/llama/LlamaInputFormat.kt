package org.sbx.llama

interface LlamaInputFormat {
    val userHeader: String
    val userEndHeader: String
    val systemHeader: String
    val systemEndHeader: String
    val assistantHeader: String
    val assistantEndHeader: String
}