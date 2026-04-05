package com.ktv.stb.server.auth

import com.ktv.stb.session.SessionManager

class SessionAuthInterceptor(
    private val sessionManager: SessionManager,
) {
    fun isBoundSessionActive(): Boolean = sessionManager.isBound()
}
