package exchange.persistence;

import exchange.domain.CurrencyPair;
import exchange.domain.Order;
import exchange.domain.Trade;

import java.util.List;

/**
 * Долговечное хранилище состояния биржи: заявки, сделки и очередь оповещений.
 * Одно соединение с БД; все методы потокобезопасны (синхронизированы на реализации).
 * Транзакция управляется явно: begin() ... commit()/rollback() вокруг одной операции placeOrder.
 */
public interface ExchangeStore {

    /** Начинает транзакцию записи. */
    void begin();

    /** Атомарно фиксирует всё, что было записано после begin(). */
    void commit();

    /** Откатывает транзакцию после begin(). */
    void rollback();

    /** Вставляет или обновляет заявку (в том числе когда она исполнилась целиком). */
    void saveOrder(CurrencyPair pair, Order order);

    /** Сохраняет сделку в историю. */
    void saveTrade(Trade trade);

    /** Кладёт оповещение о сделке в очередь клиента (FIFO). */
    void addNotification(String clientId, long tradeId);

    /** Ожидающие (не исполненные) заявки для восстановления книг после перезапуска. */
    List<StoredOrder> loadWaitingOrders();

    /** Номер последней сохранённой заявки (0, если заявок не было). */
    long maxOrderId();

    /** Номер последней сохранённой сделки (0, если сделок не было). */
    long maxTradeId();

    /** Очередь оповещений клиента в порядке их появления. */
    List<Trade> loadNotifications(String clientId);

    /** Удаляет все сохранённые оповещения клиента (после их доставки). */
    void deleteNotifications(String clientId);

    /** Закрывает соединение с БД (штатное завершение). */
    void close();

    /** Заявка вместе с валютной парой: у самой заявки пары нет, она живёт в книге. */
    record StoredOrder(CurrencyPair pair, Order order) {
    }
}
