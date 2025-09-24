package com.example.puffs.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.puffs.data.PuffRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver: BroadcastReceiver(){
    override fun onReceive(context: Context, intent: Intent) {
        val act = intent.getStringExtra("action") ?: return
        val repo = PuffRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            when(act){
                "ADD" -> repo.addPuff()
                "UNDO" -> repo.undo()
            }
        }
    }
}