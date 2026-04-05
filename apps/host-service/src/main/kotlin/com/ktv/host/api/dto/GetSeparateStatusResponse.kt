package com.ktv.host.api.dto

data class GetSeparateStatusResponse(
    val taskId: String,
    val status: String,
    val progress: Int,
)
