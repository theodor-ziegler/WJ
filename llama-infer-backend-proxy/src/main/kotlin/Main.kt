package org.sbx

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.sbx.llama.chat.LlamaApiInferChat
import org.sbx.llama.chat.LlamaEventHandlerChat
import org.sbx.llama.chat.functions.LlamaToolChat
import org.sbx.llama.chat.functions.LlamaToolChatCollection
import org.sbx.llama.chat.functions.LlamaToolParameter
import org.sbx.llama.chat.functions.LlamaToolParameterType
import java.io.File
import kotlin.time.Clock

fun main() {
    val llama = LlamaApiInferChat(
        "http://localhost:8492/v1/chat/completions",
        LlamaToolChatCollection(
            mapOf(
                "clock" to object : LlamaToolChat {
                    override val name: String = "clock"
                    override val description: String = "Get the current time (year-month-day hour:minute:second)"
                    override val parameters: List<LlamaToolParameter> = listOf()
                    override val requiredParameters: List<String> = listOf()

                    override fun execute(toolCallData: String): String {
                        val now = Clock.System.now()
                        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
                        return "%04d-%02d-%02d %02d:%02d:%02d".format(
                            local.year,
                            local.month.ordinal + 1,
                            local.day,
                            local.hour,
                            local.minute,
                            local.second
                        )
                    }
                },
                "list-data" to object : LlamaToolChat {
                    override val name: String = "list-data"
                    override val parameters: List<LlamaToolParameter> = listOf(LlamaToolParameter(
                        "path",
                        LlamaToolParameterType.String,
                        "The search path, must not use directory up commands (..). Separate folders with /, but do not prefix with / or ./, as this will result in an invalid path. The full file path must be provided beginning from the database root directory."
                    ))
                    override val requiredParameters: List<String> = listOf("path")
                    override val description: String = "List the available options in a specific data search path, setting the path to an empty string is equivalent to the root data folder."

                    override fun execute(toolCallData: String): String {
                        println(toolCallData)
                        try {
                            val path = Json.decodeFromString<JsonObject>(toolCallData)["path"]!!.jsonPrimitive.content
                            if (path.split("..").size > 1) return "Directory up (..) is not allowed."

                            val file = File("./auxiliary-data/$path")

                            if (!file.exists()) return "Search path does not exist."
                            if (file.isFile) return "Search path is not a directory."

                            val result = StringBuilder()
                            file.listFiles().forEach {
                                result.append("Name: ${it.name}; Full path: $path/${it.name}\n")
                            }
                            return result.toString()
                        } catch (_: SerializationException) {
                            return "Invalid tool call formatting."
                        } catch (_: NullPointerException) {
                            return "'path' is not set (required)."
                        }
                    }

                },
                "cat" to object : LlamaToolChat {
                    override val name: String = "cat"
                    override val parameters: List<LlamaToolParameter> = listOf(LlamaToolParameter(
                        "file-path",
                        LlamaToolParameterType.String,
                        "The file path to the file you want to see the contents of, directory up command (..) not allowed. Separate folders with /, but do not prefix with / or ./ as this will result in an invalid path. The full file path must be provided beginning from the database root directory."
                    ))
                    override val requiredParameters: List<String> = listOf("file-path")
                    override val description: String = "Return the content of a file on your search path, use the 'list-data' tool to find available files. Behaves similarly to the Linux terminal 'cat' command."

                    override fun execute(toolCallData: String): String {
                        println(toolCallData)
                        try {
                            val path = Json.decodeFromString<JsonObject>(toolCallData)["file-path"]!!.jsonPrimitive.content
                            if (path.split("..").size > 1) return "Directory up (..) is not allowed."

                            val file = File("./auxiliary-data/$path")

                            if (!file.exists()) {
                                println("Does not exist error")
                                return "File at provided path does not exist."
                            }
                            if (file.isDirectory) {
                                println("Directory error")
                                return "Path points to a directory, only files are allowed."
                            }

                            return "<file>${file.readText()}</file>"
                        } catch (_: SerializationException) {
                            return "Invalid tool call formatting."
                        } catch (_: NullPointerException) {
                            println("Null error")
                            return "'file-path' is not set (required)."
                        }
                    }
                }
                )
        )
    )

    val server = TCPSocketWrapper(3737, { client ->
        val currentContext = llama.getContextJson(client.context) ?: buildJsonArray {  }
        sendClientPacket('C', currentContext.toString(), client.output)
    }) { command, packet, client ->
        when(command) {
            'Q' -> {
                llama.infer(packet, client.context, true, object : LlamaEventHandlerChat {
                    override fun onNewToken(token: String) {
                        // Communication note: send tokens with `n` prefix for "normal" over socket
                        sendClientPacket('n', token, client.output)
                    }

                    override fun onEndThinking() {
                        // Big `R` for Reasoning start or end
                        sendClientPacket('R', "end", client.output)
                    }

                    override fun onToolUsed(toolName: String) {
                        // Small `t` for tool call end
                        sendClientPacket('t', toolName, client.output)
                    }

                    override fun onBeginToolCall() {
                        // Big `T` for Tool call begin
                        sendClientPacket('T', "Tool call begins", client.output)
                    }

                    override fun onBeginReasoning() {
                        // Big `R` for Reasoning start or end
                        sendClientPacket('R', "start", client.output)
                    }

                    override fun onReasoningToken(token: String) {
                        // Communication note: send tokens with `r` prefix for "reasoning" over socket
                        sendClientPacket('r', token, client.output)
                    }

                    override fun onFinish() {
                        // `F` for Finished
                        sendClientPacket('F', "Finished", client.output)
                    }
                })
            }

            'L' -> {
                val index = packet.toULongOrNull() ?: 0u
                client.context = index
                sendClientPacket('C',  llama.getContextJson(index).toString(), client.output)
            }

            'P' -> {
                sendClientPacket('P', "pong", client.output)
            }

            'D' -> {
                val index = packet.toULongOrNull()
                if (index == null) {
                    sendClientPacket('D', "Error, invalid index", client.output)
                    return@TCPSocketWrapper
                }

                llama.deleteContextIndex(index)
                sendClientPacket('D', "Deleted", client.output)
            }

            'l' -> {
                sendClientPacket('l', Json.encodeToString(llama.contextStoreList()), client.output)
            }

            't' -> {
                if (packet.length < 16) {
                    sendClientPacket('h', "Invalid packet size", client.output)
                    println("Packet length error")
                    return@TCPSocketWrapper
                }

                val index = packet.take(16).toULongOrNull(radix = 16)

                if (index == null) {
                    sendClientPacket('h', "Invalid packet content", client.output)
                    println("Packet content error")
                    return@TCPSocketWrapper
                }

                val data = packet.drop(16)

                llama.setContextTitle(index, data)
                sendClientPacket('h', "Title set success", client.output)
            }
        }
    }

    server.start()
}