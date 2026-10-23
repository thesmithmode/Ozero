#include <chrono>
#include <ctime>
#include <iostream>

#include "fptn-protocol-lib/time/time_provider.h"

int main() {
  using Clock = std::chrono::steady_clock;
  const auto started = Clock::now();
  const auto* provider = fptn::time::TimeProvider::Instance();
  const auto elapsed = Clock::now() - started;
  if (elapsed >= std::chrono::milliseconds(200)) {
    std::cerr << "Time initialization blocked authentication on NTP\n";
    return 1;
  }
  const auto now = static_cast<std::uint32_t>(std::time(nullptr));
  if (provider->NowTimestamp() < now - 1 ||
      provider->NowTimestamp() > now + 1 ||
      provider->OffsetSeconds() != 0 || provider->Rfc7231Date().empty()) {
    std::cerr << "System time unavailable while NTP is pending\n";
    return 1;
  }
  return 0;
}
