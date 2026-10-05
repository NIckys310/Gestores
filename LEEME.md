# Gestores · Controla dispositivos con gestos desde el reloj

Tres partes que trabajan juntas:

| Parte | Carpeta | Qué hace |
|---|---|---|
| ⌚ Reloj (Wear OS) | `wear/` | Lee acelerómetro y giroscopio, reconoce el gesto (IA) y lo envía |
| 📱 Celular | `mobile/` | Recibe el gesto y ejecuta la acción: abrir apps, cámara, linterna, música, luces |
| 💻 PC | `pc/gestores_pc.py` | Recibe órdenes: diapositivas, música, volumen, abrir programas, bloquear |

Funciona en el **emulador de reloj** de Android Studio y en relojes reales con **Wear OS**
(Galaxy Watch 4 o más nuevo, Pixel Watch, TicWatch).

## Vista de reloj en el celular
La app del celular se ve como un reloj redondo. Desliza dentro de la esfera:
**Hora** (activar gestos, confirmar Sí/No) → **Apps** (tus apps en círculo, Gestores al centro, "+" para elegirlas)
→ **Gestos** (toca uno para cambiar su acción) → **Ajustes** (preguntar antes, sensibilidad, más ajustes).
La corona del reloj vuelve a la hora.

## Ajustes
- **Preguntar antes de ejecutar**: confirma con "Sí" o repitiendo el mismo gesto antes de 8 s (funciona desde el reloj).
- **Días**: todos, entre semana, fin de semana o días sueltos (L M X J V S D).
- **Horario**: desde / hasta (admite horario nocturno, por ejemplo 22:00 a 06:00).
- **Sensibilidad**: de poco a muy sensible. **Vibración**: apagada, suave, media o fuerte.
- **Avisos**: se borran solos (5 s, 15 s, 1 min o nunca), se pueden deslizar, y hay botón para borrarlos todos.

## Opción usada: celular en la muñeca + reloj Honor
Si tu reloj no es Wear OS (por ejemplo **Honor Choice Watch 2i**):
1. Ponte el celular en la muñeca o el antebrazo con una banda deportiva.
2. En Gestores (celular) activa **📱 Celular en la muñeca**: el celular detecta los gestos.
3. En **Honor Health → tu reloj → Notificaciones** activa las notificaciones de **Gestores**.
   Cada gesto vibra y aparece en tu reloj con el resultado.
4. Opcional: en **Honor Health → tu reloj → Música** activa **Controlar la música del teléfono** y
   en Gestores activa **⌚ Botones de música del reloj**: ⏯️ ⏭️ ⏮️ del reloj también ejecutan acciones.

## Gestos
- ⬆️ Alzar rápido · ⬇️ Bajar rápido · 🔄 Girar muñeca · 👋 Sacudir · ✊ Doble golpe
- 🧠 Gesto IA 1, 2 y 3: los inventas tú. El reloj los aprende con 5 muestras
  (Dynamic Time Warping + vecino más cercano, todo dentro del reloj, sin internet).

## Cómo viaja un gesto
```
Reloj ──(Wearable Data Layer)──> Celular ──(Wi-Fi)──> PC / luces inteligentes
  └──────────────── sin celular: directo por Wi-Fi ───────────────┘
```

## Paso 1 · PC
1. Instala Python 3 desde python.org (marca **Add Python to PATH**).
2. Doble clic en `pc/Iniciar_Gestores_PC.bat`. Anota la dirección que muestra, por ej. `192.168.1.10:8765`.
3. Si Windows pregunta por el Firewall → **Permitir** (redes privadas).

## Paso 2 · Abrir el proyecto
Android Studio → **File → Open** → carpeta `Gestores` → Trust → espera el Gradle sync.
Arriba, en la lista de configuraciones, verás **wear** y **mobile**.

## Paso 3 · Reloj virtual (emulador)
1. **Device Manager → + → Create Virtual Device** → categoría **Wear OS** →
   *Wear OS Large Round* → imagen **API 34** (Wear OS) → Finish.
2. Arriba elige la configuración **wear** y el emulador del reloj → **▶ Run**.
3. En el reloj: activa **Detectar gestos**, baja a **Probar gestos** y toca **⬆️ Alzar rápido**.
   Con el programa del PC abierto, ¡se abre Guardián en tu PC! (el emulador llega al PC por 10.0.2.2).
4. Para mover el reloj virtual: barra del emulador **⋮ → Virtual sensors** → pestañas **Rotate** y **Move**.
   Arrastra rápido para hacer un gesto. Si cuesta, sube la **Sensibilidad** en el reloj.
5. Entrenar la IA: **Entrenar IA → Gesto IA 1** → haz el mismo movimiento 5 veces → Terminar.

## Paso 4 · Celular
1. Arriba elige **mobile** y tu celular → **▶ Run** (o Build → Generate APKs, módulo mobile).
2. En la app: activa **Mostrar sobre otras apps**, escribe la dirección del PC → **Guardar**.
3. En cada gesto toca **Cambiar** y elige la acción. Se envía sola al reloj.

## Paso 5 · Unir reloj y celular
- **Reloj real:** empareja el reloj con el celular (app Galaxy Wearable o Pixel Watch) e instala
  **wear** en el reloj y **mobile** en el celular desde Android Studio.
- **Reloj virtual:** Device Manager → **⋮** junto al reloj virtual → **Pair Wearable** y sigue el asistente
  (pide un celular con Play Store y la app de Wear OS).
- Ambas apps tienen el mismo `applicationId` (`com.gestores.app`); así se encuentran solas.
- Sin celular emparejado, el reloj envía los gestos **directo al PC**.

## Dispositivos inteligentes (webhook)
- Shelly: `http://IP/relay/0?turn=toggle`
- Tasmota / ESP32 / ESP8266: `http://IP/cm?cmnd=Power%20TOGGLE`
- Home Assistant: `http://IP:8123/api/webhook/ID` (POST)
- IFTTT (Alexa, Google Home, Tuya, Smart Life…): `https://maker.ifttt.com/trigger/EVENTO/with/key/CLAVE`

## Qué archivo cambiar
| Quiero cambiar… | Archivo |
|---|---|
| Qué tan fuerte debe ser un gesto | `wear/.../GestureEngine.kt` (BasicClassifier) |
| La IA de gestos personalizados | `wear/.../GestureEngine.kt` (Learner) |
| Pantalla del reloj | `wear/.../MainActivity.kt` |
| Acciones del reloj sin celular | `wear/.../Shared.kt` (ConfigStore) |
| Acciones del celular | `mobile/.../ActionRunner.kt` |
| Pantalla del celular | `mobile/.../MainActivity.kt` |
| Acciones del PC | `pc/gestores_pc.py` |
