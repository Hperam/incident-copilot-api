# Database Connection Runbook

## Signals

- `HikariPool` timeouts
- `Connection refused`
- Authentication failures after secret rotation

## First checks

1. Verify the database endpoint, credentials, and recent secret rotations.
2. Confirm the database accepts new connections and is not at its max connection limit.
3. Compare connection pool settings against recent traffic spikes or deploy changes.

## Safe mitigations

- Roll back the deploy if the incident began immediately after release.
- Reduce traffic or background jobs if the pool is saturated.
- Rotate credentials only after confirming current secret state.
