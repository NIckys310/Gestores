# Seguridad de Gestores

| Tema | Cómo se protege |
|---|---|
| Datos personales | Gestores no tiene cuentas ni servidor: todo se queda en el celular (SharedPreferences privadas de la app). Solo usa internet para el clima (Open-Meteo, sin clave). |
| Contraseñas | La app no pide ni guarda contraseñas. La única contraseña del proyecto es la de la llave de firma: está **cifrada con DPAPI de Windows** fuera del repositorio ([SIGNING.md](SIGNING.md)). |
| Llave de firma | Privada, fuera del repositorio y de GitHub. El `.gitignore` bloquea `*.jks` y `*.keystore`. |
| APK de producción | Firmado y ofuscado con R8 (más difícil de copiar o modificar). |
| Acciones con sonido | Siempre preguntan "¿Deseas…?" antes; si la app está cerrada, la pregunta llega como notificación. |
| Abrir otras apps | Solo las que la persona eligió para cada gesto. |
| Fallos | Se guardan en el propio celular (`fallos.log`, últimos 60); nada se envía a terceros. |
