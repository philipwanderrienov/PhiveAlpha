package com.phivealpha.stock;

public record IdxStock(String symbol, String name, String sector) {
    public static final int SHARES_PER_LOT = 100;
}
