package exchange.core;

import exchange.api.ClientListener;
import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.common.IdSequence;
import exchange.domain.Order;
import exchange.domain.Trade;
import exchange.matching.OrderBooks;
import exchange.notification.ClientConnections;
import exchange.notification.TradeNotifier;

import java.util.List;

/**
 * Биржа: принимает запросы клиентов и раздаёт работу помощникам.
 * Сама ничего не считает: матчинг делают книги ордеров, доставку оповещений делает нотификатор.
 */
public final class Exchange implements ExchangeApi {

    private final OrderBooks orderBooks;
    private final ClientConnections connections;
    private final TradeNotifier notifier;
    private final IdSequence orderIds;

    public Exchange(OrderBooks orderBooks,
                    ClientConnections connections,
                    TradeNotifier notifier,
                    IdSequence orderIds) {
        this.orderBooks = orderBooks;
        this.connections = connections;
        this.notifier = notifier;
        this.orderIds = orderIds;
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
        Order order = createOrder(request);
        List<Trade> trades = orderBooks.forPair(request.pair()).place(order);
        trades.forEach(this::notifyBothSides);
        return order.id();
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
