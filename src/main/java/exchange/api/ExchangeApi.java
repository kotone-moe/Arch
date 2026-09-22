package exchange.api;

/** Всё, что клиент может делать с биржей. Клиент знает только этот интерфейс. */
public interface ExchangeApi {

    /** Клиент выходит в сеть; все накопленные оповещения будут доставлены сразу. */
    void connect(String clientId, ClientListener listener);

    /** Клиент уходит из сети; оповещения для него начнут копиться. */
    void disconnect(String clientId);

    /** Ставит ордер на биржу и возвращает его номер. */
    long placeOrder(OrderRequest request);
}
