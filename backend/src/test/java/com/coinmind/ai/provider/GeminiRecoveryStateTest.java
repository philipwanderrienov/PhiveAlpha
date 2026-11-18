package com.coinmind.ai.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class GeminiRecoveryStateTest {
    static class Time extends Clock {
        Instant now = Instant.parse("2026-11-06T00:00:00Z");
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        void advance(long seconds) { now = now.plusSeconds(seconds); }
    }
    @Test void cooldownBackoffAndSingleHalfOpen() {
        var clock = new Time(); var s = new GeminiRecoveryState(clock);
        assertThat(s.admit(10)).isTrue(); s.failed("HTTP_SERVER_ERROR", 503, null);
        clock.advance(299); assertThat(s.admit(10)).isFalse();
        clock.advance(1); assertThat(s.admit(10)).isTrue(); assertThat(s.admit(10)).isFalse();
        assertThat(s.snapshot().get("state")).isEqualTo("HALF_OPEN");
        s.failed("VALIDATION_FAILED", 200, null);
        var delay = Duration.between(clock.instant(), (Instant)s.snapshot().get("nextEligibleRecoveryTime")).toSeconds();
        assertThat(delay).isBetween(600L, 719L);
        clock.advance(delay); assertThat(s.admit(10)).isTrue(); s.completed();
        assertThat(s.snapshot().get("state")).isEqualTo("CLOSED");
        assertThat(s.snapshot().get("dataFreshness")).isEqualTo("FRESH");
    }
    @Test void rollingQuotaAndRetryAfterAndAuth() {
        var clock = new Time(); var s = new GeminiRecoveryState(clock);
        assertThat(s.admit(1)).isTrue(); s.completed(); clock.advance(60);
        assertThat(s.admit(1)).isFalse(); clock.advance(86400); assertThat(s.admit(1)).isTrue();
        s.failed("HTTP_RATE_LIMITED", 429, clock.instant().plusSeconds(7200));
        clock.advance(7199); assertThat(s.admit(10)).isFalse(); clock.advance(1);
        assertThat(s.admit(10)).isTrue(); s.failed("HTTP_UNAUTHORIZED", 401, null);
        clock.advance(86400); assertThat(s.admit(10)).isFalse();
    }
    @Test void statusPrunesExpiredUsageBeforeReportingEligibility() {
        var clock = new Time(); var s = new GeminiRecoveryState(clock);
        assertThat(s.admit(2)).isTrue(); s.completed();
        clock.advance(60);
        assertThat(s.admit(2)).isTrue(); s.completed();
        clock.advance(86340);
        s.snapshot();
        assertThat(s.calls).containsExactly(clock.instant().minusSeconds(86340));
        assertThat(s.admit(2)).isTrue();
    }
    @Test void concurrentAdmissionAndCappedBackoff() throws Exception {
        var clock = new Time(); var s = new GeminiRecoveryState(clock);
        var accepted = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) jobs.add(pool.submit(() -> { if (s.admit(10)) accepted.incrementAndGet(); }));
            for (var job : jobs) job.get();
        }
        assertThat(accepted.get()).isEqualTo(1);
        for (int i = 0; i < 8; i++) {
            s.failed("HTTP_SERVER_ERROR", 503, null);
            long delay = Duration.between(clock.instant(), (Instant)s.snapshot().get("nextEligibleRecoveryTime")).toSeconds();
            assertThat(delay).isBetween(300L, 3600L);
            clock.advance(delay); assertThat(s.admit(10)).isTrue();
        }
    }
    @Test void durableRestartAndExclusiveProcess(@TempDir Path directory) {
        var clock = new Time();
        try (var s = new GeminiRecoveryState(clock, directory)) {
            assertThat(s.admit(10)).isTrue(); s.failed("HTTP_SERVER_ERROR", 503, null);
            assertThatThrownBy(() -> new GeminiRecoveryState(clock, directory)).isInstanceOf(IllegalStateException.class);
        }
        try (var s = new GeminiRecoveryState(clock, directory)) {
            assertThat(s.snapshot().get("reasonCode")).isEqualTo("HTTP_SERVER_ERROR");
            assertThat(s.admit(10)).isFalse(); clock.advance(300); assertThat(s.admit(10)).isTrue();
        }
        try (var s = new GeminiRecoveryState(clock, directory)) {
            assertThat(s.snapshot().get("state")).isEqualTo("OPEN");
            assertThat(s.admit(10)).isFalse();
            clock.advance(3600);
            assertThat(s.admit(2)).isFalse(); // Both pre-crash attempts still consume quota.
            assertThat(s.admit(10)).isTrue();
            assertThat(s.admit(10)).isFalse();
            s.completed();
        }
    }
    @Test void reducedCapWaitsUntilEnoughAttemptsExpireAcrossRestart(@TempDir Path directory) {
        var clock = new Time();
        Instant first = clock.instant();
        try (var s = new GeminiRecoveryState(clock, directory)) {
            for (int i = 0; i < 3; i++) {
                assertThat(s.admit(10)).isTrue(); s.completed(); clock.advance(60);
            }
        }
        try (var s = new GeminiRecoveryState(clock, directory)) {
            s.snapshot();
            Instant eligible = s.quotaEligibleAt(2);
            assertThat(eligible).isEqualTo(first.plusSeconds(86460));
            clock.now = first.plusSeconds(86400);
            assertThat(s.admit(2)).isFalse();
            clock.now = eligible;
            assertThat(s.admit(2)).isTrue();
        }
    }
    @Test void corruptLedgerFailsClosedAndReleasesLock(@TempDir Path directory) throws Exception {
        var clock = new Time();
        try (var s = new GeminiRecoveryState(clock, directory)) {
            assertThat(s.admit(10)).isTrue();
            s.failed("HTTP_SERVER_ERROR", 503, null);
        }
        var file = directory.resolve("gemini.properties");
        String original = java.nio.file.Files.readString(file);
        java.nio.file.Files.writeString(file, original.replace("circuit=OPEN", "circuit=UNKNOWN"));
        assertThatThrownBy(() -> new GeminiRecoveryState(clock, directory))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refusing requests");
        java.nio.file.Files.writeString(file, original.replaceAll("(?m)^calls=.*\\R?", ""));
        assertThatThrownBy(() -> new GeminiRecoveryState(clock, directory))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("refusing requests");
        java.nio.file.Files.writeString(file, original);
        try (var restored = new GeminiRecoveryState(clock, directory)) {
            assertThat(restored.admit(10)).isFalse();
            assertThat(restored.calls).hasSize(1);
        }
    }
}
