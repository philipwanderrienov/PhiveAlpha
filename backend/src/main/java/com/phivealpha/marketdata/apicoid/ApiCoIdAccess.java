package com.phivealpha.marketdata.apicoid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

final class ApiCoIdAccess {
    enum Product {
        IDX(350), FX(60), HOLIDAY(12);

        final int defaultLimit;

        Product(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }
    }

    enum Dataset {
        INSTRUMENTS(Product.IDX), DAILY(Product.IDX), MARKET_NEWS(Product.IDX), SYMBOL_NEWS(Product.IDX),
        QUOTE(Product.IDX), CORPORATE_ACTIONS(Product.IDX), FOREIGN_FLOW(Product.IDX), BROKER_FLOW(Product.IDX),
        IDX_CALENDAR(Product.IDX), IDX_HOLIDAYS(Product.IDX),
        USD_IDR(Product.FX), NATIONAL_HOLIDAYS(Product.HOLIDAY);

        final Product product;

        Dataset(Product product) {
            this.product = product;
        }
    }

    record Window(Instant start, Instant end, int limit) {
        Window {
            if (start == null || end == null || !start.isBefore(end) || limit < 1 || limit > 100_000) {
                throw new IllegalArgumentException("Invalid internal budget window");
            }
        }

        boolean active(Instant now) {
            return !now.isBefore(start) && now.isBefore(end);
        }
    }

    record Grant(Instant expires, boolean contractReviewed, boolean freeConfirmed, boolean metadataRetention) {}

    record VendorQuota(int limit, int remainingAtObservation, Instant observedAt) {
        VendorQuota {
            if (limit < 0 || remainingAtObservation < 0 || remainingAtObservation > limit || observedAt == null) {
                throw new IllegalArgumentException("Invalid vendor quota evidence");
            }
        }
    }

    private final Map<Product, Window> windows;
    private final Map<Product, VendorQuota> vendorQuota;
    private final Map<Dataset, Grant> grants;
    private final ApiCoIdQuotaLedger ledger;
    private final Clock clock;
    private final AtomicBoolean journalFailed;

    ApiCoIdAccess(Map<Product, Window> windows, Map<Dataset, Grant> grants, ApiCoIdQuotaLedger ledger, Clock clock) {
        this(windows, grants, ledger, clock, new AtomicBoolean(), Map.of());
    }

    private ApiCoIdAccess(Map<Product, Window> windows, Map<Dataset, Grant> grants, ApiCoIdQuotaLedger ledger,
                          Clock clock, AtomicBoolean journalFailed, Map<Product, VendorQuota> vendorQuota) {
        this.journalFailed = journalFailed;
        this.vendorQuota = Map.copyOf(vendorQuota);
        this.windows = Map.copyOf(windows);
        this.grants = Map.copyOf(grants);
        this.ledger = ledger;
        this.clock = clock;
    }

