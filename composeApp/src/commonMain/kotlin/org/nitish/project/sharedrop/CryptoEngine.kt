package org.nitish.project.sharedrop
// Caution: it Lacks Authentication, We need to add KDF / HKDF
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH

object CryptoEngine {
    class KeyPairResult(val privateKey: ECDH.PrivateKey, val publicKeyBytes: ByteArray)

    // Generates ECDH key pair (NIST P-256) for the handshake
    suspend fun generateKeyPair(): KeyPairResult {
        val keyPair = CryptographyProvider.Default
            .get(ECDH)
            .keyPairGenerator(curve = EC.Curve.P256)
            .generateKey()
        return KeyPairResult(
            privateKey = keyPair.privateKey,
            publicKeyBytes = keyPair.publicKey.encodeToByteArray(format = EC.PublicKey.Format.RAW)
        )
    }

    // Derives a shared secret (AES key) using the local private key and remote public key
    suspend fun deriveAesKey(
        privateKey: ECDH.PrivateKey,
        remotePublicKeyBytes: ByteArray
    ): ByteArray {
        // Decoding the remotePublicKeyBytes from ByteArray to the public key object
        val remotePublicKey =
            CryptographyProvider.Default
                .get(ECDH)
                .publicKeyDecoder(curve = EC.Curve.P256)
                .decodeFromByteArray(format = EC.PublicKey.Format.RAW, bytes = remotePublicKeyBytes)
        // Actual key ECDH operation
        return privateKey.sharedSecretGenerator()
            .generateSharedSecretToByteArray(other = remotePublicKey)
    }

    // Encrypts payload using AES-GCM.
    // The library uses Implicit IV: automatically generates the nonce and appends the MAC tag.
    suspend fun encrypt(keyBytes: ByteArray, data: ByteArray): ByteArray {
        // currently key (keyBytes) is in the byteArray format but encrypt need key in the
        // AES key object so val key = ... does that job
        val key = CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        return key.cipher().encrypt(data)
    }

    // Decrypts payload using AES-GCM.
    // Automatically extracts the Implicit IV from the first 12 bytes and verifies the MAC tag.
    suspend fun decrypt(keyBytes: ByteArray, encryptedPayload: ByteArray): ByteArray {
        // currently key (keyBytes) is in the byteArray format but decrypt need key in the
        // AES key object so val key = ... does that job
        val key = CryptographyProvider.Default
            .get(AES.GCM)
            .keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        return key.cipher().decrypt(encryptedPayload)
    }
}

/* generateKeyPair()
        ↓
"Create my ECDH public/private keys."

deriveAesKey()
        ↓
"Use my private key + their public key
 to calculate our shared secret."

encrypt()
        ↓
"Use that secret as the AES key
 and encrypt data with AES-GCM."

decrypt()
        ↓
"Use the same AES key to verify and
 decrypt the received data."*/
