package exchange.support;

import exchange.api.ClientListener;
import exchange.domain.Trade;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Тестовый «клиент»: просто запоминает все оповещения, которые ему пришли. */
public final class RecordingListener implements ClientListener {

    private final ConcurrentLinkedQueue<Trade> receivedTrades = new ConcurrentLinkedQueue<>();

    @Override
    public void onTrade(Trade trade) {
        receivedTrades.add(trade);
    }

    public List<Trade> receivedTrades() {
        return new ArrayList<>(receivedTrades);
    }
}
