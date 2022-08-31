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

import io.starburst.json.JsonDeserializer.TypedDeserializer;
import io.starburst.json.util.TypeToken;

import java.lang.reflect.Type;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public interface JsonDeserializerCollector
{
    interface CollectingConsumer<T>
            extends Consumer<JsonToken>
    {
        T value();
    }

    static <T> CollectingConsumer<T> collectingConsumer(JsonDeserializer deserializer, TypeToken<T> type)
    {
        return internalCollectingConsumer(deserializer, type.type());
    }

    static <T> CollectingConsumer<T> collectingConsumer(JsonDeserializer deserializer, Class<T> type)
    {
        return internalCollectingConsumer(deserializer, type);
    }

    static <T> Collector<JsonToken, ?, T> deserializing(JsonDeserializer deserializer, TypeToken<T> type)
    {
        return internalDeserializing(deserializer, type.type());
    }

    static <T> Collector<JsonToken, ?, T> deserializing(JsonDeserializer deserializer, Class<T> type)
    {
        return internalDeserializing(deserializer, type);
    }

    private static <T> CollectingConsumer<T> internalCollectingConsumer(JsonDeserializer deserializer, Type type)
    {
        return new CollectingConsumer<>()
        {
            private TypedDeserializer first;
            private TypedDeserializer current;

            @SuppressWarnings("unchecked")
            @Override
            public T value()
            {
                return (T) first.value();
            }

            @Override
            public void accept(JsonToken jsonToken)
            {
                if (first == null) {
                    current = first = deserializer.deserializerFor(makeRootDeserializer(), type);
                }
                current = current.accept(jsonToken);
            }
        };
    }

    private static <T> Collector<JsonToken, ?, T> internalDeserializing(JsonDeserializer deserializer, Type type)
    {
        class Accumulator
        {
            TypedDeserializer first;
            TypedDeserializer current;
        }

        return new Collector<JsonToken, Accumulator, T>() {
            @Override
            public Supplier<Accumulator> supplier()
            {
                return Accumulator::new;
            }

            @Override
            public BiConsumer<Accumulator, JsonToken> accumulator()
            {
                return (accumulator, jsonToken) -> {
                    if (accumulator.first == null) {
                        accumulator.current = accumulator.first = deserializer.deserializerFor(makeRootDeserializer(), type);
                    }
                    accumulator.current = accumulator.current.accept(jsonToken);
                };
            }

            @Override
            public BinaryOperator<Accumulator> combiner()
            {
                return (accumulator, context2) -> {
                    /*
                        Caution to future library writers. Decisions you make early on that seem good at
                        the time will have far-reaching consequences for your users and your ability
                        to enhance your library in the future.

                        When the Java streams library was being developed it was determined that it would
                        be useful to have parallel streams as well as serial streams. This decision, by
                        itself, was not bad. However, the decision to burden the entire API with having
                        to support both parallel and serial streams was bad. In Java streams users have the
                        option to make _any_ stream run in parallel. Further, the operations on parallel
                        streams are exactly the same as serial streams. This decision has hampered the
                        utility of the Java streams library. Common functional idioms such as "fold"
                        are impossible to do with the current APIs unless you cheat as we have below.

                        This combiner() method has no utility when doing a fold reduction. There's no
                        way to make sense of combining two contexts mid-fold. It turns out, however,
                        that every version of the JDK streams has only called the combiner for
                        parallel streams. So, we take the decision to disallow parallel streams by
                        disallowing the combiner to run.

                        For safety reasons, you should probably prefer collectingConsumer() above
                        though it's not as convenient to use.
                     */
                    throw new UnsupportedOperationException();
                };
            }

            @SuppressWarnings("unchecked")
            @Override
            public Function<Accumulator, T> finisher()
            {
                return accumulator -> {
                    if (accumulator.first == null) {
                        throw new RuntimeException();
                    }
                    return (T)accumulator.first.value();
                };
            }

            @Override
            public Set<Characteristics> characteristics()
            {
                return Set.of();
            }
        };
    }

    private static TypedDeserializer makeRootDeserializer()
    {
        return new TypedDeserializer()
        {
            @Override
            public String toString()
            {
                return "rootTypedDeserializer";
            }

            @Override
            public Object value()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                return this;
            }
        };
    }
}
