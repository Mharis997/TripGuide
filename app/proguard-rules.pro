# Room, WorkManager, Compose and Coil ship their own consumer rules.
# Keep line numbers for readable Play Console crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Tesseract4Android ships no consumer rules; its native code reads Java fields and calls constructors by name.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
