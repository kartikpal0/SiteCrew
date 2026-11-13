import os
import sys
import uvicorn

# Ensure root directory is on Python path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    print(f"Starting Krishna Enterprises Production Server on 0.0.0.0:{port}")
    uvicorn.run("app.main:app", host="0.0.0.0", port=port, log_level="info")
