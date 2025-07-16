package com.berkay.serviceapp

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MyService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: LinearLayout

    private val job = Job()
    private val scope = CoroutineScope(job)
    private val packageName = MutableStateFlow("Hazırlanıyor")

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        println("service started")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = createOverlayTextView()
        val params = createLayoutParams()

        windowManager.addView(overlayView, params)

        startPackageNameUpdater()
        startPackageNameCollector(overlayView)

        return super.onStartCommand(intent, flags, startId)
    }

    private fun createOverlayTextView(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
            setBackgroundColor(Color.LTGRAY)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val textView = TextView(this).apply {
            text = packageName.value
            setTextColor(Color.BLACK)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        layout.addView(textView)

        return layout
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 100
        }
    }

    private fun startPackageNameUpdater() {
        scope.launch {
            while (true) {
                delay(2000)
                val name = getTopPackageName(this@MyService) ?: "Bilinmiyor"
                packageName.update { name }
            }
        }
    }

    private fun startPackageNameCollector(layout: LinearLayout) {
        scope.launch {
            packageName.collect {
                withContext(Dispatchers.Main) {
                    val textView = layout.getChildAt(0) as? TextView
                    textView?.text = it
                }
            }
        }
    }

    private fun getTopPackageName(context: Context): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 10_000 // 10 saniye geriye bak

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            beginTime,
            endTime
        )

        return stats
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (::windowManager.isInitialized && ::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

}