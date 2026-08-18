# dnsjava + slf4j
-keep class org.xbill.** { *; }
-keepclassmembers class org.xbill.** { *; }
-dontwarn org.xbill.**
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
-keep class org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticLoggerBinder