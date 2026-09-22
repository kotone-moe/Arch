package exchange.domain;

import java.math.BigDecimal;

/**
 * Ордер, лежащий в книге заявок. Отслеживает, сколько ещё осталось исполнить.
 * Класс не потокобезопасен: с ордерами работает только OrderBook под своей блокировкой.
 */
public final class Order {

    private final long id;
    private final String clientId;
    private final Side side;
    private final BigDecimal limitPrice;
    private BigDecimal remainingAmount;

    public Order(long id, String clientId, Side side, BigDecimal limitPrice, BigDecimal amount) {
        this.id = id;
        this.clientId = clientId;
        this.side = side;
        this.limitPrice = limitPrice;
        this.remainingAmount = amount;
    }

    public long id() {
        return id;
    }

    public String clientId() {
        return clientId;
    }

    public Side side() {
        return side;
    }

    public BigDecimal limitPrice() {
        return limitPrice;
    }

    public BigDecimal remainingAmount() {
        return remainingAmount;
    }

    public boolean isFilled() {
        return remainingAmount.signum() == 0;
    }

    /** Устраивает ли этому ордеру цена, которую предлагает встречный ордер. */
    public boolean acceptsPrice(BigDecimal offeredPrice) {
        return side.accepts(limitPrice, offeredPrice);
    }

    /** Исполняет часть ордера. */
    public void fill(BigDecimal amount) {
        if (amount.compareTo(remainingAmount) > 0) {
            throw new IllegalArgumentException("Нельзя исполнить больше, чем осталось в ордере");
        }
        remainingAmount = remainingAmount.subtract(amount);
    }
}
