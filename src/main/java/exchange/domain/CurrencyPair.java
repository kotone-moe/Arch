package exchange.domain;

import java.util.Objects;

/** Валютная пара, например EUR/USD: базовая валюта покупается или продаётся за котируемую. */
public record CurrencyPair(String base, String quote) {

    public CurrencyPair {
        base = normalize(base);
        quote = normalize(quote);
        if (base.equals(quote)) {
            throw new IllegalArgumentException("Валюты в паре должны различаться: " + base);
        }
    }

    private static String normalize(String currencyCode) {
        Objects.requireNonNull(currencyCode, "Код валюты не задан");
        String normalized = currencyCode.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Код валюты не может быть пустым");
        }
        return normalized;
    }

    @Override
    public String toString() {
        return base + "/" + quote;
    }
}
