# Gestores · Tu celular convertido en smartwatch

Una sola app para el celular. La pantalla es siempre un **reloj inteligente moderno** (cuadrado o
redondo, caja cobriza y correa café) y todas las apps se abren **dentro del reloj**. Arriba del menú
está **Gestores**: todos los gestos, qué hace cada uno y el interruptor para activarlos o desactivarlos.
No se conecta con nada: ni PC, ni relojes Wear OS. Solo usa internet para el clima.

- Lo que tiene **sonido** (música, volumen, timbre) pregunta antes: *"¿Deseas reproducir o pausar la música?"* **Sí / No**.
- Abrir la **cámara** o una **app** del celular se hace de una vez.

## Cómo se usa
- **Corona** (a la derecha del reloj): abre el menú; desde una app, vuelve al menú.
- **Botón lateral** (debajo de la corona): abre Gestores directo.
- **Tocar la esfera**: también abre las apps.
- **Deslizar sobre la esfera**: cambia de esfera (Digital, Cronógrafo, Minimalista, Clásica, Deportiva).
- **Deslizar a la derecha dentro de una app** o el botón Atrás del celular: volver.
- **Mantener presionada la esfera**: modo siempre encendido (pantalla oscura que no se apaga).

## Apps dentro del reloj
| Grupo | Apps |
|---|---|
| Reloj | ⏰ Alarma · ⏱️ Cronómetro · ⏲️ Temporizador · 📅 Calendario |
| Salud | 👣 Pasos · 🫁 Respirar · 💧 Agua (con recordatorio) · 🏃 Ejercicio |
| Utilidades | 🔦 Linterna · ☀️ Clima · 🧮 Calculadora · 📝 Notas · 🎵 Música del celular |
| Gestos e IA | ✋ Gestos · 🧠 Entrenar IA · ⚙️ Ajustes |

Las alarmas y el temporizador suenan aunque la app esté cerrada.

## Gestos
Con el celular en la mano o en la muñeca (banda deportiva), activa **✋ Gestos**:
- ⬆️ Alzar · ⬇️ Bajar · 🔄 Girar · 🔁 Doble giro · 👋 Sacudir · ✊ Doble golpe · 🫳 Toques en la mesa
- ⭕ ✔️ ⚡ Dibujar círculo, V o Z en el aire, y 🧠 8 gestos propios con nombre: la IA los aprende con 5 muestras
  (Dynamic Time Warping + vecino más cercano, todo dentro del celular).
- Si la IA no está segura, la esfera pregunta **“¿Fue este gesto?”** y aprende de tu respuesta.
- **🎯 Calibrar a mi fuerza** ajusta cada gesto a cómo lo haces tú.
- Cada gesto puede abrir una app del reloj, cambiar la esfera, manejar el cronómetro, la linterna, la música o el volumen.

## Abrir el proyecto
Android Studio → **File → Open** → carpeta `Gestores`. Módulo único: **mobile**.
Compilar: `gradlew :mobile:assembleDebug` (con JDK 17–21).

## Cómo está organizado (arquitectura)
```
MainActivity ─▶ WatchHome (pantalla del reloj, Compose)
                   │  lee y avisa a
                   ▼
              RelojViewModel ── qué se ve (esfera / menú / app) y qué esfera está puesta
                   │                (sobrevive a girar el celular y a que Android cierre la app)
                   ▼
   WatchState · PhoneBus ── estado compartido con el servicio de gestos (cronómetro, alarmas, "¿Deseas…?")
                   ▲
PhoneGestureService ─▶ GestureEngine (sensores → movimiento → gesto) ─▶ ActionRunner (acción)
```
- La lógica sin pantalla (motor de gestos, calculadora, tiempos, navegación) se prueba sin celular.
- La IA y la calibración guardan solo sus preferencias, nunca el `Context` (sin fugas de memoria).

## Qué archivo cambiar
| Quiero cambiar… | Archivo |
|---|---|
| El reloj, la corona y las esferas | `mobile/.../WatchHome.kt` |
| Cómo se navega dentro del reloj | `mobile/.../RelojViewModel.kt` |
| La lista de apps y las piezas comunes (botones, anillos) | `mobile/.../WatchApps.kt` |
| Alarma, cronómetro, temporizador, calendario | `mobile/.../AppsReloj.kt` |
| Pasos, respirar, agua, ejercicio | `mobile/.../AppsSalud.kt` |
| Linterna, clima, calculadora, notas, música | `mobile/.../AppsUtilidades.kt` |
| La sección Gestores, entrenar la IA y ajustes | `mobile/.../AppsGestores.kt` |
| Alarmas, temporizador y agua (que suenen a tiempo) | `mobile/.../WatchState.kt` |
| Qué tan fuerte debe ser un gesto y la IA | `mobile/.../GestureEngine.kt` |
| Lo que hace cada gesto | `mobile/.../ActionRunner.kt` y `Shared.kt` |
| Colores (negro, café oscuro y cobre; modo claro y oscuro) | `mobile/.../Theme.kt` |

## Pruebas automáticas
Gestores tiene **111 pruebas** que corren en la computadora, sin celular:

```bash
gradlew :mobile:testDebugUnitTest
```

| Archivo | Qué revisa |
|---|---|
| `BasicClassifierTest` | Reconoce alzar, bajar, girar, doble giro, sacudir, doble golpe y toques; la sensibilidad |
| `MotionSegmenterTest` | Corta los datos del sensor en movimientos; ignora ruido y caminar; pausa después de un gesto |
| `CalibrationTest` | La calibración guiada se ajusta a tu fuerza y nunca deja valores inválidos |
| `LearnerTest` | La IA aprende círculo y Z, los distingue, aprende con el uso, exporta e importa |
| `CalculadoraTest` | Suma, resta, decimales, porcentaje, división por cero, borrar |
| `FormatoTiempoTest` | Tiempos del cronómetro y temporizador |
| `AccionesTest` | Lo que tiene sonido pregunta "¿Deseas…?"; cámara y apps se abren de una vez |
| `GestosYAppsTest` | Gestos, acciones por defecto y las 16 apps del reloj |
| `RelojTest` | Cronómetro y hora exacta de las alarmas |
| `RelojViewModelTest` | Navegación del reloj (esfera ⇄ menú ⇄ apps), esferas, botón lateral y lo que piden los gestos |

En GitHub, cada cambio corre las pruebas, el análisis de Android y crea el APK (`.github/workflows/android.yml`).
