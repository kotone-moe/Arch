package exchange.api;

import exchange.domain.CurrencyPair;
import exchange.domain.Side;

import java.math.BigDecimal;
import java.util.Objects;

/** Заявка клиента на постановку ордера. Проверяет себя сама при создании. */
public record OrderRequest(
        String clientId,
        CurrencyPair pair,
        Side side,
        BigDecimal limitPrice,
        BigDecimal amount) {

    public OrderRequest {
        Objects.requireNonNull(clientId, "Не задан клиент");
        Objects.requireNonNull(pair, "Не задана валютная пара");
        Objects.requireNonNull(side, "Не задано направление");
        requirePositive(limitPrice, "Цена");
        requirePositive(amount, "Количество");
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " не задана");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " должна быть больше нуля");
        }
    }
}
