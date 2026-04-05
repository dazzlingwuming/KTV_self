package com.ktv.stb.ui.search

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.ktv.stb.R
import com.ktv.stb.app.KtvApplication
import com.ktv.stb.databinding.ActivitySearchBinding
import com.ktv.stb.domain.model.SearchSongItem
import com.ktv.stb.server.route.CreateOrderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchBinding
    private var latestKeyword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.searchSubmitButton.setOnClickListener {
            performSearch(binding.searchKeywordInput.text?.toString().orEmpty())
        }
        binding.searchRefreshButton.setOnClickListener {
            performSearch(latestKeyword)
        }
        binding.searchKeywordInput.setOnEditorActionListener { _, _, _ ->
            performSearch(binding.searchKeywordInput.text?.toString().orEmpty())
            true
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.searchResultsContainer.childCount == 0) {
            renderEmpty("请输入关键词后开始搜索")
        }
    }

    private fun performSearch(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isBlank()) {
            binding.searchStatusText.text = "请输入关键词"
            renderEmpty("请输入关键词后开始搜索")
            return
        }
        latestKeyword = normalized
        binding.searchStatusText.text = "正在搜索：$normalized"
        lifecycleScope.launch {
            val appContainer = (application as KtvApplication).appContainer
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    appContainer.bilibiliSearchDataSource.search(normalized)
                }
            }
            result.onSuccess { list ->
                binding.searchStatusText.text = if (list.isEmpty()) "没有找到结果" else "已找到 ${list.size} 条结果"
                renderResults(list)
            }.onFailure {
                binding.searchStatusText.text = "搜索失败：${it.message ?: "unknown error"}"
                renderEmpty("搜索失败，请检查网络或稍后重试")
            }
        }
    }

    private fun renderResults(items: List<SearchSongItem>) {
        binding.searchResultsContainer.removeAllViews()
        if (items.isEmpty()) {
            renderEmpty("没有找到结果")
            return
        }
        items.forEachIndexed { index, item ->
            binding.searchResultsContainer.addView(buildResultCard(item, index == 0))
        }
    }

    private fun renderEmpty(text: String) {
        binding.searchResultsContainer.removeAllViews()
        binding.searchResultsContainer.addView(
            TextView(this).apply {
                this.text = text
                textSize = 18f
                setTextColor(0xFFAAB1BD.toInt())
            },
        )
    }

    private fun buildResultCard(item: SearchSongItem, requestInitialFocus: Boolean): View {
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
        content.addView(buildText(item.title, 24f, 0xFFFFFFFF.toInt(), true))
        content.addView(buildText("${item.artist ?: "Unknown"} / ${formatDuration(item.duration)}", 16f, 0xFFAAB1BD.toInt(), false, top = 10))
        content.addView(buildText("搜索结果只负责下载并分离，点歌请到本地资源页完成。", 14f, 0xFF98A0AE.toInt(), false, top = 10))

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(16) }
        }

        val downloadButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "下载并分离"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            strokeColor = android.content.res.ColorStateList.valueOf(0xFF8CE4C2.toInt())
            strokeWidth = dp(1)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener {
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        appContainer.queueManager.createDownloadOnly(
                            CreateOrderRequest(
                                clientId = "tv-local",
                                clientName = "TV Local",
                                sourceType = item.sourceType,
                                sourceId = item.sourceId,
                                title = item.title,
                                titleBase64 = null,
                                artist = item.artist,
                                artistBase64 = null,
                                duration = item.duration,
                                coverUrl = item.coverUrl,
                            ),
                        )
                    }
                    if (result.accepted) {
                        result.songEntity?.let { appContainer.downloadManager.enqueue(it) }
                        binding.searchStatusText.text = "已加入本地下载：${item.title}"
                    } else {
                        binding.searchStatusText.text = result.message
                    }
                }
            }
        }
        buttonRow.addView(downloadButton)
        content.addView(buttonRow)
        card.addView(content)

        downloadButton.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            card.strokeWidth = if (hasFocus) dp(3) else dp(1)
            card.strokeColor = if (hasFocus) 0xFF8CE4C2.toInt() else 0xFF2A2D33.toInt()
            card.scaleX = if (hasFocus) 1.02f else 1f
            card.scaleY = if (hasFocus) 1.02f else 1f
            card.cardElevation = if (hasFocus) dp(8).toFloat() else 0f
        }

        if (requestInitialFocus) {
            card.post { downloadButton.requestFocus() }
        }
        return card
    }

    private fun buildText(text: String, sizeSp: Float, color: Int, bold: Boolean, top: Int = 0): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(top) }
        }
    }

    private fun formatDuration(duration: Long): String {
        val total = duration.coerceAtLeast(0L)
        val minutes = total / 60
        val seconds = total % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
