package exchange.core;

import exchange.api.ExchangeApi;
import exchange.common.AtomicIdSequence;
import exchange.matching.OrderBooks;
import exchange.notification.MailboxNotificationService;

/** Единственное место, где все части биржи собираются вместе. */
public final class ExchangeFactory {

    private ExchangeFactory() {
    }

    public static ExchangeApi createInMemory() {
        MailboxNotificationService notifications = new MailboxNotificationService();
        OrderBooks orderBooks = new OrderBooks(new AtomicIdSequence());
        return new Exchange(orderBooks, notifications, notifications, new AtomicIdSequence());
    }
}
