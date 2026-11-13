import uvicorn
import os
import sys

# Ensure project root is in python path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    reload_flag = os.environ.get("RELOAD", "false").lower() == "true"
    print(f"Starting Krishna Enterprises ERP Server on http://127.0.0.1:{port}")
    uvicorn.run("app.main:app", host="127.0.0.1", port=port, reload=reload_flag)
