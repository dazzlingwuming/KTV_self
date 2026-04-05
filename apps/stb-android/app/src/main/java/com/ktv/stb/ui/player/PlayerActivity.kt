package com.ktv.stb.ui.player

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ktv.stb.app.KtvApplication
import com.ktv.stb.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val appContainer = (application as KtvApplication).appContainer
        binding.playerView.player = appContainer.ktvPlayerManager.getPlayer()
        binding.qrImageView.setImageBitmap(appContainer.qrCodeManager.buildQrBitmap(220))
    }

    override fun onResume() {
        super.onResume()
        binding.playerView.player = (application as KtvApplication).appContainer.ktvPlayerManager.getPlayer()
    }

    override fun onPause() {
        binding.playerView.player = null
        super.onPause()
    }
}
