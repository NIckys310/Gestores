"""
GESTORES · Programa del PC
Recibe los gestos del reloj (o del celular) y controla tu computador.

Cómo usarlo:
  1. Instala Python 3 (https://python.org) marcando "Add Python to PATH".
  2. Doble clic en Iniciar_Gestores_PC.bat  (o en una terminal:  python gestores_pc.py)
  3. Copia la dirección que aparece (ej. 192.168.1.10:8765) en la app Gestores del celular.
  4. Si Windows pregunta por el Firewall, pulsa "Permitir" en redes privadas.

No necesita instalar librerías extra.
"""
import ctypes
import socket
import subprocess
import sys
import webbrowser
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = 8765
TOKEN = "gestores"          # si la cambias, escribe la misma en la app del celular

IS_WINDOWS = sys.platform.startswith("win")

# Teclas virtuales de Windows
KEYS = {
    "right": 0x27, "left": 0x25, "up": 0x26, "down": 0x28, "space": 0x20, "enter": 0x0D, "esc": 0x1B, "f5": 0x74,
    "playpause": 0xB3, "next": 0xB0, "prev": 0xB1, "stop": 0xB2,
    "volup": 0xAF, "voldown": 0xAE, "mute": 0xAD,
}
NAMES = {
    "right": "Siguiente diapositiva", "left": "Diapositiva anterior", "playpause": "Play / pausa",
    "next": "Siguiente canción", "prev": "Canción anterior", "volup": "Volumen +", "voldown": "Volumen −", "mute": "Silencio",
}
LINUX_KEYS = {"right": "Right", "left": "Left", "playpause": "XF86AudioPlay", "next": "XF86AudioNext",
              "prev": "XF86AudioPrev", "volup": "XF86AudioRaiseVolume", "voldown": "XF86AudioLowerVolume", "mute": "XF86AudioMute"}


def press(name: str) -> str:
    vk = KEYS.get(name)
    if vk is None:
        return f"Tecla desconocida: {name}"
    if IS_WINDOWS:
        user32 = ctypes.windll.user32
        user32.keybd_event(vk, 0, 0, 0)        # presionar
        user32.keybd_event(vk, 0, 2, 0)        # soltar
    else:  # Linux necesita xdotool
        subprocess.Popen(["xdotool", "key", LINUX_KEYS.get(name, name)])
    return NAMES.get(name, name)


def open_target(target: str) -> str:
    target = target.strip()
    if target.startswith(("http://", "https://")):
        webbrowser.open(target)
    elif IS_WINDOWS:
        # "start" abre programas registrados (spotify, notepad, calc…), rutas y archivos
        subprocess.Popen(f'start "" "{target}"', shell=True)
    else:
        subprocess.Popen(["xdg-open", target])
    return f"Abriendo {target}"


def run(cmd: str) -> str:
    if cmd == "ping":
        return f"Conectado con {socket.gethostname()}"
    if cmd.startswith("key:"):
        return press(cmd[4:])
    if cmd.startswith("open:"):
        return open_target(cmd[5:])
    if cmd == "lock":
        if IS_WINDOWS:
            ctypes.windll.user32.LockWorkStation()
        return "PC bloqueado"
    return f"Orden desconocida: {cmd}"


class Handler(BaseHTTPRequestHandler):
    def _reply(self, code: int, text: str):
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        if url.path != "/run":
            return self._reply(200, "Gestores PC funcionando")
        q = parse_qs(url.query)
        if q.get("token", [""])[0] != TOKEN:
            return self._reply(403, "Clave incorrecta")
        cmd = q.get("cmd", [""])[0]
        try:
            msg = run(cmd)
            print(f"[{datetime.now():%H:%M:%S}] {self.client_address[0]} -> {cmd}   OK: {msg}")
            self._reply(200, msg)
        except Exception as e:  # noqa: BLE001
            print(f"[{datetime.now():%H:%M:%S}] Error con {cmd}: {e}")
            self._reply(500, f"Error: {e}")

    def log_message(self, *args):  # silencia el registro por defecto
        pass


def local_ip() -> str:
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


if __name__ == "__main__":
    ip = local_ip()
    print("=" * 58)
    print("  GESTORES · PC listo para recibir gestos")
    print(f"  Dirección para el celular:  {ip}:{PORT}")
    print(f"  Clave:                      {TOKEN}")
    print("  El emulador del reloj usa sola la dirección 10.0.2.2:8765")
    print("  Deja esta ventana abierta. Ctrl + C para salir.")
    print("=" * 58)
    try:
        ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
    except KeyboardInterrupt:
        print("Hasta luego")
    except OSError as e:
        print(f"No se pudo abrir el puerto {PORT}: {e}")
        input("Pulsa Enter para cerrar…")
