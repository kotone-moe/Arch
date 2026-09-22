package exchange.notification;

import exchange.domain.Trade;

/** Умеет сообщить клиенту о сделке. Как именно и когда доставить, решает реализация. */
public interface TradeNotifier {

    void notifyClient(String clientId, Trade trade);
}
