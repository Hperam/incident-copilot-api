# Downstream Dependency Latency

## Signals

- `503` or `upstream connect error`
- `SocketTimeoutException`
- Increased retries or circuit-breaker trips

## First checks

1. Identify which dependency is timing out and whether only one region is impacted.
2. Inspect retry storms, concurrency limits, and connection pool saturation.
3. Correlate traces to locate the first slow or failing downstream hop.

## Safe mitigations

- Shift traffic away from the degraded dependency if possible.
- Temporarily widen timeouts only if it will not amplify load.
- Prefer circuit breaking or rollback over blind retry increases.
