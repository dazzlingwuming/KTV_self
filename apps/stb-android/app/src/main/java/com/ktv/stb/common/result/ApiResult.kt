package com.ktv.stb.common.result

data class ApiResult<T>(
    val code: Int = 0,
    val message: String = "ok",
    val data: T? = null,
)
