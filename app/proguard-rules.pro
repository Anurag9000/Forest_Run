# Project-specific R8 rules.

# Several persisted values use enum.name and valueOf. Keep the generated lookup
# members so minification cannot invalidate existing on-device save data.
-keepclassmembers enum com.anurag9000.forestrun.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove non-diagnostic debug logging from optimized release builds.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
