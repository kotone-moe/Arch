package exchange.metrics;

/**
 * Метрики биржи: количество обработанных заявок и доставленных оповещений.
 * Единственный способ сообщить о событии — вызвать метод; как именно считать
 * (Prometheus, печать в консоль), решает реализация.
 */
public interface ExchangeMetrics {

    /** Обработана одна заявка клиента (placeOrder завершился успешно). */
    void orderProcessed();

    /** Доставлено одно оповещение клиенту (onTrade вызван). */
    void notificationDelivered();
}
