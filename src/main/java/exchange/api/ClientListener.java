package exchange.api;

import exchange.domain.Trade;

/** Через этот интерфейс клиент получает оповещения о своих сделках. */
public interface ClientListener {

    void onTrade(Trade trade);
}
