package org.nitish.project.sharedrop

import kotlinx.coroutines.runBlocking
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import android.util.Log
actual class FileSender {

    // Thread responsible for running the file transfer
    // without blocking the Android UI.
    private var transferThread: Thread? = null

    // The socket currently being used for the transfer.
    // We keep a reference so cancel() can close it.
    private var currentSocket: Socket? = null

    actual fun sendFile(
        host: String,
        port: Int,
        absolutePath: String,
        onProgress: (Float) -> Unit,
        onResult: (Boolean) -> Unit
    ) {

        // ---------------------------------------------------------
        // DEBUG LOGS
        // These will appear in Android Studio Logcat.
        // ---------------------------------------------------------

        Log.d("SENDER", "sendFile() called")
        println("SENDER: host = $host")
        println("SENDER: port = $port")
        println("SENDER: path = $absolutePath")


        // Create a background thread for the transfer.
        transferThread = Thread {

            println("SENDER: thread started")

            runBlocking {

                try {

                    // -------------------------------------------------
                    // Connect to the receiver.
                    // -------------------------------------------------

                    Log.d("SENDER", "connecting...")

                    val socket = Socket(host, port)

                    println("SENDER: connected!")

                    // Save the socket so cancel() can close it.
                    currentSocket = socket


                    val input =
                        DataInputStream(socket.getInputStream())

                    val output =
                        DataOutputStream(socket.getOutputStream())


                    // -------------------------------------------------
                    // 1. HANDSHAKE
                    //
                    // The receiver sends us its public key first.
                    // -------------------------------------------------

                    println(
                        "SENDER: waiting for receiver public key..."
                    )

                    val receiverPubKeySize =
                        input.readInt()

                    println(
                        "SENDER: received public key size = $receiverPubKeySize"
                    )

                    val receiverPubKey =
                        ByteArray(receiverPubKeySize)

                    input.readFully(receiverPubKey)


                    // -------------------------------------------------
                    // 2. Generate our key pair and send our public key.
                    // -------------------------------------------------

                    println(
                        "SENDER: generating key pair..."
                    )

                    val keyPair =
                        CryptoEngine.generateKeyPair()

                    output.writeInt(
                        keyPair.publicKeyBytes.size
                    )

                    output.write(
                        keyPair.publicKeyBytes
                    )

                    output.flush()

                    println(
                        "SENDER: public key sent"
                    )


                    // -------------------------------------------------
                    // 3. Derive the shared AES key.
                    // -------------------------------------------------

                    println(
                        "SENDER: deriving AES key..."
                    )

                    val aesKey =
                        CryptoEngine.deriveAesKey(
                            keyPair.privateKey,
                            receiverPubKey
                        )


                    // -------------------------------------------------
                    // 4. Prepare the file.
                    // -------------------------------------------------

                    val inputFile =
                        File(absolutePath)

                    val totalBytes =
                        inputFile.length()

                    println(
                        "SENDER: file = ${inputFile.name}"
                    )

                    println(
                        "SENDER: size = $totalBytes bytes"
                    )


                    // -------------------------------------------------
                    // 5. Send encrypted filename.
                    // -------------------------------------------------

                    val encFileName =
                        CryptoEngine.encrypt(
                            aesKey,
                            inputFile.name.toByteArray()
                        )

                    output.writeInt(
                        encFileName.size
                    )

                    output.write(
                        encFileName
                    )


                    // -------------------------------------------------
                    // 6. Send encrypted file size.
                    // -------------------------------------------------

                    val encFileSize =
                        CryptoEngine.encrypt(
                            aesKey,
                            totalBytes.toString().toByteArray()
                        )

                    output.writeInt(
                        encFileSize.size
                    )

                    output.write(
                        encFileSize
                    )

                    output.flush()


                    println(
                        "SENDER: metadata sent"
                    )


                    // -------------------------------------------------
                    // 7. Send the file in encrypted 8 KB chunks.
                    // -------------------------------------------------

                    var bytesSent = 0L

                    inputFile.inputStream().use { inputStream ->

                        val buffer =
                            ByteArray(8192)

                        var bytesRead =
                            inputStream.read(buffer)

                        while (bytesRead != -1) {

                            // Use only the bytes that were actually read.
                            val chunkData =
                                if (bytesRead == buffer.size) {
                                    buffer
                                } else {
                                    buffer.copyOfRange(
                                        0,
                                        bytesRead
                                    )
                                }


                            // Encrypt this chunk.
                            val encryptedChunk =
                                CryptoEngine.encrypt(
                                    aesKey,
                                    chunkData
                                )


                            // Send encrypted chunk size.
                            output.writeInt(
                                encryptedChunk.size
                            )

                            // Send encrypted chunk.
                            output.write(
                                encryptedChunk
                            )


                            // Update progress.
                            bytesSent += bytesRead

                            onProgress(
                                bytesSent.toFloat() /
                                        totalBytes.toFloat()
                            )
                            bytesRead =
                                inputStream.read(buffer)
                        }
                    }


                    // Make sure everything has been sent.
                    output.flush()

                    println(
                        "SENDER: file transfer completed"
                    )


                    socket.close()

                    onResult(true)


                } catch (e: Exception) {

                    // This can also happen when cancel()
                    // closes the socket.
                    println(
                        "SENDER: transfer failed/cancelled"
                    )

                    e.printStackTrace()

                    onResult(false)


                } finally {

                    // The transfer has finished or was canceled.
                    transferThread = null
                    currentSocket = null

                    println(
                        "SENDER: cleanup completed"
                    )
                }
            }
        }


        // IMPORTANT:
        // Start the thread OUTSIDE the Thread block.
        transferThread?.start()
    }


    actual fun cancel() {

        println(
            "SENDER: cancel() called"
        )

        // Closing the socket interrupts any blocking
        // network operation such as read/write.
        try {
            currentSocket?.close()
        } catch (_: Exception) {
        }

        // Also interrupt the transfer thread.
        transferThread?.interrupt()

        currentSocket = null
        transferThread = null

        println(
            "SENDER: cancel completed"
        )
    }
}