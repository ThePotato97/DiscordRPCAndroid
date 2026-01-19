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
#include <mutex>

#define DISCORDPP_IMPLEMENTATION
#include "discordpp.h"

#define LOG_TAG "DiscordRPC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::atomic<uint64_t> g_applicationId{1435558259892293662};

static std::shared_ptr<discordpp::Client> g_client;
static std::thread g_callbackThread;
static std::atomic<bool> g_running{false};
static std::atomic<bool> g_connected{false};
static std::optional<discordpp::AuthorizationCodeVerifier> g_codeVerifier;
static std::mutex g_sdkMutex;

struct PendingActivity {
    std::string details;
    std::string state;
    std::string imageKey;
    std::string appName;
    long long start = 0;
    long long end = 0;
    int type = 2; // Default to Listening
    int statusDisplayType = 0;
    bool hasTimestamps = false;
};
static std::optional<PendingActivity> g_pendingActivity;

void applyPendingActivity() {
    std::lock_guard<std::mutex> lock(g_sdkMutex);
    if (!g_client || !g_connected || !g_pendingActivity) return;
    
    LOGI("Applying pending Rich Presence...");
    discordpp::Activity activity;
    
    activity.SetType(static_cast<discordpp::ActivityTypes>(g_pendingActivity->type));
    activity.SetStatusDisplayType(static_cast<discordpp::StatusDisplayTypes>(g_pendingActivity->statusDisplayType));

    activity.SetDetails(g_pendingActivity->details.c_str());
    activity.SetState(g_pendingActivity->state.c_str());
    activity.SetName(g_pendingActivity->appName.c_str());
     
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
            LOGE("Rich Presence update failed: %s", result.Error().c_str());
        } else {
            LOGI("Rich Presence updated successfully");
        }
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_updateRichPresence(JNIEnv* env, jobject thiz, jstring jAppName, jstring jdetails, jstring jstate, jstring jimageKey, jint jtype, jint jStatusDisplayType) {
    const char* appName = env->GetStringUTFChars(jAppName, nullptr);
    const char* details = env->GetStringUTFChars(jdetails, nullptr);
    const char* state = env->GetStringUTFChars(jstate, nullptr);
    const char* imageKey = env->GetStringUTFChars(jimageKey, nullptr);
    
    g_pendingActivity = {details, state, imageKey, appName, 0, 0, (int)jtype, (int)jStatusDisplayType, false};
    
    LOGI("Pending Rich Presence: App=%s, Type=%d, Display=%d", appName, (int)jtype, (int)jStatusDisplayType);
    
    applyPendingActivity();
    
    env->ReleaseStringUTFChars(jAppName, appName);
    env->ReleaseStringUTFChars(jdetails, details);
    env->ReleaseStringUTFChars(jstate, state);
    env->ReleaseStringUTFChars(jimageKey, imageKey);
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_updateRichPresenceWithTimestamps(JNIEnv* env, jobject thiz, jstring jAppName, jstring jdetails, jstring jstate, jstring jimageKey, jlong jstart, jlong jend, jint jtype, jint jStatusDisplayType) {
    const char* appName = env->GetStringUTFChars(jAppName, nullptr);
    const char* details = env->GetStringUTFChars(jdetails, nullptr);
    const char* state = env->GetStringUTFChars(jstate, nullptr);
    const char* imageKey = env->GetStringUTFChars(jimageKey, nullptr);
    
    g_pendingActivity = {details, state, imageKey, appName, (long long)jstart, (long long)jend, (int)jtype, (int)jStatusDisplayType, true};
    
    LOGI("Pending Rich Presence w/ Timestamps: App=%s", appName);
    
    applyPendingActivity();
    
    env->ReleaseStringUTFChars(jAppName, appName);
    env->ReleaseStringUTFChars(jdetails, details);
    env->ReleaseStringUTFChars(jstate, state);
    env->ReleaseStringUTFChars(jimageKey, imageKey);
}

void runCallbackLoop() {
    LOGI("Callback loop started");
    while (g_running) {
        discordpp::RunCallbacks();
        std::this_thread::sleep_for(std::chrono::milliseconds(16));
    }
    LOGI("Callback loop stopped");
}

static JavaVM* g_jvm = nullptr;
static jobject g_gateway = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_initDiscord(JNIEnv* env, jobject thiz, jlong jclientId) {
    env->GetJavaVM(&g_jvm);
    
    // Update Gateway Reference (Always)
    if (g_gateway) {
        env->DeleteGlobalRef(g_gateway);
    }
    g_gateway = env->NewGlobalRef(thiz);
    
    if (g_running && g_connected && g_client) {
        auto userOpt = g_client->GetCurrentUserV2();
        if (userOpt.has_value()) {
            auto user = *userOpt;
            
            jclass gatewayClass = env->GetObjectClass(g_gateway);
            jmethodID onUserUpdate = env->GetMethodID(gatewayClass, "onCurrentUserUpdate", "(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;)V");
            
            if (onUserUpdate) {
                jstring jName = env->NewStringUTF(user.Username().c_str());
                jstring jDisc = env->NewStringUTF("0");
                
                std::string avatarStr = "";
                auto avatarOpt = user.Avatar();
                if (avatarOpt.has_value()) {
                    avatarStr = *avatarOpt;
                }
                jstring jAvatar = env->NewStringUTF(avatarStr.c_str());
                
                env->CallVoidMethod(g_gateway, onUserUpdate, jName, jDisc, (jlong)user.Id(), jAvatar);
                
                env->DeleteLocalRef(jName);
                env->DeleteLocalRef(jDisc);
                env->DeleteLocalRef(jAvatar);
            }
        }
        return;
    }
    
    g_applicationId = static_cast<uint64_t>(jclientId);
    LOGI("Initializing Discord SDK with Client ID: %lld", (long long)jclientId);
    
    if (g_running) {
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
        LOGI("Status changed: %s", discordpp::Client::StatusToString(status).c_str());
        if (status == discordpp::Client::Status::Ready) {
            LOGI("Client is ready");
            g_connected = true;
            // Apply any pending activity that was set before connection
            applyPendingActivity();
            // Fetch User Info
            auto userOpt = g_client->GetCurrentUserV2();
            if (userOpt.has_value() && g_jvm && g_gateway) {
                 auto user = *userOpt;
                 LOGI("Got User: %s", user.Username().c_str());
                 
                 JNIEnv* env;
                 bool attached = false;
                 if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
                     g_jvm->AttachCurrentThread(&env, nullptr);
                     attached = true;
                 }
                 
                 jclass gatewayClass = env->GetObjectClass(g_gateway);
                 jmethodID onUserUpdate = env->GetMethodID(gatewayClass, "onCurrentUserUpdate", "(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;)V");
                 
                 if (onUserUpdate) {
                     jstring jName = env->NewStringUTF(user.Username().c_str());
                     jstring jDisc = env->NewStringUTF("0");
                     
                     std::string avatarStr = "";
                     auto avatarOpt = user.Avatar();
                     if (avatarOpt.has_value()) {
                         avatarStr = *avatarOpt;
                     }
                     jstring jAvatar = env->NewStringUTF(avatarStr.c_str());
                     
                     env->CallVoidMethod(g_gateway, onUserUpdate, jName, jDisc, (jlong)user.Id(), jAvatar);
                     
                     env->DeleteLocalRef(jName);
                     env->DeleteLocalRef(jDisc);
                     env->DeleteLocalRef(jAvatar);
                 }
                 
                 if (attached) {
                     g_jvm->DetachCurrentThread();
                 }
            } else {
                 LOGI("GetCurrentUserV2 returned no user.");
            }
        } else if (error != discordpp::Client::Error::None) {
            LOGE("Connection Error: %s Detail: %d", discordpp::Client::ErrorToString(error).c_str(), errorDetail);
            g_connected = false;
        }
    });
    
    g_codeVerifier = g_client->CreateAuthorizationCodeVerifier();
    
    g_running = true;
    g_callbackThread = std::thread(runCallbackLoop);
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_startAuthorization(JNIEnv* env, jobject thiz) {
    if (!g_client || !g_codeVerifier) {
        LOGE("Client not initialized");
        return;
    }
    
    LOGI("Starting OAuth authorization");
    
    discordpp::AuthorizationArgs args{};
    args.SetClientId(g_applicationId);
    args.SetScopes(discordpp::Client::GetDefaultPresenceScopes());
    args.SetCodeChallenge(g_codeVerifier->Challenge());
    args.SetCustomSchemeParam("discordrpc");
    
    g_client->Authorize(args, [](discordpp::ClientResult result, std::string code, std::string redirectUri) {
        LOGI("Authorize callback triggered");
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_connect(JNIEnv* env, jobject thiz) {
    if (!g_client) {
        LOGE("Client not initialized! Cannot connect");
        return;
    }
    LOGI("Connecting to Discord Gateway");
    g_client->Connect();
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_handleOAuthCallback(JNIEnv* env, jobject thiz, jstring jcode, jstring jredirectUri) {
    const char* code = env->GetStringUTFChars(jcode, nullptr);
    const char* redirectUri = env->GetStringUTFChars(jredirectUri, nullptr);
    
    LOGI("handleOAuthCallback called");
    
    if (!g_client || !g_codeVerifier) {
        LOGE("Client not initialized");
        env->ReleaseStringUTFChars(jcode, code);
        env->ReleaseStringUTFChars(jredirectUri, redirectUri);
        return;
    }
    
    std::string redirectUriStr(redirectUri);
    size_t queryPos = redirectUriStr.find('?');
    if (queryPos != std::string::npos) {
        redirectUriStr = redirectUriStr.substr(0, queryPos);
    }
    
    jclass gatewayClass = env->FindClass("com/thepotato/discordrpc/DiscordGateway");
    jmethodID onTokenReceivedMethod = env->GetMethodID(gatewayClass, "onTokenReceived", "(Ljava/lang/String;Ljava/lang/String;)V");

    JavaVM* jvm;
    env->GetJavaVM(&jvm);
    jobject globalGateway = env->NewGlobalRef(thiz);

    g_client->GetToken(g_applicationId, std::string(code), g_codeVerifier->Verifier(), redirectUriStr,
        [jvm, globalGateway, onTokenReceivedMethod](discordpp::ClientResult result, std::string accessToken, std::string refreshToken, discordpp::AuthorizationTokenType tokenType, int32_t expiresIn, std::string scope) {
            LOGI("GetToken callback triggered");
            if (!result.Successful()) {
                LOGE("GetToken Error: %s", result.Error().c_str());
                return;
            }
            LOGI("Access token received!");
            
            JNIEnv* env;
            if (jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                 jstring jAccess = env->NewStringUTF(accessToken.c_str());
                 jstring jRefresh = env->NewStringUTF(refreshToken.c_str());
                 
                 env->CallVoidMethod(globalGateway, onTokenReceivedMethod, jAccess, jRefresh);
                 
                 env->DeleteLocalRef(jAccess);
                 env->DeleteLocalRef(jRefresh);
                 jvm->DetachCurrentThread();
            }

            g_client->UpdateToken(discordpp::AuthorizationTokenType::Bearer, accessToken, [](discordpp::ClientResult result) {
                if (result.Successful()) {
                    LOGI("Token updated, connecting...");
                    g_client->Connect();
                } else {
                    LOGE("UpdateToken Error: %s", result.Error().c_str());
                }
            });
        });
    
    env->ReleaseStringUTFChars(jcode, code);
    env->ReleaseStringUTFChars(jredirectUri, redirectUri);
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_restoreSession(JNIEnv* env, jobject thiz, jstring jAccessToken, jstring jRefreshToken) {
    const char* accessToken = env->GetStringUTFChars(jAccessToken, nullptr);
    const char* refreshToken = env->GetStringUTFChars(jRefreshToken, nullptr);
    
    if (!g_client) {
        LOGE("Client not initialized! Cannot restore session");
         env->ReleaseStringUTFChars(jAccessToken, accessToken);
         env->ReleaseStringUTFChars(jRefreshToken, refreshToken);
        return;
    }
    
    LOGI("Restoring session with saved token");
    g_client->UpdateToken(discordpp::AuthorizationTokenType::Bearer, std::string(accessToken), [](discordpp::ClientResult result) {
         if (result.Successful()) {
             LOGI("Token restored");
             // Connect after successfully updating token
             LOGI("Connecting after token restore");
             g_client->Connect();
         } else {
             LOGE("Failed to restore token: %s", result.Error().c_str());
         }
    });

    env->ReleaseStringUTFChars(jAccessToken, accessToken);
    env->ReleaseStringUTFChars(jRefreshToken, refreshToken);
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_clearActivity(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_sdkMutex);
    if (!g_client || !g_connected) {
        LOGE("clearActivity: Client not ready or not connected");
        return;
    }
    LOGI("Clearing Rich Presence activity");
    g_client->ClearRichPresence();
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_shutdownDiscord(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_sdkMutex);
    LOGI("Shutting down Discord SDK");
    g_running = false;
    g_connected = false;
    if (g_callbackThread.joinable()) {
        g_callbackThread.join();
    }
    g_client.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_thepotato_discordrpc_DiscordGateway_requestUserUpdate(JNIEnv* env, jobject thiz) {
    if (!g_client || !g_connected) {
        LOGE("requestUserUpdate: Client not ready or not connected");
        return;
    }
    
    // Fetch User Info
    auto userOpt = g_client->GetCurrentUserV2();
    if (userOpt.has_value()) {
         auto user = *userOpt;
         LOGI("requestUserUpdate: Got User from cache: %s", user.Username().c_str());
         
         jclass gatewayClass = env->GetObjectClass(thiz);
         jmethodID onUserUpdate = env->GetMethodID(gatewayClass, "onCurrentUserUpdate", "(Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;)V");
         
         if (onUserUpdate) {
             LOGI("requestUserUpdate: Found Java callback method");
             jstring jName = env->NewStringUTF(user.Username().c_str());
             jstring jDisc = env->NewStringUTF("0");
             
             std::string avatarStr = "";
             auto avatarOpt = user.Avatar();
             if (avatarOpt.has_value()) {
                 avatarStr = *avatarOpt;
             }
             jstring jAvatar = env->NewStringUTF(avatarStr.c_str());
             
             env->CallVoidMethod(thiz, onUserUpdate, jName, jDisc, (jlong)user.Id(), jAvatar);
             
             env->DeleteLocalRef(jName);
             env->DeleteLocalRef(jDisc);
             env->DeleteLocalRef(jAvatar);
         } else {
             LOGE("requestUserUpdate: Could NOT find onCurrentUserUpdate method! Check signature.");
         }
    } else {
         LOGI("requestUserUpdate: No user logic available in client cache (yet)");
         // Attempt to force a fetch if possible, or just wait?
         // For now, logging this is crucial to know if we are waiting on Discord or on JNI.
         
         // Fallback: If no user, maybe we aren't fully ready?
         // But g_connected is true.
    }
}
