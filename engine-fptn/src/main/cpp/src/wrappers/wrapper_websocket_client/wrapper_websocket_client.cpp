#include <chrono>
#include <jni.h>
#include <utility>

#include "jnienv/jnienv.h"
#include "spdlog/spdlog.h"
#include "wrapper_websocket_client.h"

#include "fptn-protocol-lib/connection/strategies/browser_mimicry/browser_mimicry.h"
#include "fptn-protocol-lib/connection/strategies/rolling_tunnel/rolling_tunnel.h"

namespace fptn::wrapper {

WrapperWebsocketClient::WrapperWebsocketClient(
    jobject wrapper,
    std::string server_ip,
    int server_port,
    std::string tun_ipv4,
    std::string tun_ipv6,
    std::string sni,
    std::string access_token,
    std::string expected_md5_fingerprint,
    std::string client_version,
    fptn::protocol::https::CensorshipStrategy censorship_strategy,
    fptn::protocol::connection::strategies::ConnectionStrategy connection_strategy)
    : running_(false),
      reconnection_attempts_(kMaxReconnectionAttempts_),
      wrapper_(wrapper),
      server_ip_(std::move(server_ip)),
      server_port_(server_port),
      tun_ipv4_(std::move(tun_ipv4)),
      tun_ipv6_(std::move(tun_ipv6)),
      sni_(std::move(sni)),
      access_token_(std::move(access_token)),
      expected_md5_fingerprint_(std::move(expected_md5_fingerprint)),
      client_version_(std::move(client_version)),
      censorship_strategy_(censorship_strategy),
      connection_strategy_(connection_strategy) {}

WrapperWebsocketClient::~WrapperWebsocketClient() {
  Stop();
  if (byte_array_class_) {
    if (JNIEnv* env = getJniEnv()) env->DeleteGlobalRef(byte_array_class_);
    byte_array_class_ = nullptr;
  }
}

bool WrapperWebsocketClient::Start() {
  const std::unique_lock<std::mutex> lock(mutex_);
  if (running_) return false;
  running_ = true;
  th_ = std::thread(&WrapperWebsocketClient::Run, this);
  return th_.joinable();
}

bool WrapperWebsocketClient::Stop() {
  auto join = [this] {
    if (!th_.joinable()) return;
    if (std::this_thread::get_id() == th_.get_id()) th_.detach();
    else th_.join();
  };
  if (!running_) {
    join();
    return true;
  }
  {
    const std::unique_lock<std::mutex> lock(mutex_);
    if (!running_) return false;
    running_ = false;
    if (client_) client_->Stop();
  }
  join();
  {
    const std::unique_lock<std::mutex> lock(mutex_);
    client_.reset();
  }
  return true;
}

bool WrapperWebsocketClient::IsStarted() {
  return client_ && running_ && client_->IsStarted();
}

void WrapperWebsocketClient::Run() {
  constexpr auto kReconnectionWindow = std::chrono::seconds(60);
  constexpr auto kReconnectionDelay = std::chrono::milliseconds(200);
  reconnection_attempts_ = kMaxReconnectionAttempts_;
  window_start_time_ = std::chrono::steady_clock::now();
  while (running_ && reconnection_attempts_ > 0) {
    try {
      const auto server_ip = fptn::common::network::IPv4Address::Create(server_ip_);
      if (!server_ip.IsValid()) {
        SPDLOG_ERROR("Invalid server IP address");
        break;
      }
      {
        const std::unique_lock<std::mutex> lock(mutex_);
        const auto on_packets = std::bind(&WrapperWebsocketClient::onIPPackets, this, std::placeholders::_1);
        const auto on_connected = std::bind(&WrapperWebsocketClient::onConnectedCallback, this);
        const auto on_socket_opened = std::bind(&WrapperWebsocketClient::onSocketOpened, this, std::placeholders::_1);
        const fptn::protocol::https::ConnectionConfig config{
            .common = {
                .server_ip = server_ip,
                .server_port = static_cast<std::uint16_t>(server_port_),
                .sni = sni_,
                .md5_fingerprint = expected_md5_fingerprint_,
                .client_version = client_version_,
                .censorship_strategy = censorship_strategy_,
                .tun_interface_address_ipv4 = fptn::common::network::IPv4Address(tun_ipv4_),
                .tun_interface_address_ipv6 = fptn::common::network::IPv6Address(tun_ipv6_),
                .on_connected_callback = on_connected,
                .recv_ip_packet_batch_callback = on_packets,
                .on_socket_opened_callback = on_socket_opened,
            },
        };
        namespace strategies = fptn::protocol::connection::strategies;
        switch (connection_strategy_) {
          case strategies::ConnectionStrategy::kDualRollingTunnel:
            client_ = strategies::DualRollingTunnel::Create(access_token_, config);
            break;
          case strategies::ConnectionStrategy::kTripleRollingTunnel:
            client_ = strategies::TripleRollingTunnel::Create(access_token_, config);
            break;
          case strategies::ConnectionStrategy::kBrowserMimicry:
            client_ = strategies::BrowserMimicry::Create(access_token_, config);
            break;
          case strategies::ConnectionStrategy::kSingleRollingTunnel:
          default:
            client_ = strategies::SingleRollingTunnel::Create(access_token_, config);
            break;
        }
      }
      if (running_ && client_) client_->Start();
    } catch (const std::exception& ex) {
      SPDLOG_ERROR("WebSocket client exception: {}", ex.what());
    } catch (...) {
      SPDLOG_ERROR("WebSocket client unknown exception");
    }
    if (!running_) break;
    const auto elapsed = std::chrono::steady_clock::now() - window_start_time_;
    if (elapsed >= kReconnectionWindow) {
      reconnection_attempts_ = kMaxReconnectionAttempts_;
      window_start_time_ = std::chrono::steady_clock::now();
    } else {
      --reconnection_attempts_;
    }
    if (running_ && reconnection_attempts_ > 0) std::this_thread::sleep_for(kReconnectionDelay);
  }
  if (running_ && reconnection_attempts_ == 0) {
    running_ = false;
    JNIEnv* env = getJniEnv();
    if (!env) return;
    jclass cls = env->GetObjectClass(wrapper_);
    if (!cls) return;
    const jmethodID on_failure = env->GetMethodID(cls, "onFailureImpl", "()V");
    if (on_failure) env->CallVoidMethod(wrapper_, on_failure);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
  }
}

bool WrapperWebsocketClient::ResolveMessageCallback(JNIEnv* env) {
  if (on_message_impl_ && byte_array_class_) return true;
  jclass cls = env->GetObjectClass(wrapper_);
  if (!cls) return false;
  on_message_impl_ = env->GetMethodID(cls, "onMessageImpl", "([[B)V");
  env->DeleteLocalRef(cls);
  if (!on_message_impl_) return false;
  jclass byte_array_class = env->FindClass("[B");
  if (!byte_array_class) {
    on_message_impl_ = nullptr;
    return false;
  }
  byte_array_class_ = static_cast<jclass>(env->NewGlobalRef(byte_array_class));
  env->DeleteLocalRef(byte_array_class);
  return byte_array_class_ != nullptr;
}

void WrapperWebsocketClient::onIPPackets(fptn::common::network::BatchIPPacketPtr packets) {
  if (packets.empty() || !running_) return;
  JNIEnv* env = getJniEnv();
  if (!env || !ResolveMessageCallback(env)) return;
  jsize count = 0;
  for (const auto& packet : packets) if (packet && !packet->Data().empty()) ++count;
  if (count == 0) return;
  jobjectArray java_packets = env->NewObjectArray(count, byte_array_class_, nullptr);
  if (!java_packets) return;
  jsize index = 0;
  for (const auto& packet : packets) {
    if (!packet || packet->Data().empty()) continue;
    const auto& data = packet->Data();
    jbyteArray java_packet = env->NewByteArray(static_cast<jsize>(data.size()));
    if (!java_packet) break;
    env->SetByteArrayRegion(java_packet, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte*>(data.data()));
    env->SetObjectArrayElement(java_packets, index++, java_packet);
    env->DeleteLocalRef(java_packet);
  }
  if (!env->ExceptionCheck()) env->CallVoidMethod(wrapper_, on_message_impl_, java_packets);
  if (env->ExceptionCheck()) env->ExceptionClear();
  env->DeleteLocalRef(java_packets);
}

void WrapperWebsocketClient::onConnectedCallback() {
  if (!running_) return;
  reconnection_attempts_ = kMaxReconnectionAttempts_;
  window_start_time_ = std::chrono::steady_clock::now();
  bool expected = false;
  if (!has_opened_.compare_exchange_strong(expected, true)) return;
  JNIEnv* env = getJniEnv();
  if (!env) return;
  jclass cls = env->GetObjectClass(wrapper_);
  if (!cls) return;
  const jmethodID on_open = env->GetMethodID(cls, "onOpenImpl", "()V");
  if (on_open) env->CallVoidMethod(wrapper_, on_open);
  if (env->ExceptionCheck()) env->ExceptionClear();
  env->DeleteLocalRef(cls);
}

void WrapperWebsocketClient::onSocketOpened(int socket_fd) {
  JNIEnv* env = getJniEnv();
  if (!env) return;
  jclass cls = env->GetObjectClass(wrapper_);
  if (!cls) return;
  const jmethodID on_socket_opened = env->GetMethodID(cls, "onSocketOpenedImpl", "(I)V");
  if (on_socket_opened) env->CallVoidMethod(wrapper_, on_socket_opened, static_cast<jint>(socket_fd));
  if (env->ExceptionCheck()) env->ExceptionClear();
  env->DeleteLocalRef(cls);
}

bool WrapperWebsocketClient::Send(fptn::common::network::IPPacketData data) {
  if (!running_) return false;
  try {
    auto packet = fptn::common::network::IPPacket::Parse(std::move(data));
    if (!packet) return false;
    const std::unique_lock<std::mutex> lock(mutex_);
    if (!running_ || !client_ || !client_->IsStarted()) return false;
    client_->Send(std::move(packet));
    return true;
  } catch (const std::exception& ex) {
    SPDLOG_ERROR("WebSocket send exception: {}", ex.what());
  } catch (...) {
    SPDLOG_ERROR("WebSocket send unknown exception");
  }
  return false;
}

}  // namespace fptn::wrapper
