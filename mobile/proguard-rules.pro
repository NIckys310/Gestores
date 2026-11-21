# =========================================================
# GESTORES · Reglas de R8 (versión de producción)
# R8 quita el código que no se usa y acorta los nombres.
# =========================================================

# Para que los errores de la app digan en qué línea fallaron
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# kotlinx.serialization: copia de los gestos aprendidos y reglas de cada gesto
-keepclassmembers @kotlinx.serialization.Serializable class com.gestores.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
-keepclasseswithmembers class com.gestores.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gestores.app.**$$serializer { *; }
-dontnote kotlinx.serialization.**

# Receptores y servicios declarados en el manifiesto (Android los crea por nombre)
-keep class com.gestores.app.**Receiver { *; }
-keep class com.gestores.app.**Service { *; }
