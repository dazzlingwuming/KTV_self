package com.ktv.stb.app

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.ktv.stb.common.constants.AppConstants
import com.ktv.stb.data.local.db.KtvDatabase
import com.ktv.stb.data.remote.bilibili.BilibiliMediaResolver
import com.ktv.stb.data.remote.bilibili.BilibiliAuthManager
import com.ktv.stb.data.remote.bilibili.BilibiliNetworkDiagnostics
import com.ktv.stb.data.remote.bilibili.BilibiliSearchDataSource
import com.ktv.stb.data.remote.host.HostSeparateApi
import com.ktv.stb.data.remote.host.HostSeparatorDiagnostics
import com.ktv.stb.downloader.BilibiliSongDownloader
import com.ktv.stb.downloader.DownloadManager
import com.ktv.stb.downloader.FileAllocator
import com.ktv.stb.domain.usecase.CreateOrderUseCase
import com.ktv.stb.domain.usecase.GetPlayerStatusUseCase
import com.ktv.stb.domain.usecase.GetQueueUseCase
import com.ktv.stb.domain.usecase.MoveQueueItemNextUseCase
import com.ktv.stb.domain.usecase.PausePlayerUseCase
import com.ktv.stb.domain.usecase.PlayNextUseCase
import com.ktv.stb.domain.usecase.RemoveQueueItemUseCase
import com.ktv.stb.domain.usecase.ResumePlayerUseCase
import com.ktv.stb.domain.usecase.SetAccompanimentVolumeUseCase
import com.ktv.stb.domain.usecase.SetVolumeUseCase
import com.ktv.stb.domain.usecase.SetVocalVolumeUseCase
import com.ktv.stb.domain.usecase.SwitchPlayModeUseCase
import com.ktv.stb.player.KtvPlayerManager
import com.ktv.stb.player.PlaybackStateDispatcher
import com.ktv.stb.qrcode.QrCodeManager
import com.ktv.stb.qrcode.QrPayloadSigner
import com.ktv.stb.queue.QueueManager
import com.ktv.stb.separate.SeparateTaskManager
import com.ktv.stb.server.auth.SessionAuthInterceptor
import com.ktv.stb.server.http.MobileApiServer
import com.ktv.stb.server.http.ServerConfig
import com.ktv.stb.server.route.OrderRoutes
import com.ktv.stb.server.route.LocalRoutes
import com.ktv.stb.server.route.BilibiliAuthRoutes
import com.ktv.stb.server.route.ImageRoutes
import com.ktv.stb.server.route.PlayerRoutes
import com.ktv.stb.server.route.QueueRoutes
import com.ktv.stb.server.route.RouteRegistry
import com.ktv.stb.server.route.SearchRoutes
import com.ktv.stb.server.route.SessionRoutes
import com.ktv.stb.server.route.toBroadcastPayload
import com.ktv.stb.server.staticweb.StaticWebAssetHandler
import com.ktv.stb.server.ws.MobileWebSocketHub
import com.ktv.stb.session.SessionManager
import com.ktv.stb.storage.SongFileManager
import com.ktv.stb.storage.StoragePaths
import com.ktv.stb.storage.LocalFileStatusService
import com.ktv.stb.storage.MediaFileInspector

class AppContainer(private val context: Context) {
    val dispatchersProvider = DispatchersProvider()
    val gson = Gson()

    val database: KtvDatabase by lazy {
        Room.databaseBuilder(context, KtvDatabase::class.java, AppConstants.DB_NAME)
            .fallbackToDestructiveMigration()
            .build()
    }

    val sessionManager: SessionManager by lazy {
        SessionManager(context = context, deviceConfigDao = database.deviceConfigDao())
    }

    val qrCodeManager: QrCodeManager by lazy {
        QrCodeManager(sessionManager = sessionManager, signer = QrPayloadSigner(), gson = gson)
    }

    val webSocketHub: MobileWebSocketHub by lazy {
        MobileWebSocketHub(gson = gson)
    }

    val staticWebAssetHandler: StaticWebAssetHandler by lazy {
        StaticWebAssetHandler(context.assets, "mobile-web")
    }

    val sessionAuthInterceptor: SessionAuthInterceptor by lazy {
        SessionAuthInterceptor(sessionManager)
    }

    val queueManager: QueueManager by lazy {
        QueueManager(
            songDao = database.songDao(),
            queueDao = database.queueDao(),
        )
    }

    private fun broadcastQueueSnapshot() {
        webSocketHub.broadcastQueueUpdated(GetQueueUseCase(queueManager).execute().toBroadcastPayload())
    }

    val bilibiliSearchDataSource: BilibiliSearchDataSource by lazy {
        BilibiliSearchDataSource()
    }

    val bilibiliAuthManager: BilibiliAuthManager by lazy {
        BilibiliAuthManager(
            deviceConfigDao = database.deviceConfigDao(),
            sessionManager = sessionManager,
        )
    }

    val bilibiliMediaResolver: BilibiliMediaResolver by lazy {
        BilibiliMediaResolver(bilibiliAuthManager)
    }

    val bilibiliNetworkDiagnostics: BilibiliNetworkDiagnostics by lazy {
        BilibiliNetworkDiagnostics()
    }

    val hostSeparateApi: HostSeparateApi by lazy {
        HostSeparateApi(gson)
    }

    val hostSeparatorDiagnostics: HostSeparatorDiagnostics by lazy {
        HostSeparatorDiagnostics()
    }

    val storagePaths: StoragePaths by lazy {
        StoragePaths(context)
    }

