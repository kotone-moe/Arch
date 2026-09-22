package exchange.notification;

import exchange.api.ClientListener;

/** Учёт того, какие клиенты сейчас в сети. */
public interface ClientConnections {

    void connect(String clientId, ClientListener listener);

    void disconnect(String clientId);
}
