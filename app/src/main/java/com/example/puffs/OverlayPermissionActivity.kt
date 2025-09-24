package com.example.puffs

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.Settings

class OverlayPermissionActivity: Activity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+packageName)))
        finish()
    }
}