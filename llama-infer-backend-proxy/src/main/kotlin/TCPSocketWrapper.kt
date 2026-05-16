package org.sbx

import org.sbx.control.TCPClientData
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket

class TCPSocketWrapper(port: Int, val initializer: TCPSocketWrapper.(TCPClientData) -> Unit, val handler: TCPSocketWrapper.(Char, String, TCPClientData) -> Unit) {
    private val server: ServerSocket = ServerSocket(port)

    init {
        println("Server running on port ${server.localPort}")
    }

    fun sendClientPacket(action: Char, payload: String, output: DataOutputStream) {
        val string = "$action$payload"
        val stringBytes = string.toByteArray()
        output.writeInt(stringBytes.size)
        output.write(stringBytes)
        output.flush()
    }

    fun start() {
        while (true) {
            val client = server.accept()
            println("Client connected: ${client.inetAddress}")

            Thread {
                val socketClient: TCPClientData
                client.use { socket ->
                    val input = DataInputStream(
                        BufferedInputStream(socket.inputStream)
                    )

                    val output = DataOutputStream(
                        BufferedOutputStream(socket.outputStream)
                    )

                    socketClient = TCPClientData(output, input, 0u)

                    initializer(socketClient)

                    try {
                        while (true) {
                            val length = input.readInt()
                            val data = ByteArray(length)
                            input.readFully(data)

                            val dataString = data.toString(Charsets.UTF_8)
                            val actionTypeParam = dataString[0]
                            val dataPacket = dataString.drop(1)
                            println("Action: $actionTypeParam")
                            println("Data: $dataPacket")

                            handler(actionTypeParam, dataPacket, socketClient)
                        }
                    } catch (_: EOFException) {
                        println("Client disconnected")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }.start()
        }
    }
}