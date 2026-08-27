# ProGuard / R8 Obfuscation & Hardening Rules against Reverse Engineering

# Ignore missing optional dependencies (e.g. slf4j logger)
-dontwarn org.slf4j.**

# Repackage all classes into the root package for heavy obfuscation
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Strip source file attributes and line numbers to complicate decompilation analysis
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable

# Keep essential Android Entry Points
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Preserve WebSocket library dependencies while obfuscating app internal logic
-keep class org.java_websocket.** { *; }
