# Pruebas de Gestores

## En la computadora (126 pruebas)
```bash
gradlew :mobile:testDebugUnitTest
```
| Archivo | Qué revisa |
|---|---|
| `BasicClassifierTest` | Reconoce alzar, bajar, girar, doble giro, sacudir, doble golpe y toques |
| `MotionSegmenterTest` | Corta los datos del sensor en movimientos; ignora ruido y caminar |
| `CalibrationTest` | La calibración se ajusta a tu fuerza y nunca deja valores inválidos |
| `LearnerTest` | La IA aprende círculo y Z, los distingue, aprende con el uso, exporta e importa |
| `CalculatorTest` · `TimeFormatTest` · `WatchTest` | Calculadora, tiempos, cronómetro y alarmas |
| `ActionsTest` · `GesturesAndAppsTest` | Lo que tiene sonido pregunta antes; gestos, acciones y apps |
| `WatchViewModelTest` | Navegación del reloj (esfera ⇄ menú ⇄ apps) |
| `LanguageTest` | Traductor y que ningún texto quede sin traducir |
| `AccessibilityTest` | Contraste WCAG AA (4,5:1) en modo claro y oscuro; letras de al menos 12 sp |
| `ErrorLogTest` | Registro de errores: guarda dónde pasó y solo los últimos 60 |

## En el emulador o el celular (5 pruebas de interfaz)
```bash
gradlew :mobile:connectedDebugAndroidTest
```
`WatchUiTest`: la corona abre el menú, el botón lateral abre Gestores y el interruptor los enciende,
cambiar a inglés traduce el reloj y una acción con sonido pregunta "¿Deseas…?".

## En GitHub
Cada cambio corre pruebas, análisis de Android (lint), APK y las pruebas de interfaz en un emulador
(`.github/workflows/android.yml`).
