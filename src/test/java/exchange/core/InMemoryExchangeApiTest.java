package exchange.core;

import exchange.api.ExchangeApi;

/** Поведенческие сценарии на обычной (in-process) бирже. */
class InMemoryExchangeApiTest extends AbstractExchangeApiTest {

    @Override
    protected ExchangeApi createExchange() {
        return ExchangeFactory.createInMemory();
    }
}
