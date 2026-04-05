package com.ktv.stb.ui.settings

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ktv.stb.app.KtvApplication
import com.ktv.stb.data.local.entity.DeviceConfigEntity
import com.ktv.stb.databinding.ActivitySettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private var authPollingJob: Job? = null
    private val sectionViews: List<View> by lazy {
        listOf(
            binding.bilibiliCard,
            binding.deviceInfoCard,
            binding.sessionInfoCard,
            binding.controlInfoCard,
            binding.debugInfoCard,
            binding.networkDiagnosticCard,
        )
    }

    private fun appContainer() = (application as KtvApplication).appContainer

    private fun currentHostAddressInput(): String {
        return binding.hostAddressInput.text?.toString()?.trim().orEmpty().trimEnd('/')
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.refreshBilibiliQrButton.setOnClickListener {
            lifecycleScope.launch {
                appContainer().bilibiliAuthManager.ensureQrLogin()
                renderAll()
            }
        }
        binding.clearBilibiliLoginButton.setOnClickListener {
            lifecycleScope.launch {
                appContainer().bilibiliAuthManager.clearLogin()
                renderAll()
            }
        }
        binding.refreshNetworkDiagnosticButton.setOnClickListener {
            lifecycleScope.launch {
                renderNetworkDiagnostic()
            }
        }
        binding.saveHostAddressButton.setOnClickListener {
            lifecycleScope.launch {
                saveHostAddress()
            }
        }
        binding.testHostAddressButton.setOnClickListener {
            lifecycleScope.launch {
                testHostAddress()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            runCatching {
                val appContainer = appContainer()
                appContainer.bilibiliAuthManager.loadState()
                if (!appContainer.bilibiliAuthManager.getState().isLoggedIn) {
                    appContainer.bilibiliAuthManager.ensureQrLogin()
                }
                renderAll()
                renderNetworkDiagnostic()
                startAuthPolling()
            }.onFailure {
                binding.bilibiliStatusText.text = "B站登录初始化失败: ${it.message ?: "unknown error"}"
            }
        }
    }

    override fun onPause() {
        authPollingJob?.cancel()
        authPollingJob = null
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (shouldMoveWithinSection(View.FOCUS_DOWN)) {
                        return super.dispatchKeyEvent(event)
                    }
                    if (moveToSection(1)) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (shouldMoveWithinSection(View.FOCUS_UP)) {
                        return super.dispatchKeyEvent(event)
                    }
                    if (moveToSection(-1)) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun startAuthPolling() {
        authPollingJob?.cancel()
        authPollingJob = lifecycleScope.launch {
            while (true) {
                runCatching {
                    val manager = appContainer().bilibiliAuthManager
                    val current = manager.getState()
                    if (!current.isLoggedIn && !current.qrKey.isNullOrBlank()) {
                        manager.pollQrLogin()
                        renderAll()
                    }
                }.onFailure {
                    binding.bilibiliStatusText.text = "B站登录轮询异常: ${it.message ?: "unknown error"}"
                }
                delay(2000)
            }
        }
    }

    private suspend fun renderAll() {
        val appContainer = appContainer()
        val session = appContainer.sessionManager.getCurrentSession()
        val authState = appContainer.bilibiliAuthManager.getState()

        binding.settingsSummaryText.text =
            "当前保留机顶盒局域网服务、公共队列、本地下载与本地播放，并开始接入 B站登录态用于后续高清下载。"
        binding.deviceInfoText.text = buildString {
            append("设备名: ${session.deviceName}\n")
            append("设备 ID: ${session.deviceId}")
        }
        binding.sessionInfoText.text = buildString {
            append("会话状态: ${session.bindStatus.name.lowercase()}\n")
            append("已连接手机数: ${session.clientCount}\n")
            append("会话 ID: ${session.sessionId}")
        }
        binding.controlPageInfoText.text = buildString {
            append("控制页 URL\n${appContainer.sessionManager.resolveWebEntryUrl()}\n\n")
            append("客户端摘要\n")
            append(
                if (session.clients.isEmpty()) {
                    "暂无已连接客户端"
                } else {
                    session.clients.joinToString(separator = "\n") {
                        "${it.clientName ?: "client"} (${it.clientId.take(8)})"
                    }
                },
            )
        }
        binding.debugInfoText.text = buildString {
            append("Server running: ${appContainer.mobileApiServer.isRunning()}\n")
            append("Host: ${session.hostIp}:${session.port}\n")
            append("Expire at: ${session.expireAt}\n\n")
            append("QR Entry URL\n${appContainer.qrCodeManager.buildQrEntryUrl()}\n\n")
            append("QR Payload\n${appContainer.qrCodeManager.buildQrPayload()}")
        }

        val config = withContext(Dispatchers.IO) {
            appContainer.database.deviceConfigDao().getByDeviceId(session.deviceId)
        }
        val savedHostAddress = config?.hostAddress.orEmpty()
        val currentHostInput = currentHostAddressInput()
        val shouldSyncHostInput = !binding.hostAddressInput.hasFocus() &&
            (currentHostInput.isBlank() || currentHostInput == savedHostAddress)
        if (shouldSyncHostInput && binding.hostAddressInput.text?.toString().orEmpty() != savedHostAddress) {
            binding.hostAddressInput.setText(savedHostAddress)
        }
        val effectiveHostAddress = currentHostAddressInput().ifBlank { savedHostAddress }
        binding.hostAddressStatusText.text = if (effectiveHostAddress.isBlank()) {
            "尚未配置主机分离地址"
        } else {
            "当前主机地址: $effectiveHostAddress"
        }
        if (binding.hostConnectivityText.text.isNullOrBlank() ||
            binding.hostConnectivityText.text.toString().contains("可测试") ||
            binding.hostConnectivityText.text.toString().contains("请先填写")
        ) {
            binding.hostConnectivityText.text = if (effectiveHostAddress.isBlank()) {
                "请先填写主机地址，例如 http://192.168.1.100:9090"
            } else {
                "可测试: $effectiveHostAddress/api/ping"
            }
        }

        binding.bilibiliQrImageView.setImageBitmap(appContainer.bilibiliAuthManager.buildQrBitmap(260))
        binding.bilibiliStatusText.text = if (authState.isLoggedIn) {
            "已登录 B站"
        } else {
            authState.statusMessage ?: "请扫码登录 B站"
        }
        binding.bilibiliUserInfoText.text = buildString {
            append("登录状态: ${if (authState.isLoggedIn) "已登录" else "未登录"}\n")
            append("用户名: ${authState.userName ?: "-"}\n")
            append("用户 ID: ${authState.userId ?: "-"}\n")
            append("会员状态: ${if (authState.isVip) "会员" else "普通用户"}\n")
            append("Cookie 过期: ${if (authState.cookieExpireAt > 0) authState.cookieExpireAt.toString() else "-"}\n")
            append("二维码状态: ${authState.qrStatus}")
        }
    }

    private suspend fun renderNetworkDiagnostic() {
        binding.networkDiagnosticText.text = "正在检测机顶盒网络..."
        val result = withContext(Dispatchers.IO) {
            appContainer().bilibiliNetworkDiagnostics.run()
        }
        binding.networkDiagnosticText.text = buildString {
            append("DNS 解析: ${if (result.dnsOk) "成功" else "失败"}\n")
            append("api.bilibili.com -> ${result.dnsAddress ?: "-"}\n")
            if (!result.dnsMessage.isNullOrBlank()) {
                append("DNS 错误: ${result.dnsMessage}\n")
            }
            append("\n")
            append("B站搜索接口: ${if (result.searchApiOk) "可访问" else "不可访问"}\n")
            append("HTTP Code: ${result.searchApiCode?.toString() ?: "-"}\n")
            if (!result.searchApiMessage.isNullOrBlank()) {
                append("接口错误: ${result.searchApiMessage}")
            }
        }
    }

    private suspend fun saveHostAddress() {
        val appContainer = appContainer()
        val session = appContainer.sessionManager.getCurrentSession()
        val trimmed = currentHostAddressInput()
        persistHostAddress(session.deviceId, session.deviceName, trimmed)
        binding.hostAddressStatusText.text = if (trimmed.isBlank()) {
            "主机地址已清空"
        } else {
            "已保存主机地址: $trimmed"
        }
        binding.hostConnectivityText.text = if (trimmed.isBlank()) {
            "请先填写主机地址，例如 http://192.168.1.100:9090"
        } else {
            "主机地址已保存，可测试: $trimmed/api/ping"
        }
    }

    private suspend fun testHostAddress() {
        val appContainer = appContainer()
        val session = appContainer.sessionManager.getCurrentSession()
        val trimmed = currentHostAddressInput()
        persistHostAddress(session.deviceId, session.deviceName, trimmed)
        binding.hostAddressStatusText.text = if (trimmed.isBlank()) {
            "尚未配置主机分离地址"
        } else {
            "当前主机地址: $trimmed"
        }
        binding.hostConnectivityText.text = "正在测试主机连通性..."
        val result = withContext(Dispatchers.IO) {
            appContainer.hostSeparatorDiagnostics.ping(trimmed)
        }
        binding.hostConnectivityText.text = if (result.success) {
            "主机可连接，HTTP ${result.httpCode ?: 200}"
        } else {
            "主机不可连接，HTTP ${result.httpCode ?: "-"} ${result.message.orEmpty()}".trim()
        }
    }

    private suspend fun persistHostAddress(deviceId: String, deviceName: String, hostAddress: String) {
        val appContainer = appContainer()
        val current = withContext(Dispatchers.IO) {
            appContainer.database.deviceConfigDao().getByDeviceId(deviceId)
        }
        val next = (current ?: DeviceConfigEntity(
            deviceId = deviceId,
            deviceName = deviceName,
            hostAddress = "",
            defaultPlayMode = "original",
            allowDuplicateOrder = false,
            cacheLimitMb = 4096L,
        )).copy(hostAddress = hostAddress)
        withContext(Dispatchers.IO) {
            appContainer.database.deviceConfigDao().upsert(next)
        }
    }

    private fun moveToSection(direction: Int): Boolean {
        val scrollView = binding.root as? ScrollView ?: return false
        val currentIndex = resolveCurrentSectionIndex(scrollView)
        val nextIndex = (currentIndex + direction).coerceIn(0, sectionViews.lastIndex)
        if (nextIndex == currentIndex) return true
        val targetSection = sectionViews[nextIndex]
        scrollView.smoothScrollTo(0, (targetSection.top - dp(24)).coerceAtLeast(0))
        val focusTarget = findFocusableDescendant(targetSection)
        targetSection.post {
            (focusTarget ?: targetSection).requestFocus()
        }
        return true
    }

    private fun shouldMoveWithinSection(direction: Int): Boolean {
        val focused = currentFocus ?: return false
        val currentSection = sectionViews.firstOrNull { isDescendantOf(focused, it) } ?: return false
        val nextFocus = focused.focusSearch(direction) ?: return false
        return isDescendantOf(nextFocus, currentSection)
    }

    private fun resolveCurrentSectionIndex(scrollView: ScrollView): Int {
        val focused = currentFocus
        if (focused != null) {
            sectionViews.forEachIndexed { index, section ->
                if (isDescendantOf(focused, section)) return index
            }
        }
        val scrollY = scrollView.scrollY
        return sectionViews.indexOfLast { it.top - dp(32) <= scrollY }.coerceAtLeast(0)
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = (current.parent as? View)
        }
        return false
    }

    private fun findFocusableDescendant(root: View): View? {
        if (root.isFocusable && root.visibility == View.VISIBLE) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            val target = findFocusableDescendant(child)
            if (target != null) return target
        }
        return null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
