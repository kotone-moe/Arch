package exchange.matching;

import exchange.common.AtomicIdSequence;
import exchange.domain.CurrencyPair;
import exchange.domain.Order;
import exchange.domain.Side;
import exchange.domain.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static exchange.support.Decimals.assertNumberEquals;
import static exchange.support.Decimals.d;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderBookTest {

    private static final CurrencyPair EUR_USD = new CurrencyPair("EUR", "USD");

    private OrderBook book;
    private long lastOrderId;

    @BeforeEach
    void setUp() {
        book = new OrderBook(EUR_USD, new AtomicIdSequence());
        lastOrderId = 0;
    }

    @Test
    void buyerAndSellerWithSamePriceMakeOneTrade() {
        book.place(sell("Vasya", "1.10", "100"));

        List<Trade> trades = book.place(buy("Petya", "1.10", "100"));

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals("Petya", trade.buyerId());
        assertEquals("Vasya", trade.sellerId());
        assertNumberEquals("1.10", trade.price());
        assertNumberEquals("100", trade.amount());
    }

    @Test
    void tradeIsMadeAtThePriceOfTheWaitingOrder() {
        book.place(sell("Vasya", "1.08", "10"));

        List<Trade> trades = book.place(buy("Petya", "1.10", "10"));

        assertNumberEquals("1.08", trades.get(0).price());
    }

    @Test
    void partiallyFilledOrderKeepsWaitingForTheRest() {
        book.place(sell("Vasya", "1.10", "100"));

        List<Trade> firstTrades = book.place(buy("Petya", "1.10", "40"));
        List<Trade> secondTrades = book.place(buy("Masha", "1.10", "60"));
        List<Trade> thirdTrades = book.place(buy("Kolya", "1.10", "1"));

        assertNumberEquals("40", firstTrades.get(0).amount());
        assertNumberEquals("60", secondTrades.get(0).amount());
        assertTrue(thirdTrades.isEmpty(), "Продавец уже всё продал");
    }

    @Test
    void ordersWithoutPriceOverlapDoNotTrade() {
        book.place(sell("Vasya", "1.10", "10"));

        List<Trade> trades = book.place(buy("Petya", "1.05", "10"));

        assertTrue(trades.isEmpty());
    }

    @Test
    void bigOrderIsFilledByAFewWaitingOrders() {
        book.place(sell("Vasya", "1.10", "30"));
        book.place(sell("Masha", "1.11", "30"));

        List<Trade> trades = book.place(buy("Petya", "1.11", "50"));

        assertEquals(2, trades.size());
        assertEquals("Vasya", trades.get(0).sellerId());
        assertNumberEquals("30", trades.get(0).amount());
        assertEquals("Masha", trades.get(1).sellerId());
        assertNumberEquals("20", trades.get(1).amount());
    }

    @Test
    void cheaperSellerIsServedFirst() {
        book.place(sell("Expensive", "1.12", "10"));
        book.place(sell("Cheap", "1.09", "10"));

        List<Trade> trades = book.place(buy("Petya", "1.20", "10"));

        assertEquals("Cheap", trades.get(0).sellerId());
        assertNumberEquals("1.09", trades.get(0).price());
    }

    @Test
    void sellerWhoCameEarlierIsServedFirstAtTheSamePrice() {
        book.place(sell("First", "1.10", "10"));
        book.place(sell("Second", "1.10", "10"));

        List<Trade> trades = book.place(buy("Petya", "1.10", "10"));

        assertEquals("First", trades.get(0).sellerId());
    }

    @Test
    void sellOrderIsMatchedAgainstWaitingBuyer() {
        book.place(buy("Petya", "1.10", "10"));

        List<Trade> trades = book.place(sell("Vasya", "1.08", "10"));

        assertEquals("Petya", trades.get(0).buyerId());
        assertEquals("Vasya", trades.get(0).sellerId());
        assertNumberEquals("1.10", trades.get(0).price());
    }

    private Order buy(String clientId, String price, String amount) {
        return new Order(++lastOrderId, clientId, Side.BUY, d(price), d(amount));
    }

    private Order sell(String clientId, String price, String amount) {
        return new Order(++lastOrderId, clientId, Side.SELL, d(price), d(amount));
    }
}
