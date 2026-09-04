from pathlib import Path

path = Path("app/src/main/java/ru/ozero/app/ui/settings/engines/singbox/SingboxProbeService.kt")
text = path.read_text()

old_recovery = '''        val rejectedProfiles = mutableListOf<RejectedProfile>()
        val probeCandidates = profiles.mapNotNull { profile ->
            when (val recovered = PersistedProfileRecovery.recover(profile.beanBlob, profile.protocolType)) {
                is RecoveryResult.Failure -> {
                    rejectedProfiles += RejectedProfile(
                        protocol = PersistedProtocol.fromId(profile.protocolType)?.label ?: "unknown",
                        schema = recovered.detectedSchemas.joinToString("+") { it.name }.ifEmpty { "none" },
                        reason = recovered.category.name,
                    )
                    profileDao.updateProbeResultIfCurrent(
                        expected = profile,
                        latency = LATENCY_FAILED,
                        error = PROBE_ERROR_UNSUPPORTED,
                    )
                    null
                }
                is RecoveryResult.Success -> profile to recovered.bean
            }
        }
        logRejectedProfiles(rejectedProfiles)'''
new_recovery = '''        val probeCandidates = recoverProbeCandidates(profiles)'''
if text.count(old_recovery) != 1:
    raise SystemExit(f"expected recovery block once, found {text.count(old_recovery)}")
text = text.replace(old_recovery, new_recovery, 1)

old_batch_probe = '''        val batchProbe = profileProbe as? SingboxBatchProfileProbe
        val pending = probeCandidates.associate { it.first.id to it.first }.toMutableMap()'''
new_batch_probe = '''        val pending = probeCandidates.associate { it.first.id to it.first }.toMutableMap()'''
if text.count(old_batch_probe) != 1:
    raise SystemExit(f"expected batchProbe setup once, found {text.count(old_batch_probe)}")
text = text.replace(old_batch_probe, new_batch_probe, 1)

old_try = '''        try {
            indexedCandidates.chunked(MAX_PROBE_RUNTIME_TARGETS).forEach { batch ->
                val outcomes = if (batchProbe != null) {
                    probeBatch(batch, probeSettings, batchProbe, onProfileTestingChanged)
                } else {
                    probeLegacyBatch(batch, probeSettings, onProfileTestingChanged)
                }
                batch.forEach { candidate ->
                    val outcome = outcomes[candidate.profile.id]
                        ?: SingboxProbeOutcome.Failure(PROBE_ERROR_FAILED)
                    if (persistProbeOutcome(candidate, outcome, results)) {
                        pending.remove(candidate.profile.id)
                    }
                }
            }
        } catch (error: CancellationException) {'''
new_try = '''        try {
            probeCandidateBatches(
                indexedCandidates = indexedCandidates,
                settings = probeSettings,
                onProfileTestingChanged = onProfileTestingChanged,
                results = results,
                pending = pending,
            )
        } catch (error: CancellationException) {'''
if text.count(old_try) != 1:
    raise SystemExit(f"expected batch execution once, found {text.count(old_try)}")
text = text.replace(old_try, new_try, 1)

marker = '''    private suspend fun persistProbeOutcome(
        candidate: IndexedProbeCandidate,'''
helpers = '''    private suspend fun recoverProbeCandidates(
        profiles: List<ProxyProfile>,
    ): List<Pair<ProxyProfile, AbstractBean>> {
        val rejectedProfiles = mutableListOf<RejectedProfile>()
        val candidates = mutableListOf<Pair<ProxyProfile, AbstractBean>>()
        profiles.forEach { profile ->
            when (val recovered = PersistedProfileRecovery.recover(profile.beanBlob, profile.protocolType)) {
                is RecoveryResult.Failure -> recordRejectedProfile(profile, recovered, rejectedProfiles)
                is RecoveryResult.Success -> candidates += profile to recovered.bean
            }
        }
        logRejectedProfiles(rejectedProfiles)
        return candidates
    }

    private suspend fun recordRejectedProfile(
        profile: ProxyProfile,
        recovered: RecoveryResult.Failure,
        rejectedProfiles: MutableList<RejectedProfile>,
    ) {
        rejectedProfiles += RejectedProfile(
            protocol = PersistedProtocol.fromId(profile.protocolType)?.label ?: "unknown",
            schema = recovered.detectedSchemas.joinToString("+") { it.name }.ifEmpty { "none" },
            reason = recovered.category.name,
        )
        profileDao.updateProbeResultIfCurrent(
            expected = profile,
            latency = LATENCY_FAILED,
            error = PROBE_ERROR_UNSUPPORTED,
        )
    }

    private suspend fun probeCandidateBatches(
        indexedCandidates: List<IndexedProbeCandidate>,
        settings: SingboxProfileProbeSettings,
        onProfileTestingChanged: (Long, Boolean) -> Unit,
        results: ConcurrentLinkedQueue<ProbeResult>,
        pending: MutableMap<Long, ProxyProfile>,
    ) {
        val batchProbe = profileProbe as? SingboxBatchProfileProbe
        indexedCandidates.chunked(MAX_PROBE_RUNTIME_TARGETS).forEach { batch ->
            val outcomes = if (batchProbe != null) {
                probeBatch(batch, settings, batchProbe, onProfileTestingChanged)
            } else {
                probeLegacyBatch(batch, settings, onProfileTestingChanged)
            }
            batch.forEach { candidate ->
                val outcome = outcomes[candidate.profile.id]
                    ?: SingboxProbeOutcome.Failure(PROBE_ERROR_FAILED)
                if (persistProbeOutcome(candidate, outcome, results)) {
                    pending.remove(candidate.profile.id)
                }
            }
        }
    }

    private suspend fun persistProbeOutcome(
        candidate: IndexedProbeCandidate,'''
if text.count(marker) != 1:
    raise SystemExit(f"expected persist marker once, found {text.count(marker)}")
text = text.replace(marker, helpers, 1)
path.write_text(text)
