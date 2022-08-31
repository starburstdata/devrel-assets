package io.starburst.json.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@FunctionalInterface
public interface Cache<K, V>
{
    V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

    static <K, V> Cache<K, V> noCache()
    {
        return (key, mappingFunction) -> mappingFunction.apply(key);
    }

    static <K, V> Cache<K, V> simple()
    {
        Map<K, V> cache = new ConcurrentHashMap<>();
        return cache::computeIfAbsent;
    }
}