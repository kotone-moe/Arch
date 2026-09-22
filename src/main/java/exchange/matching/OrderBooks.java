package exchange.matching;

import exchange.common.IdSequence;
import exchange.domain.CurrencyPair;
import exchange.persistence.ExchangeStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Хранит по одной книге ордеров на каждую валютную пару и создаёт их по мере необходимости. */
public final class OrderBooks {

    private final Map<CurrencyPair, OrderBook> booksByPair = new ConcurrentHashMap<>();
    private final IdSequence tradeIds;
    private final ExchangeStore store;

    public OrderBooks(IdSequence tradeIds) {
        this(tradeIds, null);
    }

    public OrderBooks(IdSequence tradeIds, ExchangeStore store) {
        this.tradeIds = tradeIds;
        this.store = store;
    }

    public OrderBook forPair(CurrencyPair pair) {
        return booksByPair.computeIfAbsent(pair, newPair -> new OrderBook(newPair, tradeIds, store));
    }
}
