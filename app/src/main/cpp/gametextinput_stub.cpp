#include <game-text-input/gametextinput.h>
#include <jni.h>

// Stub implementations for GameTextInput functions
// These are minimal no-op implementations since we don't use text input

extern "C" {

void GameTextInput_getState(GameTextInput *input,
                            void (*callback)(void *, const GameTextInputState *),
                            void *context) {
    // No-op stub
}

void GameTextInput_processEvent(GameTextInput *input, jobject eventState) {
    // No-op stub
}

GameTextInput* GameTextInput_create(const struct android_app* app) {
    return nullptr;
}

void GameTextInput_destroy(GameTextInput *input) {
    // No-op stub
}

void GameTextInput_setEventCallback(GameTextInput *input,
                                    GameTextInputEventCallback callback,
                                    void *context) {
    // No-op stub
}

void GameTextInput_setState(GameTextInput *input,
                            const GameTextInputState *state) {
    // No-op stub
}

void GameTextInput_showIme(GameTextInput *input, uint32_t flags) {
    // No-op stub
}

void GameTextInput_hideIme(GameTextInput *input, uint32_t flags) {
    // No-op stub
}

GameTextInput* GameTextInput_init(JNIEnv *env, uint32_t max_string_size) {
    // No-op stub - return nullptr since we don't use text input
    return nullptr;
}

void GameTextInput_processImeInsets(GameTextInput *input, const ARect *insets) {
    // No-op stub
}

void GameTextInput_setInputConnection(GameTextInput *input, jobject inputConnection) {
    // No-op stub
}

} // extern "C"
