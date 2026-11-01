# Tivihome — règles R8
# Notre code reste intact (pas de réflexion, mais on évite toute surprise)
-keep class com.petitjord.tivihome.** { *; }

# Dépendances optionnelles d'OkHttp
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**

# Garde les numéros de ligne pour le journal de plantage
-keepattributes SourceFile,LineNumberTable
