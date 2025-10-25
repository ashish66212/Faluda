import asyncio
import sys
from telegram import Bot

TOKEN = sys.argv[1]
CHAT_ID = 6173586090

async def main():
    bot = Bot(TOKEN)
    ngrok_url = sys.argv[2] if len(sys.argv) > 2 else "No URL provided"
    message = f"🎮 Chess Engine is Live!\n\nNgrok URL: {ngrok_url}\n\nExample commands:\ncurl -X POST {ngrok_url}/start\ncurl -X POST {ngrok_url}/move -d \"white\"\ncurl -X POST {ngrok_url}/move -d \"e2e4\""
    await bot.send_message(chat_id=CHAT_ID, text=message)
    print(f"✓ Telegram notification sent to chat ID {CHAT_ID}")

if __name__ == '__main__':
    asyncio.run(main())
