package com.phivealpha.newsintelligence;

import java.time.Instant;

public record IdxHeadline(String symbol, String title, String source, Instant publishedAt) {}
