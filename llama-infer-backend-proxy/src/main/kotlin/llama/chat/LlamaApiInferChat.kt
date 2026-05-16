package org.sbx.llama.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
import org.sbx.llama.LlamaContextStore
import org.sbx.llama.LlamaRole
import org.sbx.llama.chat.functions.LlamaToolChatCollection
import org.sbx.llama.chat.functions.ToolCallAccumulator
import org.sbx.llama.rag.RAGContextFinder
import java.util.concurrent.TimeUnit

class LlamaApiInferChat(
    private val endpoint: String,
    private val tools: LlamaToolChatCollection? = null,
    private val contextPath: String = "./store/Context.json",
    private val contextStore: LlamaContextStore = LlamaContextStore.loadContext(contextPath),
) {
    private val okHttpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val specialTokens = setOf("<|im_end|>")
    private val conversationKeepLimit = 128
    private val tokenLimit = 16384

    fun infer(query: String, contextId: ULong, enableTools: Boolean, eventHandler: LlamaEventHandlerChat) {
        if (contextStore[contextId] == null) {
            contextStore[contextId] = LlamaContext(mutableListOf())
        }

        val contextTempMessages =
            if (contextStore[contextId]!!.messages.size < conversationKeepLimit) contextStore[contextId]!!.messages
            else contextStore[contextId]!!.messages.takeLast(conversationKeepLimit)

        val context = LlamaContext(buildList {
            val dataAugmenter = RAGContextFinder { options, path ->
                val context = mutableListOf<Pair<LlamaRole, String>>()
                context.add(
                    LlamaRole.System to "Your task is to determine what content is relevant to the query out of the given list of options, the user's chat history is provided below.\n" +
                            "Only output the exact option you select.\n" +
                            "Select from the options $options the one that best fits the user's query, or output \"none\" if no option fits. The current search path is $path.\n" +
                            "For example if the user is asking about a specific class, find something that could point towards that class."
                )
                contextTempMessages.forEach {
                    context.add(it.first to it.second)
                }

                context.add(LlamaRole.User to query)


                val grammar = """
root ::= ${(options + listOf("none")).joinToString(" | ") { "\"$it\"" }}
""".trimIndent()

                val requestBody = buildJsonObject {
                    put("stream", true)
                    put("messages", LlamaContext(context).toChatApiMessages())
                    put("temperature", 0.0)
                    put("top_p", 0.95)
                    put("max_new_tokens", tokenLimit)
                    put("grammar", grammar)
                    put("enable_thinking", false)
                }

                val result = StringBuilder()

                sendRequest(requestBody) { token: String, finishReason, tokenType: TokenType, _ ->
                    if (token != "<|im_end|>") result.append(token)

                    if (finishReason == FinishReason.Stop) RequestAction.Break
                    else RequestAction.Continue
                }

                result.toString()
            }

            val ragContext = dataAugmenter.ragContentDetermine()
            //println("RAG: $ragContext")
            add(LlamaRole.System to buildSystem(enableTools) + if (ragContext != "") "\nProvided additional information:\n<begin_file_body>$ragContext<end_file_body>" else "")

            contextTempMessages.forEach {
                add(it.first to it.second)
            }

            add(LlamaRole.User to query)
        })

        val finalResult = StringBuilder()

        sendLlama(context, enableTools, object : LlamaEventHandlerChat {
            override fun onNewToken(token: String) {
                finalResult.append(token)
                eventHandler.onNewToken(token)
            }

            override fun onEndThinking() {
                eventHandler.onEndThinking()
            }

            override fun onToolUsed(toolName: String) {
                eventHandler.onToolUsed(toolName)
            }

            override fun onBeginToolCall() {
                eventHandler.onBeginToolCall()
            }

            override fun onBeginReasoning() {
                eventHandler.onBeginReasoning()
            }

            override fun onReasoningToken(token: String) {
                eventHandler.onReasoningToken(token)
            }

            override fun onFinish() = eventHandler.onFinish()
        })

        contextStore[contextId] = LlamaContext(buildList {
            if (contextStore[contextId]!!.messages.size > conversationKeepLimit) {
                contextStore[contextId]!!.messages.take(contextStore[contextId]!!.messages.size - conversationKeepLimit).forEach {
                    add(it)
                }
            }

            context.messages.forEach {
                if (it.first != LlamaRole.System) add(it)
            }
            add(Pair(LlamaRole.Assistant, finalResult.toString()))
        })

        contextStore.saveContext(contextPath)
    }

    fun contextStoreList(): Map<ULong, String> = contextStore.metadata.data
    fun setContextTitle(index: ULong, title: String) = contextStore.setTitle(index, title)
    fun getContextJson(index: ULong): JsonArray? = contextStore[index]?.toChatApiMessages()

    fun deleteContextIndex(index: ULong) {
        contextStore.delete(index)
        contextStore.saveContext(contextPath)
    }


    private fun sendLlama(context: LlamaContext, enableTools: Boolean, llamaEventHandler: LlamaEventHandlerChat) {
        var cContext = context
        var requestBody = buildJsonObject {
            put("stream", true)
            put("messages", cContext.toChatApiMessages())
            put("temperature", 0.6)
            put("top_p", 0.95)
            put("max_new_tokens", tokenLimit)

            if (enableTools) {
                put("tool_choice", "auto")
                put("tools", tools!!.toJson())
            }
        }

        var reasoning = false
        var done = false
        val unhandledToolCalls = mutableMapOf<String, ToolCallAccumulator>()

        while (!done) {
            //println(cContext.toChatApiMessages())
            sendRequest(requestBody) { token: String, finishReason, tokenType: TokenType, toolCalls ->
                if (finishReason == FinishReason.Stop) {
                    done = true
                    return@sendRequest RequestAction.Break
                }
                if (specialTokens.contains(token)) return@sendRequest RequestAction.Continue

                if (toolCalls != null) {
                    //println(toolCalls)
                    for (call in toolCalls) {
                        val callData = call.jsonObject["function"]!!.jsonObject
                        val name = callData["name"]?.jsonPrimitive?.content
                        val arguments = callData["arguments"]?.jsonPrimitive?.content

                        if (name != null) {
                            llamaEventHandler.onBeginToolCall()
                            unhandledToolCalls[call.jsonObject["index"]!!.jsonPrimitive.content] = ToolCallAccumulator(name, arguments!!)
                        } else {
                            unhandledToolCalls[call.jsonObject["index"]!!.jsonPrimitive.content]!!.arguments += arguments
                        }
                    }
                }

                if (finishReason == FinishReason.ToolCall) {
                    for (call in unhandledToolCalls) {
                        val result = tools!![call.value.name].let { it?.execute(call.value.arguments) } ?: "Tool not found: ${call.value.name}"

                        cContext = LlamaContext(buildList {
                            cContext.messages.forEach {
                                add(Pair(it.first, it.second))
                            }
                            add(LlamaRole.Tool to "{\"name\":\"${call.value.name}\",\"result\":\"$result\"}")
                        })

                        llamaEventHandler.onToolUsed(call.value.name)
                    }

                    unhandledToolCalls.clear()
                }

                if (!reasoning && tokenType == TokenType.Reasoning) {
                    reasoning = true
                    llamaEventHandler.onBeginReasoning()
                } else if (reasoning && tokenType == TokenType.Normal) {
                    reasoning = false
                    llamaEventHandler.onEndThinking()
                }

                when (tokenType) {
                    TokenType.Normal -> llamaEventHandler.onNewToken(token)
                    TokenType.Reasoning -> llamaEventHandler.onReasoningToken(token)
                    TokenType.None -> {} //println("Token none-type")
                }

                //if (finishReason != FinishReason.None) println(finishReason)
                RequestAction.Continue
            }
            if (done) break

            requestBody = buildJsonObject {
                put("stream", true)
                put("messages", cContext.toChatApiMessages())
                put("temperature", 0.6)
                put("top_p", 0.95)
                put("max_new_tokens", tokenLimit)

                if (enableTools) {
                    put("tool_choice", "auto")
                    put("tools", tools!!.toJson())
                }
            }
        }

        llamaEventHandler.onFinish()
    }

    private fun sendRequest(requestBody: JsonObject, handler: (token: String, finishReason: FinishReason, type: TokenType, toolCalls: JsonArray?) -> RequestAction) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(Json.Default.encodeToString(requestBody).toRequestBody())
            .build()

        val response = okHttpClient.newCall(request).execute()
        val source: BufferedSource = response.body.source()

        var firstToken = true

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: continue
            if (!line.startsWith("data:")) continue

            val payload = line.removePrefix("data:").trim()
            if (payload == "[DONE]") break

            val json = Json.Default.decodeFromString<JsonObject>(payload)
            val choices = json["choices"]!!.jsonArray[0].jsonObject
            val finishReason = FinishReason.fromString(choices["finish_reason"]!!.jsonPrimitive.content)
            val delta = choices["delta"]!!.jsonObject
            val token = delta["content"]?.jsonPrimitive?.content
            val toolCalls = delta["tool_calls"]?.jsonArray

            //println(payload)

            val reasoningToken = if (token == null) {
                delta["reasoning_content"]?.jsonPrimitive?.content
            } else null

            if (firstToken && token == "null") {
                firstToken = false
                continue
            }

            val reasoning = token == null

            when (handler(
                if (!reasoning) token else reasoningToken ?: "",
                finishReason,
                if (token == null && reasoningToken == null) TokenType.None else if (reasoning) TokenType.Reasoning else TokenType.Normal,
                toolCalls
            )) {
                RequestAction.Continue -> continue
                RequestAction.Break -> {
                    response.close()
                    break
                }
            }
        }

        source.close()
        response.close()
    }

    private fun buildSystem(enableTools: Boolean): String = "You are a helpful assistant. Your primary objective is to help the user learn without directly showing them the answers." +
            "Do NOT use:\n" +
            "- Markdown code blocks (``` or `)\n" +
            "- Markdown tables or pipe characters (|)\n" +
            "- ASCII tables\n" +
            "- LaTeX\n" +
            "Only use plain text and simple Markdown (bold, lists). Prefer to use Godot BBCode instead of MarkDown as this will result in more accurate rendering." +
            "\nYour database contains a calendar with current important events, each line contains one event formatted like (weekday, day-month-year: title, description)" +
            "\nYour database also contains a lesson plan for the current week, each line is one day and is formatted like (weekday: lesson1; lesson2; lesson3; ...) , with one lesson being (subject, teacher, room)" +
            "\nAdditional helpful information is provided." + if (enableTools) {
        "\nYou have access to the following tools:\n" +
                formatTools() +
                """
     Use tools when:
     - The user asks for real-world data
     - The information is not known to you
     
     If you use a tool, call it instead of answering directly.
     """.trimIndent()
    } else ""

    fun formatTools(): String {
        val result = StringBuilder()

        for (tool in tools!!) {
            result.append("- ${tool.key}\n${tool.value.description}\n")
        }

        return result.toString()
    }
}