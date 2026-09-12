#pragma once

#include <jni.h>

#define FPTN_IP_ADDRESS_WITHOUT_PCAP
#include "fptn-protocol-lib/connection/strategies/base_strategy_connection.h"
#include "fptn-protocol-lib/https/websocket_client/websocket_client.h"

namespace fptn::wrapper {

class WrapperWebsocketClient final {
 public:
  WrapperWebsocketClient(jobject wrapper,
      std::string server_ip,
      int server_port,
      std::string tun_ipv4,
      std::string tun_ipv6,
      std::string sni,
      std::string access_token,
      std::string expected_md5_fingerprint,
      std::string client_version,
      fptn::protocol::https::CensorshipStrategy censorship_strategy,
      fptn::protocol::connection::strategies::ConnectionStrategy connection_strategy);

  ~WrapperWebsocketClient();

  bool Start();

  bool Stop();

  bool IsStarted();

  bool Send(fptn::common::network::IPPacketData pkt);

  jobject GetWrapper() const noexcept { return wrapper_; }

 protected:
  void Run();

  void onIPPackets(fptn::common::network::BatchIPPacketPtr packets);

  bool ResolveMessageCallback(JNIEnv* env);

  void onConnectedCallback();

  void onSocketOpened(int socket_fd);

 private:
  const int kMaxReconnectionAttempts_ = 2;

  std::thread th_;
  mutable std::mutex mutex_;
  mutable std::atomic<bool> running_;
  mutable std::atomic<int> reconnection_attempts_;
  std::chrono::steady_clock::time_point window_start_time_;
  std::atomic<bool> has_opened_{false};

  const jobject wrapper_;
  jclass byte_array_class_ = nullptr;
  jmethodID on_message_impl_ = nullptr;

  const std::string server_ip_;
  const int server_port_;
  const std::string tun_ipv4_;
  const std::string tun_ipv6_;
  const std::string sni_;
  const std::string access_token_;
  const std::string expected_md5_fingerprint_;
  const std::string client_version_;
  const fptn::protocol::https::CensorshipStrategy censorship_strategy_;
  const fptn::protocol::connection::strategies::ConnectionStrategy connection_strategy_;

  fptn::protocol::connection::strategies::StrategyConnectionPtr client_;
};
}  // namespace fptn::wrapper
