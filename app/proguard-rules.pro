-keep class com.example.chargealarm.** { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context, android.content.Intent);
}
-keepclassmembers class * extends android.app.Service {
    public void onCreate();
    public int onStartCommand(android.content.Intent, int, int);
    public void onDestroy();
}
