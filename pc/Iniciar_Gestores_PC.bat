@echo off
title Gestores PC
rem La primera vez instala el QR y el icono de la bandeja (solo para tu usuario)
python -c "import qrcode, pystray, PIL" 2>nul || python -m pip install --user --quiet qrcode pystray pillow
start "" pythonw "%~dp0gestores_pc.py"
