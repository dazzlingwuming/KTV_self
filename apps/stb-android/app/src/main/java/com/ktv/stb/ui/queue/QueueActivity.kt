package com.ktv.stb.ui.queue

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ktv.stb.R
import com.ktv.stb.app.KtvApplication
import com.ktv.stb.databinding.ActivityQueueBinding
import com.ktv.stb.domain.model.QueueItem
import com.ktv.stb.server.route.toBroadcastPayload

class QueueActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQueueBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQueueBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        refreshContent()
    }

    private fun refreshContent() {
        val appContainer = (application as KtvApplication).appContainer
        val snapshot = appContainer.queueManager.getQueueSnapshot()
        binding.currentSongText.text = snapshot.current?.title ?: "暂无播放"
        binding.queueListContainer.removeAllViews()

        val waitingItems = snapshot.items.filter { it.queueStatus != "removed" && it.queueStatus != "finished" }
        if (waitingItems.isEmpty()) {
            binding.queueListContainer.addView(buildEmptyText("当前待播列表为空"))
            return
        }

        waitingItems.forEachIndexed { index, item ->
            binding.queueListContainer.addView(buildQueueCard(item, index == 0))
        }
    }

    private fun buildQueueCard(item: QueueItem, requestFocus: Boolean): View {
        val appContainer = (application as KtvApplication).appContainer
        val card = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(0xFF181A1F.toInt())
            strokeColor = 0xFF2A2D33.toInt()
            strokeWidth = dp(1)
            isFocusable = true
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(16)
            }
            setOnFocusChangeListener { _, hasFocus ->
                strokeWidth = if (hasFocus) dp(3) else dp(1)
                strokeColor = if (hasFocus) 0xFF8CE4C2.toInt() else 0xFF2A2D33.toInt()
                scaleX = if (hasFocus) 1.02f else 1f
                scaleY = if (hasFocus) 1.02f else 1f
                cardElevation = if (hasFocus) dp(8).toFloat() else 0f
            }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = buildText(item.title, 24f, 0xFFFFFFFF.toInt(), true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(title)
        headerRow.addView(buildBadge(item.queueStatus.uppercase(), queueBadge(item.queueStatus)))
        content.addView(headerRow)

        content.addView(buildText("点歌人: ${item.orderedByClientName ?: item.orderedByClientId}", 16f, 0xFFAAB1BD.toInt(), false, top = 10))

        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }
        metaRow.addView(buildBadge(item.downloadStatus.uppercase(), downloadBadge(item.downloadStatus)))
        metaRow.addView(buildText("下载状态", 14f, 0xFF98A0AE.toInt(), false).apply {
            setPadding(dp(10), 0, 0, 0)
        })
        content.addView(metaRow)

        val reason = item.skipReason ?: item.errorMessage
        if (!reason.isNullOrBlank()) {
            content.addView(buildText("提示: ${reason.take(40)}", 15f, 0xFFFFC36B.toInt(), false, top = 10))
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(18) }
        }

        val moveButton = buildActionButton("置顶下一首").apply {
            isEnabled = item.queueStatus == "waiting"
            alpha = if (isEnabled) 1f else 0.4f
            setOnClickListener {
                if (appContainer.queueManager.moveNext(item.queueId)) {
                    appContainer.webSocketHub.broadcastQueueUpdated(appContainer.queueManager.getQueueSnapshot().toBroadcastPayload())
                    refreshContent()
                }
            }
        }
        buttonRow.addView(moveButton)

        val removeButton = buildActionButton("删除").apply {
            isEnabled = item.queueStatus == "waiting"
            alpha = if (isEnabled) 1f else 0.4f
            setOnClickListener {
                if (appContainer.queueManager.removeQueueItem(item.queueId)) {
                    appContainer.webSocketHub.broadcastQueueUpdated(appContainer.queueManager.getQueueSnapshot().toBroadcastPayload())
                    refreshContent()
                }
            }
        }
        buttonRow.addView(removeButton)

        content.addView(buttonRow)
        card.addView(content)
        if (requestFocus) {
            card.post { card.requestFocus() }
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
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setBackgroundResource(backgroundRes)
        }
    }

    private fun buildText(text: String, sizeSp: Float, color: Int, bold: Boolean, top: Int = 0): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(top) }
        }
    }

    private fun buildEmptyText(text: String): TextView {
        return buildText(text, 18f, 0xFFAAB1BD.toInt(), false)
    }

    private fun queueBadge(status: String): Int {
        return when (status) {
            "playing" -> R.drawable.bg_badge_playing
            "skipped" -> R.drawable.bg_badge_warning
            "finished" -> R.drawable.bg_badge_info
            "removed" -> R.drawable.bg_badge_error
            else -> R.drawable.bg_badge_idle
        }
    }

    private fun downloadBadge(status: String): Int {
        return when (status) {
            "success" -> R.drawable.bg_badge_success
            "failed" -> R.drawable.bg_badge_error
            "downloading" -> R.drawable.bg_badge_warning
            else -> R.drawable.bg_badge_idle
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
