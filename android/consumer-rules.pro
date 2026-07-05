# JNI exports are bound to ai.velr.internal.Native method names.
-keep class ai.velr.internal.Native { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
