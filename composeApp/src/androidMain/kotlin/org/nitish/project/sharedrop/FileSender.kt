package org.nitish.project.sharedrop

import kotlinx.coroutines.runBlocking
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket

actual class FileSender {

    private var transferThread: Thread? = null
    private var currentSocket: Socket? = null
    actual fun sendFile(
        host: String,
        port: Int,
        absolutePath: String,
        onProgress: (Float) -> Unit,
        onResult: (Boolean) -> Unit
    ) {

        transferThread = Thread {
            runBlocking {
                try {
                    val socket = Socket(host, port)
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    currentSocket = socket

                    // 1. Handshake: Receive remote public key
                    val receiverPubKeySize = input.readInt()
                    val receiverPubKey = ByteArray(receiverPubKeySize)
                    input.readFully(receiverPubKey)

                    // 2. Handshake: Generate and send local public key
                    val keyPair = CryptoEngine.generateKeyPair()
                    output.writeInt(keyPair.publicKeyBytes.size)
                    output.write(keyPair.publicKeyBytes)
                    output.flush()

                    // 3. Derive End-to-End AES key
                    val aesKey = CryptoEngine.deriveAesKey(keyPair.privateKey, receiverPubKey)

                    val inputFile = File(absolutePath)
                    val totalBytes = inputFile.length()

                    // 4. Send encrypted metadata (Filename & Size)
                    val encFileName = CryptoEngine.encrypt(aesKey, inputFile.name.toByteArray())
                    output.writeInt(encFileName.size)
                    output.write(encFileName)

                    val encFileSize =
                        CryptoEngine.encrypt(aesKey, totalBytes.toString().toByteArray())
                    output.writeInt(encFileSize.size)
                    output.write(encFileSize)
                    output.flush()

                    // 5. Stream file in encrypted 8KB chunks to prevent OOM
                    var bytesSent = 0L
                    val inputStream = inputFile.inputStream()
                    val buffer = ByteArray(8192)

                    var bytesRead = inputStream.read(buffer)
                    while (bytesRead != -1) {
                        val chunkData =
                            if (bytesRead == buffer.size) buffer else buffer.copyOfRange(
                                0,
                                bytesRead
                            )
                        val encryptedChunk = CryptoEngine.encrypt(aesKey, chunkData)

                        // Send chunk size followed by the encrypted payload
                        output.writeInt(encryptedChunk.size)
                        output.write(encryptedChunk)

                        bytesSent += bytesRead
                        onProgress(bytesSent.toFloat() / totalBytes.toFloat())

                        bytesRead = inputStream.read(buffer)
                    }

                    output.flush()
                    socket.close()
                    onResult(true)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(false)
                } finally {

                    // The transfer has finished or been canceled.
                    transferThread = null
                    currentSocket = null
                }
            }
        }
        transferThread?.start()
    }

    actual fun cancel() {
        try {
            currentSocket?.close()
        } catch (_: Exception) {
        }

        transferThread?.interrupt()

        currentSocket = null
        transferThread = null
    }
}