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

import io.starburst.json.JsonToken.NumberToken;
import io.starburst.json.JsonToken.StringToken;
import io.starburst.json.models.CustomInstant;
import io.starburst.json.util.TypeToken;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestComplexWithCustom
{
    public static class Hey
    {
        final BigInteger i;

        public Hey(BigInteger i)
        {
            this.i = i;
        }

        @Override
        public String toString()
        {
            return "i=" + i;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {return true;}
            if (o == null || getClass() != o.getClass()) {return false;}
            Hey hey = (Hey) o;
            return i.equals(hey.i);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(i);
        }
    }
    public record Foo(short s, int i, long l, CustomInstant what, Hey hey) {}
    public record Bar(CustomInstant theTime, boolean myFlag, String theName, int i, List<String> l, Foo fooFoo) {}

    public record Simple(int i, String s, What what) {}

    public record Empty(){}

    public record Container(List<Bar> bars, Optional<Simple> simple, Hey hey, SomethingInteresting<Simple> si) {}

    public static class SomethingInteresting<T>
    {
        private final T thing;
        private final int count;

        public SomethingInteresting(T thing, int count)
        {
            this.thing = thing;
            this.count = count;
        }

        public T thing()
        {
            return thing;
        }

        public int count()
        {
            return count;
        }

        @Override
        public String toString()
        {
            return "thing=" + thing + ",count=" + count;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {return true;}
            if (o == null || getClass() != o.getClass()) {return false;}
            SomethingInteresting<?> that = (SomethingInteresting<?>) o;
            return count == that.count && thing.equals(that.thing);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(thing, count);
        }
    }

    public enum What
    {
        HERE,
        WE,
        GO
    }

    @Test
    public void testComplex()
    {
        Container container = buildCustom();

        Json json = buildJsonController();

        String jsonStr = json.serializeToString(container);
        Container deserialized = json.deserialize(Container.class, jsonStr);

        assertEquals(container, deserialized);
    }

    @Test
    public void testComplexWithTree()
    {
        Container container = buildCustom();
        Json json = buildJsonController();
        String jsonStr = json.serializeToString(container);
        JsonValue<?> jsonValue = json.deserialize(JsonValue.class, jsonStr);
        String jsonValueJson = json.serializeToString(jsonValue);
        Container deserialized = json.deserialize(Container.class, jsonValueJson);
        assertEquals(container, deserialized);
    }

    private Json buildJsonController()
    {
        TypeToken<SomethingInteresting<Simple>> somethingInterestingType = new TypeToken<>() {};
        JsonClass jsonClass = JsonClass.<SomethingInteresting<Simple>>builder(somethingInterestingType)
                .addTypeVariableField("thing", SomethingInteresting::thing, Simple.class)
                .addField("count", SomethingInteresting::count, int.class)
                .build();

        JsonClass instantJsonClass = JsonClass.forSimple(CustomInstant.class,
                StringToken.class,
                token -> CustomInstant.parse(token.value()),
                (__, instant) -> new StringToken(instant.toString()));
        JsonClass heyJsonClass = JsonClass.forSimple(Hey.class,
                NumberToken.class,
                token -> new Hey(BigInteger.valueOf(token.value().longValue())),
                (__, hey) -> new NumberToken(hey.i));

        return Json.builder()
                .withPrettyPrinting()
                .withSerializationNaming(JsonNaming.SNAKE_CASE)
                .withDeserializationNaming(JsonNaming.CAMEL_CASE)
                .add(JsonValue.instance())
                .add(instantJsonClass)
                .add(heyJsonClass)
                .add(jsonClass)
                .build();
    }

    private static Container buildCustom()
    {
        Foo foo = new Foo((short) 1, 2, 3, new CustomInstant(Instant.EPOCH), new Hey(BigInteger.TWO));
        Bar bar1 = new Bar(new CustomInstant(Instant.now()), true, "foobar", 12345, List.of("one", "two", "three"), foo);
        Bar bar2 = new Bar(new CustomInstant(Instant.now().minusSeconds(100)), false, "bar2", -104835, List.of(), foo);
        List<Bar> l = List.of(bar1, bar2);
        return new Container(l, Optional.of(new Simple(10, "ten", What.GO)), new Hey(BigInteger.ONE), new SomethingInteresting<>(new Simple(345, "678", What.HERE), 93940));
    }
}