    val fileAllocator: FileAllocator by lazy {
        FileAllocator(storagePaths)
    }

    val songFileManager: SongFileManager by lazy {
        SongFileManager()
    }

    val mediaFileInspector: MediaFileInspector by lazy {
        MediaFileInspector()
    }

    val separateTaskManager: SeparateTaskManager by lazy {
        SeparateTaskManager(
            songDao = database.songDao(),
            deviceConfigDao = database.deviceConfigDao(),
            hostSeparateApi = hostSeparateApi,
            storagePaths = storagePaths,
            songFileManager = songFileManager,
            webSocketHub = webSocketHub,
            sessionManager = sessionManager,
            onSongSeparated = { songId -> ktvPlayerManager.onSongMixReady(songId) },
        )
    }

    val playbackStateDispatcher: PlaybackStateDispatcher by lazy {
        PlaybackStateDispatcher(webSocketHub)
    }

    val ktvPlayerManager: KtvPlayerManager by lazy {
        KtvPlayerManager(
            context = context,
            queueManager = queueManager,
            mediaFileInspector = mediaFileInspector,
            playbackStateDispatcher = playbackStateDispatcher,
            onQueueStateChanged = ::broadcastQueueSnapshot,
        )
    }

    val downloadManager: DownloadManager by lazy {
        DownloadManager(
            songDao = database.songDao(),
            songDownloader = BilibiliSongDownloader(bilibiliMediaResolver, bilibiliAuthManager, fileAllocator, songFileManager),
            webSocketHub = webSocketHub,
            mediaFileInspector = mediaFileInspector,
            onQueueStateChanged = ::broadcastQueueSnapshot,
            onSongReadyToPlay = { ktvPlayerManager.tryStartPlaybackFromQueue() },
            onSongReadyToSeparate = { song -> separateTaskManager.enqueue(song.songId) },
        )
    }

    val localFileStatusService: LocalFileStatusService by lazy {
        LocalFileStatusService(database.songDao(), database.queueDao(), mediaFileInspector)
    }

    val routeRegistry: RouteRegistry by lazy {
        RouteRegistry(
            sessionRoutes = SessionRoutes(sessionManager, qrCodeManager, gson),
            searchRoutes = SearchRoutes(
                searchDataSource = bilibiliSearchDataSource,
                queueManager = queueManager,
                getQueueUseCase = GetQueueUseCase(queueManager),
                downloadManager = downloadManager,
                webSocketHub = webSocketHub,
                gson = gson,
            ),
            imageRoutes = ImageRoutes(),
            bilibiliAuthRoutes = BilibiliAuthRoutes(bilibiliAuthManager, gson),
            orderRoutes = OrderRoutes(
                createOrderUseCase = CreateOrderUseCase(queueManager),
                getQueueUseCase = GetQueueUseCase(queueManager),
                downloadManager = downloadManager,
                webSocketHub = webSocketHub,
                gson = gson,
            ),
            queueRoutes = QueueRoutes(
                getQueueUseCase = GetQueueUseCase(queueManager),
                removeQueueItemUseCase = RemoveQueueItemUseCase(queueManager),
                moveQueueItemNextUseCase = MoveQueueItemNextUseCase(queueManager),
                webSocketHub = webSocketHub,
                gson = gson,
            ),
            playerRoutes = PlayerRoutes(
                getPlayerStatusUseCase = GetPlayerStatusUseCase(ktvPlayerManager),
                playNextUseCase = PlayNextUseCase(ktvPlayerManager),
                pausePlayerUseCase = PausePlayerUseCase(ktvPlayerManager),
                resumePlayerUseCase = ResumePlayerUseCase(ktvPlayerManager),
                setVolumeUseCase = SetVolumeUseCase(ktvPlayerManager),
                setVocalVolumeUseCase = SetVocalVolumeUseCase(ktvPlayerManager),
                setAccompanimentVolumeUseCase = SetAccompanimentVolumeUseCase(ktvPlayerManager),
                switchPlayModeUseCase = SwitchPlayModeUseCase(ktvPlayerManager),
                gson = gson,
            ),
            localRoutes = LocalRoutes(
                localFileStatusService = localFileStatusService,
                queueManager = queueManager,
                webSocketHub = webSocketHub,
                broadcastQueueSnapshot = ::broadcastQueueSnapshot,
                onSongReadyToPlay = { ktvPlayerManager.tryStartPlaybackFromQueue() },
                onSongSeparate = { songId -> separateTaskManager.enqueue(songId) },
                onSongDeleted = { songId -> ktvPlayerManager.handleUnavailableSong(songId) },
                gson = gson,
            ),
            staticWebAssetHandler = staticWebAssetHandler,
            authInterceptor = sessionAuthInterceptor,
            sessionManager = sessionManager,
            webSocketHub = webSocketHub,
            getQueueUseCase = GetQueueUseCase(queueManager),
        )
    }

    val mobileApiServer: MobileApiServer by lazy {
        MobileApiServer(
            serverConfig = ServerConfig(port = AppConstants.DEFAULT_HTTP_PORT),
            routeRegistry = routeRegistry,
        )
    }

    fun start() {
        sessionManager.ensureSession()
        queueManager.initialize()
        kotlinx.coroutines.runBlocking { bilibiliAuthManager.loadState() }
        mobileApiServer.startServer()
        ktvPlayerManager.tryStartPlaybackFromQueue()
    }

    fun stop() {
        mobileApiServer.stopServer()
    }
}
