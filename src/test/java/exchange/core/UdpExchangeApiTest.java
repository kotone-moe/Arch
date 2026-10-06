package exchange.core;

import exchange.api.ExchangeApi;
import exchange.network.UdpExchangeClient;
import exchange.network.UdpExchangeServer;

import java.net.SocketException;

/**
 * Те же поведенческие сценарии, но клиент ходит на биржу по UDP (loopback):
 * сервер поднимается на свободном порту, запросы и оповещения летят датаграммами.
 */
class UdpExchangeApiTest extends AbstractExchangeApiTest {

    private UdpExchangeServer server;

    @Override
    protected ExchangeApi createExchange() {
        try {
            server = new UdpExchangeServer(0, ExchangeFactory.createInMemory());
            return new UdpExchangeClient("127.0.0.1", server.port());
        } catch (SocketException e) {
            throw new IllegalStateException("Не удалось запустить UDP-сервер", e);
        }
    }

    @Override
    void tearDown() {
        super.tearDown();          // сначала закрываем клиент (поток приёма + сокет)
        server.close();            // затем гасим сервер
    }
}
