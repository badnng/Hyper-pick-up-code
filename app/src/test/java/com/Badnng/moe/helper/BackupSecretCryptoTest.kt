package com.Badnng.moe.helper

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupSecretCryptoTest {
    @Test
    fun encryptDecryptRoundTrip() {
        val plainText = "{\"openai\":\"sk-test-value\"}".toByteArray()

        val encrypted = BackupSecretCrypto.encrypt(plainText, PASSWORD)
        val restored = BackupSecretCrypto.decrypt(encrypted, PASSWORD)

        assertFalse(plainText.contentEquals(encrypted))
        assertArrayEquals(plainText, restored)
    }

    @Test
    fun rejectsWrongPassword() {
        val encrypted = BackupSecretCrypto.encrypt("secret".toByteArray(), PASSWORD)

        assertThrows(IllegalArgumentException::class.java) {
            BackupSecretCrypto.decrypt(encrypted, "wrong-password")
        }
    }

    @Test
    fun rejectsTamperedCipherText() {
        val encrypted = BackupSecretCrypto.encrypt("secret".toByteArray(), PASSWORD)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            BackupSecretCrypto.decrypt(encrypted, PASSWORD)
        }
    }

    private companion object {
        const val PASSWORD = "backup-password"
    }
}
