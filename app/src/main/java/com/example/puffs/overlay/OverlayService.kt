package com.example.puffs.overlay

import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import androidx.core.app.NotificationCompat
import com.example.puffs.MainActivity
import com.example.puffs.R
import com.example.puffs.data.PuffRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.TextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay // Added import for delay

class OverlayService: Service(){
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var repo: PuffRepository
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var isOverlayAdded = false
    private var statsJob: Job? = null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    
    override fun onCreate(){
        super.onCreate()
        repo = PuffRepository(this)
        scope.launch { repo.finalizeIfTimedOut() } // On service start, auto-finalize any stale draft
        startForeground(1, buildNotif())
        showBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        statsJob?.cancel() // Cancel the stats job
        bubble?.let {
            try { wm.removeView(it) } catch (_: Throwable) {}
        }
        bubble = null
        isOverlayAdded = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotif(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val chId = "puffs"
        nm.createNotificationChannel(NotificationChannel(chId, "Puffs", NotificationManager.IMPORTANCE_MIN))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, chId)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(0, getString(R.string.overlay_action_add), pendingBroadcast("ADD"))
            .addAction(0, getString(R.string.overlay_action_undo), pendingBroadcast("UNDO"))
            .build()
    }

    private fun pendingBroadcast(action:String): PendingIntent {
        val i = Intent("com.example.puffs.ACTION").putExtra("action", action)
        return PendingIntent.getBroadcast(this, action.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // Remove any existing overlay first
        bubble?.let { try { wm.removeView(it) } catch (_: Throwable) {} }
        bubble = null
        statsJob?.cancel()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 300
        }

        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val dragHandle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(72))
            background = GradientDrawable().apply {
                setColor(0x33FFFFFF)
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(0xCC0B0F14.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0x331F2937)
            }
            background = bg
            setPadding(dp(10), dp(10), dp(10), dp(10))
            elevation = dp(6).toFloat()
        }

        fun makeBtn(label: String) = Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            minWidth = dp(96); minHeight = dp(48)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val addBtn = makeBtn(getString(R.string.overlay_action_add)).apply {
            background = GradientDrawable().apply { setColor(0xFF10B981.toInt()); cornerRadius = dp(24).toFloat() }
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { scope.launch { repo.addPuff() } } // Changed to addPuff
        }
        val undoBtn = makeBtn(getString(R.string.overlay_action_undo)).apply {
            background = GradientDrawable().apply { setColor(0x00000000); setStroke(dp(2), 0x66E5E7EB); cornerRadius = dp(24).toFloat() }
            setTextColor(0xFFE5E7EB.toInt())
            setOnClickListener { scope.launch { repo.undo() } } // Remains repo.undo()
        }
        val saveBtn = makeBtn(getString(R.string.overlay_action_save)).apply { // “Save” now means “End Session Now”
            background = GradientDrawable().apply { setColor(0x00000000); setStroke(dp(2), 0x66E5E7EB); cornerRadius = dp(24).toFloat() }
            setTextColor(0xFFE5E7EB.toInt())
            setOnClickListener { scope.launch { repo.endSessionNow() } } // Changed to endSessionNow
        }

        fun spacer(w:Int)= View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(w), 1) }
        row.addView(addBtn); row.addView(spacer(6)); row.addView(undoBtn); row.addView(spacer(6)); row.addView(saveBtn)

        val sessionView = TextView(this).apply {
            setTextColor(0xFFE5E7EB.toInt())
            textSize = 14f
            // text = getString(R.string.overlay_session_initial_text) // Initial text will be set by statsJob
        }

        column.addView(row)
        column.addView(spacer(6))
        column.addView(sessionView)

        root.addView(dragHandle)
        root.addView(column)

        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0; private var initY = 0
            private var touchX = 0f; private var touchY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { initX = params.x; initY = params.y; touchX = e.rawX; touchY = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initX + (e.rawX - touchX).toInt()
                        params.y = initY + (e.rawY - touchY).toInt()
                        if (isOverlayAdded) wm.updateViewLayout(root, params) // Check if view is added
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        return true
                    }
                }
                return false
            }
        })

        column.setOnLongClickListener { stopSelf(); true }

        if (!isOverlayAdded) { // Ensure addView is called only once
            wm.addView(root, params)
            bubble = root
            isOverlayAdded = true
        }


        // Update session counter display to pull from the draft
        statsJob = scope.launch {
            while (true) {
                val d = repo.getDraftOnce()
                sessionView.text = "Session: " + (d?.puffCount ?: 0)
                delay(1000) // lightweight poll
            }
        }
    }
}
