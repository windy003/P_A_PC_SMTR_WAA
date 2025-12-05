package com.screenshot.monitor.model

data class StatusResponse(
    val status: String,  // "has" or "none"
    val timestamp: String,
    val message: String
)
