package com.phivealpha.marketdata.apicoid;

enum ApiCoIdEndpoint {
    INSTRUMENTS("/idx/v2/instruments"),
    QUOTE("/idx/v2/chart/quote"),
    DAILY("/idx/v2/chart/daily"),
    NEWS("/idx/v2/news"),
    CORPORATE_ACTIONS("/idx/v2/corporate-actions"),
    FOREIGN_FLOW("/idx/v2/foreign-flow"),
    BROKER_FLOW("/idx/v2/broker-flow");

    final String path;

    ApiCoIdEndpoint(String path) {
        this.path = path;
    }
}
