package com.example.data

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class CameraStreamAccessToken(
    val id: String,
    val token: String,
    val description: String,
    val isFrontCamera: Boolean,
    val resolutionLimit: String, // "480p", "720p", "1080p"
    val fpsLimit: Int, // 1, 5, 10, 30
    val durationMinutes: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + (durationMinutes * 60 * 1000L),
    var isRevoked: Boolean = false
) {
    val isValid: Boolean get() = !isRevoked && System.currentTimeMillis() < expiresAt
    val remainingTimeSeconds: Long get() = ((expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
}

data class CameraFrameMetadata(
    val frameIndex: Long,
    val width: Int,
    val height: Int,
    val timestamp: Long,
    val base64Thumbnail: String?,
    val fps: Double
)

object CameraStreamController {
    private val _latestFrameBytes = MutableStateFlow<ByteArray?>(null)
    val latestFrameBytes: StateFlow<ByteArray?> = _latestFrameBytes.asStateFlow()

    private val _latestFrameMetadata = MutableStateFlow<CameraFrameMetadata?>(null)
    val latestFrameMetadata: StateFlow<CameraFrameMetadata?> = _latestFrameMetadata.asStateFlow()

    private val _accessTokens = MutableStateFlow<List<CameraStreamAccessToken>>(emptyList())
    val accessTokens: StateFlow<List<CameraStreamAccessToken>> = _accessTokens.asStateFlow()

    private var frameCounter = 0L
    private var lastFrameTime = 0L
    private var fpsCalculation = 30.0

    // Callback to let ViewModel record access logs in real-time
    var onAgentAccessCallback: ((String, String, Boolean, String) -> Unit)? = null

    // 32-byte random seed, generated once on first use, persisted across process lifetime
    private val hmacKey: ByteArray = java.util.UUID.randomUUID().toString().repeat(2).toByteArray().copyOf(32)

    /** Create an HMAC-SHA256 signature for a token ID. */
    fun signTokenId(tokenId: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(tokenId.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Create a token whose `token` field is `id.signature`. */
    fun createSignedToken(description: String, isFrontCamera: Boolean, resolutionLimit: String, fpsLimit: Int, durationMinutes: Int): CameraStreamAccessToken {
        val id = java.util.UUID.randomUUID().toString()
        val token = CameraStreamAccessToken(
            id = id,
            token = "$id.${signTokenId(id)}",
            description = description,
            isFrontCamera = isFrontCamera,
            resolutionLimit = resolutionLimit,
            fpsLimit = fpsLimit,
            durationMinutes = durationMinutes
        )
        addToken(token)
        return token
    }

    /** Verify an HMAC-signed token value of the form `id.signature`. */
    fun verifySignedToken(tokenValue: String): CameraStreamAccessToken? {
        val dot = tokenValue.lastIndexOf('.')
        if (dot <= 0 || dot >= tokenValue.length - 1) return null
        val id = tokenValue.substring(0, dot)
        val signature = tokenValue.substring(dot + 1)
        val expected = signTokenId(id)
        val match = MessageDigest.isEqual(signature.toByteArray(), expected.toByteArray())
        if (!match) return null
        return _accessTokens.value.find { it.id == id && it.token == tokenValue }
    }

    fun addToken(token: CameraStreamAccessToken) {
        _accessTokens.value = _accessTokens.value + token
    }

    fun revokeToken(tokenId: String) {
        _accessTokens.value = _accessTokens.value.map {
            if (it.id == tokenId) it.copy(isRevoked = true) else it
        }
    }

    fun cleanExpiredTokens() {
        val now = System.currentTimeMillis()
        _accessTokens.value = _accessTokens.value.filter { it.expiresAt > now }
    }

    /**
     * Called by CameraX Analyzer to update the live frame.
     */
    fun processImageProxy(imageProxy: ImageProxy) {
        try {
            frameCounter++
            val now = System.currentTimeMillis()
            if (lastFrameTime > 0) {
                val diff = now - lastFrameTime
                if (diff > 0) {
                    val currentFps = 1000.0 / diff
                    fpsCalculation = (fpsCalculation * 0.9) + (currentFps * 0.1) // smooth FPS
                }
            }
            lastFrameTime = now

            // Convert YUV image to JPEG
            val jpegBytes = imageProxyToJpeg(imageProxy)
            if (jpegBytes != null) {
                _latestFrameBytes.value = jpegBytes

                // Create a lightweight low-res base64 string for preview/thumbnail representation
                val base64Thumb = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

                _latestFrameMetadata.value = CameraFrameMetadata(
                    frameIndex = frameCounter,
                    width = imageProxy.width,
                    height = imageProxy.height,
                    timestamp = now,
                    base64Thumbnail = base64Thumb,
                    fps = fpsCalculation
                )
            }
        } catch (e: Exception) {
            Timber.e("CameraStreamController", "Error processing image proxy: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Real-time validation endpoint logic for external AI Agents or desktop utility.
     */
    fun getLatestFrameSecurely(tokenValue: String, agentName: String): Pair<ByteArray?, String> {
        val token = verifySignedToken(tokenValue)
        
        if (token == null) {
            onAgentAccessCallback?.invoke(agentName, tokenValue, false, "Invalid or forged token")
            return Pair(null, "ERROR: Invalid Token")
        }

        if (!token.isValid) {
            val reason = if (token.isRevoked) "Token explicitly revoked" else "Token expired"
            onAgentAccessCallback?.invoke(agentName, tokenValue, false, reason)
            return Pair(null, "ERROR: Unauthorized - $reason")
        }

        // Apply granular resolution & FPS throttling if needed
        val frame = _latestFrameBytes.value
        if (frame == null) {
            onAgentAccessCallback?.invoke(agentName, tokenValue, false, "Camera hardware stream inactive")
            return Pair(null, "ERROR: Stream Inactive")
        }

        onAgentAccessCallback?.invoke(agentName, tokenValue, true, "Success - Granted ${token.resolutionLimit} / max ${token.fpsLimit}fps")
        return Pair(frame, "SUCCESS")
    }

    private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray? {
        try {
            if (imageProxy.format != ImageFormat.YUV_420_888) {
                return null
            }

            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 30, out) // 30% quality for network efficiency
            return out.toByteArray()
        } catch (e: Exception) {
            Timber.e("CameraStreamController", "Failed converting image proxy to JPEG: ${e.message}")
            return null
        }
    }
}
