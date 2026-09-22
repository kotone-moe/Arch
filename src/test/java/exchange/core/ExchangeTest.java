package exchange.core;

import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.domain.CurrencyPair;
import exchange.domain.Side;
import exchange.domain.Trade;
import exchange.support.RecordingListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static exchange.support.Decimals.assertNumberEquals;
import static exchange.support.Decimals.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExchangeTest {

    private static final CurrencyPair EUR_USD = new CurrencyPair("EUR", "USD");

    private ExchangeApi exchange;
    private RecordingListener vasya;
    private RecordingListener petya;

    @BeforeEach
    void setUp() {
        exchange = ExchangeFactory.createInMemory();
        vasya = new RecordingListener();
        petya = new RecordingListener();
    }

    @Test
    void bothClientsAreNotifiedAboutTheTrade() {
        exchange.connect("Vasya", vasya);
        exchange.connect("Petya", petya);

        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

        assertEquals(1, vasya.receivedTrades().size());
        assertEquals(1, petya.receivedTrades().size());
        assertEquals(vasya.receivedTrades().get(0), petya.receivedTrades().get(0));
    }

    @Test
    void clientWithoutTradesGetsNothing() {
        RecordingListener masha = new RecordingListener();
        exchange.connect("Vasya", vasya);
        exchange.connect("Petya", petya);
        exchange.connect("Masha", masha);

        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

        assertTrue(masha.receivedTrades().isEmpty());
    }

    @Test
    void offlineClientReceivesNotificationAfterConnecting() {
        exchange.connect("Petya", petya);
        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));   // Вася не в сети

        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "100"));
        exchange.connect("Vasya", vasya);

        assertEquals(1, vasya.receivedTrades().size());
        assertNumberEquals("100", vasya.receivedTrades().get(0).amount());
    }

    @Test
    void clientWhoWentOfflineReceivesTradesInTheOrderTheyHappened() {
        exchange.connect("Vasya", vasya);
        exchange.connect("Petya", petya);
        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
        exchange.disconnect("Vasya");

        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "10"));
        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "20"));
        RecordingListener vasyaAfterReturn = new RecordingListener();
        exchange.connect("Vasya", vasyaAfterReturn);

        List<Trade> trades = vasyaAfterReturn.receivedTrades();
        assertEquals(2, trades.size());
        assertNumberEquals("10", trades.get(0).amount());
        assertNumberEquals("20", trades.get(1).amount());
    }

    @Test
    void storedNotificationsAreDeliveredOnlyOnce() {
        exchange.connect("Petya", petya);
        exchange.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
        exchange.placeOrder(request("Petya", Side.BUY, "1.10", "100"));
        exchange.connect("Vasya", vasya);

        exchange.disconnect("Vasya");
        RecordingListener secondSession = new RecordingListener();
        exchange.connect("Vasya", secondSession);

        assertEquals(1, vasya.receivedTrades().size());
        assertTrue(secondSession.receivedTrades().isEmpty());
    }

    @Test
    void orderRequestWithNonPositivePriceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> request("Vasya", Side.BUY, "0", "10"));
    }

    @Test
    void orderRequestWithNonPositiveAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> request("Vasya", Side.BUY, "1.10", "-5"));
    }

    private static OrderRequest request(String clientId, Side side, String price, String amount) {
        return new OrderRequest(clientId, EUR_USD, side, d(price), d(amount));
    }
}
