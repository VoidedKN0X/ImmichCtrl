# kotlinx.serialization models (library ships its own rules; these keep our DTOs and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.immichctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.immichctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.immichctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.maplibre.**
-keep class org.maplibre.android.** { *; }
