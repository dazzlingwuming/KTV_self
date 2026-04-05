package com.ktv.stb.ui.local

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ktv.stb.R
import com.ktv.stb.app.KtvApplication
import com.ktv.stb.common.util.TitleSanitizer
import com.ktv.stb.databinding.ActivityLocalLibraryBinding
import com.ktv.stb.server.route.toBroadcastPayload
import com.ktv.stb.storage.FileStatusResult

class LocalLibraryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLocalLibraryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    private fun refreshContent() {
        val appContainer = (application as KtvApplication).appContainer
        val list = appContainer.localFileStatusService.listAll()
        binding.localListContainer.removeAllViews()

        if (list.isEmpty()) {
            binding.localListContainer.addView(buildEmptyText("暂无本地资源"))
            return
        }

        list.forEachIndexed { index, item ->
            binding.localListContainer.addView(buildLocalCard(item, index == 0))
        }
    }

    private fun buildLocalCard(item: FileStatusResult, requestInitialFocus: Boolean): View {
        val appContainer = (application as KtvApplication).appContainer
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(0xFF181A1F.toInt())
            strokeColor = 0xFF2A2D33.toInt()
            strokeWidth = dp(1)
            isFocusable = false
            isClickable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(16) }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }

        content.addView(buildText(TitleSanitizer.sanitize(item.title), 24f, 0xFFFFFFFF.toInt(), true))

        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
        }
        badgeRow.addView(buildBadge(item.downloadStatus.uppercase(), badgeForStatus(item.downloadStatus)))
        badgeRow.addView(space(10))
        badgeRow.addView(buildBadge(item.separateStatus.uppercase(), badgeForStatus(item.separateStatus)))
        badgeRow.addView(space(10))
        badgeRow.addView(buildBadge(if (item.isValid) "有效" else "无效", if (item.isValid) R.drawable.bg_badge_success else R.drawable.bg_badge_warning))
        content.addView(badgeRow)

        content.addView(buildText("文件大小: ${formatBytes(item.fileSize)} / 视频轨 ${if (item.videoTrackExists) "有" else "无"} / 音频轨 ${if (item.audioTrackExists) "有" else "无"}", 16f, 0xFFD7DCE4.toInt(), false, top = 12))
        content.addView(buildText("时长 ${formatTime(item.durationMs)}${if (item.width != null && item.height != null) " / ${item.width}x${item.height}" else ""}", 16f, 0xFFD7DCE4.toInt(), false, top = 8))
        content.addView(buildText("分离结果 ${if (!item.accompanimentPath.isNullOrBlank()) "伴奏已生成" else "伴奏未生成"} / ${if (!item.vocalPath.isNullOrBlank()) "人声已生成" else "人声未生成"}", 16f, 0xFFD7DCE4.toInt(), false, top = 8))
        if (!item.errorCode.isNullOrBlank() || !item.errorMessage.isNullOrBlank()) {
            val msg = buildString {
                append("提示: ")
                append(item.errorCode ?: "-")
                if (!item.errorMessage.isNullOrBlank()) append(" / ${item.errorMessage}")
            }
            content.addView(buildText(msg, 15f, 0xFFFFC36B.toInt(), false, top = 8))
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) }
        }

        val localOrderButton = buildActionButton("本地点歌").apply {
            isEnabled = item.isValid && item.downloadStatus == "success"
            alpha = if (isEnabled) 1f else 0.4f
            setOnClickListener {
                Log.d("LocalLibrary", "local_order_clicked songId=${item.songId} title=${item.title}")
                if (appContainer.queueManager.createLocalOrder(item.songId, "tv-local", "TV Local")) {
                    appContainer.webSocketHub.broadcastQueueUpdated(appContainer.queueManager.getQueueSnapshot().toBroadcastPayload())
                    appContainer.ktvPlayerManager.tryStartPlaybackFromQueue()
                    refreshContent()
                }
            }
        }
        buttonRow.addView(localOrderButton)

        val separateButton = buildActionButton("伴奏分离").apply {
            isEnabled = item.downloadStatus == "success" && item.separateStatus != "processing"
            alpha = if (isEnabled) 1f else 0.4f
            setOnClickListener {
                appContainer.separateTaskManager.enqueue(item.songId)
                refreshContent()
            }
        }
        buttonRow.addView(separateButton)

        val deleteButton = buildActionButton("删除缓存").apply {
            setOnClickListener {
                Log.d("LocalLibrary", "local_delete_clicked songId=${item.songId} title=${item.title}")
                val deleted = appContainer.localFileStatusService.deleteSong(item.songId)
                if (deleted) {
                    appContainer.queueManager.removeSongFromQueue(item.songId)
                    appContainer.queueManager.markSongUnavailable(item.songId, "LOCAL_FILE_DELETED", "local cache deleted")
                    appContainer.ktvPlayerManager.handleUnavailableSong(item.songId)
                    val status = appContainer.localFileStatusService.getStatus(item.songId)
                    appContainer.webSocketHub.broadcastDownloadUpdated(
                        mapOf(
                            "song_id" to status.songId,
                            "source_id" to status.sourceId,
                            "title" to status.title,
                            "download_status" to status.downloadStatus,
                            "progress" to 0,
                            "file_path" to status.filePath,
                            "error_code" to status.errorCode,
                            "error_message" to status.errorMessage,
                        ),
                    )
                    appContainer.webSocketHub.broadcastQueueUpdated(appContainer.queueManager.getQueueSnapshot().toBroadcastPayload())
                    refreshContent()
                }
            }
        }
        buttonRow.addView(deleteButton)

        val fileStatusButton = buildActionButton("file-status").apply {
            setOnClickListener {
                val status = appContainer.localFileStatusService.getStatus(item.songId)
                android.widget.Toast.makeText(
                    this@LocalLibraryActivity,
                    "download=${status.downloadStatus}, separate=${status.separateStatus}, valid=${status.isValid}",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
        buttonRow.addView(fileStatusButton)

        content.addView(buttonRow)
        card.addView(content)

        val focusWatcher = View.OnFocusChangeListener { _, _ ->
            val active = localOrderButton.isFocused || separateButton.isFocused || deleteButton.isFocused || fileStatusButton.isFocused
            card.strokeWidth = if (active) dp(3) else dp(1)
            card.strokeColor = if (active) 0xFF8CE4C2.toInt() else 0xFF2A2D33.toInt()
            card.scaleX = if (active) 1.02f else 1f
            card.scaleY = if (active) 1.02f else 1f
            card.cardElevation = if (active) dp(8).toFloat() else 0f
        }
        listOf(localOrderButton, separateButton, deleteButton, fileStatusButton).forEach { it.onFocusChangeListener = focusWatcher }

        if (requestInitialFocus) {
            val initial = when {
                localOrderButton.isEnabled -> localOrderButton
                separateButton.isEnabled -> separateButton
                else -> deleteButton
            }
            card.post { initial.requestFocus() }
        }
        return card
    }

    private fun buildActionButton(text: String): MaterialButton {
        return MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            strokeColor = android.content.res.ColorStateList.valueOf(0xFF8CE4C2.toInt())
            strokeWidth = dp(1)
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(12) }
        }
    }

    private fun buildBadge(text: String, backgroundRes: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFFE7EEF8.toInt())
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(backgroundRes)
        }
    }

    private fun buildText(text: String?, sizeSp: Float, color: Int, bold: Boolean, top: Int = 0): TextView {
        return TextView(this).apply {
            this.text = text ?: "-"
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(top) }
        }
    }

    private fun buildEmptyText(text: String): TextView = buildText(text, 18f, 0xFFAAB1BD.toInt(), false)

    private fun badgeForStatus(status: String): Int {
        return when (status.lowercase()) {
            "success", "playing", "valid" -> R.drawable.bg_badge_success
            "failed", "error", "invalid" -> R.drawable.bg_badge_error
            "downloading", "buffering", "skipped", "pending", "paused", "processing" -> R.drawable.bg_badge_warning
            else -> R.drawable.bg_badge_idle
        }
    }

    private fun formatBytes(bytes: Long): String {
        val value = bytes.coerceAtLeast(0L).toDouble()
        return when {
            value < 1024 -> "${value.toLong()} B"
            value < 1024 * 1024 -> String.format("%.1f KB", value / 1024)
            value < 1024 * 1024 * 1024 -> String.format("%.1f MB", value / 1024 / 1024)
            else -> String.format("%.1f GB", value / 1024 / 1024 / 1024)
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun space(widthDp: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
