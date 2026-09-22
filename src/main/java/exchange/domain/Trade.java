package exchange.domain;

import java.math.BigDecimal;

/** Сделка: кто купил, кто продал, по какой цене и сколько. */
public record Trade(
        long id,
        CurrencyPair pair,
        BigDecimal price,
        BigDecimal amount,
        String buyerId,
        String sellerId) {
}
