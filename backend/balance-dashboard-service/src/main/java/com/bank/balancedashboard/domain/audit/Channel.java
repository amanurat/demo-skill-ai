package com.bank.balancedashboard.domain.audit;

/**
 * Channel through which the balance inquiry was initiated.
 * Domain enum — ZERO Spring/Kafka/Redis imports permitted in this package.
 */
public enum Channel {
    MOBILE_BANKING,
    WEB,
    API;

    /**
     * Derive channel from HTTP request User-Agent or a dedicated header.
     * Defaults to MOBILE_BANKING for BDS v1 (mobile-first feature).
     */
    public static Channel fromUserAgent(String userAgent) {
        if (userAgent == null) {
            return MOBILE_BANKING;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return MOBILE_BANKING;
        }
        if (ua.contains("mozilla") || ua.contains("chrome") || ua.contains("safari")) {
            return WEB;
        }
        return API;
    }
}
