package io.starburst.json;

import io.starburst.json.JsonToken;

import java.util.Iterator;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public interface JsonAssertions
{
    @SuppressWarnings("unchecked")
    static <T extends JsonToken> T assertToken(Class<T> clazz, Iterator<JsonToken> iterator)
    {
        assertTrue(iterator.hasNext());
        JsonToken token = iterator.next();
        assertEquals(clazz, token.getClass(), "Expected %s but received %s".formatted(clazz.getSimpleName(), token.getClass().getSimpleName()));
        return (T) token;
    }

    static void assertNoToken(Iterator<JsonToken> iterator)
    {
        assertFalse(iterator.hasNext());
    }

    @SuppressWarnings("unchecked")
    static <T extends JsonToken> void assertToken(Class<T> clazz, Iterator<JsonToken> iterator, Predicate<T> predicate)
    {
        assertTrue(iterator.hasNext());
        JsonToken token = iterator.next();
        assertEquals(clazz, token.getClass(), "Expected %s but received %s".formatted(clazz.getSimpleName(), token.getClass().getSimpleName()));
        assertTrue(predicate.test((T)token));
    }
}