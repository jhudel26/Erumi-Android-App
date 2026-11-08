# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class com.erumi.nativeapp.data.local.entity.** { *; }
-keep class com.erumi.nativeapp.data.remote.model.** { *; }
-keep class com.erumi.nativeapp.domain.model.** { *; }
