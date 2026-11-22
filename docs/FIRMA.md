# Firma de Gestores

La llave de producción es **privada**: no está en este repositorio.

| Dato | Valor |
|---|---|
| Archivo | `C:/Users/Nicky/.claves-android/gestores-release.jks` (fuera del repo) |
| Alias | `gestores` |
| Contraseña | cifrada con DPAPI de Windows en `~/.gradle/gradle.properties` como `GE_STORE_PASSWORD_DPAPI`: solo tu usuario de Windows en este PC puede descifrarla. Nunca está en texto plano ni en GitHub |

## Crear el APK de producción

```
./gradlew :mobile:assembleRelease
```

Sale en `mobile/build/outputs/apk/release/mobile-release.apk`, firmado y optimizado con R8.
Haz una copia de seguridad de la carpeta `.claves-android`.
