# Add project specific ProGuard rules here.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.yangfei.smrtenglish.bean.** { *; }
-keep class com.yangfei.smrtenglish.http.CookieItem { *; }
