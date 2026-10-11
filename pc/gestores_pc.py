"""
GESTORES · Programa del PC
Recibe los gestos del reloj (o del celular) y controla tu computador.

Cómo usarlo:
  1. Instala Python 3 (https://python.org) marcando "Add Python to PATH".
  2. Doble clic en Iniciar_Gestores_PC.bat  (la primera vez instala el QR y el ícono de la bandeja).
  3. En la app Gestores del celular: Computador → "Escanear QR del PC" y apunta a la ventana.
  4. Si Windows pregunta por el Firewall, pulsa "Permitir" solo en redes privadas.

Seguridad:
  • La clave es única y larga; se crea sola la primera vez y se guarda en tu carpeta de usuario.
  • Solo acepta órdenes desde tu red de casa (Wi-Fi local); desde internet se rechazan.
  • No guarda direcciones IP ni historial en el disco: el registro de la ventana se borra al cerrar.
"""
import ctypes
import hmac
import ipaddress
import json
import secrets
import socket
import subprocess
import sys
import threading
import webbrowser
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, urlparse

PORT = 8765
IS_WINDOWS = sys.platform.startswith("win")
CONFIG_FILE = Path.home() / ".gestores_pc.json"


# ------------------------------------------------------------------ clave única
def load_token(new: bool = False) -> str:
    """Lee la clave del PC o crea una nueva, larga y aleatoria."""
    if not new and CONFIG_FILE.exists():
        try:
            tok = json.loads(CONFIG_FILE.read_text(encoding="utf-8")).get("token", "")
            if len(tok) >= 24:
                return tok
        except (OSError, ValueError):
            pass
    tok = secrets.token_urlsafe(24)
    CONFIG_FILE.write_text(json.dumps({"token": tok}), encoding="utf-8")
    return tok


TOKEN = load_token()


# ------------------------------------------------------------------ teclas
KEYS = {
    "right": 0x27, "left": 0x25, "up": 0x26, "down": 0x28, "space": 0x20, "enter": 0x0D, "esc": 0x1B, "f5": 0x74,
    "playpause": 0xB3, "next": 0xB0, "prev": 0xB1, "stop": 0xB2,
    "volup": 0xAF, "voldown": 0xAE, "mute": 0xAD,
}
VK_WIN, VK_ALT, VK_TAB, VK_SNAPSHOT, VK_D = 0x5B, 0x12, 0x09, 0x2C, 0x44
NAMES = {
    "right": "Siguiente diapositiva", "left": "Diapositiva anterior", "playpause": "Play / pausa",
    "next": "Siguiente canción", "prev": "Canción anterior", "volup": "Volumen +", "voldown": "Volumen −", "mute": "Silencio",
}
LINUX_KEYS = {"right": "Right", "left": "Left", "playpause": "XF86AudioPlay", "next": "XF86AudioNext",
              "prev": "XF86AudioPrev", "volup": "XF86AudioRaiseVolume", "voldown": "XF86AudioLowerVolume", "mute": "XF86AudioMute"}
FOLDERS = {"descargas": "Downloads", "documentos": "Documents", "imagenes": "Pictures", "escritorio": "Desktop", "musica": "Music"}


def combo(*vks: int) -> None:
    """Presiona varias teclas juntas (ej. Alt + Tab) y las suelta en orden inverso."""
    user32 = ctypes.windll.user32
    for vk in vks:
        user32.keybd_event(vk, 0, 0, 0)
    for vk in reversed(vks):
        user32.keybd_event(vk, 0, 2, 0)


def press(name: str) -> str:
    vk = KEYS.get(name)
    if vk is None:
        return f"Tecla desconocida: {name}"
    if IS_WINDOWS:
        combo(vk)
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


