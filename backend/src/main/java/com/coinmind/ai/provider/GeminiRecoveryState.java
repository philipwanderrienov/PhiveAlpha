package com.coinmind.ai.provider;

import java.time.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;
import java.util.*;

/** Durable local admission ledger. Exactly one process per shared state directory. */
public final class GeminiRecoveryState implements AutoCloseable {
    final Clock clock;
    final Deque<Instant> calls = new ArrayDeque<>();
    Instant next = Instant.EPOCH, success, failure;
    String circuit = "CLOSED", reason = "UNVERIFIED";
    int failures;
    boolean busy;
    private Path file;
    private FileChannel channel;
    private FileLock lock;

    GeminiRecoveryState(Clock clock) { this.clock = clock; }
    public GeminiRecoveryState(Clock clock, Path directory) {
        this(clock);
        try {
            Files.createDirectories(directory);
            channel = FileChannel.open(directory.resolve("gemini.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) throw new IOException("locked");
            file = directory.resolve("gemini.properties");
            if (Files.exists(file)) {
                Properties p = new Properties();
                try (var in = Files.newInputStream(file)) { p.load(in); }
                for (String required : List.of("next", "circuit", "reason", "failures", "success", "failure", "calls")) {
                    if (!p.containsKey(required)) throw new IOException("incomplete ledger");
                }
                next = Instant.parse(p.getProperty("next"));
                circuit = p.getProperty("circuit"); reason = p.getProperty("reason");
                failures = Integer.parseInt(p.getProperty("failures"));
                success = parse(p.getProperty("success")); failure = parse(p.getProperty("failure"));
                for (String s : p.getProperty("calls", "").split(",")) if (!s.isEmpty()) calls.add(Instant.parse(s));
                if (!Set.of("CLOSED", "OPEN", "HALF_OPEN", "AUTH_ERROR").contains(circuit)
                        || reason == null || !reason.matches("[A-Z_]{1,64}") || failures < 0
                        || calls.size() > 10) throw new IOException("invalid ledger");
                Instant previous = null;
                for (Instant attempt : calls) {
                    if (previous != null && attempt.isBefore(previous)) throw new IOException("unordered ledger");
                    previous = attempt;
                }
                // The old process owns no request after restart. Retain its conservative
                // deadline but allow the scheduler to reconsider genuine work afterward.
                if ("HALF_OPEN".equals(circuit)) circuit = "OPEN";
            }
        } catch (Exception e) { close(); throw new IllegalStateException("Gemini durable ledger unavailable; refusing requests"); }
    }
    private static Instant parse(String s) { return s == null || s.isEmpty() ? null : Instant.parse(s); }
    synchronized boolean admit(int limit) {
        Instant now = clock.instant();
        while (!calls.isEmpty() && !calls.peekFirst().plusSeconds(86400).isAfter(now)) calls.removeFirst();
        if (busy || circuit.equals("AUTH_ERROR") || next.isAfter(now)) return false;
        if (calls.size() >= Math.max(0, Math.min(10, limit))) return false;
        if (!calls.isEmpty() && calls.peekLast().plusSeconds(60).isAfter(now)) return false;
        busy = true;
        if (!circuit.equals("CLOSED")) circuit = "HALF_OPEN";
        calls.add(now);
        // Persist a conservative cooldown before sending, including crash during HALF_OPEN.
        if (circuit.equals("HALF_OPEN")) next = now.plusSeconds(3600);
        save(); return true;
    }
    synchronized void completed() { success = clock.instant(); circuit = "CLOSED"; reason = "VALIDATED_SUCCESS"; failures = 0; next = Instant.EPOCH; busy = false; save(); }
    synchronized Instant quotaEligibleAt(int limit) {
        // Call after snapshot has pruned the rolling window, with a positive exhausted cap.
        return calls.stream().skip(calls.size() - limit).findFirst().orElseThrow().plusSeconds(86400);
    }
    synchronized void failed(String code, int status, Instant retryAfter) {
        failure = clock.instant(); reason = code; busy = false;
        if (status == 401 || status == 403) { circuit = "AUTH_ERROR"; next = Instant.EPOCH; }
        else {
            circuit = "OPEN";
            long base = Math.min(3600, 300L << Math.min(failures++, 4));
            long seconds = failures == 1 ? 300 : Math.min(3600, base + java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1, base / 5)));
            next = failure.plusSeconds(seconds);
            if (retryAfter != null && retryAfter.isAfter(next)) next = retryAfter;
        }
        save();
    }
    public synchronized Map<String, Object> snapshot() {
        Instant now = clock.instant();
        while (!calls.isEmpty() && !calls.peekFirst().plusSeconds(86400).isAfter(now)) calls.removeFirst();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("state", circuit); m.put("reasonCode", reason); m.put("lastSuccess", success); m.put("lastFailure", failure);
        Instant eligible = next;
        if (!calls.isEmpty()) eligible = eligible.isAfter(calls.peekLast().plusSeconds(60)) ? eligible : calls.peekLast().plusSeconds(60);
        m.put("nextEligibleRecoveryTime", circuit.equals("AUTH_ERROR") || eligible.equals(Instant.EPOCH) ? null : eligible);
        m.put("dataFreshness", success == null ? "MISSING" : success.plusSeconds(900).isAfter(clock.instant()) ? "FRESH" : "STALE");
        return m;
    }
    private void save() {
        if (file == null) return;
        try {
            Properties p = new Properties();
            p.setProperty("next", next.toString()); p.setProperty("circuit", circuit); p.setProperty("reason", reason);
            p.setProperty("failures", "" + failures); p.setProperty("success", success == null ? "" : success.toString()); p.setProperty("failure", failure == null ? "" : failure.toString());
            p.setProperty("calls", String.join(",", calls.stream().map(Instant::toString).toList()));
            Path temp = file.resolveSibling("gemini.tmp");
            try (var out = Files.newOutputStream(temp)) { p.store(out, "Gemini admission ledger; no credentials"); }
            try (var c = FileChannel.open(temp, StandardOpenOption.WRITE)) { c.force(true); }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { circuit = "AUTH_ERROR"; busy = true; throw new IllegalStateException("Gemini ledger write failed; requests blocked"); }
    }
    @Override public void close() { try { if (lock != null) lock.release(); if (channel != null) channel.close(); } catch (IOException ignored) { } }
}
