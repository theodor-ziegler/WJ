package org.sbx.control

import java.io.DataInputStream
import java.io.DataOutputStream

data class TCPClientData(val output: DataOutputStream, val input: DataInputStream, var context: ULong)
