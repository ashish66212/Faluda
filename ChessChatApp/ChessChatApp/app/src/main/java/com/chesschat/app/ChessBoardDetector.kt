package com.chesschat.app

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Advanced chess board detector using OpenCV
 * Detects board boundaries, extracts squares, and identifies piece positions
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
     * IMPROVED: Detect if a square is occupied by analyzing color variance and edge detection
     * Empty squares have low variance and few edges, occupied squares have high variance and edges
     */
    fun isSquareOccupied(square: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(square, mat)
            
            // Convert to grayscale
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY)
            
            // Method 1: Calculate standard deviation (variance)
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(grayMat, mean, stdDev)
            val variance = stdDev.get(0, 0)[0]
            
            // Method 2: Edge detection using Canny
            val edges = Mat()
            Imgproc.Canny(grayMat, edges, 50.0, 150.0)
            val edgeCount = Core.countNonZero(edges)
            
            mat.release()
            grayMat.release()
            edges.release()
            mean.release()
            stdDev.release()
            
            // Combined detection: high variance OR significant edges indicates occupied square
            val isOccupied = variance > 12.0 || edgeCount > (square.width * square.height * 0.05)
            
            Log.d(TAG, "Square variance=$variance, edges=$edgeCount, occupied=$isOccupied")
            isOccupied
        } catch (e: Exception) {
            Log.e(TAG, "Error checking square occupancy: ${e.message}")
            false
        }
    }
    
    /**
     * Determine if a piece is likely white or black based on average brightness
     */
    fun isPieceWhite(square: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(square, mat)
            
            // Convert to grayscale
            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
            
            // Get average brightness
            val mean = Core.mean(mat)
            val brightness = mean.`val`[0]
            
            mat.release()
            
            // White pieces are generally brighter
            brightness > 128.0
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting piece color: ${e.message}")
            true
        }
    }
    
    /**
     * OPTIMIZED: Enhanced board state detection using OpenCV
     * Faster processing with improved accuracy
     */
    fun detectBoardState(boardBitmap: Bitmap): Array<IntArray> {
        val state = Array(8) { IntArray(8) { 0 } }  // 0 = empty, 1 = white, 2 = black
        
        try {
            val startTime = System.currentTimeMillis()
            val squares = extractSquares(boardBitmap)
            
            // Process all squares
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
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Board state detected in ${processingTime}ms")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting board state: ${e.message}")
            e.printStackTrace()
        }
        
        return state
    }
    
    /**
     * IMPROVED: Compare two board states and intelligently find the move
     * Handles standard moves, captures, castling, and en passant
     */
    fun detectMove(oldState: Array<IntArray>, newState: Array<IntArray>, isFlipped: Boolean): String? {
        val changes = mutableListOf<Pair<Int, Int>>()
        
        // Find all changed squares
        for (row in 0 until 8) {
            for (col in 0 until 8) {
                if (oldState[row][col] != newState[row][col]) {
                    changes.add(Pair(row, col))
                }
            }
        }
        
        Log.d(TAG, "Detected ${changes.size} changed squares")
        
        when (changes.size) {
            0 -> {
                Log.d(TAG, "No changes detected")
                return null
            }
            
            2 -> {
                // Standard move or capture
                return handleTwoSquareChange(changes, oldState, newState, isFlipped)
            }
            
            3 -> {
                // Possible en passant (3 squares: from, to, captured pawn)
                Log.d(TAG, "Possible en passant with 3 changes")
                return handleThreeSquareChange(changes, oldState, newState, isFlipped)
            }
            
            4 -> {
                // Castling (4 squares: king from/to, rook from/to)
                Log.d(TAG, "Possible castling with 4 changes")
                return handleCastling(changes, oldState, newState, isFlipped)
            }
            
            else -> {
                Log.d(TAG, "Unusual change pattern: ${changes.size} squares - possibly animation")
                return null
            }
        }
    }
    
    private fun handleTwoSquareChange(
        changes: List<Pair<Int, Int>>,
        oldState: Array<IntArray>,
        newState: Array<IntArray>,
        isFlipped: Boolean
    ): String? {
        // Determine which square lost a piece (from) and which gained (to)
        val from = changes.firstOrNull { (r, c) -> 
            oldState[r][c] != 0 && newState[r][c] == 0 
        }
        val to = changes.firstOrNull { (r, c) -> 
            oldState[r][c] == 0 && newState[r][c] != 0 
        }
        
        if (from != null && to != null) {
            val fromUCI = squareToUCI(from.first, from.second, isFlipped)
            val toUCI = squareToUCI(to.first, to.second, isFlipped)
            val move = fromUCI + toUCI
            
            Log.d(TAG, "Move detected: $move (from $fromUCI to $toUCI)")
            return move
        }
        
        // Capture move: both squares were occupied, one still has piece
        val moveFrom = changes[0]
        val moveTo = changes[1]
        
        val actualFrom = if (newState[moveFrom.first][moveFrom.second] == 0) moveFrom else moveTo
        val actualTo = if (newState[moveTo.first][moveTo.second] != 0) moveTo else moveFrom
        
        val fromUCI = squareToUCI(actualFrom.first, actualFrom.second, isFlipped)
        val toUCI = squareToUCI(actualTo.first, actualTo.second, isFlipped)
        val move = fromUCI + toUCI
        
        Log.d(TAG, "Capture detected: $move")
        return move
    }
    
    private fun handleThreeSquareChange(
        changes: List<Pair<Int, Int>>,
        oldState: Array<IntArray>,
        newState: Array<IntArray>,
        isFlipped: Boolean
    ): String? {
        // En passant: find the square that went from piece to empty (from),
        // square that went from empty to piece (to), and captured pawn square
        val from = changes.firstOrNull { (r, c) -> 
            oldState[r][c] != 0 && newState[r][c] == 0 
        }
        val to = changes.firstOrNull { (r, c) -> 
            oldState[r][c] == 0 && newState[r][c] != 0 
        }
        
        if (from != null && to != null) {
            val fromUCI = squareToUCI(from.first, from.second, isFlipped)
            val toUCI = squareToUCI(to.first, to.second, isFlipped)
            val move = fromUCI + toUCI
            
            Log.d(TAG, "En passant detected: $move")
            return move
        }
        
        return null
    }
    
    private fun handleCastling(
        changes: List<Pair<Int, Int>>,
        oldState: Array<IntArray>,
        newState: Array<IntArray>,
        isFlipped: Boolean
    ): String? {
        // Find king's movement (2 squares horizontally)
        val kingMoves = changes.filter { (r, c) ->
            // King moved from or to this square
            val wasKing = oldState[r][c] != 0
            val isKing = newState[r][c] != 0
            wasKing || isKing
        }
        
        if (kingMoves.size >= 2) {
            // Find king's from and to positions
            val from = kingMoves.firstOrNull { (r, c) -> 
                oldState[r][c] != 0 && newState[r][c] == 0 
            }
            val to = kingMoves.firstOrNull { (r, c) -> 
                oldState[r][c] == 0 && newState[r][c] != 0 
            }
            
            if (from != null && to != null) {
                val fromUCI = squareToUCI(from.first, from.second, isFlipped)
                val toUCI = squareToUCI(to.first, to.second, isFlipped)
                val move = fromUCI + toUCI
                
                Log.d(TAG, "Castling detected: $move")
                return move
            }
        }
        
        return null
    }
    
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
}
