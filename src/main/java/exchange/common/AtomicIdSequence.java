package exchange.common;

import java.util.concurrent.atomic.AtomicLong;

/** Потокобезопасный счётчик: два потока никогда не получат один и тот же номер. */
public final class AtomicIdSequence implements IdSequence {

    private final AtomicLong lastId;

    public AtomicIdSequence() {
        this(0);
    }

    /** Продолжает нумерацию с указанного значения: next() вернёт lastId + 1. */
    public AtomicIdSequence(long lastId) {
        this.lastId = new AtomicLong(lastId);
    }

    @Override
    public long next() {
        return lastId.incrementAndGet();
    }
}
