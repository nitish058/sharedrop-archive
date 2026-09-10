package org.nitish.project.sharedrop

import kotlinx.coroutines.runBlocking
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket

actual class FileReceiver {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    @Volatile
    private var currentClient: Socket? = null

    actual fun startReceiving(
        port: Int,
        onProgress: (fileName: String, progress: Float) -> Unit,
        onFileReceived: (fileName: String, tempFilePath: String) -> Unit
    ) {
        Thread {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true

                while (isRunning) {
                    val client: Socket = serverSocket?.accept() ?: break
                    currentClient = client

                    Thread {
                        var tempFile: File? = null
                        runBlocking {
                            try {
                                val input = DataInputStream(client.getInputStream())
                                val output = DataOutputStream(client.getOutputStream())

                                // 1. Handshake: Generate and send local public key
                                val keyPair = CryptoEngine.generateKeyPair()
                                output.writeInt(keyPair.publicKeyBytes.size)
                                output.write(keyPair.publicKeyBytes)
                                output.flush()

                                // 2. Handshake: Receive remote public key
                                val senderPubKeySize = input.readInt()
                                val senderPubKey = ByteArray(senderPubKeySize)
                                input.readFully(senderPubKey)

                                // 3. Derive End-to-End AES key
                                val aesKey = CryptoEngine.deriveAesKey(keyPair.privateKey, senderPubKey)

                                // 4. Receive and decrypt metadata
                                val encFileNameSize = input.readInt()
                                val encFileName = ByteArray(encFileNameSize)
                                input.readFully(encFileName)
                                val fileName = String(CryptoEngine.decrypt(aesKey, encFileName))

                                val encFileSizeSize = input.readInt()
                                val encFileSize = ByteArray(encFileSizeSize)
                                input.readFully(encFileSize)
                                val fileLength = String(CryptoEngine.decrypt(aesKey, encFileSize)).toLong()

                                val systemTempDir = System.getProperty("java.io.tmpdir")
                                tempFile = File(systemTempDir, "temp_$fileName")
                                onProgress(fileName, 0f)

                                // 5. Receive, decrypt, and flush chunks to disk to prevent OOM
                                var totalDecryptedRead = 0L
                                tempFile.outputStream().use { fileOut ->
                                    while (totalDecryptedRead < fileLength) {
                                        val encChunkSize = input.readInt()
                                        val encChunk = ByteArray(encChunkSize)

                                        // readFully ensures the whole AES-GCM block is downloaded before decryption
                                        input.readFully(encChunk)

                                        val decChunk = CryptoEngine.decrypt(aesKey, encChunk)
                                        fileOut.write(decChunk)

                                        totalDecryptedRead += decChunk.size
                                        onProgress(fileName, totalDecryptedRead.toFloat() / fileLength.toFloat())
                                    }
                                }

                                onFileReceived(fileName, tempFile.absolutePath)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                tempFile?.delete()
                            } finally {
                                try {
                                    client.close()
                                } catch (_: Exception) {
                                }
                                if (currentClient === client) currentClient = null
                            }
                        }
                    }.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    actual fun cancelCurrentTransfer() {
        try {
            currentClient?.close()
        } catch (_: Exception) {
        }
    }

    actual fun stopReceiving() {
        isRunning = false
        cancelCurrentTransfer()
        serverSocket?.close()
        serverSocket = null
    }
}
