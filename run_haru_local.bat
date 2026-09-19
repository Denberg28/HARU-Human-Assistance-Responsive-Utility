@echo off
setlocal

title HARU Local

echo.
echo ==========================================
echo   HARU Local - Ollama + Streamlit Launcher
echo ==========================================
echo.

where ollama >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Ollama is not installed or not in PATH.
  echo Install Ollama, then reopen this launcher.
  pause
  exit /b 1
)

where python >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Python is not available in PATH.
  pause
  exit /b 1
)

echo Checking Ollama...
curl -s http://localhost:11434/api/tags >nul 2>nul
if errorlevel 1 (
  echo Starting Ollama service...
  start "" /min ollama serve
  timeout /t 2 /nobreak >nul
)

echo Checking installed models...
for /f "delims=" %%A in ('ollama list 2^>nul ^| findstr /R /C:"^[A-Za-z0-9]"') do (
  set FOUND_MODEL=1
  goto :models_ok
)

:models_ok
if not defined FOUND_MODEL (
  echo.
  echo No Ollama models found.
  echo Recommended lightweight model: qwen3:4b
  choice /M "Install qwen3:4b now"
  if errorlevel 2 goto :skip_pull
  ollama pull qwen3:4b
)

:skip_pull
if not exist ".venv\Scripts\python.exe" (
  echo Creating HARU virtual environment...
  python -m venv .venv
)

call .venv\Scripts\activate.bat
python -m pip install -r requirements.txt

echo.
echo Starting HARU at http://localhost:8501
echo Close this window to stop HARU.
echo.

python -m streamlit run streamlit_app.py --server.address localhost --server.port 8501

endlocal
