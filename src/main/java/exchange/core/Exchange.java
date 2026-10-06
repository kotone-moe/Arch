package exchange.core;

import exchange.api.ClientListener;
import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.common.IdSequence;
import exchange.domain.Order;
import exchange.domain.Trade;
import exchange.matching.OrderBooks;
import exchange.metrics.ExchangeMetrics;
import exchange.notification.ClientConnections;
import exchange.notification.TradeNotifier;
import exchange.persistence.ExchangeStore;

import java.util.List;
import java.util.Objects;

/**
 * Биржа: принимает запросы клиентов и раздаёт работу помощникам.
 * Сама ничего не считает: матчинг делают книги ордеров, доставку оповещений делает нотификатор.
 * Каждая успешно обработанная заявка учитывается в метриках (ExchangeMetrics).
 * С хранилищем каждая заявка — одна транзакция: заявки, сделки и оповещения
 * фиксируются атомарно, поэтому при нештатном завершении ничего не теряется.
 */
public final class Exchange implements ExchangeApi {

    private final OrderBooks orderBooks;
    private final ClientConnections connections;
    private final TradeNotifier notifier;
    private final IdSequence orderIds;
    private final ExchangeStore store;
    private final ExchangeMetrics metrics;

    public Exchange(OrderBooks orderBooks,
                    ClientConnections connections,
                    TradeNotifier notifier,
                    IdSequence orderIds,
                    ExchangeMetrics metrics) {
        this(orderBooks, connections, notifier, orderIds, metrics, null);
    }

    public Exchange(OrderBooks orderBooks,
                    ClientConnections connections,
                    TradeNotifier notifier,
                    IdSequence orderIds,
                    ExchangeMetrics metrics,
                    ExchangeStore store) {
        this.orderBooks = orderBooks;
        this.connections = connections;
        this.notifier = notifier;
        this.orderIds = orderIds;
        this.metrics = Objects.requireNonNull(metrics, "Метрики не заданы");
        this.store = store;
    }

    @Override
    public void connect(String clientId, ClientListener listener) {
        connections.connect(clientId, listener);
    }

    @Override
    public void disconnect(String clientId) {
        connections.disconnect(clientId);
    }

    @Override
    public long placeOrder(OrderRequest request) {
        if (store == null) {
            Order order = createOrder(request);
            List<Trade> trades = orderBooks.forPair(request.pair()).place(order);
            trades.forEach(this::notifyBothSides);
            metrics.orderProcessed();
            return order.id();
        }

        Order order;
        List<Trade> trades;
        synchronized (store) {
            store.begin();
            try {
                order = createOrder(request);
                trades = orderBooks.forPair(request.pair()).place(order);
                for (Trade trade : trades) {
                    store.addNotification(trade.buyerId(), trade.id());
                    store.addNotification(trade.sellerId(), trade.id());
                }
                store.commit();
            } catch (RuntimeException e) {
                store.rollback();
                throw e;
            }
        }
        // Доставка — после транзакции и без блокировки хранилища.
        trades.forEach(this::notifyBothSides);
        metrics.orderProcessed();
        return order.id();
    }

    @Override
    public void close() {
        if (store != null) {
            store.close();
        }
    }

    private Order createOrder(OrderRequest request) {
        return new Order(orderIds.next(), request.clientId(), request.side(),
                request.limitPrice(), request.amount());
    }

    private void notifyBothSides(Trade trade) {
        notifier.notifyClient(trade.buyerId(), trade);
        notifier.notifyClient(trade.sellerId(), trade);
    }
}
