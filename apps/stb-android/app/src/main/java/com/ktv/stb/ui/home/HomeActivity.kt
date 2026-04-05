package com.ktv.stb.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.ktv.stb.R
import com.ktv.stb.app.KtvApplication
import com.ktv.stb.databinding.ActivityHomeBinding
import com.ktv.stb.ui.local.LocalLibraryActivity
import com.ktv.stb.ui.player.PlayerActivity
import com.ktv.stb.ui.queue.QueueActivity
import com.ktv.stb.ui.search.SearchActivity
import com.ktv.stb.ui.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private var refreshJob: Job? = null
    private var suppressVolumeCallback = false
    private var suppressMixCallback = false

    private val viewModel: HomeViewModel by viewModels {
        val appContainer = (application as KtvApplication).appContainer
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(appContainer) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appContainer = (application as KtvApplication).appContainer
        binding.homePlayerView.player = appContainer.ktvPlayerManager.getPlayer()

        setupEntryCard(binding.searchCard, SearchActivity::class.java)
        setupEntryCard(binding.queueCard, QueueActivity::class.java)
        setupEntryCard(binding.localCard, LocalLibraryActivity::class.java)
        setupEntryCard(binding.settingsCard, SettingsActivity::class.java)
        setupEntryCard(binding.playerCard)

        binding.videoFocusOverlay.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        binding.homePauseButton.setOnClickListener { appContainer.ktvPlayerManager.pause() }
        binding.homeResumeButton.setOnClickListener { appContainer.ktvPlayerManager.resume() }
        binding.homeNextButton.setOnClickListener { appContainer.ktvPlayerManager.skipToNext() }

        binding.homeVolumeSlider.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (!fromUser || suppressVolumeCallback) return@addOnChangeListener
            val nextVolume = value.toInt().coerceIn(0, 100)
            binding.homeVolumeValueText.text = nextVolume.toString()
            appContainer.ktvPlayerManager.setVolume(nextVolume)
        }
        binding.homeVocalSlider.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (!fromUser || suppressMixCallback) return@addOnChangeListener
            val nextVolume = value.toInt().coerceIn(0, 100)
            binding.homeVocalValueText.text = nextVolume.toString()
            appContainer.ktvPlayerManager.setVocalVolume(nextVolume)
        }
        binding.homeAccompanimentSlider.addOnChangeListener { _: Slider, value: Float, fromUser: Boolean ->
            if (!fromUser || suppressMixCallback) return@addOnChangeListener
            val nextVolume = value.toInt().coerceIn(0, 100)
            binding.homeAccompanimentValueText.text = nextVolume.toString()
            appContainer.ktvPlayerManager.setAccompanimentVolume(nextVolume)
        }

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.currentSongText.text = state.currentTitle
                binding.playStatusBadgeText.text = state.playStatus.uppercase()
                binding.playStatusBadgeText.setBackgroundResource(playerBadgeBackground(state.playStatus))
                binding.progressText.text = "${formatTime(state.currentTimeMs)} / ${formatTime(state.durationMs)}"
                binding.clientCountValueText.text = "${state.clientCount} 台手机在线"
                binding.deviceNameText.text = "设备: ${state.deviceName}"
                binding.videoPlaceholderText.visibility =
                    if (state.playStatus == "idle" && state.currentTitle == "暂无播放") View.VISIBLE else View.GONE
                binding.nextSongText.text = state.nextTitle ?: "暂无下一首"
                binding.qrImageView.setImageBitmap(viewModel.buildQrBitmap())
                bindVolume(state.volume)
                bindMix(state.vocalVolume, state.accompanimentVolume, state.mixAvailable)
            }
        }

        binding.videoFocusOverlay.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        binding.homePlayerView.player = (application as KtvApplication).appContainer.ktvPlayerManager.getPlayer()
        startRefreshing()
    }

    override fun onPause() {
        refreshJob?.cancel()
        refreshJob = null
        binding.homePlayerView.player = null
        super.onPause()
    }

    private fun bindVolume(volume: Int) {
        val safeVolume = volume.coerceIn(0, 100)
        binding.homeVolumeValueText.text = safeVolume.toString()
        if (binding.homeVolumeSlider.value.toInt() != safeVolume) {
            suppressVolumeCallback = true
            binding.homeVolumeSlider.value = safeVolume.toFloat()
            suppressVolumeCallback = false
        }
    }

    private fun bindMix(vocalVolume: Int, accompanimentVolume: Int, mixAvailable: Boolean) {
        binding.mixControlGroup.visibility = if (mixAvailable) View.VISIBLE else View.GONE
        if (!mixAvailable) return
        suppressMixCallback = true
        binding.homeVocalSlider.value = vocalVolume.coerceIn(0, 100).toFloat()
        binding.homeAccompanimentSlider.value = accompanimentVolume.coerceIn(0, 100).toFloat()
        binding.homeVocalValueText.text = vocalVolume.coerceIn(0, 100).toString()
        binding.homeAccompanimentValueText.text = accompanimentVolume.coerceIn(0, 100).toString()
        suppressMixCallback = false
    }

    private fun startRefreshing() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (true) {
                viewModel.refresh()
                delay(1000)
            }
        }
    }

    private fun setupEntryCard(card: MaterialCardView, activityClass: Class<*>? = null) {
        card.setOnFocusChangeListener { _, hasFocus ->
            card.strokeWidth = if (hasFocus) dpToPx(3) else dpToPx(1)
            card.strokeColor = if (hasFocus) 0xFF8CE4C2.toInt() else 0xFF2A2D33.toInt()
            card.cardElevation = if (hasFocus) dpToPx(8).toFloat() else 0f
            card.scaleX = if (hasFocus) 1.02f else 1f
            card.scaleY = if (hasFocus) 1.02f else 1f
        }
        if (activityClass != null) {
            card.setOnClickListener { startActivity(Intent(this, activityClass)) }
        }
    }

    private fun playerBadgeBackground(playStatus: String): Int {
        return when (playStatus.lowercase()) {
            "playing" -> R.drawable.bg_badge_playing
            "paused" -> R.drawable.bg_badge_paused
            "error" -> R.drawable.bg_badge_error
            "buffering" -> R.drawable.bg_badge_warning
            else -> R.drawable.bg_badge_idle
        }
    }

    private fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs.coerceAtLeast(0L) / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
