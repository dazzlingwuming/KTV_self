package com.ktv.host.api.dto

data class GetSeparateResultResponse(
    val taskId: String,
    val status: String,
    val accompanimentUrl: String?,
    val vocalUrl: String?,
)
