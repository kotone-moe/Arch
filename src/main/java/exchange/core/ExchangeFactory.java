package exchange.core;

import exchange.api.ExchangeApi;
import exchange.common.AtomicIdSequence;
import exchange.matching.OrderBooks;
import exchange.notification.MailboxNotificationService;
import exchange.persistence.ExchangeStore;
import exchange.persistence.JdbcExchangeStore;

/** Единственное место, где все части биржи собираются вместе. */
public final class ExchangeFactory {

    private ExchangeFactory() {
    }

    /** Биржа, которая живёт только в оперативной памяти (данные теряются при завершении). */
    public static ExchangeApi createInMemory() {
        MailboxNotificationService notifications = new MailboxNotificationService();
        OrderBooks orderBooks = new OrderBooks(new AtomicIdSequence());
        return new Exchange(orderBooks, notifications, notifications, new AtomicIdSequence());
    }

    /**
     * Биржа с хранилищем в СУБД H2 по заданному адресу (например, jdbc:h2:file:./data/exchange).
     * При повторном открытии того же адреса состояние восстанавливается:
     * ждущие заявки, номера заявок и сделок, очередь оповещений офлайн-клиентов.
     */
    public static ExchangeApi createPersistent(String jdbcUrl) {
        ExchangeStore store = new JdbcExchangeStore(jdbcUrl);
        AtomicIdSequence tradeIds = new AtomicIdSequence(store.maxTradeId());
        OrderBooks orderBooks = new OrderBooks(tradeIds, store);
        for (ExchangeStore.StoredOrder stored : store.loadWaitingOrders()) {
            orderBooks.forPair(stored.pair()).restore(stored.order());
        }
        MailboxNotificationService notifications = new MailboxNotificationService(store);
        return new Exchange(orderBooks, notifications, notifications,
                new AtomicIdSequence(store.maxOrderId()), store);
    }
}
