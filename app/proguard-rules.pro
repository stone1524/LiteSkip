# kotlinx.serialization: 保留规则模型与生成的 serializer
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.litejump.app.**$$serializer { *; }
-keepclassmembers class com.litejump.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.litejump.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
