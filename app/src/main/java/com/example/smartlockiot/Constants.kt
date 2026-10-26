package com.example.smartlockiot

import java.util.UUID

object Constants {
    const val COL_LOGS = "access_logs"
    const val COL_UIDS = "authorized_uids"
    const val COL_ADMINS = "admins"
    const val COL_DOOR = "door"
    const val DOC_DOOR_STATE = "state"

    const val STATUS_SUCCESS = "SUCCESS"
    const val STATUS_DENIED = "DENIED"

    /** true = solo cuentas con documento admins/{uid} pueden entrar (rol Administrador). */
    const val REQUIRE_ADMIN_DOC = false

    const val CHANNEL_ID = "access_events"

    val BLE_SERVICE: UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
    val BLE_CHAR: UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
}
