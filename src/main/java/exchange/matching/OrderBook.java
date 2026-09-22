package exchange.matching;

import exchange.common.IdSequence;
import exchange.domain.CurrencyPair;
import exchange.domain.Order;
import exchange.domain.Side;
import exchange.domain.Trade;
import exchange.persistence.ExchangeStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Книга ордеров одной валютной пары: хранит ждущие ордера и сводит покупателей с продавцами.
 * Метод place синхронизирован, поэтому внутри книги в каждый момент работает один поток.
 */
public final class OrderBook {

    // Лучший покупатель тот, кто платит больше; при равных ценах раньше пришедший.
    private static final Comparator<Order> BUY_PRIORITY =
            Comparator.comparing(Order::limitPrice).reversed().thenComparingLong(Order::id);

    // Лучший продавец тот, кто просит меньше; при равных ценах раньше пришедший.
    private static final Comparator<Order> SELL_PRIORITY =
            Comparator.comparing(Order::limitPrice).thenComparingLong(Order::id);

    private final CurrencyPair pair;
    private final IdSequence tradeIds;
    private final ExchangeStore store;
    private final Queue<Order> buyOrders = new PriorityQueue<>(BUY_PRIORITY);
    private final Queue<Order> sellOrders = new PriorityQueue<>(SELL_PRIORITY);

    public OrderBook(CurrencyPair pair, IdSequence tradeIds) {
        this(pair, tradeIds, null);
    }

    public OrderBook(CurrencyPair pair, IdSequence tradeIds, ExchangeStore store) {
        this.pair = pair;
        this.tradeIds = tradeIds;
        this.store = store;
    }

    /**
     * Принимает новый ордер, исполняет его против ждущих и возвращает получившиеся сделки.
     * Что не исполнилось, остаётся в книге.
     * При наличии хранилища все изменения фиксируются в нём (в текущей транзакции).
     */
    public synchronized List<Trade> place(Order incoming) {
        List<Trade> trades = matchAgainstWaitingOrders(incoming);
        if (!incoming.isFilled()) {
            waitingOrders(incoming.side()).add(incoming);
        }
        if (store != null) {
            store.saveOrder(pair, incoming);
        }
        return trades;
    }

    /** Кладёт заявку в книгу без матчинга — используется при восстановлении из хранилища. */
    public synchronized void restore(Order waiting) {
        if (!waiting.isFilled()) {
            waitingOrders(waiting.side()).add(waiting);
        }
    }

    private List<Trade> matchAgainstWaitingOrders(Order incoming) {
        Queue<Order> counterSide = waitingOrders(incoming.side().opposite());
        List<Trade> trades = new ArrayList<>();

        while (!incoming.isFilled() && canTradeWithBest(incoming, counterSide)) {
            Order waiting = counterSide.peek();
            trades.add(execute(incoming, waiting));
            if (waiting.isFilled()) {
                counterSide.poll();
            }
        }
        return trades;
    }

    private boolean canTradeWithBest(Order incoming, Queue<Order> counterSide) {
        Order best = counterSide.peek();
        return best != null && incoming.acceptsPrice(best.limitPrice());
    }

    private Trade execute(Order incoming, Order waiting) {
        BigDecimal amount = incoming.remainingAmount().min(waiting.remainingAmount());
        incoming.fill(amount);
        waiting.fill(amount);

        Order buyer = incoming.side() == Side.BUY ? incoming : waiting;
        Order seller = incoming.side() == Side.BUY ? waiting : incoming;

        // Сделка проходит по цене ордера, который ждал в книге.
        Trade trade = new Trade(tradeIds.next(), pair, waiting.limitPrice(), amount,
                buyer.clientId(), seller.clientId());
        if (store != null) {
            store.saveOrder(pair, waiting);
            store.saveTrade(trade);
        }
        return trade;
    }

    private Queue<Order> waitingOrders(Side side) {
        return side == Side.BUY ? buyOrders : sellOrders;
    }
}
