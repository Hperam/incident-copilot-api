# Recent Deploy Regression

## Signals

- Errors start immediately after rollout
- New feature flags or config changes shipped with the release
- One environment is failing while another remains healthy

## First checks

1. Diff the current deployment manifest against the last known good release.
2. Inspect feature flags, secret versions, and schema changes shipped in the rollout.
3. Validate startup probes, readiness checks, and canary error rates.
