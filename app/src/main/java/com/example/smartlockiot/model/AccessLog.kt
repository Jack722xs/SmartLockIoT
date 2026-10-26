package com.example.smartlockiot.model

import com.google.firebase.Timestamp

data class AccessLog(
    var timestamp: Timestamp? = null,
    var userId: String = "",
    var status: String = "",
    var statusColor: String = ""
)