def brightness(delta: int) -> str:
    """Brillo de la pantalla (portátiles). Los monitores externos normalmente no lo permiten."""
    ps = ("$m=Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightness -ErrorAction Stop;"
          f"$v=[Math]::Max(0,[Math]::Min(100,$m.CurrentBrightness+({delta})));"
          "Invoke-CimMethod -InputObject (Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods) "
          "-MethodName WmiSetBrightness -Arguments @{Timeout=1;Brightness=[byte]$v} | Out-Null; $v")
    r = subprocess.run(["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True, timeout=10,
                       creationflags=0x08000000)  # sin ventana negra
    out = r.stdout.strip()
    return f"Brillo {out} %" if out.isdigit() else "Esta pantalla no permite cambiar el brillo"


def run(cmd: str) -> str:
    if cmd == "ping":
        return f"Conectado con {socket.gethostname()}"
    if cmd.startswith("key:"):
        return press(cmd[4:])
    if cmd.startswith("open:"):
        return open_target(cmd[5:])
    if cmd.startswith("folder:"):
        sub = FOLDERS.get(cmd[7:], "")
        return open_target(str(Path.home() / sub)).replace("Abriendo", "Carpeta")
    if not IS_WINDOWS:
        return "Esta orden solo funciona en Windows"
    if cmd == "lock":
        ctypes.windll.user32.LockWorkStation()
        return "PC bloqueado"
    if cmd == "screenshot":
        combo(VK_WIN, VK_SNAPSHOT)  # Windows la guarda en Imágenes › Capturas de pantalla
        return "Captura guardada en Imágenes › Capturas de pantalla"
    if cmd == "alttab":
        combo(VK_ALT, VK_TAB)
        return "Cambiar de ventana"
    if cmd == "desktop":
        combo(VK_WIN, VK_D)
        return "Mostrar escritorio"
    if cmd in ("bright_up", "bright_down"):
        return brightness(20 if cmd == "bright_up" else -20)
    if cmd == "sleep":
        subprocess.Popen("rundll32.exe powrprof.dll,SetSuspendState 0,1,0", shell=True)
        return "PC en suspensión"
    if cmd == "shutdown":
        subprocess.Popen("shutdown /s /t 60", shell=True)  # 1 minuto para arrepentirse
        return "El PC se apaga en 1 minuto (gesto de cancelar para detenerlo)"
    if cmd == "cancel_shutdown":
        subprocess.Popen("shutdown /a", shell=True)
        return "Apagado cancelado"
    return f"Orden desconocida: {cmd}"


# ------------------------------------------------------------------ servidor
def is_home_network(ip: str) -> bool:
    """Solo la red local (Wi-Fi de casa) o este mismo PC."""
    try:
        a = ipaddress.ip_address(ip)
        return a.is_private or a.is_loopback or a.is_link_local
    except ValueError:
        return False


log_listeners = []


def log(text: str) -> None:
    line = f"[{datetime.now():%H:%M:%S}] {text}"
    try:
        print(line)
    except (UnicodeEncodeError, OSError, AttributeError):  # consola sin tildes o sin consola (pythonw)
        pass
    for fn in log_listeners:
        fn(line)


class Handler(BaseHTTPRequestHandler):
    def _reply(self, code: int, text: str):
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not is_home_network(self.client_address[0]):
            return self._reply(403, "Solo desde la red de casa")
        url = urlparse(self.path)
        if url.path != "/run":
            return self._reply(200, "Gestores PC funcionando")
        q = parse_qs(url.query)
        if not hmac.compare_digest(q.get("token", [""])[0], TOKEN):
            log("Intento con clave incorrecta (rechazado)")
            return self._reply(403, "Clave incorrecta: vuelve a escanear el QR del PC")
        cmd = q.get("cmd", [""])[0]
        try:
            msg = run(cmd)
            log(f"{cmd}  →  {msg}")
            self._reply(200, msg)
        except Exception as e:  # noqa: BLE001
            log(f"Error con {cmd}: {e}")
            self._reply(500, f"Error: {e}")

    def log_message(self, *args):  # silencia el registro por defecto (no guarda IPs)
        pass


def local_ip() -> str:
    """La dirección del PC en el Wi-Fi de casa (la que usa el celular)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        if not ip.startswith("127."):
            return ip
    except OSError:
        pass
    finally:
        s.close()
    # Sin internet: se busca entre las direcciones del PC la de la red local
    try:
        ips = {a[4][0] for a in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)}
    except OSError:
        ips = set()
    home = sorted((i for i in ips if is_home_network(i) and not i.startswith(("127.", "169.254."))),
                  key=lambda i: (not i.startswith("192.168."), i))
    return home[0] if home else "127.0.0.1"


def pair_link(ip: str) -> str:
    """Lo que lleva el QR: la app del celular lo lee y se configura sola."""
    return f"gestores://pc?addr={ip}:{PORT}&token={quote(TOKEN)}"


# ------------------------------------------------------------------ ventana
GOLD, INK, PANEL, TEXT, MUTED = "#D4AF37", "#0B0A08", "#17140F", "#F3EBD8", "#9C927E"


def window(server: ThreadingHTTPServer, ip: str) -> None:
    import tkinter as tk

    root = tk.Tk()
    root.title("Gestores PC")
    root.configure(bg=INK)
    root.geometry("440x660")
    root.minsize(400, 560)

    tk.Label(root, text="GESTORES · PC", font=("Georgia", 20, "bold"), fg=GOLD, bg=INK).pack(pady=(18, 0))
    status = tk.Label(root, text="● Listo para recibir gestos", font=("Segoe UI", 10), fg="#7BC47F", bg=INK)
    status.pack()

    card = tk.Frame(root, bg=PANEL, highlightbackground=GOLD, highlightthickness=1)
    card.pack(padx=18, pady=12, fill="x")
    qr_label = tk.Label(card, bg=PANEL)
    qr_label.pack(pady=(12, 4))
    info = tk.Label(card, font=("Consolas", 11), fg=TEXT, bg=PANEL, justify="center")
    info.pack(pady=(0, 4))
    tk.Label(card, text="En el celular: Gestores → Computador → Escanear QR del PC",
             font=("Segoe UI", 9), fg=MUTED, bg=PANEL, wraplength=380).pack(pady=(0, 12))

    def paint_pairing():
        info.config(text=f"Dirección: {ip}:{PORT}" if not ip.startswith("127.") else "Sin Wi-Fi: conecta el PC a tu red")
        try:
            import qrcode  # pip install qrcode
            from PIL import ImageTk
            img = qrcode.make(pair_link(ip), box_size=6, border=2)
            img = getattr(img, "get_image", lambda: img)().convert("RGB")
            photo = ImageTk.PhotoImage(img)
            qr_label.config(image=photo, text="")
            qr_label.image = photo
        except Exception:  # noqa: BLE001  — sin la librería del QR, se muestra la clave para escribirla
            qr_label.config(text=f"Clave:\n{TOKEN}", font=("Consolas", 10), fg=GOLD, wraplength=380)

    paint_pairing()

    def watch_ip():
        """Si cambias de Wi-Fi o el PC se conecta tarde, el QR se actualiza solo."""
        nonlocal ip
        now = local_ip()
        if now != ip:
            ip = now
            paint_pairing()
        root.after(10_000, watch_ip)

    root.after(10_000, watch_ip)

    btns = tk.Frame(root, bg=INK)
    btns.pack()

    def new_key():
        global TOKEN
        TOKEN = load_token(new=True)
        paint_pairing()
        log("Clave nueva creada: vuelve a escanear el QR en el celular")

    def button(text, cmd):
        return tk.Button(btns, text=text, command=cmd, bg=PANEL, fg=GOLD, activebackground=GOLD, activeforeground=INK,
                         relief="flat", padx=12, pady=6, font=("Segoe UI", 9, "bold"), cursor="hand2")

    button("🔑 Clave nueva", new_key).grid(row=0, column=0, padx=4)
    button("🧪 Probar", lambda: log("Prueba: " + run("ping"))).grid(row=0, column=1, padx=4)

    tk.Label(root, text="Últimos gestos", font=("Georgia", 11, "bold"), fg=GOLD, bg=INK).pack(anchor="w", padx=18, pady=(12, 2))
    box = tk.Listbox(root, bg=PANEL, fg=TEXT, font=("Consolas", 9), relief="flat", highlightthickness=0, height=10)
    box.pack(fill="both", expand=True, padx=18, pady=(0, 10))

    def add_line(line: str):
        def _():
            box.insert(0, line)
            box.delete(60, "end")
            status.config(text="● " + line.split("] ", 1)[-1][:48])
        root.after(0, _)

    log_listeners.append(add_line)

    # Bandeja del sistema: al cerrar la ventana sigue funcionando junto al reloj de Windows
    tray = None
    try:
        import pystray  # pip install pystray pillow
        from PIL import Image, ImageDraw

        icon_img = Image.new("RGB", (64, 64), INK)
        d = ImageDraw.Draw(icon_img)
        d.ellipse((6, 6, 58, 58), outline=GOLD, width=5)
        d.line((32, 32, 32, 14), fill=GOLD, width=4)
        d.line((32, 32, 46, 38), fill=GOLD, width=4)

        def show(*_):
            root.after(0, root.deiconify)

        def quit_all(*_):
            tray.stop()
            root.after(0, root.destroy)

        tray = pystray.Icon("gestores", icon_img, "Gestores PC",
                            menu=pystray.Menu(pystray.MenuItem("Abrir", show, default=True), pystray.MenuItem("Salir", quit_all)))
        threading.Thread(target=tray.run, daemon=True).start()
        root.protocol("WM_DELETE_WINDOW", root.withdraw)
        tk.Label(root, text="Al cerrar, sigue en la bandeja (junto al reloj). Clic derecho → Salir.",
                 font=("Segoe UI", 8), fg=MUTED, bg=INK).pack(pady=(0, 8))
    except Exception:  # noqa: BLE001
        pass

    root.mainloop()
    if tray:
        tray.stop()
    server.shutdown()


# ------------------------------------------------------------------ inicio
if __name__ == "__main__":
    ip = local_ip()
    try:
        server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    except OSError as e:
        msg = f"No se pudo abrir el puerto {PORT}: {e}\n¿Ya está abierto Gestores PC? Búscalo en la bandeja, junto al reloj."
        try:
            import tkinter.messagebox as mb
            mb.showerror("Gestores PC", msg)
        except Exception:  # noqa: BLE001
            print(msg)
        sys.exit(1)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print(f"Gestores PC listo en {ip}:{PORT}")
    if "--sin-ventana" in sys.argv:
        print(f"Clave: {TOKEN}\nCtrl + C para salir.")
        try:
            threading.Event().wait()
        except KeyboardInterrupt:
            server.shutdown()
    else:
        window(server, ip)
