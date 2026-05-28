---
title: MasterDNS fake SSH response specificity
sources:
  - daily/2026-05-28.md
created: 2026-05-28
updated: 2026-05-28
---

# MasterDNS fake SSH response specificity

## Key Points
- Fake SSH command matching by first substring can return the wrong response when command bodies contain overlapping fragments.
- `apt-get` can match inside a Dockerfile body and steal a response intended for a more specific docker build or run command.
- Test fakes should choose the longest matching substring when responses are registered by partial command.
- This issue affected MasterDNS deploy tests and belongs with [[concepts/masterdns-startup-hardening]] and [[concepts/masterdns-deploy-hardening]].

## Details

MasterDNS deploy tests used a fake SSH transport that returned the first response whose substring appeared in the command. This was too weak for deployment scripts because a single remote command can include multiple shell fragments, including Dockerfile content with `apt-get`. A broad response key could therefore intercept a more specific command and convert a happy path into an apparent deploy or build failure.

The durable rule is that substring-based command fakes must rank matches by specificity. In this case, longest-substring matching makes the intended `docker run -d` or docker build response beat broad fragments such as `docker rm -f` or `apt-get`. This preserves deterministic test behavior without relaxing assertions around the MasterDNS deploy flow.

## Related Concepts
- [[concepts/masterdns-startup-hardening]]
- [[concepts/masterdns-deploy-hardening]]
- [[concepts/test-tautology-always-green]]
- [[concepts/ci-extra-modules-test-gate]]

## Sources
- [[daily/2026-05-28]] records the MasterDNS failures from the newly enabled extra-modules job and identifies fake SSH substring matching as the root cause.
- [[daily/2026-05-28]] records the chosen fix: longest substring match so more specific fake SSH responses win over broad command fragments.
