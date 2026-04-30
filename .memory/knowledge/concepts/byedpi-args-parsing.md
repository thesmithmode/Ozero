---
title: "ByeDPI Args Parsing Traps"
aliases: [byedpi-getopt, ciadpi-argv, byedpi-flags]
tags: [byedpi, native, parsing, gotcha]
sources:
  - "daily/2026-04-30.md"
created: 2026-04-30
updated: 2026-04-30
---

# ByeDPI Args Parsing Traps

ByeDPI (`hufrea/byedpi`) parses command-line arguments via `getopt_long` in C (`main.c`/`extend.c`/`native-lib.c`). Several non-obvious behaviors can silently disable DPI bypass or cause argument loss, making the tunnel appear functional (TCP handshake completes, byte counters grow) while HTTPS traffic hangs.

## Key Points

- `argv[0]` must be a program name (e.g. `"ciadpi"`) — `getopt_long` skips it and parses from `argv[1]`. Omitting it causes the first real flag to be swallowed as the program name
- `-Ku` means `--proto u` = UDP-only whitelist. All TCP/TLS traffic passes through without desync, so DPI blocks TLS handshakes unimpeded
- `-K` flag values: `t`=TCP, `h`=HTTP/HTTPS, `u`=UDP. Correct for full bypass: `-Kt,h` or `-Kt,h,u` or omit `-K` entirely (default = all)
- Multi-strategy chains use repeated `-a1` boundaries: each `-a1` starts a new strategy group, ByeDPI tries them sequentially on failure
- `-A` group trigger flags: `t`=torst, `r`=redirect, `s`=ssl_err, `n`=none (fallthrough), `c`=conn

## Details

### argv[0] Program Name Requirement

`getopt_long` follows POSIX convention: `argv[0]` is the program name. When Ozero initially passed args without a program name prefix (`["-p", "1080", "-Ku", ...]`), the `-p` flag was consumed as the program name, `1080` became a free positional argument, and the actual port was never set. ByeByeDPI and ByeDPIAndroid both prepend `"ciadpi"` as `argv[0]` and add `--ip 127.0.0.1` explicitly. The fix in `ByeDpiEngine.buildArgs`:

```kotlin
return (listOf("ciadpi", "--ip", "127.0.0.1", "-p", config.socksPort.toString()) + extra).toTypedArray()
```

### The `-Ku` Trap

`-Ku` is shorthand for `--proto u`, which whitelists only the UDP protocol for desync operations. With this flag, TCP and TLS traffic flows through the SOCKS5 proxy without any DPI bypass applied. The symptom is deceptive: the tunnel is up, byte statistics show activity (TCP SYN/ACK packets), but all HTTPS connections hang because the TLS handshake is not modified to evade DPI inspection. The initial Ozero default args included `-Ku`, which was the primary reason DPI bypass failed in early v0.0.1 builds.

### Multi-Strategy Chains

ByeDPI supports chaining multiple desync strategies separated by `-a1` boundaries. Each group has a trigger condition set via `-A` flags that determines when ByeDPI falls back to that group. A working preset for Russian ISP TSPU bypass uses 5+ phases:

```
-s1 -q1 -a1 -Y -Ar -a1 -s5 -o2 -At -f-1 -r1+s -a1 -As -s1 -o1+s -s-1 -a1
```

This chain combines TLS record splitting, fake packets, disorder, and DISOOB techniques, with fallback triggers on redirect (`-Ar`), timeout (`-At`), and SSL error (`-As`).

### Default Args Reference

ByeDPIAndroid's UI mode defaults to approximately `-Kt,h -d1` (disorder at position 1 for HTTP+HTTPS). This is a known working baseline for most TSPU-equipped ISPs and serves as a safe starting point when custom strategies are not configured.

## Related Concepts

- [[concepts/v001-dpi-bypass-fix-chain]] - Args parsing was fix #5 and #9 in the v0.0.1 chain
- [[concepts/byedpi-auto-strategy-testing]] - Auto-strategy test mode iterates over strategy args

## Sources

- [[daily/2026-04-30.md]] - 5-subagent research session reading byedpi C source; argv[0] and `-Ku` identified as root causes of DPI bypass failure
