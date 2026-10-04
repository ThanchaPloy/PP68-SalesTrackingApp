package com.example.pp68_salestrackingapp.utils

object SyncStatusNavigation {
    const val EXTRA_OPEN_SYNC_STATUS = "open_sync_status"
    const val SCREEN_SYNC_STATUS = "sync_status"

    fun destination(openSyncStatus: Boolean): String? =
        if (openSyncStatus) SCREEN_SYNC_STATUS else null
}
