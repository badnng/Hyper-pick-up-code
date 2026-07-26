package com.Badnng.moe.helper

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object BackupSecretCrypto {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val FORMAT_VERSION = 1
    private val MAGIC = byteArrayOf('H'.code.toByte(), 'N'.code.toByte(), 'S'.code.toByte(), 'K'.code.toByte())

    fun encrypt(plainText: ByteArray, password: String): ByteArray {
        require(password.length >= 8) { "备份密码至少需要 8 位" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        val cipherText = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            doFinal(plainText)
        }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { envelope ->
            envelope.write(MAGIC)
            envelope.writeByte(FORMAT_VERSION)
            envelope.writeInt(ITERATIONS)
            envelope.writeByte(salt.size)
            envelope.writeByte(iv.size)
            envelope.writeInt(cipherText.size)
            envelope.write(salt)
            envelope.write(iv)
            envelope.write(cipherText)
        }
        return output.toByteArray()
    }

    fun decrypt(envelopeBytes: ByteArray, password: String): ByteArray {
        if (password.length < 8) throw IllegalArgumentException("备份密码至少需要 8 位")
        return try {
            val input = DataInputStream(ByteArrayInputStream(envelopeBytes))
            val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC)) { "密钥加密区格式无效" }
            require(input.readUnsignedByte() == FORMAT_VERSION) { "不支持的密钥加密格式" }
            val iterations = input.readInt()
            require(iterations in 100_000..1_000_000) { "密钥派生参数无效" }
            val saltSize = input.readUnsignedByte()
            val ivSize = input.readUnsignedByte()
            val cipherTextSize = input.readInt()
            require(saltSize == SALT_BYTES && ivSize == IV_BYTES) { "密钥加密参数无效" }
            require(cipherTextSize >= TAG_BITS / 8 && cipherTextSize <= input.available() - saltSize - ivSize) {
                "密钥加密区长度无效"
            }
            val salt = ByteArray(saltSize).also { input.readFully(it) }
            val iv = ByteArray(ivSize).also { input.readFully(it) }
            val cipherText = ByteArray(cipherTextSize).also { input.readFully(it) }
            require(input.available() == 0) { "密钥加密区包含多余数据" }
            val key = deriveKey(password, salt, iterations)
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                doFinal(cipherText)
            }
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("备份密码错误或密钥数据已损坏")
        } catch (_: EOFException) {
            throw IllegalArgumentException("密钥加密区格式无效")
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("备份密码错误或密钥数据已损坏")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val passwordChars = password.toCharArray()
        val specification = PBEKeySpec(passwordChars, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(specification)
                .encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            specification.clearPassword()
            passwordChars.fill('\u0000')
        }
    }
}
