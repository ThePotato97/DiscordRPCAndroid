---
description: Captures and searches device logs for crashes and errors.
---

1. Clear old logs
   ```bash
   adb logcat -c
   ```

2. Capture logs (waiting for potential crash/events)
   ```bash
   Start-Sleep -Seconds 5; adb logcat -d > logs_dump.txt
   ```

3. Search for errors
   ```bash
   Select-String -Path logs_dump.txt -Pattern "FATAL|Exception|Error" -Context 0,5
   ```
