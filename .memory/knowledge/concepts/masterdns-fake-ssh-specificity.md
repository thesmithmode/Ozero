---
title: MasterDNS fake SSH response specificity
sources:
  - daily/2026-05-28.md
  - daily/2026-06-04.md
created: 2026-05-28
updated: 2026-06-09
---

# MasterDNS fake SSH response specificity

## Key Points
- Fake SSH command matching by first substring can return the wrong response when command bodies contain overlapping fragments.
- `apt-get` can match inside a Dockerfile body and steal a response intended for a more specific docker build or run command.
- Test fakes should choose the longest matching substring when responses are registered by partial command.
- Over-broad keys such as `encrypt_key` can steal responses intended for the full key-read command and turn MasterDNS happy-path tests red.
- This issue affected MasterDNS deploy tests and belongs with [[concepts/masterdns-startup-hardening]] and [[concepts/masterdns-deploy-hardening]].

## Details

MasterDNS deploy tests used a fake SSH transport that returned the first response whose substring appeared in the command. This was too weak for deployment scripts because a single remote command can include multiple shell fragments, including Dockerfile content with `apt-get`. A broad response key could therefore intercept a more specific command and convert a happy path into an apparent deploy or build failure.

The durable rule is that substring-based command fakes must rank matches by specificity. In this case, longest-substring matching makes the intended `docker run -d` or docker build response beat broad fragments such as `docker rm -f` or `apt-get`. This preserves deterministic test behavior without relaxing assertions around the MasterDNS deploy flow.

The same pattern recurred on 2026-06-04 in `engine-masterdns`: several tests failed because the fake matched a general `encrypt_key` fragment too early while `deployMasterDns` expected the specific command that reads the encryption key. The fix remained in the test double, not production logic, because the real contract was sound and the fake's matching semantics were too broad.

## Related Concepts
- [[concepts/masterdns-startup-hardening]]
- [[concepts/masterdns-deploy-hardening]]
- [[concepts/test-tautology-always-green]]
- [[concepts/ci-extra-modules-test-gate]]
- [[concepts/ci-grouped-job-failure-attribution]]

## Sources
- [[daily/2026-05-28]] records the MasterDNS failures from the newly enabled extra-modules job and identifies fake SSH substring matching as the root cause.
- [[daily/2026-05-28]] records the chosen fix: longest substring match so more specific fake SSH responses win over broad command fragments.
- [[daily/2026-06-04]]: session 15:58 records `engine-masterdns` failures caused by an over-general `encrypt_key` fake response key and the decision to fix the test double.
