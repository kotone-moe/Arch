package exchange.common;

import java.util.concurrent.atomic.AtomicLong;

/** Потокобезопасный счётчик: два потока никогда не получат один и тот же номер. */
public final class AtomicIdSequence implements IdSequence {

    private final AtomicLong lastId = new AtomicLong();

    @Override
    public long next() {
        return lastId.incrementAndGet();
    }
}
