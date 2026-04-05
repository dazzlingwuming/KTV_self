package com.ktv.host.app

import com.ktv.host.api.controller.SeparateController
import com.ktv.host.config.HostServerConfig
import com.ktv.host.storage.HostStoragePaths
import com.ktv.host.task.InMemorySeparateTaskRepository
import com.ktv.host.task.SeparateTaskManager
import com.ktv.host.upload.MultipartUploadHandler
import io.ktor.serialization.gson.gson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

fun main() {
    val config = HostServerConfig()
    embeddedServer(Netty, host = config.host, port = config.port) {
        hostModule()
    }.start(wait = true)
}

fun Application.hostModule() {
    install(ContentNegotiation) {
        gson()
    }

    val storagePaths = HostStoragePaths()
    val repository = InMemorySeparateTaskRepository()
    val taskManager = SeparateTaskManager(repository, storagePaths)
    val uploadHandler = MultipartUploadHandler(storagePaths)
    val controller = SeparateController(taskManager, uploadHandler)

    routing {
        controller.register(this)
    }
}
