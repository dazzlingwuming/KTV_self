package com.ktv.host.common

data class HostApiResult<T>(
    val code: Int = 0,
    val message: String = "ok",
    val data: T? = null,
)
