#include <jni.h>
#include <android/log.h>
#include <iostream>
#include <thread>
#include <atomic>
#include <string>
#include <functional>
#include <csignal>
#include <vector>
#include <optional>
#include <memory>
#include <chrono> // Added for std::chrono::milliseconds

#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

// Log tag
#define LOG_TAG "DiscordRPC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<uint64_t> g_applicationId{1435558259892293662};

static std::shared_ptr<discordpp::Client> g_client;
static std::thread g_callbackThread;
static std::atomic<bool> g_running{false};
static std::atomic<bool> g_connected{false};
static std::optional<discordpp::AuthorizationCodeVerifier> g_codeVerifier;

struct PendingActivity {
    std::string details;
    std::string state;
    std::string imageKey;
    long long start = 0;
    long long end = 0;
    int type = 2; // Default to Listening
    bool hasTimestamps = false;
};
static std::optional<PendingActivity> g_pendingActivity;

void applyPendingActivity() {
    if (!g_client || !g_connected || !g_pendingActivity) return;
    
    LOGI("🔄 Applying pending Rich Presence (Absolute Timestamps)...");
    discordpp::Activity activity;
    
    activity.SetType(static_cast<discordpp::ActivityTypes>(g_pendingActivity->type));
    activity.SetStatusDisplayType(discordpp::StatusDisplayTypes::State);

    activity.SetState(g_pendingActivity->details.c_str());   // Title -> State
    activity.SetDetails(g_pendingActivity->state.c_str());   // Artist -> Details
    
    if (g_pendingActivity->hasTimestamps) {
        discordpp::ActivityTimestamps timestamps;
        if (g_pendingActivity->start > 0) timestamps.SetStart(g_pendingActivity->start / 1000);
        if (g_pendingActivity->end > 0) timestamps.SetEnd(g_pendingActivity->end / 1000);
        activity.SetTimestamps(timestamps);
    }
    
    discordpp::ActivityAssets assets;
    if (!g_pendingActivity->imageKey.empty()) {
        assets.SetLargeImage(g_pendingActivity->imageKey.c_str());
        assets.SetLargeText(g_pendingActivity->state.c_str()); 
    }
    activity.SetAssets(assets);
    
    g_client->UpdateRichPresence(activity, [](discordpp::ClientResult result) {
        if (!result.Successful()) {
            LOGE("❌ Rich Presence update failed: %s", result.Error().c_str());
        } else {
            LOGI("✅ Rich Presence updated successfully!");
        }
    });
}
// ... (callbacks stay same)

extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_updateRichPresence(JNIEnv* env, jobject thiz, jstring jdetails, jstring jstate, jstring jimageKey, jint jtype) {
    const char* details = env->GetStringUTFChars(jdetails, nullptr);
    const char* state = env->GetStringUTFChars(jstate, nullptr);
    const char* imageKey = env->GetStringUTFChars(jimageKey, nullptr);
    
    g_pendingActivity = {details, state, imageKey, 0, 0, (int)jtype, false};
    
    LOGI("🎮 Pending Rich Presence (Standard): Type=%d, Key=%s", (int)jtype, imageKey);
    
    applyPendingActivity();
    
    env->ReleaseStringUTFChars(jdetails, details);
    env->ReleaseStringUTFChars(jstate, state);
    env->ReleaseStringUTFChars(jimageKey, imageKey);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_updateRichPresenceWithTimestamps(JNIEnv* env, jobject thiz, jstring jdetails, jstring jstate, jstring jimageKey, jlong jstart, jlong jend, jint jtype) {
    const char* details = env->GetStringUTFChars(jdetails, nullptr);
    const char* state = env->GetStringUTFChars(jstate, nullptr);
    const char* imageKey = env->GetStringUTFChars(jimageKey, nullptr);
    
    g_pendingActivity = {details, state, imageKey, (long long)jstart, (long long)jend, (int)jtype, true};
    
    LOGI("🎮 Pending Rich Presence w/ Timestamps: Type=%d, Key=%s", (int)jtype, imageKey);
    
    applyPendingActivity();
    
    env->ReleaseStringUTFChars(jdetails, details);
    env->ReleaseStringUTFChars(jstate, state);
    env->ReleaseStringUTFChars(jimageKey, imageKey);
}

void runCallbackLoop() {
    LOGI("🔄 Callback loop started");
    while (g_running) {
        discordpp::RunCallbacks();
        std::this_thread::sleep_for(std::chrono::milliseconds(16));
    }
    LOGI("🛑 Callback loop stopped");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_initDiscord(JNIEnv* env, jobject thiz, jlong jclientId) {
    g_applicationId = static_cast<uint64_t>(jclientId);
    LOGI("🚀 Initializing Discord SDK with Client ID: %lld", (long long)jclientId);
    
    if (g_running) {
        LOGI("⚠️ Discord SDK already running, stopping previous instance...");
        g_running = false;
        g_connected = false;
        if (g_callbackThread.joinable()) {
            g_callbackThread.join();
        }
    }
    
    g_client = std::make_shared<discordpp::Client>();
    
    g_client->AddLogCallback([](auto message, auto severity) {
        LOGI("[Discord SDK] %s", message.c_str());
    }, discordpp::LoggingSeverity::Info);
    
    g_client->SetStatusChangedCallback([](discordpp::Client::Status status, discordpp::Client::Error error, int32_t errorDetail) {
        LOGI("🔄 Status changed: %s", discordpp::Client::StatusToString(status).c_str());
        if (status == discordpp::Client::Status::Ready) {
            LOGI("✅ Client is ready!");
            g_connected = true;
            applyPendingActivity();
        } else if (error != discordpp::Client::Error::None) {
            LOGE("❌ Connection Error: %s Detail: %d", discordpp::Client::ErrorToString(error).c_str(), errorDetail);
            g_connected = false;
        }
    });
    
    g_codeVerifier = g_client->CreateAuthorizationCodeVerifier();
    
    g_running = true;
    g_callbackThread = std::thread(runCallbackLoop);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_startAuthorization(JNIEnv* env, jobject thiz) {
    if (!g_client || !g_codeVerifier) {
        LOGE("❌ Client not initialized!");
        return;
    }
    
    LOGI("🚀 Starting OAuth authorization...");
    
    discordpp::AuthorizationArgs args{};
    args.SetClientId(g_applicationId);
    args.SetScopes(discordpp::Client::GetDefaultPresenceScopes());
    args.SetCodeChallenge(g_codeVerifier->Challenge());
    args.SetCustomSchemeParam("discordrpc");
    
    g_client->Authorize(args, [](discordpp::ClientResult result, std::string code, std::string redirectUri) {
        // This callback won't be called since we handle it manually in handleOAuthCallback
        LOGI("📞 Authorize callback (should not be called)");
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_connect(JNIEnv* env, jobject thiz) {
    if (!g_client) {
        LOGE("❌ Client not initialized! Cannot connect.");
        return;
    }
    LOGI("🚀 Manually connecting to Discord Gateway...");
    g_client->Connect();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_handleOAuthCallback(JNIEnv* env, jobject thiz, jstring jcode, jstring jredirectUri) {
    const char* code = env->GetStringUTFChars(jcode, nullptr);
    const char* redirectUri = env->GetStringUTFChars(jredirectUri, nullptr);
    
    LOGI("📞 handleOAuthCallback called from Java!");
    LOGI("   Code: %s", code);
    LOGI("   Redirect URI: %s", redirectUri);
    
    if (!g_client || !g_codeVerifier) {
        LOGE("❌ Client not initialized!");
        env->ReleaseStringUTFChars(jcode, code);
        env->ReleaseStringUTFChars(jredirectUri, redirectUri);
        return;
    }
    
    // Strip query parameters from redirect URI
    std::string redirectUriStr(redirectUri);
    size_t queryPos = redirectUriStr.find('?');
    if (queryPos != std::string::npos) {
        redirectUriStr = redirectUriStr.substr(0, queryPos);
    }
    
    // Manually trigger the token exchange
    LOGI("🔄 Calling GetToken manually with URI: %s", redirectUriStr.c_str());
    
    // We need a global ref to class to call static method? Or just find class.
    // Ideally we should cache methodID in JNI_OnLoad, but finding it here works for now.
    jclass gatewayClass = env->FindClass("com/example/discordrpc/DiscordGateway");
    jmethodID onTokenReceivedMethod = env->GetMethodID(gatewayClass, "onTokenReceived", "(Ljava/lang/String;Ljava/lang/String;)V");

    // Need to use GlobalRef for JNI usage inside lambda thread
    JavaVM* jvm;
    env->GetJavaVM(&jvm);
    jobject globalGateway = env->NewGlobalRef(thiz); // Thiz is the object instance

    g_client->GetToken(g_applicationId, std::string(code), g_codeVerifier->Verifier(), redirectUriStr,
        [jvm, globalGateway, onTokenReceivedMethod](discordpp::ClientResult result, std::string accessToken, std::string refreshToken, discordpp::AuthorizationTokenType tokenType, int32_t expiresIn, std::string scope) {
            LOGI("📞 GetToken callback triggered!");
            if (!result.Successful()) {
                LOGE("❌ GetToken Error: %s", result.Error().c_str());
                return;
            }
            LOGI("🔓 Access token received! Connecting...");
            
            // Call Java to save token
            JNIEnv* env;
            if (jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                 jstring jAccess = env->NewStringUTF(accessToken.c_str());
                 jstring jRefresh = env->NewStringUTF(refreshToken.c_str());
                 
                 // DiscordGateway is a singleton object, so we call method on the instance we got
                 // But wait, 'thiz' in Kotlin object is the singleton instance.
                 jclass gatewayClasses = env->GetObjectClass(globalGateway);
                 // Re-find method ID just to be safe or reuse? Reuse is fine if class didn't unload.
                 // Actually easier to just find class again or use the object.
                 env->CallVoidMethod(globalGateway, onTokenReceivedMethod, jAccess, jRefresh);
                 
                 env->DeleteLocalRef(jAccess);
                 env->DeleteLocalRef(jRefresh);
                 jvm->DetachCurrentThread();
            }

            g_client->UpdateToken(discordpp::AuthorizationTokenType::Bearer, accessToken, [](discordpp::ClientResult result) {
                LOGI("📞 UpdateToken callback triggered!");
                if (result.Successful()) {
                    LOGI("🔑 Token updated, calling Connect()");
                    g_client->Connect();
                    g_connected = true;
                } else {
                    LOGE("❌ UpdateToken Error: %s", result.Error().c_str());
                }
            });
        });
    
    env->ReleaseStringUTFChars(jcode, code);
    env->ReleaseStringUTFChars(jredirectUri, redirectUri);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_restoreSession(JNIEnv* env, jobject thiz, jstring jAccessToken, jstring jRefreshToken) {
    const char* accessToken = env->GetStringUTFChars(jAccessToken, nullptr);
    const char* refreshToken = env->GetStringUTFChars(jRefreshToken, nullptr);
    
    if (!g_client) {
        LOGE("❌ Client not initialized! Cannot restore session.");
         env->ReleaseStringUTFChars(jAccessToken, accessToken);
         env->ReleaseStringUTFChars(jRefreshToken, refreshToken);
        return;
    }
    
    LOGI("♻️ Restoring session with saved token...");
    // Update token and THEN connect
    g_client->UpdateToken(discordpp::AuthorizationTokenType::Bearer, std::string(accessToken), [](discordpp::ClientResult result) {
         if (result.Successful()) {
             LOGI("✅ Token restored. Ready to connect.");
             // We don't connect here automatically, we wait for the explicit connect() call or called it here?
             // The user code calls init -> restore -> connect. So we just set state.
         } else {
             LOGE("❌ Failed to restore token: %s", result.Error().c_str());
         }
    });

    env->ReleaseStringUTFChars(jAccessToken, accessToken);
    env->ReleaseStringUTFChars(jRefreshToken, refreshToken);
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_discordrpc_DiscordGateway_shutdownDiscord(JNIEnv* env, jobject thiz) {
    LOGI("🛑 Shutting down Discord SDK");
    g_running = false;
    g_connected = false;
    if (g_callbackThread.joinable()) {
        g_callbackThread.join();
    }
    g_client.reset();
}
