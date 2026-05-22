#include <jni.h>

#include "wrappers/utils/utils.h"
#include "wrappers/wrapper_https_client/wrapper_https_client.h"
#include "wrappers/wrapper_websocket_client/wrapper_websocket_client.h"
#include "fptn-protocol-lib/https/obfuscator/methods/tls/tls_obfuscator.h"

namespace {
jobject create_response(JNIEnv* env,
    const std::string& response_body,
    int code,
    const std::string& error_message) {
  jclass clazz = nullptr;
  jobject response_obj = nullptr;
  jstring body_str = nullptr;
  jstring error_str = nullptr;

  try {
    clazz = env->FindClass("ru/ozero/enginefptn/FptnNativeResponse");
    if (!clazz) {
      throw std::runtime_error("Failed to find FptnNativeResponse class");
    }

    jmethodID constructor = env->GetMethodID(
        clazz, "<init>", "(ILjava/lang/String;Ljava/lang/String;)V");
    if (!constructor) {
      throw std::runtime_error("Failed to get constructor method ID");
    }

    body_str = env->NewStringUTF(response_body.c_str());
    if (!body_str && !response_body.empty()) {
      throw std::runtime_error("Failed to create body string");
    }

    error_str = env->NewStringUTF(error_message.c_str());
    if (!error_str && !error_message.empty()) {
      throw std::runtime_error("Failed to create error string");
    }

    response_obj =
        env->NewObject(clazz, constructor, code, body_str, error_str);
    if (!response_obj) {
      throw std::runtime_error("Failed to create response object");
    }
    env->DeleteLocalRef(clazz);
    env->DeleteLocalRef(body_str);
    env->DeleteLocalRef(error_str);
    return response_obj;
  } catch (const std::runtime_error& err) {
    SPDLOG_WARN("Create response: {}", err.what());
  }
  if (clazz) {
    env->DeleteLocalRef(clazz);
  }
  if (body_str) {
    env->DeleteLocalRef(body_str);
  }
  if (error_str) {
    env->DeleteLocalRef(error_str);
  }
  return response_obj;
}
}  // namespace

using fptn::wrapper::WrapperHttpsClient;

extern "C" JNIEXPORT jlong JNICALL
Java_ru_ozero_enginefptn_FptnNativeHttpsClient_nativeCreate(
    JNIEnv* env,
    jobject thiz,
    jstring host_param,
    jint port_param,
    jstring sni_param,
    jstring md5_fingerprint_param,
    jstring censorship_strategy_name_param) {
  fptn::wrapper::init_logger();

  auto host = fptn::wrapper::ConvertToCString(env, host_param);
  int port = port_param;
  auto sni = fptn::wrapper::ConvertToCString(env, sni_param);
  auto md5_fingerprint =
      fptn::wrapper::ConvertToCString(env, md5_fingerprint_param);

  const auto censorship_strategy_name = fptn::wrapper::ConvertToCString(
      env, censorship_strategy_name_param);
  fptn::protocol::https::CensorshipStrategy censorship_strategy =
      fptn::protocol::https::CensorshipStrategy::kSni;
  if (censorship_strategy_name == "OBFUSCATION") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kTlsObfuscator;
  } else if (censorship_strategy_name == "SNI-REALITY") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityMode;
  } else if (censorship_strategy_name == "SNI-REALITY-CHROME-147") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeChrome147;
  } else if (censorship_strategy_name == "SNI-REALITY-CHROME-146") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeChrome146;
  } else if (censorship_strategy_name == "SNI-REALITY-CHROME-145") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeChrome145;
  } else if (censorship_strategy_name == "SNI-REALITY-FIREFOX-149") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeFirefox149;
  } else if (censorship_strategy_name == "SNI-REALITY-YANDEX-26") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeYandex26;
  } else if (censorship_strategy_name == "SNI-REALITY-YANDEX-25") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeYandex25;
  } else if (censorship_strategy_name == "SNI-REALITY-YANDEX-24") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeYandex24;
  } else if (censorship_strategy_name == "SNI-REALITY-SAFARI-26") {
    censorship_strategy = fptn::protocol::https::CensorshipStrategy::kSniRealityModeSafari26;
  }

  auto* https_client = new WrapperHttpsClient(env, nullptr,
      std::move(host), port, std::move(sni), std::move(md5_fingerprint), censorship_strategy);
  return reinterpret_cast<jlong>(https_client);
}

extern "C" JNIEXPORT void JNICALL
Java_ru_ozero_enginefptn_FptnNativeHttpsClient_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong native_handle) {
  (void)env;
  (void)thiz;

  auto* https_client = reinterpret_cast<WrapperHttpsClient*>(native_handle);
  if (https_client) {
    delete https_client;
  }
}

extern "C" JNIEXPORT jobject JNICALL
Java_ru_ozero_enginefptn_FptnNativeHttpsClient_nativeGet(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle,
    jstring http_handle_param,
    jint timeout_param) {
  (void)thiz;

  const auto http_handle =
      fptn::wrapper::ConvertToCString(env, http_handle_param);
  int timeout = timeout_param;

  auto* https_client = reinterpret_cast<WrapperHttpsClient*>(native_handle);
  if (https_client) {
    const auto resp = https_client->Get(http_handle, timeout);
    return create_response(env, resp.body, resp.code, resp.errmsg);
  }
  return create_response(env, "{}", 400, "Object is empty");
}

extern "C" JNIEXPORT jobject JNICALL
Java_ru_ozero_enginefptn_FptnNativeHttpsClient_nativePost(
    JNIEnv* env,
    jobject thiz,
    jlong native_handle,
    jstring http_handle_param,
    jstring http_request_param,
    jint timeout_param) {
  (void)thiz;

  const auto http_handle =
      fptn::wrapper::ConvertToCString(env, http_handle_param);
  const auto http_request =
      fptn::wrapper::ConvertToCString(env, http_request_param);
  int timeout = timeout_param;

  auto* https_client = reinterpret_cast<WrapperHttpsClient*>(native_handle);
  if (https_client) {
    const auto resp = https_client->Post(http_handle, http_request, timeout);
    return create_response(env, resp.body, resp.code, resp.errmsg);
  }
  return create_response(env, "{}", 400, "Object is empty");
}
