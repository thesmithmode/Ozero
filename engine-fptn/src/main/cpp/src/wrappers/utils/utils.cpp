#include "utils.h"

#include <mutex>

#include "common/logger/logger.h"

bool fptn::wrapper::init_logger() {
  static std::once_flag flag;
  static bool initialized = false;

  std::call_once(
      flag, []() { initialized = fptn::logger::init("fptn-android-client"); });

  return initialized;
}
