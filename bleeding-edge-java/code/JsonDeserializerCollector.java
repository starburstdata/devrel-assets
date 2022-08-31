import java.lang.reflect.Type;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public interface JsonDeserializerCollector
{
    static <T> Collector<JsonToken, ?, T> deserializing(JsonDeserializer deserializer, TypeToken<T> type)
    {
        return internalDeserializing(deserializer, type.type());
    }

    static <T> Collector<JsonToken, ?, T> deserializing(JsonDeserializer deserializer, Class<T> type)
    {
        return internalDeserializing(deserializer, type);
    }

    static Collector<JsonToken, ?, Object> deserializing(JsonDeserializer deserializer, Type type)
    {
        return internalDeserializing(deserializer, type);
    }

    private static <T> Collector<JsonToken, ?, T> internalDeserializing(JsonDeserializer deserializer, Type type)
    {
        class Accumulator
        {
            JsonDeserializer.TypedDeserializer first;
            JsonDeserializer.TypedDeserializer current;
        }

        JsonDeserializer.TypedDeserializer rootTypedDeserializer = new JsonDeserializer.TypedDeserializer()
        {
            @Override
            public Object value()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public JsonDeserializer.TypedDeserializer accept(JsonToken jsonToken)
            {
                return this;
            }
        };

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
                        accumulator.current = accumulator.first = deserializer.deserializerFor(rootTypedDeserializer, type);
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
                    return (T) accumulator.first.value();
                };
            }

            @Override
            public Set<Characteristics> characteristics()
            {
                return Set.of();
            }
        };
    }
}
