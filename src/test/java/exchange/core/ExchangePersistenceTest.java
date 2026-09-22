package exchange.core;

import exchange.api.ExchangeApi;
import exchange.api.OrderRequest;
import exchange.domain.CurrencyPair;
import exchange.domain.Side;
import exchange.support.RecordingListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static exchange.support.Decimals.assertNumberEquals;
import static exchange.support.Decimals.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сценарии завершения работы программы с хранилищем H2:
 * штатное (close) и нештатное (close не вызываем, открываем биржу заново на том же файле).
 */
class ExchangePersistenceTest {

    private static final CurrencyPair EUR_USD = new CurrencyPair("EUR", "USD");

    @TempDir
    Path tempDir;

    // --- Штатное завершение: close() -> открыть заново -------------------------------------

    @Test
    void gracefulShutdownRestoresWaitingOrders() {
        String db = dbUrl();
        try (ExchangeApi first = ExchangeFactory.createPersistent(db)) {
            first.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
            first.placeOrder(request("Masha", Side.BUY, "1.05", "50"));
        }

        try (ExchangeApi second = ExchangeFactory.createPersistent(db)) {
            RecordingListener petya = new RecordingListener();
            second.connect("Petya", petya);

            long id = second.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

            assertEquals(3, id, "Номера заявок должны продолжаться, а не начинаться заново");
            assertEquals(1, petya.receivedTrades().size());
            assertNumberEquals("1.10", petya.receivedTrades().get(0).price());
            assertNumberEquals("100", petya.receivedTrades().get(0).amount());
        }
    }

    @Test
    void gracefulShutdownRestoresPartiallyFilledOrder() {
        String db = dbUrl();
        try (ExchangeApi first = ExchangeFactory.createPersistent(db)) {
            RecordingListener petya = new RecordingListener();
            first.connect("Petya", petya);
            first.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
            first.placeOrder(request("Petya", Side.BUY, "1.10", "40"));   // остаток продавца: 60
            assertEquals(1, petya.receivedTrades().size());
        }

        try (ExchangeApi second = ExchangeFactory.createPersistent(db)) {
            RecordingListener petya = new RecordingListener();
            second.connect("Petya", petya);

            second.placeOrder(request("Petya", Side.BUY, "1.10", "60"));   // добирает остаток
            second.placeOrder(request("Petya", Side.BUY, "1.10", "1"));    // исполненной заявки больше нет

            assertEquals(1, petya.receivedTrades().size(), "Лишняя заявка не должна дать сделку");
            assertNumberEquals("60", petya.receivedTrades().get(0).amount());
        }
    }

    @Test
    void gracefulShutdownRestoresOfflineNotifications() {
        String db = dbUrl();
        try (ExchangeApi first = ExchangeFactory.createPersistent(db)) {
            RecordingListener petya = new RecordingListener();
            first.connect("Petya", petya);
            first.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));  // Вася офлайн
            first.placeOrder(request("Petya", Side.BUY, "1.10", "100"));
        }

        try (ExchangeApi second = ExchangeFactory.createPersistent(db)) {
            RecordingListener vasya = new RecordingListener();
            second.connect("Vasya", vasya);
            assertEquals(1, vasya.receivedTrades().size());
            assertNumberEquals("100", vasya.receivedTrades().get(0).amount());

            second.disconnect("Vasya");
            RecordingListener secondSession = new RecordingListener();
            second.connect("Vasya", secondSession);
            assertTrue(secondSession.receivedTrades().isEmpty(),
                    "Оповещение должно доставляться только один раз");
        }
    }

    @Test
    void orderIdsAreNotReusedAfterRestart() {
        String db = dbUrl();
        try (ExchangeApi first = ExchangeFactory.createPersistent(db)) {
            first.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));  // заявки 1 и 2
            first.placeOrder(request("Petya", Side.BUY, "1.10", "100"));   // сделка 1, обе заявки исполнены
        }

        try (ExchangeApi second = ExchangeFactory.createPersistent(db)) {
            RecordingListener masha = new RecordingListener();
            second.connect("Masha", masha);

            long sellId = second.placeOrder(request("Masha", Side.SELL, "1.10", "100"));
            long buyId = second.placeOrder(request("Oleg", Side.BUY, "1.10", "100"));

            assertEquals(3, sellId, "Исполненные заявки не должны «откатывать» номера");
            assertEquals(4, buyId);
            assertEquals(1, masha.receivedTrades().size());
            assertEquals(2, masha.receivedTrades().get(0).id(),
                    "Номера сделок не должны повторяться после рестарта");
        }
    }

    // --- Нештатное завершение: close() не вызываем, открываем биржу заново -----------------

    @Test
    void abnormalShutdownRestoresWaitingOrders() {
        String db = dbUrl();
        try (ExchangeApi crashed = ExchangeFactory.createPersistent(db)) {
            crashed.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));
            // close() не вызываем — процесс «упал»; данные уже закоммичены в БД
            try (ExchangeApi restarted = ExchangeFactory.createPersistent(db)) {
                RecordingListener petya = new RecordingListener();
                restarted.connect("Petya", petya);

                long id = restarted.placeOrder(request("Petya", Side.BUY, "1.10", "100"));

                assertEquals(2, id);
                assertEquals(1, petya.receivedTrades().size());
                assertNumberEquals("1.10", petya.receivedTrades().get(0).price());
                assertNumberEquals("100", petya.receivedTrades().get(0).amount());
            }
        }
    }

    @Test
    void abnormalShutdownRestoresOfflineNotifications() {
        String db = dbUrl();
        try (ExchangeApi crashed = ExchangeFactory.createPersistent(db)) {
            RecordingListener petya = new RecordingListener();
            crashed.connect("Petya", petya);
            crashed.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));  // Вася офлайн
            crashed.placeOrder(request("Petya", Side.BUY, "1.10", "100"));
            // «крэш» без close()
            try (ExchangeApi restarted = ExchangeFactory.createPersistent(db)) {
                RecordingListener vasya = new RecordingListener();
                restarted.connect("Vasya", vasya);
                assertEquals(1, vasya.receivedTrades().size());
                assertNumberEquals("100", vasya.receivedTrades().get(0).amount());

                restarted.disconnect("Vasya");
                RecordingListener secondSession = new RecordingListener();
                restarted.connect("Vasya", secondSession);
                assertTrue(secondSession.receivedTrades().isEmpty());
            }
        }
    }

    @Test
    void abnormalShutdownDoesNotReuseTradeIds() {
        String db = dbUrl();
        try (ExchangeApi crashed = ExchangeFactory.createPersistent(db)) {
            crashed.placeOrder(request("Vasya", Side.SELL, "1.10", "100"));  // сделка 1
            crashed.placeOrder(request("Petya", Side.BUY, "1.10", "100"));
            // «крэш» без close()
            try (ExchangeApi restarted = ExchangeFactory.createPersistent(db)) {
                RecordingListener masha = new RecordingListener();
                restarted.connect("Masha", masha);

                restarted.placeOrder(request("Masha", Side.SELL, "1.10", "100"));
                restarted.placeOrder(request("Oleg", Side.BUY, "1.10", "100"));

                assertEquals(1, masha.receivedTrades().size());
                assertEquals(2, masha.receivedTrades().get(0).id(),
                        "После крэша номер сделки не должен повторяться");
            }
        }
    }

    private String dbUrl() {
        return "jdbc:h2:file:" + tempDir.resolve("exchange").toString().replace('\\', '/');
    }

    private static OrderRequest request(String clientId, Side side, String price, String amount) {
        return new OrderRequest(clientId, EUR_USD, side, d(price), d(amount));
    }
}
