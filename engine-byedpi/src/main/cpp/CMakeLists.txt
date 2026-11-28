cmake_minimum_required(VERSION 3.22.1)

project(byedpi_native)

# byedpi C sources — git submodule: https://github.com/hufrea/byedpi
file(GLOB BYE_DPI_SRC byedpi/*.c)
list(REMOVE_ITEM BYE_DPI_SRC ${CMAKE_CURRENT_SOURCE_DIR}/byedpi/win_service.c)

set(UPSTREAM_PROXY "${CMAKE_CURRENT_SOURCE_DIR}/byedpi/proxy.c")
set(PATCHED_PROXY "${CMAKE_CURRENT_BINARY_DIR}/proxy-ozero.c")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${UPSTREAM_PROXY}")
file(READ "${UPSTREAM_PROXY}" PROXY_SOURCE)
set(LISTENER_ANCHOR "    server_fd = srvfd;")
string(FIND "${PROXY_SOURCE}" "${LISTENER_ANCHOR}" LISTENER_OFFSET)
if(LISTENER_OFFSET EQUAL -1)
    message(FATAL_ERROR "Unsupported ByeDPI listener initialization")
endif()
string(REPLACE "${LISTENER_ANCHOR}"
    "${LISTENER_ANCHOR}\n    if (ozero_listener_publish(srvfd) < 0) { close(srvfd); return -1; }"
    PROXY_SOURCE "${PROXY_SOURCE}")
file(WRITE "${PATCHED_PROXY}" "#include \"listener-control.h\"\n${PROXY_SOURCE}")
list(REMOVE_ITEM BYE_DPI_SRC "${UPSTREAM_PROXY}")

add_library(byedpi SHARED ${BYE_DPI_SRC} "${PATCHED_PROXY}" native-lib.c listener-control.c)
target_include_directories(byedpi PRIVATE byedpi ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_options(byedpi PRIVATE -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter -pedantic)
target_compile_definitions(byedpi PRIVATE ANDROID_APP)

target_link_libraries(byedpi PRIVATE android log)
