#pragma once

#include <jni.h>
#include <spdlog/spdlog.h>

#include <string>

namespace fptn::wrapper {
inline std::string ConvertToCString(JNIEnv* p_env, jstring jstr) {
  return p_env->GetStringUTFChars(jstr, nullptr);
}

bool init_logger();
}  // namespace fptn::wrapper