    static ApiCoIdAccess load(String manifest, String journal, Clock clock) {
        if (manifest == null && journal == null) {
            return new ApiCoIdAccess(Map.of(), Map.of(), null, clock);
        }
        try {
            var file = Path.of(manifest);
            var ledgerPath = Path.of(journal);
            if (!file.isAbsolute() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !ledgerPath.isAbsolute()) {
                throw new IllegalArgumentException();
            }
            var properties = new Properties();
            try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                var bytes = in.readNBytes(16_385);
                if (bytes.length > 16_384) {
                    throw new IllegalArgumentException();
                }
                properties.load(new ByteArrayInputStream(bytes));
            }
            var windows = new EnumMap<Product, Window>(Product.class);
            var observed = new EnumMap<Product, VendorQuota>(Product.class);
            for (var product : Product.values()) {
                var prefix = product.name() + ".";
                if (has(properties, prefix + "reset-reference")) {
                    windows.put(product, new Window(Instant.parse(properties.getProperty(prefix + "start")),
                            Instant.parse(properties.getProperty(prefix + "end")),
                            Integer.parseInt(properties.getProperty(prefix + "limit",
                                    Integer.toString(product.defaultLimit)))));
                    if (has(properties, prefix + "vendor-quota-reference")) {
                        var value = new VendorQuota(Integer.parseInt(properties.getProperty(prefix + "vendor-limit")),
                                Integer.parseInt(properties.getProperty(prefix + "vendor-remaining")),
                                Instant.parse(properties.getProperty(prefix + "vendor-observed-at")));
                        if (!windows.get(product).active(value.observedAt()) || value.observedAt().isAfter(clock.instant())) {
                            throw new IllegalArgumentException("Vendor quota observation outside reviewed window");
                        }
                        observed.put(product, value);
                    }
                }
            }
            var grants = new EnumMap<Dataset, Grant>(Dataset.class);
            for (var dataset : Dataset.values()) {
                String prefix = dataset.name() + ".";
                if (has(properties, prefix + "entitlement-reference") && has(properties, prefix + "expires")) {
                    grants.put(dataset, new Grant(Instant.parse(properties.getProperty(prefix + "expires")),
                            has(properties, prefix + "contract-reference"),
                            "true".equals(properties.getProperty(prefix + "free-confirmed")),
                            has(properties, prefix + "metadata-retention-reference")));
                }
            }
            return new ApiCoIdAccess(windows, grants, new ApiCoIdQuotaLedger(ledgerPath), clock, new AtomicBoolean(), observed);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid API.co.id approvals or quota configuration");
        }
    }

    ApiCoIdAccess restrictedToFree(Set<Dataset> scope) {
        var allowed = new EnumMap<Dataset, Grant>(Dataset.class);
        grants.forEach((dataset, grant) -> {
            if (scope.contains(dataset) && grant.freeConfirmed()) {
                allowed.put(dataset, grant);
            }
        });
        return new ApiCoIdAccess(windows, allowed, ledger, clock, journalFailed, vendorQuota);
    }

    void check(Dataset dataset) {
        if (!entitled(dataset)) {
            throw new ApiCoIdFailure(ApiCoIdData.Quality.UNVERIFIED, "ENTITLEMENT_UNVERIFIED");
        }
        if (journalFailed.get()) {
            throw new ApiCoIdFailure(ApiCoIdData.Quality.UNAVAILABLE, "QUOTA_LEDGER_UNAVAILABLE");
        }
        var window = windows.get(dataset.product);
        if (ledger == null || window == null || !window.active(clock.instant())) {
            throw new ApiCoIdFailure(ApiCoIdData.Quality.UNAVAILABLE, "QUOTA_WINDOW_UNAVAILABLE");
        }
    }

    ApiCoIdQuotaLedger.Reservation reserve(Dataset dataset) {
        check(dataset);
        return ledger.reserve(dataset, windows.get(dataset.product), clock.instant());
    }

    void finish(ApiCoIdQuotaLedger.Reservation reservation, int status, String result, Instant retryAt,
                java.time.Duration cooldown) {
        try {
            var now = clock.instant();
            Instant retryUntil = null;
            if (status == 429) {
                var local = now.plus(cooldown.compareTo(java.time.Duration.ofMinutes(1)) < 0
                        ? java.time.Duration.ofMinutes(1) : cooldown);
                retryUntil = retryAt != null && retryAt.isAfter(local) ? retryAt : local;
            }
            ledger.finish(reservation, now, status, result, retryUntil);
        } catch (ApiCoIdFailure failure) {
            journalFailed.set(true);
            throw failure;
        }
    }

    boolean entitled(Dataset dataset) {
        Grant grant = grants.get(dataset);
        return grant != null && clock.instant().isBefore(grant.expires());
    }

    boolean freeEntitled(Dataset dataset) {
        return entitled(dataset) && grants.get(dataset).freeConfirmed();
    }

    boolean metadataAllowed(Dataset dataset) {
        return entitled(dataset) && grants.get(dataset).metadataRetention();
    }

    boolean verified(Dataset dataset) {
        return entitled(dataset) && grants.get(dataset).contractReviewed();
    }

    ApiCoIdQuotaLedger.Usage usage(Product product) {
        var window = windows.get(product);
        return ledger == null || window == null || !window.active(clock.instant())
                ? null : ledger.usage(product, window);
    }

    VendorQuota vendorQuota(Product product) {
        var window = windows.get(product);
        var now = clock.instant();
        var observation = vendorQuota.get(product);
        return window != null && window.active(now) && observation != null && !observation.observedAt().isAfter(now)
                ? observation : null;
    }

    int configuredLimit(Product product) {
        var window = windows.get(product);
        return window == null ? product.defaultLimit : window.limit();
    }

    private static boolean has(Properties properties, String key) {
        return !properties.getProperty(key, "").isBlank();
    }
}
