---
title: "Backup AWG Field Roundtrip Loss"
aliases: [backup-serializer-fields, awg-backup-loss, backup-hardening]
tags: [backup, warp, amneziawg, gotcha, data-integrity]
sources:
  - "daily/2026-05-12.md"
created: 2026-05-12
updated: 2026-05-12
---

# Backup AWG Field Roundtrip Loss

`BackupWarpSerializer` omitted 5 AmneziaWG extended fields (awgS3, awgS4, awgI1, awgI2, awgI5) during backup serialization. A backup→restore roundtrip silently dropped these fields, producing a WARP config that connected with only basic obfuscation params (Jc/Jmin/Jmax/H1-H4) but without the extended set. On Russian ISPs with advanced TSPU, the restored config would fail where the original worked.

## Key Points

- `BackupWarpSerializer` serialized 14 of 19 WarpConfig keys — the 5 extended AWG fields (S3, S4, I1, I2, I5) were missing
- Symptom: backup/restore appears to work but WARP tunnel fails on ISPs that require extended AWG obfuscation
- Silent data loss — no error, no warning; the restored config simply lacks fields that weren't serialized
- Fix: add all 19 fields to both `serialize()` and `deserialize()` in `BackupWarpSerializer`
- Additionally found: `AppBackupSerializer.deserialize` had no input size limit (OOM vector on malicious file), no typed exception on parse failure, and accepted `version < 1`

## Details

### The Missing Fields

WarpConfig was expanded from 8 to 19 keys during the AmneziaWG migration ([[concepts/amnezia-wg-warp-migration]]). The initial `BackupWarpSerializer` was written against the 14-key subset visible at the time. The 5 extended AWG fields (S3, S4, I1, I2, I5) were added later in the WARP AWG obfuscation work ([[concepts/warp-awg-obfuscation-russian-isps]]) but the backup serializer was not updated.

The fields correspond to AmneziaWG protocol parameters beyond the basic Jc/Jmin/Jmax set:
- `S3`/`S4`: Additional packet size obfuscation parameters
- `I1`/`I2`/`I5`: Interval-based jitter parameters for timing obfuscation

Without these fields, a restored config uses default values (typically zero), which may be insufficient for ISPs with TSPU that inspect WireGuard handshake timing patterns.

### AppBackupSerializer Hardening

Three additional issues were found in the parent `AppBackupSerializer.deserialize`:

1. **No size limit**: `deserialize(inputStream)` reads the entire stream into memory without checking size. A 1GB malicious backup file causes OOM. Fix: enforce a reasonable maximum (e.g., 10MB) before parsing.

2. **Untyped exception**: Parse failures threw generic `Exception`. Fix: throw a typed `BackupParseException` so callers can distinguish parse errors from IO errors.

3. **No version floor**: `version < 1` was not rejected — a backup file with `version: 0` or negative was accepted. Fix: reject versions below 1.

### Sprint 2-5 Store Coverage

The audit also found that `AppBackupManager` did not include the new stores from Sprint 2-5 (DomainListManager, SavedStrategyStore, GeneMemory). A backup created after Sprint 5 would not include saved strategies, domain lists, or evolution history. Fix: register all new stores in `AppBackupManager`.

## Related Concepts

- [[concepts/core-backup-module]] - The backup module architecture; this article documents a field-completeness bug in the serializer
- [[concepts/amnezia-wg-warp-migration]] - WarpConfig 8→19 key expansion that the serializer didn't fully track
- [[concepts/warp-awg-obfuscation-russian-isps]] - The extended AWG fields (S3/S4/I1/I2/I5) required for TSPU bypass

## Sources

- [[daily/2026-05-12.md]] - Session 18:34: audit found BackupWarpSerializer missing awgS3/S4/I1/I2/I5 → silent data loss on roundtrip; AppBackupSerializer.deserialize lacks size limit, typed exception, version floor; AppBackupManager missing Sprint 2-5 stores
