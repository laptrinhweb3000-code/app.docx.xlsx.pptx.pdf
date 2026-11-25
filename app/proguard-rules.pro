# Keep Apache POI legacy parsers intact if minification is enabled later
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn javax.xml.**
-dontwarn java.awt.**
