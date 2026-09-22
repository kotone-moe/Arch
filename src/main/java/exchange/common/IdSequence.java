package exchange.common;

/** Источник уникальных номеров (для ордеров и сделок). */
public interface IdSequence {

    long next();
}
