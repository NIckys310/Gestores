# Gestores · Tu celular convertido en smartwatch

Una sola app para el celular. La pantalla es siempre un **reloj de lujo** (correa de cuero, bisel y
corona dorados) y todas las apps se abren **dentro de la esfera**. No se conecta con nada: ni PC,
ni relojes Wear OS, ni otras apps. Solo usa internet para el clima.

## Cómo se usa
- **Corona dorada** (a la derecha del reloj): abre las apps; desde una app, vuelve a la lista.
- **Tocar la esfera**: también abre las apps.
- **Deslizar sobre la esfera**: cambia de esfera (Clásica romana, Cronógrafo, Minimalista, Digital de lujo, Deportiva).
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

## Qué archivo cambiar
| Quiero cambiar… | Archivo |
|---|---|
| El reloj, la corona y las esferas | `mobile/.../WatchHome.kt` |
| Las apps de dentro del reloj | `mobile/.../WatchApps.kt` |
| Alarmas, temporizador, agua | `mobile/.../WatchState.kt` |
| Qué tan fuerte debe ser un gesto y la IA | `mobile/.../GestureEngine.kt` |
| Lo que hace cada gesto | `mobile/.../ActionRunner.kt` y `Shared.kt` |
| Colores (oro, negro, marfil) | `mobile/.../Theme.kt` |
