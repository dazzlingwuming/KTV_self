package com.ktv.host.api.dto

data class CreateSeparateTaskRequest(
    val songId: String,
    val fileFormat: String,
)
