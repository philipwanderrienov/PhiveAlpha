package com.phivealpha.persistence;

import java.net.URI;
import java.util.Set;

public final class IdxDevelopmentDatabase {
    private IdxDevelopmentDatabase() {}

    public static void requireIsolated(String url, String username) {
        try {
            if (url.startsWith("r2dbc:postgresql://") && username.matches("phivealpha_dev_[a-z0-9_]+")) {
                var uri = URI.create(url.substring(6));
                if (uri.getHost() != null && Set.of("127.0.0.1", "[::1]").contains(uri.getHost())
                        && uri.getPort() > 0 && uri.getRawPath().matches("/phivealpha_dev_[a-z0-9_]+")
                        && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null) {
                    return;
                }
            }
        } catch (RuntimeException ignored) {
            // Keep connection details out of startup diagnostics.
        }
        throw new IllegalArgumentException("Operation requires an isolated PhiveAlpha development database");
    }
}
