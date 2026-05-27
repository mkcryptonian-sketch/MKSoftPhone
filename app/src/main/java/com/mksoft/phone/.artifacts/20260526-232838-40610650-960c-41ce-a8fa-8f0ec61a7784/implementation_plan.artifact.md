# Hot-Reloading Settings Implementation Plan

This plan outlines the changes required to implement hot-reloading for VoIP settings (AEC, AGC, WakeLock, etc.) so that changes take effect immediately without requiring a full app restart.

## Proposed Changes

### [Core SIP Engine]

#### [SipEngineManager.kt](file:///C:/Users/kawch/Documents/antigravity/amazing-volta/app/src/main/java/com/mksoft/phone/core/sip/SipEngineManager.kt)

- Add a method `updateAudioMediaConfig(aecEnabled: Boolean, agcEnabled: Boolean)` to dynamically apply AEC and AGC settings using PJSIP's `libInit` (if supported) or by modifying the active media configuration if possible.
- *Note*: PJSIP usually requires a restart for deep media config changes, but some parameters can be tweaked. If a full restart is needed for these, I will implement a "soft restart" of the engine.

### [Background Service]

#### [SipService.kt](file:///C:/Users/kawch/Documents/antigravity/amazing-volta/app/src/main/java/com/mksoft/phone/service/SipService.kt)

- Observe `DataRepository.settings` flow in `onCreate` or `onStartCommand`.
- On settings change:
    - Update WakeLock/WiFi lock status (acquire or release based on new `wakeLockEnabled` value).
    - Call `sipEngineManager.reinitialize()` or a specific update method if AEC/AGC changed.
    - Re-schedule the keep-alive alarm if `registrationExpiry` or `backgroundKeepAliveEnabled` changed.

## Verification Plan

### Manual Verification
1.  **WakeLock**: Toggle "Keep CPU Awake" in Settings and verify via Logcat that `SipService` acquires or releases the WakeLock immediately.
2.  **Audio Config**: Toggle AEC/AGC and verify that the SIP engine reflects these changes (may require observing PJSIP logs).
3.  **Keep-Alive**: Change the registration expiry and verify that the next alarm is scheduled with the new interval.
