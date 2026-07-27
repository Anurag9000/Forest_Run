
-keepattributes Signature,*Annotation*

# Enum names are persisted in SharedPreferences and must remain stable.
-keepclassmembers enum com.yourname.forest_run.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Strip verbose/debug logging only; retain warnings and errors for release diagnosis.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
