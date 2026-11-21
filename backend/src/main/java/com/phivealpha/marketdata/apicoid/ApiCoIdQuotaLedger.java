package com.phivealpha.marketdata.apicoid;

import static com.phivealpha.marketdata.apicoid.ApiCoIdAccess.*;
import static com.phivealpha.marketdata.apicoid.ApiCoIdData.Quality.UNAVAILABLE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ApiCoIdQuotaLedger {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Object PROCESS_LOCK = new Object();
    private final Path path;

    record Reservation(UUID id, Dataset dataset, Instant start, Instant end, Instant attemptedAt) {}
    record Result(Instant completedAt, int httpStatus, String outcome, Instant retryUntil) {}
    record DatasetUsage(int attempts, int errors, int pending, Instant lastSuccessfulTransport) {}
    record Usage(Instant start, Instant end, int internalLimit, int used, int estimatedRemaining,
                 Map<Dataset, DatasetUsage> datasets) {}

    ApiCoIdQuotaLedger(Path path) {
        this.path = path;
    }

    Reservation reserve(Dataset dataset, Window window, Instant at) {
        return locked((channel, entries) -> {
            var current = current(entries, dataset.product, window);
            if (!window.active(at)) {
                throw blocked("QUOTA_WINDOW_UNAVAILABLE");
            }
            if (current.size() >= window.limit()) {
                throw blocked("PERSISTENT_BUDGET_EXHAUSTED");
            }
            if (current.values().stream().anyMatch(row -> row.result != null
                    && (row.result.httpStatus() == 401 || row.result.httpStatus() == 402
                    || row.result.httpStatus() == 403))) {
                throw blocked("PERSISTENT_ENTITLEMENT_BLOCK");
            }
            if (current.values().stream().anyMatch(row -> row.result != null && row.result.httpStatus() == 429
                    && at.isBefore(row.result.retryUntil() == null ? row.result.completedAt().plusSeconds(60)
                            : row.result.retryUntil()))) {
                throw blocked("PERSISTENT_RATE_LIMIT");
            }
            var id = UUID.randomUUID();
            append(channel, String.join("\t", "A", id.toString(), dataset.name(), window.start().toString(),
                    window.end().toString(), at.toString()));
            return new Reservation(id, dataset, window.start(), window.end(), at);
        });
    }

    void finish(Reservation reservation, Instant time, int status, String result, Instant retryUntil) {
        locked((channel, entries) -> {
            var entry = entries.get(reservation.id());
            if (entry == null || !entry.reservation.equals(reservation) || entry.result != null
                    || time.isBefore(reservation.attemptedAt()) || !validResult(status, result)
                    || retryUntil != null && (status != 429 || retryUntil.isBefore(time))) {
                throw blocked("QUOTA_LEDGER_UNAVAILABLE");
            }
            append(channel, String.join("\t", "R", reservation.id().toString(), time.toString(),
                    Integer.toString(status), result, retryUntil == null ? "" : retryUntil.toString()));
            return null;
        });
    }

    Usage usage(Product product, Window window) {
        return locked((channel, entries) -> {
            var current = current(entries, product, window);
            var stats = new EnumMap<Dataset, DatasetUsage>(Dataset.class);
            current.values().forEach(entry -> {
                var key = entry.reservation.dataset();
                var previous = stats.getOrDefault(key, new DatasetUsage(0, 0, 0, null));
                var last = previous.lastSuccessfulTransport();
                var result = entry.result;
                boolean ok = result != null && "OK".equals(result.outcome());
                if (ok && (last == null || result.completedAt().isAfter(last))) {
                    last = result.completedAt();
                }
                stats.put(key, new DatasetUsage(previous.attempts() + 1,
                        previous.errors() + (result != null && !ok ? 1 : 0),
                        previous.pending() + (result == null ? 1 : 0), last));
            });
            return new Usage(window.start(), window.end(), window.limit(), current.size(),
                    Math.max(0, window.limit() - current.size()), Map.copyOf(stats));
        });
    }

    private static Map<UUID, Entry> current(Map<UUID, Entry> entries, Product product, Window window) {
        var current = new HashMap<UUID, Entry>();
        for (var entry : entries.values()) {
            var reservation = entry.reservation;
            if (reservation.dataset().product == product && reservation.start().isBefore(window.end())
                    && window.start().isBefore(reservation.end())) {
                if (!reservation.start().equals(window.start()) || !reservation.end().equals(window.end())) {
                    throw blocked("QUOTA_WINDOW_CONFLICT");
                }
                current.put(reservation.id(), entry);
            }
        }
        return current;
    }

    private <T> T locked(Operation<T> operation) {
        synchronized (PROCESS_LOCK) {
            // The journal must already exist. No automatic bootstrap or fallback to a fresh counter.
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw blocked("QUOTA_LEDGER_UNAVAILABLE");
            }
            try (var channel = FileChannel.open(path, READ, WRITE, LinkOption.NOFOLLOW_LINKS);
                    var lock = channel.tryLock()) {
                if (lock == null) {
                    throw blocked("QUOTA_LEDGER_BUSY");
                }
                var entries = read(channel);
                return operation.apply(channel, entries);
            } catch (ApiCoIdFailure failure) {
                throw failure;
            } catch (IOException | RuntimeException failure) {
                throw blocked("QUOTA_LEDGER_UNAVAILABLE");
            }
        }
    }

    private static Map<UUID, Entry> read(FileChannel channel) throws IOException {
        if (channel.size() > MAX_BYTES) {
            throw new IOException();
        }
        var bytes = ByteBuffer.allocate((int) channel.size());
        while (bytes.hasRemaining() && channel.read(bytes) != -1) {
            // Read to the locked size.
        }
        String contents = new String(bytes.array(), StandardCharsets.US_ASCII);
        if (!contents.startsWith("PHIVEALPHA-QUOTA-V1\n") || !contents.endsWith("\n")) {
            throw new IOException();
        }
        var entries = new HashMap<UUID, Entry>();
        for (var line : contents.lines().skip(1).toList()) {
            var fields = line.split("\t", -1);
            var id = UUID.fromString(fields[1]);
            if ("A".equals(fields[0]) && fields.length == 6) {
                var row = new Reservation(id, Dataset.valueOf(fields[2]), Instant.parse(fields[3]),
                        Instant.parse(fields[4]), Instant.parse(fields[5]));
                if (!new Window(row.start(), row.end(), 1).active(row.attemptedAt())
                        || entries.putIfAbsent(id, new Entry(row, null)) != null) {
                    throw new IOException();
                }
            } else if ("R".equals(fields[0]) && (fields.length == 5 || fields.length == 6)) {
                var entry = entries.get(id);
                var retryUntil = fields.length == 6 && !fields[5].isEmpty() ? Instant.parse(fields[5]) : null;
                var result = new Result(Instant.parse(fields[2]), Integer.parseInt(fields[3]), fields[4], retryUntil);
                if (entry == null || entry.result != null || result.completedAt().isBefore(entry.reservation.attemptedAt())
                        || !validResult(result.httpStatus(), result.outcome())
                        || retryUntil != null && (result.httpStatus() != 429 || retryUntil.isBefore(result.completedAt()))) {
                    throw new IOException();
                }
                entries.put(id, new Entry(entry.reservation, result));
            } else {
                throw new IOException();
            }
        }
        return entries;
    }

    private static boolean validResult(int status, String result) {
        return (status == 0 || status >= 100 && status <= 599) && result.matches("[A-Z_]{1,40}");
    }

    private static void append(FileChannel channel, String row) throws IOException {
        var bytes = StandardCharsets.US_ASCII.encode(row + "\n");
        if (channel.size() + bytes.remaining() > MAX_BYTES) {
            throw new IOException();
        }
        channel.position(channel.size());
        while (bytes.hasRemaining()) {
            channel.write(bytes);
        }
        channel.force(true);
    }

    private static ApiCoIdFailure blocked(String reason) {
        return new ApiCoIdFailure(UNAVAILABLE, reason);
    }

    private record Entry(Reservation reservation, Result result) {}

    @FunctionalInterface
    private interface Operation<T> {
        T apply(FileChannel channel, Map<UUID, Entry> entries) throws IOException;
    }
}
