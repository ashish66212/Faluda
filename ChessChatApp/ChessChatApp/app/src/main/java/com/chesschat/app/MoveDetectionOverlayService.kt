package com.chesschat.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Improved Move Detection Overlay Service
 * Features:
 * - Compact floating overlay (80x80px, expandable)
 * - Memory-efficient bitmap pooling
 * - App-specific capture profiles
 * - Stockfish integration via Ngrok
 * - Automated move execution
 * - Connection pooling
 * - Adaptive frame rate
 */
class MoveDetectionOverlayService : Service() {

    companion object {
        private const val TAG = "MoveDetection"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "chess_move_detection"
    }

    // UI Components
    private var windowManager: WindowManager? = null
    private var compactOverlay: View? = null
    private var expandedOverlay: View? = null
    private var isExpanded = false
    
    // Status Views
    private var statusTextView: TextView? = null
    private var moveLogTextView: TextView? = null
    
    // Screen Capture
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // Memory Management
    private val bitmapPool = BitmapPool(maxPoolSize = 3)
    private var previousBitmap: Bitmap? = null
    
    // Detection State
    private val handler = Handler(Looper.getMainLooper())
    private var isDetecting = false
    private var isAutoPlayEnabled = false
    
    // Board Configuration
    private var boardX = 50
    private var boardY = 300
    private var boardSize = 800
    private var isFlipped = false
    
    // App Profile
    private var currentProfile: ChessAppProfile = ChessAppProfiles.profiles.last()
    private var detectionInterval = 1000L
    
    // Network
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()
    
    private var ngrokUrl: String = ""
    
    // Touch Simulation
    private val touchSimulator = TouchSimulator()
    
    // Detection Runnable
    private val detectionRunnable = object : Runnable {
        override fun run() {
            if (isDetecting) {
                captureScreen()
                handler.postDelayed(this, detectionInterval)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Load saved settings
        loadSettings()
        
        // Detect current chess app and apply profile
        detectCurrentApp()
        
        // Create compact overlay first
        createCompactOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val resultCode = it.getIntExtra("resultCode", 0)
            val data = it.getParcelableExtra<Intent>("data")
            val x = it.getIntExtra("boardX", 50)
            val y = it.getIntExtra("boardY", 300)
            val size = it.getIntExtra("boardSize", 800)
            
            boardX = x
            boardY = y
            boardSize = size
            
            saveSettings()
            
            if (resultCode != 0 && data != null) {
                // Apply capture delay from app profile
                handler.postDelayed({
                    startScreenCapture(resultCode, data)
                }, currentProfile.captureDelay)
            }
        }
        return START_STICKY
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("ChessChatPrefs", Context.MODE_PRIVATE)
        ngrokUrl = prefs.getString("base_url", "") ?: ""
        boardX = prefs.getInt("board_x", 50)
        boardY = prefs.getInt("board_y", 300)
        boardSize = prefs.getInt("board_size", 800)
        isFlipped = prefs.getBoolean("board_flipped", false)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("ChessChatPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("board_x", boardX)
            .putInt("board_y", boardY)
            .putInt("board_size", boardSize)
            .putBoolean("board_flipped", isFlipped)
            .apply()
    }

    private fun detectCurrentApp() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.getRunningTasks(1)
            if (tasks.isNotEmpty()) {
                val topActivity = tasks[0].topActivity
                val packageName = topActivity?.packageName ?: ""
                currentProfile = ChessAppProfiles.getProfile(packageName)
                detectionInterval = currentProfile.frameRateLimit
                Log.d(TAG, "Detected app: ${currentProfile.appName}, profile applied")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not detect current app: ${e.message}")
        }
    }

    /**
     * Create compact 80x80px floating overlay
     */
    private fun createCompactOverlay() {
        val layoutParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                dp(80),
                dp(80),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                dp(80),
                dp(80),
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        }

        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = dp(20)
        layoutParams.y = dp(100)

        // Compact button with robot emoji
        val container = FrameLayout(this).apply {
            setBackgroundResource(android.R.drawable.btn_default)
            background.alpha = 200  // Semi-transparent
            elevation = dp(10).toFloat()
        }

        val iconTextView = TextView(this).apply {
            text = "🤖"
            textSize = 32f
            gravity = Gravity.CENTER
            setOnClickListener {
                toggleExpanded()
            }
        }

        container.addView(iconTextView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Make draggable
        makeDraggable(container, layoutParams)

        compactOverlay = container
        windowManager?.addView(compactOverlay, layoutParams)
    }

    /**
     * Create expanded control panel
     */
    private fun createExpandedOverlay() {
        val layoutParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
        }

        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = dp(20)
        layoutParams.y = dp(200)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E0263238"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            elevation = dp(10).toFloat()
        }

