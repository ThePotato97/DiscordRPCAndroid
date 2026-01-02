---
description: Builds the application to verify code integrity.
---

1. Run Gradle assembly
   ```bash
   gradlew installDebug && adb shell am force-stop com.example.discordrpc && adb shell am start -n com.example.discordrpc/com.example.discordrpc.MainActivity
   ```