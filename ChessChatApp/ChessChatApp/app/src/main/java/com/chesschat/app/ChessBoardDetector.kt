package com.chesschat.app

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Advanced chess board detector using OpenCV Frame Differencing
 * Uses computer vision to detect visual changes between frames
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
     * Data class to represent a changed region
     */
    data class ChangedRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    )
    
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
     * LEGACY METHODS - Kept for backward compatibility but not used
     * These methods use the old piece-detection approach
     */
    
    /**
     * Extract 64 squares from the board bitmap
     */
    fun extractSquares(boardBitmap: Bitmap): List<Bitmap> {
        val squares = mutableListOf<Bitmap>()
        val squareSize = boardBitmap.width / 8
        
        try {
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val x = col * squareSize
                    val y = row * squareSize
                    
                    val square = Bitmap.createBitmap(
                        boardBitmap,
                        x, y,
                        squareSize, squareSize
                    )
                    squares.add(square)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting squares: ${e.message}")
        }
        
        return squares
    }
    
    /**
     * Detect if a square is occupied by analyzing color variance and edge detection
     */
    fun isSquareOccupied(square: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(square, mat)
            
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
            
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(grayMat, mean, stdDev)
            val variance = stdDev.get(0, 0)[0]
            
            val edges = Mat()
            Imgproc.Canny(grayMat, edges, 50.0, 150.0)
            val edgeCount = Core.countNonZero(edges)
            
            mat.release()
            grayMat.release()
            edges.release()
            mean.release()
            stdDev.release()
            
            variance > 12.0 || edgeCount > (square.width * square.height * 0.05)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking square occupancy: ${e.message}")
            false
        }
    }
    
    /**
     * Determine if a piece is white or black
     */
    fun isPieceWhite(square: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(square, mat)
            
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
            
            val mean = Core.mean(mat)
            val brightness = mean.`val`[0]
            
            mat.release()
            
            brightness > 128.0
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting piece color: ${e.message}")
            true
        }
    }
    
    /**
     * Detect board state (legacy method)
     */
    fun detectBoardState(boardBitmap: Bitmap): Array<IntArray> {
        val state = Array(8) { IntArray(8) { 0 } }
        
        try {
            val squares = extractSquares(boardBitmap)
            
            for (row in 0 until 8) {
                for (col in 0 until 8) {
                    val index = row * 8 + col
                    if (index < squares.size) {
                        val square = squares[index]
                        
                        if (isSquareOccupied(square)) {
                            state[row][col] = if (isPieceWhite(square)) 1 else 2
                        }
                        
                        square.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting board state: ${e.message}")
        }
        
        return state
    }
    
    /**
     * Detect move from board states (legacy method)
     */
    fun detectMove(oldState: Array<IntArray>, newState: Array<IntArray>, isFlipped: Boolean): String? {
        val changes = mutableListOf<Pair<Int, Int>>()
        
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (oldState[row][col] != newState[row][col]) {
                    changes.add(Pair(row, col))
                }
            }
        }
        
        when (changes.size) {
            2 -> {
                val from = changes.firstOrNull { (r, c) -> 
                    oldState[r][c] != 0 && newState[r][c] == 0 
                }
                val to = changes.firstOrNull { (r, c) -> 
                    oldState[r][c] == 0 && newState[r][c] != 0 
                }
                
                if (from != null && to != null) {
                    val fromUCI = squareToUCI(from.first, from.second, isFlipped)
                    val toUCI = squareToUCI(to.first, to.second, isFlipped)
                    return fromUCI + toUCI
                }
            }
        }
        
        return null
    }
}
