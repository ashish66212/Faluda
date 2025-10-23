package com.chesschat.app

import android.app.*
import android.content.ComponentName
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
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Improved Move Detection Overlay Service with COMPREHENSIVE LIVE LOGGING
 * All operations are logged to both LogCat and on-screen live log
 */
class MoveDetectionOverlayService : Service() {

    companion object {
        private const val TAG = "MoveDetection"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "chess_move_detection"
    }

    // Game State
    private var gameStarted = false
    private var playerColor: String? = null
    
    // UI Components
    private var windowManager: WindowManager? = null
    private var compactOverlay: View? = null
    private var expandedOverlay: View? = null
    private var isExpanded = false
    
    // Status Views
    private var statusTextView: TextView? = null
    private var liveLogTextView: TextView? = null
    private val logBuffer = StringBuilder()
    
    // Screen Capture
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // Memory Management
    private val bitmapPool = BitmapPool(maxPoolSize = 3)
    private var previousBitmap: Bitmap? = null
    
    // OpenCV-based board detection
    private val boardDetector = ChessBoardDetector()
    private var previousBoardState: Array<IntArray>? = null
    
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
    private var detectionInterval = 100L  // Optimized detection interval (100ms for balance of speed and accuracy)
    
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
        addLog("onCreate", "Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Load saved settings
        loadSettings()
        
        // Detect current chess app and apply profile
        detectCurrentApp()
        
        // Create compact overlay first
        createCompactOverlay()
        addLog("onCreate", "Overlay created successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        addLog("onStartCommand", "Service starting with intent")
        intent?.let {
            val resultCode = it.getIntExtra("resultCode", 0)
            val data = it.getParcelableExtra<Intent>("data")
            val x = it.getIntExtra("boardX", 50)
            val y = it.getIntExtra("boardY", 300)
            val size = it.getIntExtra("boardSize", 800)
            
            boardX = x
            boardY = y
            boardSize = size
            
            addLog("onStartCommand", "Board config: X=$x, Y=$y, Size=$size")
            saveSettings()
            
            if (resultCode != 0 && data != null) {
                addLog("onStartCommand", "Starting screen capture immediately (0ms)")
                // Start immediately - no delay for maximum responsiveness
                startScreenCapture(resultCode, data)
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
        addLog("loadSettings", "Server: $ngrokUrl")
        addLog("loadSettings", "Board: X=$boardX Y=$boardY Size=$boardSize Flipped=$isFlipped")
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("ChessChatPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("base_url", ngrokUrl)
            .putInt("board_x", boardX)
            .putInt("board_y", boardY)
            .putInt("board_size", boardSize)
            .putBoolean("board_flipped", isFlipped)
            .apply()
        addLog("saveSettings", "Settings saved (API: $ngrokUrl)")
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
                addLog("detectCurrentApp", "App: ${currentProfile.appName}")
                addLog("detectCurrentApp", "Interval: ${detectionInterval}ms")
            }
        } catch (e: Exception) {
            addLog("detectCurrentApp", "ERROR: ${e.message}")
        }
    }

    private fun minimizeOverlay() {
        compactOverlay?.let { windowManager?.removeView(it) }
        compactOverlay = null
        isExpanded = false
        createMinimizedButton()
        addLog("minimizeOverlay", "Overlay minimized to icon")
    }
    
    private fun createMinimizedButton() {
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

        val container = FrameLayout(this).apply {
            setBackgroundResource(android.R.drawable.btn_default)
            background.alpha = 200
            elevation = dp(10).toFloat()
        }

        val iconTextView = TextView(this).apply {
            text = "🤖"
            textSize = 32f
            gravity = Gravity.CENTER
            setOnClickListener {
                container.let { windowManager?.removeView(it) }
                createCompactOverlay()
            }
        }

        container.addView(iconTextView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        makeDraggable(container, layoutParams)
        windowManager?.addView(container, layoutParams)
    }
    
    private fun sendStartCommand() {
        addLog("sendStartCommand", "CALLED - Initiating game start")
        
        if (ngrokUrl.isEmpty()) {
            updateStatus("⚠️ No server URL")
            addLog("sendStartCommand", "FAILED - Server URL not configured!")
            Toast.makeText(this, "Configure server URL in main app first", Toast.LENGTH_LONG).show()
            return
        }

        addLog("sendStartCommand", "URL: $ngrokUrl/start")
        addLog("sendStartCommand", "Sending POST request...")
        updateStatus("🚀 Starting game...")

        Thread {
            try {
                val request = Request.Builder()
                    .url("$ngrokUrl/start")
                    .post("".toRequestBody(null))
                    .build()

                addLog("HTTP", "POST $ngrokUrl/start")
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        addLog("sendStartCommand", "FAILED - ${e.message}")
                        handler.post {
                            updateStatus("⚠️ Connection failed")
                            Toast.makeText(this@MoveDetectionOverlayService, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string() ?: "No response"
                        addLog("HTTP", "Response ${response.code}: $responseBody")
                        
                        if (response.isSuccessful) {
                            addLog("sendStartCommand", "SUCCESS - Game started")
                            handler.post {
                                gameStarted = true
                                updateStatus("✅ Game started!")
                                Toast.makeText(this@MoveDetectionOverlayService, "Game started!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            addLog("sendStartCommand", "FAILED - Server error ${response.code}")
                            handler.post {
                                updateStatus("⚠️ Server error")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                addLog("sendStartCommand", "EXCEPTION - ${e.message}")
                handler.post {
                    updateStatus("⚠️ Error")
                }
            }
        }.start()
    }
    
    private fun sendColorCommand(color: String) {
        addLog("sendColorCommand", "CALLED - Setting color to $color")
        
        if (ngrokUrl.isEmpty()) {
            updateStatus("⚠️ No server URL")
            addLog("sendColorCommand", "FAILED - No server URL")
            Toast.makeText(this, "Configure server URL first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!gameStarted) {
            updateStatus("⚠️ Start game first")
            addLog("sendColorCommand", "FAILED - Game not started")
            Toast.makeText(this, "Press Start button first", Toast.LENGTH_SHORT).show()
            return
        }

        addLog("sendColorCommand", "URL: $ngrokUrl/move")
        addLog("sendColorCommand", "Data: $color")
        updateStatus("🎨 Setting color: $color")

        Thread {
            try {
                val requestBody = color.toRequestBody(null)
                val request = Request.Builder()
                    .url("$ngrokUrl/move")
                    .post(requestBody)
                    .build()

                addLog("HTTP", "POST $ngrokUrl/move body=$color")
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        addLog("sendColorCommand", "FAILED - ${e.message}")
                        handler.post {
                            updateStatus("⚠️ Connection failed")
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string() ?: "No response"
                        addLog("HTTP", "Response ${response.code}: $responseBody")
                        
                        if (response.isSuccessful) {
                            addLog("sendColorCommand", "SUCCESS - Playing as $color")
                            addLog("sendColorCommand", "Engine response: $responseBody")
                            handler.post {
                                playerColor = color
                                updateStatus("✅ Playing as $color")
                                
                                // If engine made first move (when user chose black), execute it
                                if (color == "black" && isAutoPlayEnabled && responseBody.isNotEmpty()) {
                                    addLog("sendColorCommand", "Engine moved first: $responseBody")
                                    if (responseBody.matches("[a-h][1-8][a-h][1-8]".toRegex())) {
                                        executeMoveAutomatically(responseBody)
                                    }
                                }
                            }
                        } else {
                            addLog("sendColorCommand", "FAILED - Server error ${response.code}")
                            handler.post {
                                updateStatus("⚠️ Server error")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                addLog("sendColorCommand", "EXCEPTION - ${e.message}")
                handler.post {
                    updateStatus("⚠️ Error")
                }
            }
        }.start()
    }

    private fun createCompactOverlay() {
        addLog("createCompactOverlay", "Building overlay UI")
        
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
        layoutParams.y = dp(100)

        // Inflate the overlay layout
        val overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_compact, null)
        
        // Get all button references
        val startButton = overlayView.findViewById<Button>(R.id.startButton)
        val blackButton = overlayView.findViewById<Button>(R.id.blackButton)
        val whiteButton = overlayView.findViewById<Button>(R.id.whiteButton)
        val detectButton = overlayView.findViewById<Button>(R.id.detectButton)
        val stopButton = overlayView.findViewById<Button>(R.id.stopButton)
        val flipButton = overlayView.findViewById<Button>(R.id.flipButton)
        val updateApiButton = overlayView.findViewById<Button>(R.id.updateApiButton)
        val minimizeButton = overlayView.findViewById<Button>(R.id.minimizeButton)
        val autoPlayCheckbox = overlayView.findViewById<CheckBox>(R.id.autoPlayCheckbox)
        statusTextView = overlayView.findViewById(R.id.statusTextView)
        liveLogTextView = overlayView.findViewById(R.id.liveLogTextView)
        
        // Make log scrollable
        liveLogTextView?.movementMethod = ScrollingMovementMethod()
        
        // Set up button handlers
        startButton.setOnClickListener {
            addLog("UI", "Start button clicked")
            sendStartCommand()
        }
        
        blackButton.setOnClickListener {
            addLog("UI", "Black button clicked")
            sendColorCommand("black")
        }
        
        whiteButton.setOnClickListener {
            addLog("UI", "White button clicked")
            sendColorCommand("white")
        }
        
        detectButton.setOnClickListener {
            addLog("UI", "Detect button clicked")
            startDetection()
        }
        
        stopButton.setOnClickListener {
            addLog("UI", "Stop button clicked")
            stopDetection()
        }
        
        flipButton.setOnClickListener {
            addLog("UI", "Flip button clicked")
            flipBoard()
        }
        
        updateApiButton.setOnClickListener {
            addLog("UI", "Update API button clicked")
            showUpdateApiDialog()
        }
        
        minimizeButton.setOnClickListener {
            addLog("UI", "Minimize button clicked")
            minimizeOverlay()
        }
        
        autoPlayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            isAutoPlayEnabled = isChecked
            updateStatus(if (isChecked) "🤖 Auto-Play ON" else "👀 Watching")
            addLog("UI", "Auto-play ${if (isChecked) "ENABLED" else "DISABLED"}")
        }
        
        // Make draggable
        makeDraggable(overlayView, layoutParams)

        compactOverlay = overlayView
        isExpanded = true
        windowManager?.addView(compactOverlay, layoutParams)
        
        addLog("createCompactOverlay", "Overlay displayed successfully")
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
        addLog("startScreenCapture", "CALLED - Initializing screen capture")
        
        val metrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        addLog("startScreenCapture", "Screen: ${width}x${height}")
        
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
        addLog("startScreenCapture", "SUCCESS - Virtual display created")
    }

    private fun startDetection() {
        addLog("startDetection", "CALLED - Starting move detection")
        isDetecting = true
        updateStatus("🔍 Detecting...")
        handler.post(detectionRunnable)
        addLog("startDetection", "Detection loop started (interval: ${detectionInterval}ms)")
    }

    private fun stopDetection() {
        addLog("stopDetection", "CALLED - Stopping detection")
        isDetecting = false
        handler.removeCallbacks(detectionRunnable)
        updateStatus("⚪ Stopped")
        addLog("stopDetection", "Detection stopped")
    }
    
    /**
     * Resume detection IMMEDIATELY (0ms) after executing an automated move
     * Captures fresh baseline and starts detecting for opponent's response instantly
     */
    private fun resumeDetectionImmediately() {
        addLog("resumeDetectionImmediately", "=== INSTANT DETECTION START (0ms) ===")
        
        // Clear previous board state to start fresh
        previousBoardState = null
        addLog("resumeDetectionImmediately", "Cleared previous board state")
        
        // Start detection loop immediately - no delay
        isDetecting = true
        updateStatus("🔍 Detecting...")
        addLog("resumeDetectionImmediately", "Detection active with ${detectionInterval}ms interval")
        
        // Post immediately to start detection loop
        handler.post(detectionRunnable)
        
        addLog("resumeDetectionImmediately", "Detection started instantly - waiting for opponent move")
    }

    private fun flipBoard() {
        isFlipped = !isFlipped
        saveSettings()
        val orientation = if (isFlipped) "Black bottom" else "White bottom"
        updateStatus(if (isFlipped) "⬛ Black Bottom" else "⬜ White Bottom")
        addLog("flipBoard", "Board flipped - $orientation")
    }
    
    /**
     * Show dialog to update API endpoint URL
     */
    private fun showUpdateApiDialog() {
        addLog("showUpdateApiDialog", "Opening dialog to update API endpoint")
        
        handler.post {
            // Create EditText for URL input
            val input = android.widget.EditText(this)
            input.setText(ngrokUrl)
            input.hint = "https://your-ngrok-url.ngrok.io"
            input.setPadding(dp(16), dp(16), dp(16), dp(16))
            
            // Create AlertDialog
            val dialog = AlertDialog.Builder(this)
                .setTitle("Update API Endpoint")
                .setMessage("Enter your ngrok or API server URL:")
                .setView(input)
                .setPositiveButton("Update") { _, _ ->
                    val newUrl = input.text.toString().trim()
                    if (newUrl.isNotEmpty()) {
                        ngrokUrl = newUrl
                        saveSettings()
                        addLog("showUpdateApiDialog", "API endpoint updated to: $ngrokUrl")
                        updateStatus("🔗 API Updated")
                        Toast.makeText(this, "API endpoint updated!", Toast.LENGTH_SHORT).show()
                    } else {
                        addLog("showUpdateApiDialog", "Empty URL - no changes made")
                        Toast.makeText(this, "URL cannot be empty!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    addLog("showUpdateApiDialog", "Dialog cancelled")
                    dialog.cancel()
                }
                .create()
            
            // Set window type for overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            } else {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_PHONE)
            }
            
            dialog.show()
            addLog("showUpdateApiDialog", "Dialog displayed")
        }
    }

    private fun captureScreen() {
        var fullBitmap: Bitmap? = null
        try {
            addLog("captureScreen", "CALLED - Capturing screen with OpenCV detection")
            
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                addLog("captureScreen", "SKIPPED - No image available from imageReader")
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
            
            // Use OpenCV to detect board state
            addLog("captureScreen", "Analyzing board with OpenCV...")
            val currentBoardState = boardDetector.detectBoardState(boardBitmap)
            
            if (previousBoardState != null) {
                addLog("captureScreen", "Comparing board states to detect move")
                val move = boardDetector.detectMove(previousBoardState!!, currentBoardState, isFlipped)
                
                if (move != null) {
                    addLog("captureScreen", "MOVE DETECTED: $move - Sending to engine")
                    onMoveDetected(move)
                } else {
                    addLog("captureScreen", "No valid move detected")
                }
            } else {
                addLog("captureScreen", "FIRST CAPTURE - Establishing baseline board state")
            }
            
            previousBoardState = currentBoardState
            boardBitmap.recycle()
            addLog("captureScreen", "Board state saved for next comparison")
        } catch (e: Exception) {
            addLog("captureScreen", "ERROR - ${e.message}")
            e.printStackTrace()
            bitmapPool.recycle(fullBitmap)
        }
    }

    private fun extractBoardArea(fullBitmap: Bitmap): Bitmap {
        return try {
            val extracted = Bitmap.createBitmap(fullBitmap, boardX, boardY, boardSize, boardSize)
            extracted
        } catch (e: Exception) {
            addLog("extractBoardArea", "ERROR - ${e.message}")
            fullBitmap
        }
    }

    private fun onMoveDetected(move: String) {
        addLog("onMoveDetected", "OPPONENT MOVE DETECTED: $move")
        updateStatus("♟ Move: $move")
        
        // STOP detection immediately after detecting move
        addLog("onMoveDetected", "Stopping detection until next auto-play execution")
        stopDetection()
        
        // Send to Stockfish engine via Flask API
        if (ngrokUrl.isNotEmpty()) {
            addLog("onMoveDetected", "Sending to engine...")
            sendMoveToEngine(move)
        } else {
            addLog("onMoveDetected", "SKIPPED - No server URL")
        }
    }

    private fun sendMoveToEngine(move: String) {
        addLog("sendMoveToEngine", "CALLED - Move: $move")
        
        if (ngrokUrl.isEmpty()) {
            addLog("sendMoveToEngine", "FAILED - No ngrok URL")
            return
        }

        Thread {
            try {
                // Send as plain text body (matching Flask API)
                val requestBody = move.toRequestBody(null)
                
                val request = Request.Builder()
                    .url("$ngrokUrl/move")
                    .post(requestBody)
                    .build()

                addLog("HTTP", "POST $ngrokUrl/move body=$move")
                
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        addLog("sendMoveToEngine", "FAILED - ${e.message}")
                        handler.post {
                            updateStatus("⚠️ Connection failed")
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string() ?: ""
                        addLog("HTTP", "Response ${response.code}: $responseBody")
                        
                        if (response.isSuccessful && responseBody.isNotEmpty()) {
                            addLog("sendMoveToEngine", "Engine response: $responseBody")
                            handler.post {
                                processEngineResponse(responseBody)
                            }
                        } else {
                            addLog("sendMoveToEngine", "FAILED - Server error ${response.code}")
                            handler.post {
                                updateStatus("⚠️ Engine error")
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                addLog("sendMoveToEngine", "EXCEPTION - ${e.message}")
            }
        }.start()
    }

    private fun processEngineResponse(response: String) {
        addLog("processEngineResponse", "CALLED - Response: $response")
        
        try {
            // Flask API returns plain text like "e2e4" or "Checkmate, I win!"
            // Extract move if present (format: a-h, 1-8, a-h, 1-8, optional promotion)
            val movePattern = "[a-h][1-8][a-h][1-8][qrbn]?".toRegex()
            val bestMove = movePattern.find(response)?.value
            
            if (bestMove != null) {
                addLog("processEngineResponse", "Best move extracted: $bestMove")
                
                if (isAutoPlayEnabled) {
                    addLog("processEngineResponse", "Auto-play enabled - executing move")
                    executeMoveAutomatically(bestMove)
                } else {
                    updateStatus("💡 Best: $bestMove")
                    addLog("processEngineResponse", "Suggestion: $bestMove (auto-play OFF)")
                }
            } else {
                addLog("processEngineResponse", "No move found in response")
                updateStatus("ℹ️ $response")
            }
        } catch (e: Exception) {
            addLog("processEngineResponse", "ERROR - ${e.message}")
        }
    }

    private fun executeMoveAutomatically(move: String) {
        addLog("executeMoveAutomatically", "CALLED - Move: $move")
        
        if (move.length < 4) {
            addLog("executeMoveAutomatically", "FAILED - Invalid format (length=${move.length})")
            updateStatus("⚠️ Invalid move")
            return
        }

        val fromSquare = move.substring(0, 2)
        val toSquare = move.substring(2, 4)

        addLog("executeMoveAutomatically", "From: $fromSquare, To: $toSquare")
        addLog("executeMoveAutomatically", "Board: X=$boardX Y=$boardY Size=$boardSize Flipped=$isFlipped")

        val fromCoords = touchSimulator.getSquareCoordinates(fromSquare, boardX, boardY, boardSize, isFlipped)
        val toCoords = touchSimulator.getSquareCoordinates(toSquare, boardX, boardY, boardSize, isFlipped)

        addLog("executeMoveAutomatically", "From coords: (${fromCoords.first}, ${fromCoords.second})")
        addLog("executeMoveAutomatically", "To coords: (${toCoords.first}, ${toCoords.second})")

        updateStatus("🤖 Playing: $move")

        // Check if accessibility service is enabled using proper Android API
        if (!isAccessibilityServiceEnabled()) {
            addLog("executeMoveAutomatically", "FAILED - Accessibility service NOT enabled!")
            addLog("executeMoveAutomatically", "Go to Settings > Accessibility > Chess Automation")
            updateStatus("⚠️ Enable accessibility")
            Toast.makeText(this, "Enable accessibility service first!", Toast.LENGTH_LONG).show()
            return
        }

        addLog("executeMoveAutomatically", "Accessibility service OK - using CLICK format (tap-tap)")
        
        // Simulate CLICK format: tap from square, then tap to square
        // First tap (select piece)
        val firstTapSuccess = touchSimulator.simulateTouch(
            fromCoords.first, fromCoords.second
        )
        
        if (!firstTapSuccess) {
            addLog("executeMoveAutomatically", "FAILED - First tap failed at $fromSquare")
            updateStatus("⚠️ First tap failed")
            Toast.makeText(this, "Touch failed - check accessibility", Toast.LENGTH_LONG).show()
            return
        }
        
        addLog("executeMoveAutomatically", "First tap SUCCESS at $fromSquare")
        
        // Minimal delay between taps (200ms to allow chess app to register selection)
        handler.postDelayed({
            addLog("executeMoveAutomatically", "Executing second tap at $toSquare")
            
            // Second tap (move piece to destination)
            val secondTapSuccess = touchSimulator.simulateTouch(
                toCoords.first, toCoords.second
            )
            
            if (secondTapSuccess) {
                addLog("executeMoveAutomatically", "SUCCESS - Move executed: $move")
                updateStatus("✅ Played: $move")
                
                // Start detection IMMEDIATELY (0ms delay) after move execution
                addLog("executeMoveAutomatically", "Restarting detection NOW (0ms delay)")
                resumeDetectionImmediately()
            } else {
                addLog("executeMoveAutomatically", "FAILED - Second tap failed")
                addLog("executeMoveAutomatically", "Possible reasons:")
                addLog("executeMoveAutomatically", "  1. Accessibility service crashed")
                addLog("executeMoveAutomatically", "  2. Gesture queue full")
                addLog("executeMoveAutomatically", "  3. Coordinates out of bounds")
                updateStatus("⚠️ Second tap failed")
                Toast.makeText(this, "Second tap failed - check accessibility", Toast.LENGTH_LONG).show()
            }
        }, 200)
    }
    
    /**
     * Temporarily pause detection to avoid detecting our own automated move
     * Clears previous bitmap so detection starts fresh when resumed
     * ALWAYS resumes detection after delay (auto-starts if not running)
     */
    private fun pauseDetectionTemporarily(delayMs: Long) {
        addLog("pauseDetectionTemporarily", "=== PAUSING DETECTION ===")
        addLog("pauseDetectionTemporarily", "Duration: ${delayMs}ms")
        
        // Stop detection if it was running
        if (isDetecting) {
            isDetecting = false
            handler.removeCallbacks(detectionRunnable)
            addLog("pauseDetectionTemporarily", "Detection was active - paused")
        } else {
            addLog("pauseDetectionTemporarily", "Detection was not active - will start after pause")
        }
        
        // Clear previous board state so detection starts fresh after our automated move
        addLog("pauseDetectionTemporarily", "Clearing previousBoardState to reset comparison")
        previousBoardState = null
        addLog("pauseDetectionTemporarily", "Waiting for opponent to make their move...")
        
        // ALWAYS resume detection after delay (even if it wasn't running before)
        handler.postDelayed({
            addLog("pauseDetectionTemporarily", "=== RESUMING DETECTION ===")
            addLog("pauseDetectionTemporarily", "Next capture will establish baseline")
            addLog("pauseDetectionTemporarily", "Following capture will detect opponent move")
            isDetecting = true
            updateStatus("🔍 Detecting...")
            handler.post(detectionRunnable)
        }, delayMs)
    }

    /**
     * Properly check if accessibility service is enabled using Android API
     * This is more reliable than checking the instance variable
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        // Use ComponentName to get the properly formatted service name
        val componentName = ComponentName(this, ChessAccessibilityService::class.java)
        val service = componentName.flattenToString()
        
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val isEnabled = enabledServices?.contains(service) == true
        
        addLog("isAccessibilityServiceEnabled", "Checking service: $service")
        addLog("isAccessibilityServiceEnabled", "Enabled services: $enabledServices")
        addLog("isAccessibilityServiceEnabled", "Result: $isEnabled")
        
        return isEnabled
    }

    private fun updateStatus(message: String) {
        handler.post {
            statusTextView?.text = message
        }
    }
    
    private fun addLog(function: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val logLine = "[$timestamp] $function: $message\n"
        
        // Log to LogCat
        Log.d(TAG, "$function: $message")
        
        // Add to buffer
        logBuffer.append(logLine)
        
        // Keep only last 100 lines
        val lines = logBuffer.lines()
        if (lines.size > 100) {
            logBuffer.clear()
            logBuffer.append(lines.takeLast(100).joinToString("\n"))
        }
        
        // Update UI
        handler.post {
            liveLogTextView?.text = logBuffer.toString()
            // Auto-scroll to bottom
            liveLogTextView?.let { textView ->
                val scrollAmount = textView.layout?.getLineTop(textView.lineCount) ?: 0
                if (scrollAmount > textView.height) {
                    textView.scrollTo(0, scrollAmount - textView.height)
                }
            }
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
        addLog("onDestroy", "Service shutting down")
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
        addLog("onDestroy", "Cleanup complete")
    }
}
