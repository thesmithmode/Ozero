-keep class ru.ozero.enginefptn.FptnNativeResponse {
    <init>(int, java.lang.String, java.lang.String);
}
-keepclassmembers class ru.ozero.enginefptn.FptnNativeWebSocket {
    void onOpenImpl();
    void onMessageImpl(byte[]);
    void onFailureImpl();
}
