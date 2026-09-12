#pragma once

#include <string>

#include "fptn-protocol-lib/https/censorship_strategy.h"

namespace fptn::wrapper {

inline fptn::protocol::https::CensorshipStrategy ParseCensorshipStrategy(
    const std::string& name) {
  namespace https = fptn::protocol::https;
  if (name == "OBFUSCATION") return https::CensorshipStrategy::kTlsObfuscator;
  if (name == "SNI-REALITY") return https::CensorshipStrategy::kSniRealityMode;
  if (name == "SNI-REALITY-CHROME-149") return https::CensorshipStrategy::kSniRealityModeChrome149;
  if (name == "SNI-REALITY-CHROME-148") return https::CensorshipStrategy::kSniRealityModeChrome148;
  if (name == "SNI-REALITY-CHROME-147") return https::CensorshipStrategy::kSniRealityModeChrome147;
  if (name == "SNI-REALITY-CHROME-146") return https::CensorshipStrategy::kSniRealityModeChrome146;
  if (name == "SNI-REALITY-CHROME-145") return https::CensorshipStrategy::kSniRealityModeChrome145;
  if (name == "SNI-REALITY-FIREFOX-151") return https::CensorshipStrategy::kSniRealityModeFirefox151;
  if (name == "SNI-REALITY-FIREFOX-150") return https::CensorshipStrategy::kSniRealityModeFirefox150;
  if (name == "SNI-REALITY-FIREFOX-149") return https::CensorshipStrategy::kSniRealityModeFirefox149;
  if (name == "SNI-REALITY-YANDEX-26-4" || name == "SNI-REALITY-YANDEX-26") return https::CensorshipStrategy::kSniRealityModeYandex26_4;
  if (name == "SNI-REALITY-YANDEX-26-3") return https::CensorshipStrategy::kSniRealityModeYandex26_3;
  if (name == "SNI-REALITY-YANDEX-25") return https::CensorshipStrategy::kSniRealityModeYandex25;
  if (name == "SNI-REALITY-YANDEX-24") return https::CensorshipStrategy::kSniRealityModeYandex24;
  if (name == "SNI-REALITY-SAFARI-26-5" || name == "SNI-REALITY-SAFARI-26") return https::CensorshipStrategy::kSniRealityModeSafari26_5;
  if (name == "SNI-REALITY-SAFARI-26-4") return https::CensorshipStrategy::kSniRealityModeSafari26_4;
  return https::CensorshipStrategy::kSni;
}

}  // namespace fptn::wrapper
