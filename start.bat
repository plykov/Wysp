@echo off
rem Starts the Krysp Local GUI using the project's virtual environment.
rem If the app fails to start silently, run this for the error output:
rem   .venv\Scripts\python.exe -m krysp_local.main
cd /d "%~dp0"

if not exist ".venv\Scripts\pythonw.exe" (
    echo Virtual environment not found. Create it first:
    echo   py -3.11 -m venv .venv
    echo   .venv\Scripts\python.exe -m pip install -r requirements.txt
    echo   .venv\Scripts\python.exe -m pip install -e .
    pause
    exit /b 1
)

start "Krysp Local" ".venv\Scripts\pythonw.exe" -m krysp_local.main
