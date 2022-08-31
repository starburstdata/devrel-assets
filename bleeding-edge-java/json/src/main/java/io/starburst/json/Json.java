package io.starburst.json;

import io.starburst.json.util.Cache;
import io.starburst.json.util.TypeToken;
import io.starburst.json.JsonDeserializerCollector.CollectingConsumer;
import io.starburst.json.JsonSerializer.PredicateSerializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.stream.Collectors;

import static io.starburst.json.JsonDeserializerCollector.collectingConsumer;

public interface Json
{
    static Json instance()
    {
        return builder().build();
    }

    String serializeToString(Object o);

    void serializeToWriter(Object o, Writer writer);

    <T> T deserialize(TypeToken<T> type, String json);

    <T> T deserialize(Class<T> type, String json);

    Object deserialize(Type type, String json);

    <T> T deserialize(TypeToken<T> type, Reader reader);

    <T> T deserialize(Class<T> type, Reader reader);

    Object deserialize(Type type, Reader reader);


    interface Builder
    {
        Builder addSerializer(PredicateSerializer predicateSerializer);

        Builder addDeserializer(JsonDeserializer.PredicateDeserializer predicateSerializer);

        Builder add(JsonClass jsonClass);

        Builder withoutStandard();

        Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache);

        Builder withSerializationNaming(JsonNaming naming);

        Builder withDeserializationNaming(JsonNaming naming);

        Builder withPrettyPrinting();

        Builder withPrettyPrinting(int indent);

        Json build();
    }

    static Builder builder()
    {
        return new Builder() {
            private final JsonSerializer.Builder serializerBuilder = JsonSerializer.builder();
            private final JsonDeserializer.Builder deserializerBuilder = JsonDeserializer.builder();
            private JsonParser parser = JsonParser.instance();
            private JsonPrinter printer = JsonPrinter.instance();
            private Cache<Class<?>, RecordComponent[]> recordCache = Cache.simple();
            private boolean addStandard = true;

            @Override
            public Builder addSerializer(PredicateSerializer predicateSerializer)
            {
                serializerBuilder.add(predicateSerializer);
                return this;
            }

            @Override
            public Builder addDeserializer(JsonDeserializer.PredicateDeserializer predicateSerializer)
            {
                deserializerBuilder.add(predicateSerializer);
                return this;
            }

            @Override
            public Builder add(JsonClass jsonClass)
            {
                addDeserializer(jsonClass);
                addSerializer(jsonClass);
                return this;
            }

            @Override
            public Builder withoutStandard()
            {
                addStandard = false;
                return this;
            }

            @Override
            public Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache)
            {
                this.recordCache = recordCache;
                return this;
            }

            @Override
            public Builder withSerializationNaming(JsonNaming naming)
            {
                printer = printer.withNaming(naming);
                return this;
            }

            @Override
            public Builder withDeserializationNaming(JsonNaming naming)
            {
                parser = parser.withNaming(naming);
                return this;
            }

            @Override
            public Builder withPrettyPrinting()
            {
                printer = printer.pretty();
                return this;
            }

            @Override
            public Builder withPrettyPrinting(int indent)
            {
                printer = printer.pretty(indent);
                return this;
            }

            @Override
            public Json build()
            {
                if (addStandard) {
                    serializerBuilder.addStandard();
                    deserializerBuilder.addStandard();
                }
                serializerBuilder.withAlternateRecordCache(recordCache);
                deserializerBuilder.withAlternateRecordCache(recordCache);
                return Json.build(serializerBuilder.build(), deserializerBuilder.build(), parser, printer);
            }
        };
    }

    private static Json build(JsonSerializer serializer, JsonDeserializer deserializer, JsonParser parser, JsonPrinter printer)
    {
        return new Json()
        {
            @Override
            public String serializeToString(Object o)
            {
                return serializer.serialize(o).map(printer::print).collect(Collectors.joining());
            }

            @Override
            public void serializeToWriter(Object o, Writer writer)
            {
                serializer.serialize(o).map(printer::print).forEach(charSequence -> {
                    try {
                        writer.append(charSequence);
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T deserialize(TypeToken<T> type, String json)
            {
                return (T) deserialize(type.type(), json);
            }

            @SuppressWarnings("unchecked")
            @Override
            public <T> T deserialize(TypeToken<T> type, Reader reader)
            {
                return (T) deserialize(type.type(), reader);
            }

            @Override
            public <T> T deserialize(Class<T> type, String json)
            {
                return type.cast(deserialize((Type) type, json));
            }

            @Override
            public <T> T deserialize(Class<T> type, Reader reader)
            {
                return type.cast(deserialize((Type) type, reader));
            }

            @Override
            public Object deserialize(Type type, String json)
            {
                CollectingConsumer<Object> collectingConsumer = collectingConsumer(deserializer, type);
                parser.parse(json.chars()).forEachOrdered(collectingConsumer);
                return collectingConsumer.value();
            }

            @Override
            public Object deserialize(Type type, Reader reader)
            {
                CollectingConsumer<Object> collectingConsumer = collectingConsumer(deserializer, type);
                BufferedReader bufferedReader = new BufferedReader(reader);
                parser.parse(bufferedReader.lines().flatMapToInt(String::chars)).forEachOrdered(collectingConsumer);
                return collectingConsumer.value();
            }
        };
    }
}
