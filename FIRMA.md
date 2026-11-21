# Firma de Gestores

La llave de producción es **privada**: no está en este repositorio.

| Dato | Valor |
|---|---|
| Archivo | `C:/Users/Nicky/.claves-android/gestores-release.jks` (fuera del repo) |
| Alias | `gestores` |
| Contraseña | en `~/.gradle/gradle.properties` como `GE_STORE_PASSWORD` (no se sube a GitHub) |

## Crear el APK de producción

```
./gradlew :mobile:assembleRelease
```

Sale en `mobile/build/outputs/apk/release/mobile-release.apk`, firmado y optimizado con R8.
Haz una copia de seguridad de la carpeta `.claves-android`.
