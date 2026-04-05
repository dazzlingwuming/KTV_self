package com.ktv.stb.qrcode

import java.security.MessageDigest

class QrPayloadSigner {
    fun sign(rawPayload: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(rawPayload.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
