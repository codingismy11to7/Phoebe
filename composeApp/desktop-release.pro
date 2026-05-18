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

# JNA builds native library proxies reflectively. Keep both JNA itself and the desktop
# DWM bridge so release packages can still style the Windows title bar.
-keep class com.sun.jna.** { *; }
-keep class com.phoebe.app.MainKt$WindowsWindowChrome** { *; }
-keep class com.phoebe.app.MainKt$WindowsWindowChrome$WinUser32** { *; }
-keep class com.phoebe.app.MainKt$WindowsWindowChrome$DwmApi** { *; }

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
