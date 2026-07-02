@echo off
REM Build a standalone KryspLocal.exe (Windows only). Run from repo root
REM after `pip install -r requirements.txt`.

pyinstaller --noconfirm --windowed --onefile ^
    --name KryspLocal ^
    --paths src ^
    src\krysp_local\main.py

echo.
echo Built dist\KryspLocal.exe
