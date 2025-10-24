package com.chesschat.app

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Advanced chess board detector using OpenCV Frame Differencing with COLOR-BASED detection
 * Detects moves by analyzing piece colors (Black/White) and board state changes
 */
class ChessBoardDetector {
    
    companion object {
        private const val TAG = "ChessBoardDetector"
        
        init {
            try {
                System.loadLibrary("opencv_java4")
                Log.d(TAG, "OpenCV loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "OpenCV load failed: ${e.message}")
            }
        }
    }
    
    /**
     * Data classes for move classification
     */
    data class BlackMove(val fromSquare: String, val toSquare: String) {
        override fun toString() = "Black($fromSquare->$toSquare)"
        fun toUCI() = fromSquare + toSquare
    }
    
    data class WhiteMove(val fromSquare: String, val toSquare: String) {
        override fun toString() = "White($fromSquare->$toSquare)"
        fun toUCI() = fromSquare + toSquare
    }
    
    /**
     * Square color state: Empty (0), White (1), Black (2)
     */
    enum class PieceColor {
        EMPTY, WHITE, BLACK
    }
    
    /**
     * Board state: 8x8 array of piece colors
     */
    private var previousBoardState: Array<Array<PieceColor>>? = null
    
    /**
     * Data class to represent a changed region
     */
    data class ChangedRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )
    
    /**
     * Data class for automatic board configuration
     */
    data class BoardConfig(
        val x: Int,
        val y: Int,
        val size: Int,
        val isWhiteBottom: Boolean
    )
    
    /**
     * AUTOMATIC BOARD DETECTION
     * Detects chessboard position and orientation automatically
     * Returns BoardConfig with X, Y, Size, and orientation
     */
    fun detectBoardAutomatically(fullScreenBitmap: Bitmap): BoardConfig? {
        try {
            Log.d(TAG, "Starting automatic board detection...")
            Log.d(TAG, "Image size: ${fullScreenBitmap.width}x${fullScreenBitmap.height}")
            
            val mat = bitmapToMat(fullScreenBitmap)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            val edges = Mat()
            Imgproc.Canny(gray, edges, 30.0, 100.0)
            
            val lines = Mat()
            Imgproc.HoughLinesP(
                edges, lines, 1.0, Math.PI / 180, 80, 200.0, 20.0
            )
            
            val horizontalLines = mutableListOf<Int>()
            val verticalLines = mutableListOf<Int>()
            
            for (i in 0 until lines.rows()) {
                val line = lines.get(i, 0)
                val x1 = line[0].toInt()
                val y1 = line[1].toInt()
                val x2 = line[2].toInt()
                val y2 = line[3].toInt()
                
                val length = Math.sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble())
                if (length < 100) continue
                
                val angle = Math.abs(Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()) * 180 / Math.PI)
                
                when {
                    angle < 5 || angle > 175 -> {
                        horizontalLines.add(Math.min(y1, y2))
                        horizontalLines.add(Math.max(y1, y2))
                    }
                    angle in 85.0..95.0 -> {
                        verticalLines.add(Math.min(x1, x2))
                        verticalLines.add(Math.max(x1, x2))
                    }
                }
            }
            
            val config = if (horizontalLines.size >= 2 && verticalLines.size >= 2) {
                horizontalLines.sort()
                verticalLines.sort()
                
                val boardTop = horizontalLines.first()
                val boardBottom = horizontalLines.last()
                val boardLeft = verticalLines.first()
                val boardRight = verticalLines.last()
                
                val boardWidth = boardRight - boardLeft
                val boardHeight = boardBottom - boardTop
                val boardSize = Math.min(boardWidth, boardHeight)
                
                Log.d(TAG, "Line-based detection: X=$boardLeft, Y=$boardTop, Size=$boardSize")
                
                if (boardSize > 400) {
                    val isWhiteBottom = detectOrientation(fullScreenBitmap, boardLeft, boardTop, boardSize)
                    BoardConfig(boardLeft, boardTop, boardSize, isWhiteBottom)
                } else {
                    Log.d(TAG, "Board too small, using fallback")
                    detectBoardFallback(fullScreenBitmap)
                }
            } else {
                Log.d(TAG, "Not enough lines, using fallback")
                detectBoardFallback(fullScreenBitmap)
            }
            
