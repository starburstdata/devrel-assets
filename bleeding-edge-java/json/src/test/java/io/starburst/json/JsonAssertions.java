/*
 * Copyright 2022 Starburst Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.starburst.json;

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