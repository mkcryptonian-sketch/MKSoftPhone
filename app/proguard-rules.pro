# ProGuard / R8 rules for PJSIP and callbacks JNI entry points

# Linphone SDK - Keep all classes and interfaces for JNI compatibility
-keep class org.linphone.core.** { *; }
-keep interface org.linphone.core.** { *; }
-keep class org.linphone.mediastream.** { *; }
-keep class org.linphone.core.tools.** { *; }

# Keep our own core SIP classes and states
-keep class com.mksoft.phone.core.sip.** { *; }

# Keep MainActivity and other manifest-declared components to avoid obfuscation issues
-keep class com.mksoft.phone.MainActivity { *; }
-keep class com.mksoft.phone.service.** { *; }
-keep class com.mksoft.phone.receiver.** { *; }

# Keep Firebase Messaging related classes
-keep class com.google.firebase.** { *; }

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
