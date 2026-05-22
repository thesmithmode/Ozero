#pragma once

#include <jni.h>
#include <spdlog/spdlog.h>

#include <string>

namespace fptn::wrapper {
inline std::string ConvertToCString(JNIEnv* p_env, jstring jstr) {
  const char* chars = p_env->GetStringUTFChars(jstr, nullptr);
  std::string result(chars ? chars : "");
  p_env->ReleaseStringUTFChars(jstr, chars);
  return result;
}

bool init_logger();
}  // namespace fptn::wrapper
