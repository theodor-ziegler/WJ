package org.sbx.llama.manual

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import org.sbx.control.RequestAction
import org.sbx.llama.LlamaContext
import org.sbx.llama.LlamaEventHandler
import org.sbx.llama.LlamaInputFormat
import org.sbx.llama.LlamaRole
import org.sbx.llama.LlamaToolCallHandler
import org.sbx.llama.LlamaToolCollection

class LlamaApiInferManual(
    private val endpoint: String,
    private val tools: LlamaToolCollection? = null,
    private val contextMap: MutableMap<ULong, LlamaContext> = mutableMapOf(),
) {
    private val okHttpClient = OkHttpClient()
    // TODO: Support more input formats
    private val inputFormat = object : LlamaInputFormat {
        override val userHeader: String = "<|im_start|>user\n"
        override val userEndHeader: String = "<|im_end|>\n"
        override val systemHeader: String= "<|im_start|>system\n"
        override val systemEndHeader: String = "<|im_end|>\n"
        override val assistantHeader: String = "<|im_start|>assistant\n"
        override val assistantEndHeader: String = "<|im_end|>\n"
    }

    private val toolCallHandler = object : LlamaToolCallHandler {
        override val toolCallPrefix: String = "<tool_code>"
        override val toolCallSuffix: String = "</tool_code>"
        override val toolUsePrompt: String = """
            Tool use:
            You can use tools by inserting $toolCallPrefix[tool-body-here]$toolCallSuffix
            Ensure the tool body is a valid JsonObject and contains the "tool_name" field
            You should prefer to use tools during thinking
        """.trimIndent()

        override fun findToolName(toolCallData: String): String = try {
            Json.Default.decodeFromString<JsonObject>(toolCallData)["tool_name"]!!.jsonPrimitive.content
        } catch (e: SerializationException)  {
            e.printStackTrace()
            "Syntax error, ensure the tool call is a JsonObject and exactly follows the required syntax"
        } catch (e: NullPointerException) {
            e.printStackTrace()
            "Tool name not set"
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            "Tool body is not considered a JsonObject"
        }
    }

    fun infer(query: String, contextId: ULong, allowThinking: Boolean, enableTools: Boolean, eventHandler: LlamaEventHandler) {
        if (contextMap[contextId] == null) {
            contextMap[contextId] = createDefaultContext(enableTools)
        }

        val context = LlamaContext(buildList {
            contextMap[contextId]!!.messages.forEach {
                add(Pair(it.first, it.second))
            }
            // TODO: Here goes additional context stuff like RAG and probably system prompt as well so it isn't forgotten in case context grows
            add(Pair(LlamaRole.User, query))
        })

        val fullReply = StringBuilder()
        sendLlama(context, allowThinking, enableTools, object : LlamaEventHandler {
            override fun onNewToken(token: String) {
                fullReply.append(token)
                if (!token.startsWith("<tool_result>")) eventHandler.onNewToken(token)
            }

            override fun onEndThinking() {
                fullReply.append("</think>")
                eventHandler.onEndThinking()
            }

            override fun onToolUsed(toolName: String) {
                eventHandler.onToolUsed(toolName)
            }

            override fun onBeginToolCall() {
                eventHandler.onBeginToolCall()
            }
        })

        fullReply.append(inputFormat.assistantEndHeader)

        contextMap[contextId] = LlamaContext(buildList {
            context.messages.forEach {
                add(Pair(it.first, it.second))
            }
            add(Pair(LlamaRole.Assistant, fullReply.toString()))
        })
    }

    private fun sendLlama(context: LlamaContext, allowThinking: Boolean, enableTools: Boolean, llamaEventHandler: LlamaEventHandler) {
        val currentGeneration = StringBuilder()
        val finalFormattedQuery = formatContext(context, allowThinking, inputFormat)


        var requestBody = buildJsonObject {
            put("stream", true)
            put("prompt", finalFormattedQuery)
            put("temperature", 0.6)
            put("top_p", 0.95)
            put("stop", buildJsonArray {
                add(inputFormat.assistantEndHeader)
            })
        }

        var done = false
        while (!done) {
            var requestHandleTool = false
            sendRequest(requestBody) { token ->
                currentGeneration.append(token)
                if (token == "</think>") {
                    llamaEventHandler.onEndThinking()
                    return@sendRequest RequestAction.Continue
                }

                if (enableTools && (token == toolCallHandler.toolCallPrefix ||
                            currentGeneration.endsWith(toolCallHandler.toolCallPrefix) ||
                            currentGeneration.endsWith(toolCallHandler.toolCallPrefix + "{"))) {
                    llamaEventHandler.onBeginToolCall()
                }

                if (enableTools && (token == toolCallHandler.toolCallSuffix ||
                            currentGeneration.endsWith(toolCallHandler.toolCallSuffix))) {
                    llamaEventHandler.onNewToken(token)
                    requestHandleTool = true
                    return@sendRequest RequestAction.Break
                }

                if (token != inputFormat.assistantEndHeader.trim()) llamaEventHandler.onNewToken(token) else done = true
                RequestAction.Continue
            }

            if (requestHandleTool) {
                val toolContent = currentGeneration.toString()
                    .split(toolCallHandler.toolCallPrefix).last()
                    .removeSuffix(toolCallHandler.toolCallSuffix).trim()
                val toolResult = toolCallHandler.handle(toolContent, tools!!)
                currentGeneration.append("${inputFormat.assistantEndHeader}\n<tool_result>$toolResult</tool_result>${inputFormat.assistantHeader}")
                if (!currentGeneration.contains("</think>")) currentGeneration.append("<think>")

                llamaEventHandler.onToolUsed(toolCallHandler.findToolName(toolContent))
                llamaEventHandler.onNewToken("<tool_result>$toolResult</tool_result>")

                requestBody = buildJsonObject {
                    put("stream", true)
                    put("prompt", finalFormattedQuery + currentGeneration)
                    put("temperature", 0.6)
                    put("top_p", 0.95)
                    put("stop", buildJsonArray {
                        add(inputFormat.assistantEndHeader)
                    })
                }
            }
        }
    }

    private fun sendRequest(requestBody: JsonObject, handler: (String) -> RequestAction) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(Json.Default.encodeToString(requestBody).toRequestBody())
            .build()

        val response = okHttpClient.newCall(request).execute()
        val source: BufferedSource = response.body.source()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (!line.startsWith("data:")) continue

            //println(line)

            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break

            val json = Json.Default.decodeFromString<JsonObject>(payload)
            val token = json["choices"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content

            when (handler(token)) {
                RequestAction.Continue -> continue
                RequestAction.Break -> {
                    response.close()
                    break
                }
            }
        }
    }

    private fun formatContext(context: LlamaContext, allowThinking: Boolean, inputFormat: LlamaInputFormat): String {
        val result = StringBuilder()
        context.messages.forEach {
            result.append(when(it.first) {
                LlamaRole.User -> "${inputFormat.userHeader}${it.second}${inputFormat.userEndHeader}"
                LlamaRole.System -> "${inputFormat.systemHeader}${it.second}${inputFormat.systemEndHeader}"
                LlamaRole.Assistant -> "${inputFormat.assistantHeader}${it.second}${inputFormat.assistantEndHeader}"
                else -> throw IllegalArgumentException()
            })
        }
        result.append(inputFormat.assistantHeader)
        if (allowThinking) result.append("<think>")
        return result.toString()
    }

    // TODO: maybe do something more useful here, or remove once context is handled better
    private fun createDefaultContext(toolsEnabled: Boolean): LlamaContext = LlamaContext(buildList {
        add(Pair(LlamaRole.System, buildSystem(toolsEnabled)))
    })

    private fun buildSystem(toolsEnabled: Boolean): String = "You are a helpful assistant.${
        if (toolsEnabled) "\n${toolCallHandler.toolUsePrompt}" + buildToolList()
        else "" 
    }"

    private fun buildToolList(): String {
        val result = StringBuilder()
        result.append("\nTools available:")

        for (tool in tools!!) {
            result.append("\n" + tool.value.description)
        }

        return result.toString()
    }
}