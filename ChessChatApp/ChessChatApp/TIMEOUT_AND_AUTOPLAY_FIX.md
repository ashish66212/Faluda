# Timeout and Auto-Play Fix

## Problems Fixed

### Issue 1: Color Setup Timeout

**Problem**: `sendColorCommandAutomatic: FAILED - timeout`

The color selection API call was timing out because:
- HTTP client timeout was 10 seconds
- Stockfish computation can take 2-3 seconds
- Network latency + processing time exceeded 10 seconds

**Solution**: Increased all HTTP timeouts from 10 to 30 seconds:
```kotlin
// OLD
.connectTimeout(10, TimeUnit.SECONDS)
.readTimeout(10, TimeUnit.SECONDS)
.writeTimeout(10, TimeUnit.SECONDS)

// NEW
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(30, TimeUnit.SECONDS)
.writeTimeout(30, TimeUnit.SECONDS)
```

### Issue 2: Moves Not Executing

**Problem**: Logs showed "SUCCESS - Move executed" but moves weren't actually happening on the board.

The root cause: `isAutoPlayEnabled` was `false` by default. Users had to manually check the auto-play checkbox, but this wasn't obvious.

**Solution**: Automatically enable auto-play when starting the automatic workflow:
```kotlin
private fun startAutomaticWorkflow() {
    // ... validation checks ...
    
    // Enable auto-play automatically for automatic workflow
    isAutoPlayEnabled = true
    addLog("startAutomaticWorkflow", "✓ Auto-play ENABLED automatically")
    
    // ... continue workflow ...
}
```

### Issue 3: Poor Debugging Visibility

**Problem**: Hard to diagnose why moves weren't executing.

**Solution**: Added detailed logging:
```kotlin
addLog("sendColorCommandAutomatic", "Extracted move from response: ${engineMove ?: "NONE"}")
addLog("sendColorCommandAutomatic", "Auto-play enabled: $isAutoPlayEnabled")

if (engineMove != null && isAutoPlayEnabled && responseBody.isNotEmpty()) {
    addLog("sendColorCommandAutomatic", "✓ Engine made first move: $engineMove - EXECUTING NOW")
    handler.post {
        executeMoveAutomatically(engineMove)
    }
} else {
    if (engineMove == null) {
        addLog("sendColorCommandAutomatic", "No move to execute (engine didn't move first)")
    } else if (!isAutoPlayEnabled) {
        addLog("sendColorCommandAutomatic", "Move found but auto-play is DISABLED")
    }
}
```

## Complete Workflow Now

### When User Clicks "🤖 Auto Start":

1. **Validation**
   - Check server URL configured ✓
   - Check screen capture active ✓
   - Check manual board setup done ✓

2. **Auto-play Setup**
   - ✅ **NEW**: Automatically enable `isAutoPlayEnabled = true`
   - Log: "✓ Auto-play ENABLED automatically"

3. **Game Initialization**
   - Send `/start` to backend
   - Wait for response with **30 second timeout** (was 10)

4. **Color Selection**
   - Send user's color to `/move` endpoint
   - Wait for response with **30 second timeout** (was 10)
   - Extract engine's first move (if any)

5. **Move Execution**
   - If engine moved first (white): Execute immediately
   - If user moves first (black): Wait for detection
   - All moves automatically executed (auto-play is ON)

## Testing Checklist

- [x] Timeout increased to 30 seconds
- [x] Auto-play automatically enabled
- [x] Detailed logging added
- [x] Engine's first move extracted and executed
- [x] Works for both white and black colors

## Expected Logs After Fix

```
[TIME] startAutomaticWorkflow: === STARTING AUTOMATIC WORKFLOW ===
[TIME] startAutomaticWorkflow: ✓ Auto-play ENABLED automatically
[TIME] startAutomaticWorkflow: ✓ Using MANUAL SETUP configuration
[TIME] sendStartCommandAutomatic: Sending game start request...
[TIME] sendStartCommandAutomatic: Response 200: Game started. Choose engine color: black or white?
[TIME] sendStartCommandAutomatic: ✓ Game started successfully
[TIME] sendColorCommandAutomatic: Sending color: white
[TIME] sendColorCommandAutomatic: Response 200: Engine is white. First move: e2e4
[TIME] sendColorCommandAutomatic: ✓ Color set to white
[TIME] sendColorCommandAutomatic: Extracted move from response: e2e4
[TIME] sendColorCommandAutomatic: Auto-play enabled: true
[TIME] sendColorCommandAutomatic: ✓ Engine made first move: e2e4 - EXECUTING NOW
[TIME] executeMoveAutomatically: CALLED - Move: e2e4
[TIME] executeMoveAutomatically: From: e2, To: e4
[TIME] executeMoveAutomatically: Accessibility service OK - using CLICK format (tap-tap)
[TIME] executeMoveAutomatically: First tap SUCCESS at e2
[TIME] executeMoveAutomatically: Executing second tap at e4
[TIME] executeMoveAutomatically: SUCCESS - Move executed: e2e4
```

## Files Modified

- `app/src/main/java/com/chesschat/app/MoveDetectionOverlayService.kt`
  - Line 106-114: Increased HTTP timeouts to 30 seconds
  - Line 310-312: Auto-enable auto-play in automatic workflow
  - Line 506-523: Enhanced logging for move extraction and execution

## Impact

1. **No More Timeouts**: 30 seconds is sufficient for Stockfish + network latency
2. **Automatic Move Execution**: Users don't need to manually enable checkbox
3. **Better Debugging**: Clear logs show exactly what's happening at each step
4. **Improved UX**: Seamless automatic gameplay without manual intervention
