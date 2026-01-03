<div align="center">
  <br>
  <h1>DiscordRPCAndroid</h1>
  <p><b>Mirror your Android media sessions (YouTube, Spotify, Mobile Games) to Discord Rich Presence.</b></p>
  
  <p>
    <img src="https://img.shields.io/badge/Android-7.0%20to%2016-3DDC84?style=for-the-badge&logo=android&logoColor=white">
    <img src="https://img.shields.io/badge/Discord-Social_SDK-5865F2?style=for-the-badge&logo=discord&logoColor=white">
    <img src="https://img.shields.io/github/v/release/ThePotato97/DiscordRPCAndroid?style=for-the-badge&color=orange">
  </p>
  
  <p>Built with the official <b>Discord Social SDK</b> for seamless mobile status mirroring.</p>
</div>

---

### ✨ Features
- **Auto-Detection:** Detects active media from YouTube, Spotify, Twitch, and more.
- **Custom Client ID:** Fully personalized Rich Presence using your own app assets.
- **Background Service:** Reliability maintained via a Notification Listener service.
- **Session Persistence:** Securely saves your OAuth tokens for one-tap connections.

> [!TIP]
> **Safety First:** This app uses the official **Discord Social SDK** and OAuth2 for authorization. It does **not** require your account token and does **not** involve any self-botting, making it safe to use without risk to your Discord account.

---

### 🛠️ Developer Setup (Social SDK)
To use this as your own custom RPC, you must configure a Discord Application:

1. Create a new App on the **[Discord Developer Portal](https://discord.com/developers/applications)**.
2. Go to **Social SDK** -> **Getting Started** and fill out the information (this is not manually checked and is approved instantly).
3. Go to **OAuth2** -> **Redirects**.
4. Add the following Redirect URI: `discordrpc:/authorize/callback`
5. Copy your **Client ID** (found under **General Information** as Application ID, or in the **OAuth2** tab) and paste it into the app's onboarding screen.

---

### 🚀 Quick Start (Installation)

1. **Download** the latest APK from the [Releases](https://github.com/ThePotato97/DiscordRPCAndroid/releases) page.
2. **Install** on your device (Android 7.0 to 16 supported).
3. **Open** the app and follow the onboarding to enter your Client ID.

---

<div align="center">
  <sub>Built by ThePotato97 • Licensed under <a href="LICENSE">GPL-3.0</a></sub>
</div>
