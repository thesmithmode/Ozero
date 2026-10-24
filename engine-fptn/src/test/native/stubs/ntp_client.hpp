#pragma once

#include <chrono>
#include <cstdint>
#include <string>
#include <thread>

namespace ntp {
class NTPClient {
 public:
  NTPClient(const std::string&, std::uint16_t) {}
  std::uint64_t request_time() {
    std::this_thread::sleep_for(std::chrono::milliseconds(500));
    return 0;
  }
};
}
