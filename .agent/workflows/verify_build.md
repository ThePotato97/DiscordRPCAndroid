---
description: Builds the application to verify code integrity.
---

1. Run Gradle assembly
   ```bash
   gradlew installDebug && adb shell am force-stop com.thepotato.discordrpc && adb shell am start -n com.thepotato.discordrpc/com.thepotato.discordrpc.MainActivity
   ```