        // Status Display
        statusTextView = TextView(this).apply {
            text = "⚪ Ready"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        container.addView(statusTextView)

        // Control Buttons Row
        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val startBtn = createCompactButton("▶", "Start") {
            startDetection()
        }
        val stopBtn = createCompactButton("⏹", "Stop") {
            stopDetection()
        }
        val refreshBtn = createCompactButton("🔄", "Flip") {
            flipBoard()
        }

        buttonsRow.addView(startBtn)
        buttonsRow.addView(stopBtn)
        buttonsRow.addView(refreshBtn)
        container.addView(buttonsRow)

        // Auto-Play Toggle
        val autoPlayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        val autoPlayCheckbox = CheckBox(this).apply {
            isChecked = isAutoPlayEnabled
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, isChecked ->
                isAutoPlayEnabled = isChecked
                updateStatus(if (isChecked) "🤖 Auto-Play ON" else "👀 Watching")
            }
        }
        
        val autoPlayLabel = TextView(this).apply {
            text = "Auto-Play"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, 0, 0)
        }
        
        autoPlayLayout.addView(autoPlayCheckbox)
        autoPlayLayout.addView(autoPlayLabel)
        container.addView(autoPlayLayout)

        // Close Button
        val closeBtn = createCompactButton("✖", "Close") {
            toggleExpanded()
        }
        container.addView(closeBtn)

        expandedOverlay = container
        windowManager?.addView(expandedOverlay, layoutParams)
    }

    private fun createCompactButton(icon: String, contentDesc: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = icon
            textSize = 18f
            contentDescription = contentDesc
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                dp(60),
                dp(50)
            ).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        }
    }

    private fun toggleExpanded() {
        if (isExpanded) {
            // Collapse
            expandedOverlay?.let { windowManager?.removeView(it) }
            expandedOverlay = null
            isExpanded = false
        } else {
            // Expand
            createExpandedOverlay()
            isExpanded = true
        }
    }

    private fun makeDraggable(view: View, layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (initialTouchX - event.rawX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        // Add flags to prevent black screen detection
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ChessAutomation",
            width,
            height,
            metrics.densityDpi,
            flags,
            imageReader?.surface,
            null,
            null
        )

        updateStatus("📸 Capture Ready")
        Log.d(TAG, "Screen capture initialized with anti-detection flags")
    }

    private fun startDetection() {
        isDetecting = true
        updateStatus("🔍 Detecting...")
        handler.post(detectionRunnable)
        Log.d(TAG, "Detection started with interval: ${detectionInterval}ms")
    }

    private fun stopDetection() {
        isDetecting = false
        handler.removeCallbacks(detectionRunnable)
        updateStatus("⚪ Stopped")
        Log.d(TAG, "Detection stopped")
    }

    private fun flipBoard() {
        isFlipped = !isFlipped
        saveSettings()
        updateStatus(if (isFlipped) "⬛ Black Bottom" else "⬜ White Bottom")
    }

    private fun captureScreen() {
        var fullBitmap: Bitmap? = null
        try {
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Log.w(TAG, "No image available")
                return
            }

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            // Use bitmap pool for memory efficiency
            fullBitmap = bitmapPool.obtain(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val boardBitmap = extractBoardArea(fullBitmap)
            bitmapPool.recycle(fullBitmap)
            
            if (previousBitmap != null) {
                val move = detectMove(previousBitmap!!, boardBitmap)
                if (move != null) {
                    onMoveDetected(move)
                }
                bitmapPool.recycle(previousBitmap)
            }
            
            previousBitmap = boardBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Capture error: ${e.message}")
            bitmapPool.recycle(fullBitmap)
        }
    }

    private fun extractBoardArea(fullBitmap: Bitmap): Bitmap {
        return try {
            val extracted = Bitmap.createBitmap(fullBitmap, boardX, boardY, boardSize, boardSize)
            extracted
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting board: ${e.message}")
            fullBitmap
        }
    }

    private fun detectMove(oldBoard: Bitmap, newBoard: Bitmap): String? {
        val squareSize = oldBoard.width / 8
        val changes = mutableListOf<Pair<Int, Int>>()

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val x = col * squareSize
                val y = row * squareSize
                
                if (hasSquareChanged(oldBoard, newBoard, x, y, squareSize)) {
                    changes.add(Pair(row, col))
                }
            }
        }

        // Standard move: exactly 2 squares changed
        if (changes.size == 2) {
            val from = changes[0]
            val to = changes[1]
            return squareToUCI(from.first, from.second) + 
                   squareToUCI(to.first, to.second)
        }

        return null
    }

    private fun hasSquareChanged(old: Bitmap, new: Bitmap, x: Int, y: Int, size: Int): Boolean {
        var diffPixels = 0
        val sampleRate = 4  // Sample every 4th pixel for performance
        val threshold = 0.12

        for (dy in 0 until size step sampleRate) {
            for (dx in 0 until size step sampleRate) {
                if (x + dx < old.width && y + dy < old.height &&
                    x + dx < new.width && y + dy < new.height) {
                    
                    val oldPixel = old.getPixel(x + dx, y + dy)
                    val newPixel = new.getPixel(x + dx, y + dy)
                    
                    if (colorDifference(oldPixel, newPixel) > 25) {
                        diffPixels++
                    }
                }
            }
        }

        val totalSamples = (size / sampleRate) * (size / sampleRate)
        return (diffPixels.toFloat() / totalSamples) > threshold
    }

    private fun colorDifference(color1: Int, color2: Int): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2)
    }

    private fun squareToUCI(row: Int, col: Int): String {
        val actualRow = if (isFlipped) row else 7 - row
        val actualCol = if (isFlipped) 7 - col else col
        
        val file = ('a' + actualCol).toString()
        val rank = (actualRow + 1).toString()
        
        return "$file$rank"
    }

    private fun onMoveDetected(move: String) {
        Log.d(TAG, "Move detected: $move")
        updateStatus("♟ Move: $move")
        
        // Send to Stockfish engine
        if (ngrokUrl.isNotEmpty()) {
            sendMoveToEngine(move)
        }
    }

    private fun sendMoveToEngine(move: String) {
        if (ngrokUrl.isEmpty()) {
            Log.w(TAG, "Ngrok URL not configured")
            return
        }

        Thread {
            try {
                val json = """{"move": "$move"}"""
                val body = json.toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url("$ngrokUrl/position")
                    .post(body)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Engine request failed: ${e.message}")
                        handler.post {
                            updateStatus("⚠️ Connection failed")
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            Log.d(TAG, "Engine response: $responseBody")
                            handler.post {
                                processEngineResponse(responseBody)
                            }
                        } else {
                            Log.w(TAG, "Engine error: ${response.code}")
                            handler.post {
                                updateStatus("⚠️ Engine error")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error sending to engine: ${e.message}")
            }
        }.start()
    }

    private fun processEngineResponse(response: String) {
        try {
            // Parse engine response (expecting JSON with "best_move" field)
            val bestMove = extractBestMove(response)
            if (bestMove != null && isAutoPlayEnabled) {
                executeMoveAutomatically(bestMove)
            } else if (bestMove != null) {
                updateStatus("💡 Best: $bestMove")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing response: ${e.message}")
        }
    }

    private fun extractBestMove(json: String): String? {
        return try {
            // Simple JSON parsing (in production, use proper JSON library)
            val pattern = """"best_move"\s*:\s*"([a-h][1-8][a-h][1-8][qrbn]?)"""".toRegex()
            pattern.find(json)?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract best move from: $json")
            null
        }
    }

    private fun executeMoveAutomatically(move: String) {
        if (move.length < 4) {
            Log.w(TAG, "Invalid move format: $move")
            return
        }

        val fromSquare = move.substring(0, 2)
        val toSquare = move.substring(2, 4)

        val fromCoords = touchSimulator.getSquareCoordinates(fromSquare, boardX, boardY, boardSize, isFlipped)
        val toCoords = touchSimulator.getSquareCoordinates(toSquare, boardX, boardY, boardSize, isFlipped)

        Log.d(TAG, "Auto-executing move: $move")
        updateStatus("🤖 Playing: $move")

        // Simulate drag gesture
        val success = touchSimulator.simulateDrag(
            fromCoords.first, fromCoords.second,
            toCoords.first, toCoords.second,
            300
        )

        if (!success) {
            Log.w(TAG, "Touch simulation failed - accessibility service may not be enabled")
            updateStatus("⚠️ Auto-play unavailable")
        }
    }

    private fun updateStatus(message: String) {
        handler.post {
            statusTextView?.text = message
            Log.d(TAG, "Status: $message")
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chess Automation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Chess move detection and automation service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chess Automation Active")
            .setContentText("Tap the robot icon to control")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDetection()
        compactOverlay?.let { windowManager?.removeView(it) }
        expandedOverlay?.let { windowManager?.removeView(it) }
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        bitmapPool.clear()
        bitmapPool.recycle(previousBitmap)
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        Log.d(TAG, "Service destroyed, resources cleaned up")
    }
}
