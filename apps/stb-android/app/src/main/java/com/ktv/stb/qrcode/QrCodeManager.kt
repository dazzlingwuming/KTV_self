package com.ktv.stb.qrcode

import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ktv.stb.session.SessionManager

class QrCodeManager(
    private val sessionManager: SessionManager,
    private val signer: QrPayloadSigner,
    private val gson: Gson,
) {
    fun buildQrEntryUrl(): String {
        val session = sessionManager.getCurrentSession()
        return "${sessionManager.resolveWebEntryUrl()}?session_id=${session.sessionId}"
    }

    fun buildQrPayload(): String {
        val session = sessionManager.getCurrentSession()
        val unsigned = QrPayloadUnsigned(
            schema = buildQrEntryUrl(),
            deviceId = session.deviceId,
            sessionId = session.sessionId,
            ip = session.hostIp,
            port = session.port,
            expireAt = session.expireAt,
        )
        return gson.toJson(
            QrPayload(
                schema = unsigned.schema,
                deviceId = unsigned.deviceId,
                sessionId = unsigned.sessionId,
                ip = unsigned.ip,
                port = unsigned.port,
                expireAt = unsigned.expireAt,
                sign = signer.sign(gson.toJson(unsigned)),
            ),
        )
    }

    fun buildQrBitmap(size: Int = 720): Bitmap {
        val entryUrl = buildQrEntryUrl()
        val matrix = QRCodeWriter().encode(entryUrl, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

data class QrPayloadUnsigned(
    @SerializedName("schema") val schema: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("ip") val ip: String,
    @SerializedName("port") val port: Int,
    @SerializedName("expire_at") val expireAt: Long,
)

data class QrPayload(
    @SerializedName("schema") val schema: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("ip") val ip: String,
    @SerializedName("port") val port: Int,
    @SerializedName("expire_at") val expireAt: Long,
    @SerializedName("sign") val sign: String,
)
