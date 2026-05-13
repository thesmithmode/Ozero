---
title: "ByeDPI Args Parsing Traps"
aliases: [byedpi-getopt, ciadpi-argv, byedpi-flags]
tags: [byedpi, native, parsing, gotcha]
sources:
  - "daily/2026-04-30.md"
  - "daily/2026-05-13.md"
created: 2026-04-30
updated: 2026-05-13
---

# ByeDPI Args Parsing Traps

ByeDPI (`hufrea/byedpi`) parses command-line arguments via `getopt_long` in C (`main.c`/`extend.c`/`native-lib.c`). Several non-obvious behaviors can silently disable DPI bypass or cause argument loss, making the tunnel appear functional (TCP handshake completes, byte counters grow) while HTTPS traffic hangs.

## Key Points

- `argv[0]` must be a program name (e.g. `"ciadpi"`) — `getopt_long` skips it and parses from `argv[1]`. Omitting it causes the first real flag to be swallowed as the program name
- `-Ku` means `--proto u` = UDP-only whitelist. All TCP/TLS traffic passes through without desync, so DPI blocks TLS handshakes unimpeded
- `-K` flag values: `t`=TCP, `h`=HTTP/HTTPS, `u`=UDP. Correct for full bypass: `-Kt,h` or `-Kt,h,u` or omit `-K` entirely (default = all)
- Multi-strategy chains use repeated `-a1` boundaries: each `-a1` starts a new strategy group, ByeDPI tries them sequentially on failure
- `-A` group trigger flags: `t`=torst, `r`=redirect, `s`=ssl_err, `n`=none (fallthrough), `c`=conn
- Double argv[0] trap: if C `native-lib.c` already inserts `"byedpi"` as `argv[0]`, Kotlin `buildArgs` must NOT also prepend `"ciadpi"` — double prefix silently drops the first real flag

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

### Double argv[0] Registration Trap

Commit `f0e9f206` added `"byedpi"` as `argv[0]` directly in `native-lib.c` (C layer). The Kotlin `buildArgs` function was not updated and still prepended `"ciadpi"` as `argv[0]`. The resulting array was `["ciadpi", "byedpi", ...real flags...]`. `getopt_long` skips `argv[0]` by convention (program name), so it starts parsing at index 1 — but index 1 is now the second program name string `"byedpi"`, which is not a valid flag and is silently discarded as a non-option argument. The first real flag (e.g. `-p 1080`) sits at index 2 but `getopt` treats it as argv[1] in the options parse, effectively shifting every flag one position and causing the first real option to be silently lost.

The symptom is identical to the original missing-argv[0] bug: the tunnel starts but the first flag value is wrong or missing. Diagnosis: add logging of the full argv array before passing to `byedpiMain`. Fix: remove the Kotlin-side `"ciadpi"` prepend when the C layer already inserts its own argv[0].

## Related Concepts

- [[concepts/v001-dpi-bypass-fix-chain]] - Args parsing was fix #5 and #9 in the v0.0.1 chain
- [[concepts/byedpi-auto-strategy-testing]] - Auto-strategy test mode iterates over strategy args

## Sources

- [[daily/2026-04-30.md]] - 5-subagent research session reading byedpi C source; argv[0] and `-Ku` identified as root causes of DPI bypass failure
- [[daily/2026-05-13.md]] - Double argv[0] registration: C native-lib.c adds "byedpi", Kotlin buildArgs still prepends "ciadpi" → first real flag silently dropped (commit f0e9f206)
