-keep class * implements io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider {
    public <init>();
}

-keep class org.sqlite.** {
    *;
}

-keep class javafx.** {
    *;
}

# jnativehook loads GlobalScreen$NativeHookThread.dispatchEvent via JNI; shrinking breaks release builds.
-keep class com.github.kwhat.jnativehook.** { *; }
-keepnames class com.github.kwhat.jnativehook.GlobalScreen$NativeHookThread {
    protected static void dispatchEvent(com.github.kwhat.jnativehook.NativeInputEvent);
}

-keep class com.sun.glass.** {
    *;
}

-keep class com.sun.javafx.** {
    *;
}

-keep class com.sun.media.** {
    *;
}

-keep class com.sun.prism.** {
    *;
}
