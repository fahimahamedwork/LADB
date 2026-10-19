# Termux terminal engine: JNI native methods must not be renamed
-keep class com.termux.terminal.** { *; }

# Keep kotlinx.coroutines debug internals if present
-dontwarn kotlinx.coroutines.**
