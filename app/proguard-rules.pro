# ProGuard / R8 rules for PJSIP and callbacks JNI entry points

# Keep PJSUA2 classes and methods used by JNI
-keep class org.pjsip.pjsua2.** { *; }
-keepclassmembers class org.pjsip.pjsua2.** { *; }

# Keep all native methods
-keepclasseswithmembers class * {
    native <methods>;
}

# Preserve the PJSIP JNI-related classes
-keep class org.pjsip.pjsua2.pjsua2 { *; }
-keep class org.pjsip.pjsua2.pjsua2JNI { *; }

# Keep our SipEngineManager callback subclasses
-keep class com.mksoft.phone.core.sip.SipEngineManager$MyAccount { *; }
-keep class com.mksoft.phone.core.sip.SipEngineManager$MyCall { *; }
-keep class com.mksoft.phone.core.sip.SipEngineManager { *; }
