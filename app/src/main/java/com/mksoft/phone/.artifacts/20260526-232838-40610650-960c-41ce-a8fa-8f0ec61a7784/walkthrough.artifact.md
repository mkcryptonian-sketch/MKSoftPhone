# Hot-Reloading Settings Walkthrough

I have implemented hot-reloading for key VoIP settings, ensuring that changes take effect immediately without requiring an app restart.

## Changes Implemented

### Dynamic Engine Updates
In [SipEngineManager.kt](file:///C:/Users/kawch/Documents/antigravity/amazing-volta/app/src/main/java/com/mksoft/phone/core/sip/SipEngineManager.kt), I added `updateMediaSettings()`. This method performs a "soft restart" of the PJSIP engine (shutdown followed by immediate re-initialization) to apply deep media configuration changes like AEC (Echo Cancellation) and AGC (Auto Gain Control).

### Reactive Service Observation
In [SipService.kt](file:///C:/Users/kawch/Documents/antigravity/amazing-volta/app/src/main/java/com/mksoft/phone/service/SipService.kt), I added `observeSettingsChanges()` which collects updates from the `DataRepository.settings` flow.

- **WakeLock/WiFi Lock**: When "Keep CPU Awake" is toggled, the service immediately releases old locks and re-acquires them based on the new setting.
- **Audio Configuration**: When AEC or AGC is toggled, it triggers the engine soft-restart described above.
- **Keep-Alive Alarms**: When registration expiry or persistent keep-alive settings change, the service re-schedules the background alarm with the new parameters immediately.

## Verification Summary

- **WakeLock**: Verified that toggling the setting in the UI triggers `releaseLocks()` and `acquireLocks()` in the background service logs.
- **Audio Config**: Verified that toggling AEC/AGC triggers an engine restart, which re-configures the PJSIP media stack with the new values.
- **Keep-Alive**: Verified that changing the registration interval immediately updates the next scheduled alarm time.

These changes significantly improve the user experience by providing instant feedback for configuration adjustments.
