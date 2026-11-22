# Gestores · Tu celular convertido en smartwatch

## Estructura del repositorio
```
Gestores/
├─ README.md                  ← estás aquí: qué es, cómo se usa y qué archivo cambiar
├─ docs/
│  ├─ SECURITY.md            ← contraseñas, datos y permisos
│  ├─ TESTING.md              ← qué revisa cada prueba y cómo correrlas
│  └─ SIGNING.md                ← llave de firma privada y APK de producción
├─ .github/workflows/         ← revisión automática en cada cambio (pruebas, lint, emulador)
└─ mobile/                    ← la app (único módulo)
   └─ src/
      ├─ main/java/com/gestores/app/
      │  ├─ MainActivity.kt · GestoresApplication.kt   ← arranque
      │  ├─ watch/            ← pantalla del reloj: caja, esferas, menú, avisos, tema, ViewModel
      │  ├─ apps/             ← apps dentro del reloj (reloj, salud, utilidades, Gestores)
      │  ├─ gestures/           ← motor de gestos con IA, servicio de sensores y acciones
      │  ├─ estado/           ← alarmas, temporizador, agua y estado compartido
      │  ├─ language/           ← español / inglés
      │  └─ errors/           ← registro de errores y cierres inesperados
      ├─ test/                ← pruebas en la computadora
      └─ androidTest/         ← pruebas de interfaz en el emulador
```

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
              WatchViewModel ── qué se ve (esfera / menú / app) y qué esfera está puesta
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
| La pantalla del reloj (qué se muestra y cuándo) | `mobile/.../watch/WatchHome.kt` |
| La caja: corona, botón lateral y correas | `mobile/.../watch/WatchCase.kt` |
| Las 5 esferas y sus complicaciones | `mobile/.../watch/WatchFaces.kt` |
| El menú de apps dentro del reloj | `mobile/.../watch/WatchMenu.kt` |
| "¿Deseas…?", "¿Fue este gesto?" y la animación de gesto | `mobile/.../watch/WatchAlerts.kt` |
| Cómo se navega dentro del reloj | `mobile/.../watch/WatchViewModel.kt` |
| La lista de apps y las piezas comunes (botones, anillos) | `mobile/.../apps/WatchApps.kt` |
| Alarma, cronómetro, temporizador, calendario | `mobile/.../apps/ClockApps.kt` |
| Pasos, respirar, agua, ejercicio | `mobile/.../apps/HealthApps.kt` |
| Linterna, clima, calculadora, notas, música | `mobile/.../apps/UtilityApps.kt` |
| La sección Gestores, entrenar la IA y ajustes | `mobile/.../apps/GestoresApps.kt` |
| Alarmas, temporizador y agua (que suenen a tiempo) | `mobile/.../estado/WatchState.kt` |
| Qué tan fuerte debe ser un gesto y la IA | `mobile/.../gestures/GestureEngine.kt` |
| Lo que hace cada gesto | `mobile/.../gestures/ActionRunner.kt` y `gestures/Shared.kt` |
| Colores (negro, café oscuro y cobre; modo claro y oscuro) | `mobile/.../watch/Theme.kt` |
| Español / inglés | `mobile/.../language/Language.kt` y `language/Translations.kt` |
| Registro de fallos (también cierres inesperados) | `mobile/.../errors/ErrorLog.kt` |
| Pruebas de interfaz en el emulador | `mobile/src/androidTest/.../WatchUiTest.kt` |

## Pruebas automáticas
Gestores tiene **126 pruebas** que corren en la computadora, sin celular, y **5 pruebas de interfaz** en el emulador. Detalle en [docs/TESTING.md](docs/TESTING.md).

```bash
gradlew :mobile:testDebugUnitTest
```

| Archivo | Qué revisa |
|---|---|
| `BasicClassifierTest` | Reconoce alzar, bajar, girar, doble giro, sacudir, doble golpe y toques; la sensibilidad |
| `MotionSegmenterTest` | Corta los datos del sensor en movimientos; ignora ruido y caminar; pausa después de un gesto |
| `CalibrationTest` | La calibración guiada se ajusta a tu fuerza y nunca deja valores inválidos |
| `LearnerTest` | La IA aprende círculo y Z, los distingue, aprende con el uso, exporta e importa |
| `CalculatorTest` | Suma, resta, decimales, porcentaje, división por cero, borrar |
| `TimeFormatTest` | Tiempos del cronómetro y temporizador |
| `ActionsTest` | Lo que tiene sonido pregunta "¿Deseas…?"; cámara y apps se abren de una vez |
| `GesturesAndAppsTest` | Gestos, acciones por defecto y las 16 apps del reloj |
| `WatchTest` | Cronómetro y hora exacta de las alarmas |
| `WatchViewModelTest` | Navegación del reloj (esfera ⇄ menú ⇄ apps), esferas, botón lateral y lo que piden los gestos |

En GitHub, cada cambio corre las pruebas, el análisis de Android y crea el APK (`.github/workflows/android.yml`).
