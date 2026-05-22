#include <jni.h>
#include <mutex>
#include <unordered_map>

#include "fptn-protocol-lib/https/obfuscator/methods/tls/tls_obfuscator.h"

#include "wrappers/utils/utils.h"
#include "wrappers/wrapper_websocket_client/wrapper_websocket_client.h"

using fptn::wrapper::WrapperWebsocketClient;

class SafeProxy {
 private:
  static std::mutex mutex_;
  static std::unordered_map<jlong, bool> status_clients_;

 public:
  SafeProxy() {
    mutex_.lock();
  }

  ~SafeProxy() {
    mutex_.unlock();
  }

  void Add(jlong client) { status_clients_[client] = true; }

  void Delete(jlong client) {
    auto it = status_clients_.find(client);
    if (it != status_clients_.end()) {
      status_clients_.erase(it);
    }
  }

  WrapperWebsocketClient* Get(jlong client) {
    auto it = status_clients_.find(client);
    if (it != status_clients_.end() && it->second) {
      return reinterpret_cast<WrapperWebsocketClient*>(client);
    }
    return nullptr;
  }

  SafeProxy(const SafeProxy&) = delete;
  SafeProxy& operator=(const SafeProxy&) = delete;
  SafeProxy(SafeProxy&&) = delete;
  SafeProxy& operator=(SafeProxy&&) = delete;
};

std::mutex SafeProxy::mutex_;
std::unordered_map<jlong, bool> SafeProxy::status_clients_;

extern "C" JNIEXPORT jlong JNICALL
Java_ru_ozero_enginefptn_FptnNativeWebSocket_nativeCreate(
    JNIEnv* env,
    jobject thiz,
    jstring server_ip_param,
    jint server_port_param,
    jstring tun_ipv4_param,
    jstring tun_ipv6_param,
    jstring sni_param,
    jstring access_token_param,
    jstring expected_md5_fingerprint_param,
    jstring censorship_strategy_name_param) {
  fptn::wrapper::init_logger();

  auto server_ip = fptn::wrapper::ConvertToCString(env, server_ip_param);
  int server_port = server_port_param;
  auto tun_ipv4 = fptn::wrapper::ConvertToCString(env, tun_ipv4_param);
  auto tun_ipv6 = fptn::wrapper::ConvertToCString(env, tun_ipv6_param);
  auto sni = fptn::wrapper::ConvertToCString(env, sni_param);
  auto access_token = fptn::wrapper::ConvertToCString(env, access_token_param);
  auto expected_md5_fingerprint =
      fptn::wrapper::ConvertToCString(env, expected_md5_fingerprint_param);

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

  jobject global_object_ref = env->NewWeakGlobalRef(thiz);
  auto* websocket_client = new WrapperWebsocketClient(global_object_ref,
      std::move(server_ip), server_port, std::move(tun_ipv4), std::move(tun_ipv6), std::move(sni),
      std::move(access_token), std::move(expected_md5_fingerprint), censorship_strategy);

  auto jobj_client = reinterpret_cast<jlong>(websocket_client);

  SafeProxy proxy;
  proxy.Add(jobj_client);

  return jobj_client;
}

extern "C" JNIEXPORT void JNICALL
Java_ru_ozero_enginefptn_FptnNativeWebSocket_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong native_handle) {
  (void)env;
  (void)thiz;

  SafeProxy proxy;

  auto* websocket_client = proxy.Get(native_handle);
  if (websocket_client) {
    websocket_client->Stop();
    proxy.Delete(native_handle);
    delete websocket_client;
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ru_ozero_enginefptn_FptnNativeWebSocket_nativeRun(
    JNIEnv* env, jobject thiz, jlong native_handle) {
  (void)env;
  (void)thiz;

  bool status = false;

  SafeProxy proxy;
  auto* websocket_client = proxy.Get(native_handle);
  if (websocket_client) {
    status = websocket_client->Start();
  }
  return static_cast<jboolean>(status);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ru_ozero_enginefptn_FptnNativeWebSocket_nativeStop(
    JNIEnv* env, jobject thiz, jlong native_handle) {
  (void)env;
  (void)thiz;

  bool status = false;

  SafeProxy proxy;

  auto* websocket_client = proxy.Get(native_handle);
  if (websocket_client) {
    SPDLOG_INFO("Stop websocket");
    status = websocket_client->Stop();
  }
  return static_cast<jboolean>(status);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ru_ozero_enginefptn_FptnNativeWebSocket_nativeSend(
    JNIEnv* env, jobject thiz, jlong native_handle, jbyteArray data, jlong length) {
  (void)thiz;

  bool status = false;

  SafeProxy proxy;
  auto* websocket_client = proxy.Get(native_handle);
  if (websocket_client && env && data) {
    jbyte* buffer = env->GetByteArrayElements(data, nullptr);
    if (buffer != nullptr && length != 0) {
      std::string packet(reinterpret_cast<const char*>(buffer), length);
      status = websocket_client->Send(std::move(packet));
    }
    if (buffer) {
      env->ReleaseByteArrayElements(data, buffer, JNI_ABORT);
    }
  }
  return static_cast<jboolean>(status);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_ru_ozero_enginefptn_FptnNativeWebSocket_nativeIsStarted(
    JNIEnv* env, jobject thiz, jlong native_handle) {
  (void)env;
  (void)thiz;

  bool status = false;
  SafeProxy proxy;
  auto* websocket_client = proxy.Get(native_handle);
  if (websocket_client) {
    status = websocket_client->IsStarted();
  }

  return static_cast<jboolean>(status);
}
