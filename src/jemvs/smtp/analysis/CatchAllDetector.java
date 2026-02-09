package jemvs.smtp.analysis;

import jemvs.smtp.protocol.SmtpPhase;
import jemvs.smtp.protocol.SmtpResponse;
import jemvs.smtp.transport.SmtpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CatchAllDetector {

    private static final int DEFAULT_PROBE_COUNT = 2;
    private static final int MAX_PROBE_COUNT = 5;

    // Cache configuration
    private static final long DEFAULT_CACHE_TTL_MS = 3600000; // 1 hour
    private static final int MAX_CACHE_SIZE = 10000; // Prevent unbounded growth

    private final int probeCount;
    private final String heloDomain;
    private final String mailFrom;

    // Domain-level cache
    private final ConcurrentHashMap<String, CacheEntry> domainCache;
    private final long cacheTtlMs;
    private final boolean cachingEnabled;

    // Cache statistics
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;


    public CatchAllDetector(String heloDomain, String mailFrom) {
        this(DEFAULT_PROBE_COUNT, heloDomain, mailFrom, true, DEFAULT_CACHE_TTL_MS);
    }


    public CatchAllDetector(int probeCount, String heloDomain, String mailFrom,
                            boolean cachingEnabled, long cacheTtlMs) {
        if (probeCount < 1 || probeCount > MAX_PROBE_COUNT) {
            throw new IllegalArgumentException(
                    "Probe count must be between 1 and " + MAX_PROBE_COUNT +
                            " (got: " + probeCount + ")"
            );
        }
        if (heloDomain == null || heloDomain.trim().isEmpty()) {
            throw new IllegalArgumentException("HELO domain cannot be null or empty");
        }
        if (mailFrom == null || mailFrom.trim().isEmpty()) {
            throw new IllegalArgumentException("MAIL FROM cannot be null or empty");
        }
        if (cacheTtlMs < 0) {
            throw new IllegalArgumentException("Cache TTL must be non-negative");
        }

        this.probeCount = probeCount;
        this.heloDomain = heloDomain;
        this.mailFrom = mailFrom;
        this.cachingEnabled = cachingEnabled;
        this.cacheTtlMs = cacheTtlMs;

        this.domainCache = cachingEnabled ? new ConcurrentHashMap<>() : null;
        this.cacheHits = new AtomicLong(0);
        this.cacheMisses = new AtomicLong(0);
    }

    public CatchAllDetectionResult analyzeSingleProbe(List<SmtpResponse> responses) {
        Objects.requireNonNull(responses, "responses must not be null");

        Optional<SmtpResponse> rcptTo = findRcptTo(responses);

        if (rcptTo.isEmpty()) {
            return CatchAllDetectionResult.indeterminate(
                    "No RCPT TO response available for analysis"
            );
        }

        int code = rcptTo.get().getCode();

        // Permanent rejection proves NOT catch-all
        if (isRejected(code)) {
            return CatchAllDetectionResult.notDetected(
                    "RCPT TO rejected with 5xx - server is selective"
            );
        }

        // Acceptance suggests catch-all, but cannot confirm with single probe
        if (isAccepted(code)) {
            return CatchAllDetectionResult.suspected(
                    "RCPT TO accepted - catch-all suspected but not confirmed"
            );
        }

        // Temporary failures or non-standard codes
        return CatchAllDetectionResult.indeterminate(
                "RCPT TO returned " + code + " - cannot determine catch-all status"
        );
    }


    public CatchAllDetectionResult analyzeMultiProbe(
            List<SmtpResponse> primaryResponses,
            String mailHost,
            String domain) {

        Objects.requireNonNull(primaryResponses, "primaryResponses must not be null");
        Objects.requireNonNull(mailHost, "mailHost must not be null");
        Objects.requireNonNull(domain, "domain must not be null");

        // Step 1: Check cache first (if enabled)
        if (cachingEnabled) {
            CatchAllDetectionResult cachedResult = getCachedResult(domain);
            if (cachedResult != null) {
                cacheHits.incrementAndGet();
                return cachedResult;
            }
            cacheMisses.incrementAndGet();
        }

        // Step 2: Analyze primary verification result
        CatchAllDetectionResult singleProbeResult = analyzeSingleProbe(primaryResponses);

        // Early exit if primary verification rejected
        if (singleProbeResult.getConfidence() == CatchAllDetectionResult.Confidence.NOT_DETECTED) {
            cacheResult(domain, singleProbeResult);
            return singleProbeResult;
        }

        // Early exit if indeterminate (cannot probe)
        if (singleProbeResult.getConfidence() == CatchAllDetectionResult.Confidence.INDETERMINATE) {
            // Don't cache INDETERMINATE results - they might succeed on retry
            return singleProbeResult;
        }

        // Step 3: Primary accepted - perform batched probes
        List<ProbeResult> probeResults = performBatchedProbes(mailHost, domain);

        // Step 4: Analyze probe results
        CatchAllDetectionResult result = analyzeProbeResults(probeResults);

        // Step 5: Cache the result
        cacheResult(domain, result);

        return result;
    }

    // Cache Management

    private CatchAllDetectionResult getCachedResult(String domain) {
        if (!cachingEnabled || domainCache == null) {
            return null;
        }

        CacheEntry entry = domainCache.get(domain.toLowerCase());

        if (entry == null) {
            return null;
        }

        // Check if entry has expired
        if (entry.isExpired()) {
            domainCache.remove(domain.toLowerCase());
            return null;
        }

        return entry.result;
    }

    private void cacheResult(String domain, CatchAllDetectionResult result) {
        if (!cachingEnabled || domainCache == null) {
            return;
        }

        // Don't cache INDETERMINATE results (might succeed on retry)
        if (result.getConfidence() == CatchAllDetectionResult.Confidence.INDETERMINATE) {
            return;
        }

        // Enforce max cache size to prevent unbounded growth
        if (domainCache.size() >= MAX_CACHE_SIZE) {
            // Evict expired entries first
            evictExpiredEntries();

            // If still too large, evict oldest entries
            if (domainCache.size() >= MAX_CACHE_SIZE) {
                evictOldestEntries(MAX_CACHE_SIZE / 10); // Remove 10% oldest
            }
        }

        CacheEntry entry = new CacheEntry(result, System.currentTimeMillis() + cacheTtlMs);
        domainCache.put(domain.toLowerCase(), entry);
    }


     // Removes all expired entries from the cache.

    private void evictExpiredEntries() {
        domainCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }


     // Removes the oldest N entries from the cache.

    private void evictOldestEntries(int count) {
        domainCache.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue().expiresAt, e2.getValue().expiresAt))
                .limit(count)
                .forEach(entry -> domainCache.remove(entry.getKey()));
    }

    // Clears all cached results (not used)

    public void clearCache() {
        if (cachingEnabled && domainCache != null) {
            domainCache.clear();
            cacheHits.set(0);
            cacheMisses.set(0);
        }
    }

    // Returns cache statistics. Not used (Simpler Interface for now)

    public CacheStatistics getCacheStatistics() {
        if (!cachingEnabled) {
            return new CacheStatistics(false, 0, 0, 0, 0.0);
        }

        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;

        return new CacheStatistics(
                true,
                domainCache.size(),
                hits,
                misses,
                hitRate
        );
    }

    //  Probe Execution (BATCHED)

    private List<ProbeResult> performBatchedProbes(String mailHost, String domain) {
        List<ProbeResult> results = new ArrayList<>();

        try (SmtpClient client = new SmtpClient(mailHost)) {
            client.connect();

            // Read greeting
            String greeting = client.readResponse();
            if (!isAcceptedResponse(greeting)) {
                return createAllFailedResults("Connection rejected: " + greeting);
            }

            // Send HELO
            client.sendCommand("HELO " + heloDomain);
            String heloResponse = client.readResponse();
            if (!isAcceptedResponse(heloResponse)) {
                return createAllFailedResults("HELO rejected: " + heloResponse);
            }

            // Send MAIL FROM
            client.sendCommand("MAIL FROM:<" + mailFrom + ">");
            String mailFromResponse = client.readResponse();
            if (!isAcceptedResponse(mailFromResponse)) {
                return createAllFailedResults("MAIL FROM rejected: " + mailFromResponse);
            }

            // Send multiple RCPT TO commands (one per probe)
            for (int i = 0; i < probeCount; i++) {
                String randomEmail = generateRandomEmail(domain);

                client.sendCommand("RCPT TO:<" + randomEmail + ">");
                String rcptResponse = client.readResponse();

                int code = parseResponseCode(rcptResponse);

                if (isAccepted(code)) {
                    results.add(ProbeResult.accepted(code));
                } else if (isRejected(code)) {
                    results.add(ProbeResult.rejected(code));
                } else {
                    results.add(ProbeResult.failed("Unexpected response: " + rcptResponse));
                }
            }

            // Send QUIT to cleanly close session
            client.sendCommand("QUIT");
            client.readResponse();

        } catch (IOException e) {
            return createAllFailedResults("Batch probe session failed: " + e.getMessage());
        }

        return results;
    }

    // list of failed probe results.

    private List<ProbeResult> createAllFailedResults(String reason) {
        List<ProbeResult> results = new ArrayList<>();
        for (int i = 0; i < probeCount; i++) {
            results.add(ProbeResult.failed(reason));
        }
        return results;
    }


     // Analyzes probe results to determine catch-all status.

    private CatchAllDetectionResult analyzeProbeResults(List<ProbeResult> probeResults) {
        int totalProbes = probeResults.size();
        int accepted = 0;
        int rejected = 0;
        int failed = 0;

        for (ProbeResult result : probeResults) {
            switch (result.status) {
                case ACCEPTED:
                    accepted++;
                    break;
                case REJECTED:
                    rejected++;
                    break;
                case FAILED:
                    failed++;
                    break;
            }
        }

        // Any rejection proves selectivity
        if (rejected > 0) {
            return CatchAllDetectionResult.notDetected(
                    String.format(
                            "Random address probe rejected (%d/%d probes) - server is selective",
                            rejected,
                            totalProbes
                    )
            );
        }

        // All probes accepted = confirmed catch-all
        if (accepted == totalProbes) {
            return CatchAllDetectionResult.confirmed(
                    String.format(
                            "All random address probes accepted (%d/%d) - catch-all confirmed",
                            accepted,
                            totalProbes
                    )
            );
        }

        // All probes failed - cannot confirm, fallback to suspicion
        if (failed == totalProbes) {
            return CatchAllDetectionResult.suspected(
                    String.format(
                            "Probes failed to complete (%d/%d) - catch-all suspected but not confirmed",
                            failed,
                            totalProbes
                    )
            );
        }

        // Mixed results (some accepted, some failed, no rejections)
        return CatchAllDetectionResult.suspected(
                String.format(
                        "Mixed probe results (%d accepted, %d failed) - catch-all suspected",
                        accepted,
                        failed
                )
        );
    }


     // Generates a random, invalid email address for probing.

    private String generateRandomEmail(String domain) {
        String randomLocal = "probe-" + UUID.randomUUID().toString().replace("-", "");
        return randomLocal + "@" + domain;
    }

    // Helper Methods

    private Optional<SmtpResponse> findRcptTo(List<SmtpResponse> responses) {
        return responses.stream()
                .filter(r -> r.getPhase() == SmtpPhase.RCPT_TO)
                .findFirst();
    }

    private boolean isAccepted(int code) {
        return code >= 200 && code < 300;
    }

    private boolean isRejected(int code) {
        return code >= 500 && code < 600;
    }

    private boolean isAcceptedResponse(String response) {
        if (response == null || response.length() < 3) {
            return false;
        }
        try {
            int code = Integer.parseInt(response.substring(0, 3));
            return isAccepted(code);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parseResponseCode(String response) {
        if (response == null || response.length() < 3) {
            return -1;
        }
        try {
            return Integer.parseInt(response.substring(0, 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // Inner Classes

    private static final class CacheEntry {
        final CatchAllDetectionResult result;
        final long expiresAt;

        CacheEntry(CatchAllDetectionResult result, long expiresAt) {
            this.result = result;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Cache statistics for monitoring and debugging.
     */

    public static final class CacheStatistics {
        private final boolean enabled;
        private final int size;
        private final long hits;
        private final long misses;
        private final double hitRate;

        CacheStatistics(boolean enabled, int size, long hits, long misses, double hitRate) {
            this.enabled = enabled;
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
        }

        public boolean isEnabled() { return enabled; }
        public int getSize() { return size; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public double getHitRate() { return hitRate; }

        @Override
        public String toString() {
            if (!enabled) {
                return "CacheStatistics{disabled}";
            }
            return String.format(
                    "CacheStatistics{size=%d, hits=%d, misses=%d, hitRate=%.2f%%}",
                    size, hits, misses, hitRate * 100
            );
        }
    }

    /**
     * Internal representation of a single probe result.
     */

    private static final class ProbeResult {

        enum Status {
            ACCEPTED,
            REJECTED,
            FAILED
        }

        final Status status;
        final int responseCode;
        final String failureReason;

        private ProbeResult(Status status, int responseCode, String failureReason) {
            this.status = status;
            this.responseCode = responseCode;
            this.failureReason = failureReason;
        }

        static ProbeResult accepted(int code) {
            return new ProbeResult(Status.ACCEPTED, code, null);
        }

        static ProbeResult rejected(int code) {
            return new ProbeResult(Status.REJECTED, code, null);
        }

        static ProbeResult failed(String reason) {
            return new ProbeResult(Status.FAILED, -1, reason);
        }
    }
}