            gray.release()
            edges.release()
            lines.release()
            mat.release()
            
            return config
            
        } catch (e: Exception) {
            Log.e(TAG, "Automatic board detection failed: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * IMPROVED Fallback board detection using intelligent region analysis
     * Analyzes the screen to find the chessboard based on color patterns
     */
    private fun detectBoardFallback(fullScreenBitmap: Bitmap): BoardConfig {
        val width = fullScreenBitmap.width
        val height = fullScreenBitmap.height
        
        Log.d(TAG, "Running improved fallback detection on ${width}x${height} image")
        
        try {
            val mat = bitmapToMat(fullScreenBitmap)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            val bestBoardX = (width * 0.014).toInt()
            val bestBoardSize = (width * 0.972).toInt()
            var bestBoardY = (height * 0.22).toInt()
            var maxVariance = 0.0
            
            val searchStartY = (height * 0.15).toInt()
            val searchEndY = (height * 0.35).toInt()
            val searchStep = 10
            
            Log.d(TAG, "Searching for board between Y=$searchStartY and Y=$searchEndY")
            
            for (testY in searchStartY..searchEndY step searchStep) {
                if (testY + bestBoardSize > height) {
                    break
                }
                
                try {
                    val testRegion = Mat(gray, Rect(bestBoardX, testY, bestBoardSize, bestBoardSize))
                    
                    val mean = MatOfDouble()
                    val stdDev = MatOfDouble()
                    Core.meanStdDev(testRegion, mean, stdDev)
                    val variance = stdDev.get(0, 0)[0]
                    
                    Log.d(TAG, "Y=$testY: variance=$variance")
                    
                    if (variance > maxVariance && variance > 15.0) {
                        maxVariance = variance
                        bestBoardY = testY
                    }
                    
                    testRegion.release()
                    mean.release()
                    stdDev.release()
                } catch (e: Exception) {
                    Log.d(TAG, "Error testing Y=$testY: ${e.message}")
                }
            }
            
            gray.release()
            mat.release()
            
            Log.d(TAG, "Best board position: X=$bestBoardX, Y=$bestBoardY, Size=$bestBoardSize (variance=$maxVariance)")
            
            val isWhiteBottom = detectOrientation(fullScreenBitmap, bestBoardX, bestBoardY, bestBoardSize)
            return BoardConfig(bestBoardX, bestBoardY, bestBoardSize, isWhiteBottom)
            
        } catch (e: Exception) {
            Log.e(TAG, "Improved fallback failed, using simple fallback: ${e.message}")
            
            val boardX = (width * 0.014).toInt()
            val boardY = (height * 0.22).toInt()
            val boardSize = (width * 0.972).toInt()
            
            Log.d(TAG, "Simple fallback: X=$boardX, Y=$boardY, Size=$boardSize")
            
            val isWhiteBottom = detectOrientation(fullScreenBitmap, boardX, boardY, boardSize)
            return BoardConfig(boardX, boardY, boardSize, isWhiteBottom)
        }
    }
    
    /**
     * Detect board orientation (white pieces on bottom?)
     */
    private fun detectOrientation(bitmap: Bitmap, boardX: Int, boardY: Int, boardSize: Int): Boolean {
        try {
            val squareSize = boardSize / 8
            
            val bottomY = boardY + 6 * squareSize
            val topY = boardY
            
            val bottomRegion = Bitmap.createBitmap(
                bitmap, 
                boardX, 
                bottomY, 
                boardSize, 
                2 * squareSize
            )
            val topRegion = Bitmap.createBitmap(
                bitmap,
                boardX,
                topY,
                boardSize,
                2 * squareSize
            )
            
            val bottomMat = bitmapToMat(bottomRegion)
            val topMat = bitmapToMat(topRegion)
            
            val bottomGray = Mat()
            val topGray = Mat()
            Imgproc.cvtColor(bottomMat, bottomGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(topMat, topGray, Imgproc.COLOR_RGBA2GRAY)
            
            val bottomMean = Core.mean(bottomGray).`val`[0]
            val topMean = Core.mean(topGray).`val`[0]
            
            val isWhiteBottom = bottomMean > topMean
            
            Log.d(TAG, "Orientation: Bottom brightness=$bottomMean, Top brightness=$topMean")
            Log.d(TAG, "White pieces on bottom: $isWhiteBottom")
            
            bottomRegion.recycle()
            topRegion.recycle()
            bottomMat.release()
            topMat.release()
            bottomGray.release()
            topGray.release()
            
            return isWhiteBottom
            
        } catch (e: Exception) {
            Log.e(TAG, "Orientation detection failed: ${e.message}")
            return true
        }
    }
    
    /**
     * COLOR-BASED MOVE DETECTION
     * Detects moves by analyzing piece colors and board state changes
     * Can detect both Black and White moves in a single frame
     */
    fun detectMovesWithColor(
        previousMat: Mat,
        currentMat: Mat,
        boardWidth: Int,
        boardHeight: Int,
        isFlipped: Boolean
    ): Pair<BlackMove?, WhiteMove?> {
        try {
            val squareWidth = boardWidth / 8
            val squareHeight = boardHeight / 8
            
            // Detect current board state (color of each square)
            val currentBoardState = detectBoardStateWithColor(currentMat, squareWidth, squareHeight)
            
            // Initialize previous state if first run
            if (previousBoardState == null) {
                previousBoardState = currentBoardState
                Log.d(TAG, "First frame - establishing baseline board state")
                return Pair(null, null)
            }
            
            // Find changes between previous and current state
            val changes = findBoardChanges(previousBoardState!!, currentBoardState)
            
            if (changes.isEmpty()) {
                previousBoardState = currentBoardState
                return Pair(null, null)
            }
            
            Log.d(TAG, "Detected ${changes.size} square changes")
            
            // Classify changes into Black and White moves
            val blackMove = findMoveForColor(changes, PieceColor.BLACK, isFlipped)
            val whiteMove = findMoveForColor(changes, PieceColor.WHITE, isFlipped)
            
            // Update previous state
            previousBoardState = currentBoardState
            
            if (blackMove != null) {
                Log.d(TAG, "Black move detected: $blackMove")
            }
            if (whiteMove != null) {
                Log.d(TAG, "White move detected: $whiteMove")
            }
            
            return Pair(blackMove, whiteMove)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in color-based detection: ${e.message}")
            e.printStackTrace()
            return Pair(null, null)
        }
    }
    
    /**
     * Detect the color state of each square on the board
     */
    private fun detectBoardStateWithColor(
        mat: Mat,
        squareWidth: Int,
        squareHeight: Int
    ): Array<Array<PieceColor>> {
        val state = Array(8) { Array(8) { PieceColor.EMPTY } }
        
        val gray = Mat()
        val hsv = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_RGB2HSV)
        
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val x = col * squareWidth
                val y = row * squareHeight
                val w = squareWidth
                val h = squareHeight
                
                val squareGray = Mat(gray, Rect(x, y, w, h))
                val squareHSV = Mat(hsv, Rect(x, y, w, h))
                
                // Check if square is occupied (has piece texture/complexity)
                val mean = MatOfDouble()
                val stdDev = MatOfDouble()
                Core.meanStdDev(squareGray, mean, stdDev)
                val variance = stdDev.get(0, 0)[0]
                
                if (variance > 12.0) {
                    // Square is occupied - determine piece color
                    val brightness = Core.mean(squareGray).`val`[0]
                    
                    // Use HSV to better distinguish piece colors from board colors
                    val hsvMean = Core.mean(squareHSV)
                    val saturation = hsvMean.`val`[1]
                    
                    // White pieces: high brightness, low saturation
                    // Black pieces: low brightness
                    state[row][col] = if (brightness > 100.0 && saturation < 80.0) {
                        PieceColor.WHITE
                    } else {
                        PieceColor.BLACK
                    }
                }
                
                squareGray.release()
                squareHSV.release()
                mean.release()
                stdDev.release()
            }
        }
        
        gray.release()
        hsv.release()
        
        return state
    }
    
    /**
     * Find changes between two board states
     */
    private fun findBoardChanges(
        prevState: Array<Array<PieceColor>>,
        currState: Array<Array<PieceColor>>
    ): List<Triple<Int, Int, Pair<PieceColor, PieceColor>>> {
        val changes = mutableListOf<Triple<Int, Int, Pair<PieceColor, PieceColor>>>()
        
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (prevState[row][col] != currState[row][col]) {
                    changes.add(Triple(row, col, Pair(prevState[row][col], currState[row][col])))
                }
            }
        }
        
        return changes
    }
    
    /**
     * Find move for a specific color from the list of changes
     */
    private fun findMoveForColor(
        changes: List<Triple<Int, Int, Pair<PieceColor, PieceColor>>>,
        color: PieceColor,
        isFlipped: Boolean
    ): Any? {
        // Find "from" square (where piece was removed)
        val fromSquares = changes.filter { it.third.first == color && it.third.second == PieceColor.EMPTY }
        
        // Find "to" square (where piece was added)
        val toSquares = changes.filter { it.third.first == PieceColor.EMPTY && it.third.second == color }
        
        if (fromSquares.isEmpty() || toSquares.isEmpty()) {
            return null
        }
        
        // Take the first match (closest squares for castling/en passant)
        val from = fromSquares[0]
        val to = toSquares[0]
        
        val fromUCI = squareToUCI(from.first, from.second, isFlipped)
        val toUCI = squareToUCI(to.first, to.second, isFlipped)
        
        return if (color == PieceColor.BLACK) {
            BlackMove(fromUCI, toUCI)
        } else {
            WhiteMove(fromUCI, toUCI)
        }
    }
    
    /**
     * NEW OPENCV FRAME DIFFERENCING LOGIC
     * Analyzes two frames (previous and current) to detect visual changes
     * This is the Android translation of the web OpenCV logic
     */
    fun detectMovesWithOpenCV(
        previousMat: Mat?,
        currentMat: Mat
    ): List<ChangedRegion> {
        try {
            if (previousMat == null) {
                Log.d(TAG, "No previous frame - establishing baseline")
                return emptyList()
            }
            
            val startTime = System.currentTimeMillis()
            
            // Step 1: Convert both frames to grayscale
            val prevGray = Mat()
            val currGray = Mat()
            Imgproc.cvtColor(previousMat, prevGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(currentMat, currGray, Imgproc.COLOR_RGBA2GRAY)
            
            // Step 2: Calculate absolute difference between frames
            val diff = Mat()
            Core.absdiff(prevGray, currGray, diff)
            
            // Step 3: Apply threshold to create binary image
            val threshold = Mat()
            Imgproc.threshold(diff, threshold, 30.0, 255.0, Imgproc.THRESH_BINARY)
            
            // Step 4: Morphological operations to reduce noise
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(5.0, 5.0)
            )
            val morphed = Mat()
            Imgproc.morphologyEx(threshold, morphed, Imgproc.MORPH_CLOSE, kernel)
            
            // Step 5: Find contours of changed regions
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                morphed,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )
            
            // Step 6: Extract bounding rectangles for each contour
            val changedRegions = mutableListOf<ChangedRegion>()
            
            for (i in 0 until contours.size) {
                val contour = contours[i]
                val area = Imgproc.contourArea(contour)
                
                // Filter out small noise (area threshold)
                if (area > 100) {
                    val rect = Imgproc.boundingRect(contour)
                    changedRegions.add(
                        ChangedRegion(
                            x = rect.x,
                            y = rect.y,
                            width = rect.width,
                            height = rect.height
                        )
                    )
                }
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "OpenCV detected ${changedRegions.size} changed regions in ${processingTime}ms")
            
            // Step 7: Clean up memory
            prevGray.release()
            currGray.release()
            diff.release()
            threshold.release()
            kernel.release()
            morphed.release()
            hierarchy.release()
            for (contour in contours) {
                contour.release()
            }
            
            return changedRegions
            
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV detection error: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * Convert bitmap to OpenCV Mat
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }
    
    /**
     * Detect chess move from changed regions
     * Maps pixel coordinates to chess squares and identifies the move
     */
    fun detectMoveFromRegions(
        changedRegions: List<ChangedRegion>,
        boardWidth: Int,
        boardHeight: Int,
        isFlipped: Boolean
    ): String? {
        if (changedRegions.isEmpty()) {
            Log.d(TAG, "No changed regions detected")
            return null
        }
        
        val squareWidth = boardWidth / 8
        val squareHeight = boardHeight / 8
        
        // Map regions to chess squares
        val affectedSquares = mutableSetOf<Pair<Int, Int>>()
        
        for (region in changedRegions) {
            // Calculate center of region
            val centerX = region.x + region.width / 2
            val centerY = region.y + region.height / 2
            
            // Convert to chess square coordinates
            val file = centerX / squareWidth
            val rank = centerY / squareHeight
            
            // Validate coordinates
            if (file in 0..7 && rank in 0..7) {
                affectedSquares.add(Pair(rank, file))
                
                val square = squareToUCI(rank, file, isFlipped)
                val confidence = (region.width * region.height).toFloat() / (squareWidth * squareHeight)
                Log.d(TAG, "Region mapped to square: $square (confidence: ${String.format("%.1f", confidence * 100)}%)")
            }
        }
        
        Log.d(TAG, "Total affected squares: ${affectedSquares.size}")
        
        // A valid move typically affects 2 squares (from and to)
        // Or 3-4 for special moves (castling, en passant)
        when (affectedSquares.size) {
            0 -> {
                Log.d(TAG, "No valid squares detected")
                return null
            }
            
            1 -> {
                Log.d(TAG, "Only 1 square changed - likely noise or animation")
                return null
            }
            
            2 -> {
                // Standard move: from square to square
                val squares = affectedSquares.toList()
                val from = squareToUCI(squares[0].first, squares[0].second, isFlipped)
                val to = squareToUCI(squares[1].first, squares[1].second, isFlipped)
                val move = from + to
                Log.d(TAG, "UCI Move detected: $move")
                return move
            }
            
            3, 4 -> {
                // Possible castling or en passant
                Log.d(TAG, "Special move detected (${affectedSquares.size} squares)")
                val squares = affectedSquares.toList()
                if (squares.size >= 2) {
                    val from = squareToUCI(squares[0].first, squares[0].second, isFlipped)
                    val to = squareToUCI(squares[1].first, squares[1].second, isFlipped)
                    val move = from + to
                    Log.d(TAG, "UCI Move detected (special): $move")
                    return move
                }
                return null
            }
            
            else -> {
                Log.d(TAG, "Too many squares changed (${affectedSquares.size}) - likely animation or misdetection")
                return null
            }
        }
    }
    
    /**
     * Convert row/col coordinates to UCI notation (e.g., e2, e4)
     * Handles board flipping
     */
    private fun squareToUCI(row: Int, col: Int, isFlipped: Boolean): String {
        val actualRow: Int
        val actualCol: Int
        
        if (isFlipped) {
            actualRow = row
            actualCol = 7 - col
        } else {
            actualRow = 7 - row
            actualCol = col
        }
        
        val file = ('a' + actualCol).toString()
        val rank = (actualRow + 1).toString()
        
        return "$file$rank"
    }
    
    /**
     * IMPROVED: Detect moves by analyzing square-by-square changes
     * More accurate than contour-based approach for chess move detection
     */
    fun detectMovesSquareBySquare(
        previousMat: Mat,
        currentMat: Mat,
        boardWidth: Int,
        boardHeight: Int,
        isFlipped: Boolean
    ): List<String> {
        try {
            val squareWidth = boardWidth / 8
            val squareHeight = boardHeight / 8
            
            val prevGray = Mat()
            val currGray = Mat()
            Imgproc.cvtColor(previousMat, prevGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(currentMat, currGray, Imgproc.COLOR_RGBA2GRAY)
            
            val removedSquares = mutableListOf<Pair<Int, Int>>()
            val addedSquares = mutableListOf<Pair<Int, Int>>()
            
            var maxDiffMean = 0.0
            var maxStdDiff = 0.0
            var changesDetected = 0
            
            // Analyze each square individually
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val x = col * squareWidth
                    val y = row * squareHeight
                    val w = squareWidth
                    val h = squareHeight
                    
                    // Extract square regions
                    val prevSquare = Mat(prevGray, Rect(x, y, w, h))
                    val currSquare = Mat(currGray, Rect(x, y, w, h))
                    
                    // Calculate absolute difference
                    val diff = Mat()
                    Core.absdiff(prevSquare, currSquare, diff)
                    val diffMean = Core.mean(diff).`val`[0]
                    
                    // Calculate standard deviation (indicates piece presence)
                    val prevMean = MatOfDouble()
                    val prevStdDev = MatOfDouble()
                    val currMean = MatOfDouble()
                    val currStdDev = MatOfDouble()
                    
                    Core.meanStdDev(prevSquare, prevMean, prevStdDev)
                    Core.meanStdDev(currSquare, currMean, currStdDev)
                    
                    val prevStd = prevStdDev.get(0, 0)[0]
                    val currStd = currStdDev.get(0, 0)[0]
                    val stdDiff = currStd - prevStd
                    
                    // Track maximum values for debugging
                    if (diffMean > maxDiffMean) maxDiffMean = diffMean
                    if (abs(stdDiff) > maxStdDiff) maxStdDiff = abs(stdDiff)
                    
                    // IMPROVED: Lowered thresholds for better detection
                    if (diffMean > 5.0) {  // Lowered from 10.0 to 5.0
                        changesDetected++
                        if (stdDiff < -4.0) {  // Lowered from -8.0 to -4.0
                            // Piece removed (texture decreased)
                            removedSquares.add(Pair(row, col))
                            Log.d(TAG, "Square ${squareToUCI(row, col, isFlipped)}: piece removed (diffMean=${"%.2f".format(diffMean)}, stdDiff=${"%.2f".format(stdDiff)})")
                        } else if (stdDiff > 4.0) {  // Lowered from 8.0 to 4.0
                            // Piece added (texture increased)
                            addedSquares.add(Pair(row, col))
                            Log.d(TAG, "Square ${squareToUCI(row, col, isFlipped)}: piece added (diffMean=${"%.2f".format(diffMean)}, stdDiff=${"%.2f".format(stdDiff)})")
                        } else {
                            // Change detected but not classified
                            Log.d(TAG, "Square ${squareToUCI(row, col, isFlipped)}: change detected but inconclusive (diffMean=${"%.2f".format(diffMean)}, stdDiff=${"%.2f".format(stdDiff)})")
                        }
                    }
                    
                    // Clean up
                    prevSquare.release()
                    currSquare.release()
                    diff.release()
                    prevMean.release()
                    prevStdDev.release()
                    currMean.release()
                    currStdDev.release()
                }
            }
            
            Log.d(TAG, "Detection summary: ${changesDetected} squares with changes (threshold: diffMean>5.0)")
            Log.d(TAG, "  Max diffMean: ${"%.2f".format(maxDiffMean)}, Max stdDiff: ${"%.2f".format(maxStdDiff)}")
            Log.d(TAG, "  Removed: ${removedSquares.size} squares, Added: ${addedSquares.size} squares")
            
            // Match removed squares with added squares to form moves
            val moves = mutableListOf<String>()
            val usedAdded = mutableSetOf<Int>()
            
            for (removed in removedSquares) {
                // Find closest added square (Manhattan distance)
                var bestAddIdx = -1
                var bestDist = Int.MAX_VALUE
                
                for ((idx, added) in addedSquares.withIndex()) {
                    if (idx in usedAdded) continue
                    
                    val dist = Math.abs(removed.first - added.first) + 
                              Math.abs(removed.second - added.second)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestAddIdx = idx
                    }
                }
                
                if (bestAddIdx >= 0) {
                    usedAdded.add(bestAddIdx)
                    val added = addedSquares[bestAddIdx]
                    
                    val fromSquare = squareToUCI(removed.first, removed.second, isFlipped)
                    val toSquare = squareToUCI(added.first, added.second, isFlipped)
                    val move = fromSquare + toSquare
                    
                    moves.add(move)
                    Log.d(TAG, "Move detected: $move")
                }
            }
            
            prevGray.release()
            currGray.release()
            
            return moves
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in square-by-square detection: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }
    
    /**
     * Reset board state tracking
     */
    fun resetBoardState() {
        previousBoardState = null
        Log.d(TAG, "Board state reset")
    }
}
