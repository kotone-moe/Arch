package exchange.core;

import exchange.api.ExchangeApi;
import exchange.common.AtomicIdSequence;
import exchange.matching.OrderBooks;
import exchange.metrics.ExchangeMetrics;
import exchange.metrics.PrometheusExchangeMetrics;
import exchange.notification.MailboxNotificationService;
import exchange.persistence.ExchangeStore;
import exchange.persistence.JdbcExchangeStore;

import java.util.Objects;

/** Единственное место, где все части биржи собираются вместе. */
public final class ExchangeFactory {

    private ExchangeFactory() {
    }

    /** Биржа, которая живёт только в оперативной памяти (данные теряются при завершении). */
    public static ExchangeApi createInMemory() {
        return createInMemory(new PrometheusExchangeMetrics());
    }

    /** То же самое, но метрики пишутся в переданный обработчик (его можно прочитать в тестах). */
    public static ExchangeApi createInMemory(ExchangeMetrics metrics) {
        MailboxNotificationService notifications =
                new MailboxNotificationService(null, metrics);
        OrderBooks orderBooks = new OrderBooks(new AtomicIdSequence());
        return new Exchange(orderBooks, notifications, notifications,
                new AtomicIdSequence(), metrics);
    }

    /**
     * Биржа с хранилищем в СУБД H2 по заданному адресу (например, jdbc:h2:file:./data/exchange).
     * При повторном открытии того же адреса состояние восстанавливается:
     * ждущие заявки, номера заявок и сделок, очередь оповещений офлайн-клиентов.
     */
    public static ExchangeApi createPersistent(String jdbcUrl) {
        return createPersistent(jdbcUrl, new PrometheusExchangeMetrics());
    }

    /** То же самое, но метрики пишутся в переданный обработчик (его можно прочитать в тестах). */
    public static ExchangeApi createPersistent(String jdbcUrl, ExchangeMetrics metrics) {
        Objects.requireNonNull(metrics, "Метрики не заданы");
        ExchangeStore store = new JdbcExchangeStore(jdbcUrl);
        AtomicIdSequence tradeIds = new AtomicIdSequence(store.maxTradeId());
        OrderBooks orderBooks = new OrderBooks(tradeIds, store);
        for (ExchangeStore.StoredOrder stored : store.loadWaitingOrders()) {
            orderBooks.forPair(stored.pair()).restore(stored.order());
        }
        MailboxNotificationService notifications =
                new MailboxNotificationService(store, metrics);
        return new Exchange(orderBooks, notifications, notifications,
                new AtomicIdSequence(store.maxOrderId()), metrics, store);
    }
}
