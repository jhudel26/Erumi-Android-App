# Add project specific ProGuard rules here.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class com.erumi.nativeapp.data.local.entity.** { *; }
-keep class com.erumi.nativeapp.data.remote.model.** { *; }
-keep class com.erumi.nativeapp.domain.model.** { *; }
-keep class com.erumi.nativeapp.core.updater.** { *; }
-keep class com.erumi.nativeapp.core.image.providers.** { *; }
-keep class com.erumi.nativeapp.core.database.entity.** { *; }
-keep class com.erumi.nativeapp.manga.data.local.** { *; }
-keep class com.erumi.nativeapp.manga.domain.models.** { *; }
