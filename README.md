# Chess Engine - GitHub Actions

This repository contains a brutal chess engine that runs on GitHub Actions with ngrok tunneling for 6-hour sessions.

## What's Been Set Up

✓ **chess_engine.py** - Your chess engine script (minimal modifications: only ngrok token line changed to use GitHub secrets)
✓ **.github/workflows/chess-engine.yml** - GitHub Actions workflow configured to run for up to 6 hours with pip caching
✓ **send_telegram.py** - Automatic Telegram notification script
✓ **NGROK_AUTHTOKEN secret** - Already configured in your repository settings
✓ **TELEGRAM_BOT_TOKEN secret** - Already configured in your repository settings
✓ **Pip caching** - Speeds up workflow reruns by caching Python dependencies

## Changes Made to Your Code

Only **ONE line** was modified:
- Changed: `!ngrok config add-authtoken 31TWswIKgSWHAfejOFT6s8mcW69_4UCxySRXzy6Si8mDHn9zn`
- To: `ngrok_token = os.environ.get('NGROK_AUTHTOKEN')` and `ngrok.set_auth_token(ngrok_token)`
- Removed `!` prefix from installation commands (Jupyter/Colab specific, not needed in GitHub Actions)

Everything else remains **exactly the same** as your original code.

## How to Run

1. Go to your repository: https://github.com/narayan662122-arch/faluda
2. Click on the **Actions** tab
3. Select **"Chess Engine Server"** workflow
4. Click **"Run workflow"** button
5. The workflow will start and run for up to 6 hours

## How to Access Your Chess Engine

**Automatic Telegram Notification 📱**
- When the workflow starts, you'll receive a Telegram message with the ngrok URL
- The message will be sent to your Telegram chat (ID: 6173586090)
- No need to check logs manually!

**Manual Access (Alternative)**
If you prefer to check manually:
1. Go to the workflow run details
2. Expand the **"Run chess engine server and send Telegram notification"** step
3. Look for the **PUBLIC URL** in the logs (ngrok URL)

**Playing Chess**
Use the URL from Telegram or logs to play chess:

```bash
# Start a game
curl -X POST <ngrok-url>/start

# Choose color (white or black)
curl -X POST <ngrok-url>/move -d "white"

# Make moves (UCI format)
curl -X POST <ngrok-url>/move -d "e2e4"
```

## Engine Settings

- **Depth**: 20 (Maximum)
- **Skill Level**: 20 (Brutal)
- **Threads**: 4
- **Hash**: 2048 MB
- **Session Time**: Up to 6 hours per run

## New Features

### 1. Pip Caching 🚀
- Python dependencies are cached between workflow runs
- Significantly speeds up reruns (similar to Gradle caching)
- Cache key: `pip-cache-${{ runner.os }}-${{ hashFiles('**/requirements.txt') }}`
- Saves time and GitHub Actions minutes

### 2. Automatic Telegram Notifications 📱
- Automatically sends ngrok URL to your Telegram when the server starts
- No need to manually check GitHub Actions logs
- Includes ready-to-use example commands
- Sent to chat ID: 6173586090

## Notes

- The workflow runs on `ubuntu-latest` with Python 3.11
- Stockfish is installed automatically
- Dependencies are cached for faster reruns
- You can manually trigger the workflow anytime from the Actions tab
- Telegram notifications are sent automatically when the server is ready
