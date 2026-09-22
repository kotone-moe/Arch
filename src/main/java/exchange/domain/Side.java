package exchange.domain;

import java.math.BigDecimal;

/**
 * Направление ордера: покупка или продажа.
 * Каждое направление само знает, какая цена ему подходит.
 */
public enum Side {

    BUY {
        @Override
        public boolean accepts(BigDecimal ownLimitPrice, BigDecimal offeredPrice) {
            // Покупатель согласен платить не больше своего лимита.
            return offeredPrice.compareTo(ownLimitPrice) <= 0;
        }

        @Override
        public Side opposite() {
            return SELL;
        }
    },

    SELL {
        @Override
        public boolean accepts(BigDecimal ownLimitPrice, BigDecimal offeredPrice) {
            // Продавец согласен получить не меньше своего лимита.
            return offeredPrice.compareTo(ownLimitPrice) >= 0;
        }

        @Override
        public Side opposite() {
            return BUY;
        }
    };

    public abstract boolean accepts(BigDecimal ownLimitPrice, BigDecimal offeredPrice);

    public abstract Side opposite();
}
