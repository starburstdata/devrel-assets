package io.starburst.json;

import io.starburst.json.util.Cache;
import io.starburst.json.util.StreamUtil;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public interface JsonSerializer
{
    static JsonSerializer instance()
    {
        return builder().addStandard().build();
    }

    Stream<JsonToken> serialize(Object o);

    @FunctionalInterface
    interface PredicateSerializer
    {
        Optional<Stream<JsonToken>> maybeSerialize(JsonSerializer rootSerializer, Object o);
    }

    interface Builder
    {
        Builder add(PredicateSerializer predicateSerializer);

        Builder addStandard();

        Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache);

        JsonSerializer build();
    }

    static Builder builder()
    {
        return new Builder()
        {
            private final List<PredicateSerializer> predicateSerializers = new ArrayList<>();
            private boolean addStandard = false;
            private Cache<Class<?>, RecordComponent[]> recordCache = Cache.simple();

            @Override
            public Builder add(PredicateSerializer predicateSerializer)
            {
                predicateSerializers.add(predicateSerializer);
                return this;
            }

            @Override
            public Builder addStandard()
            {
                addStandard = true;
                return this;
            }

            @Override
            public Builder withAlternateRecordCache(Cache<Class<?>, RecordComponent[]> recordCache)
            {
                this.recordCache = recordCache;
                return this;
            }

            @Override
            public JsonSerializer build()
            {
                List<PredicateSerializer> work = new ArrayList<>(predicateSerializers);
                if (addStandard) {
                    work.add((rootSerializer, o) -> serializeStandard(rootSerializer, o, recordCache));
                }
                return buildSerializer(Collections.unmodifiableList(work));
            }
        };
    }

    static Optional<Stream<JsonToken>> serializeStandard(JsonSerializer rootSerializer, Object o, Cache<Class<?>, RecordComponent[]> recordCache)
    {
        Stream<JsonToken> tokenStream = switch (o) {
            case null -> Stream.of(new JsonToken.NullToken());
            case String str -> Stream.of(new JsonToken.StringToken(str));
            case Number n -> Stream.of(new JsonToken.NumberToken(n));
            case Boolean b -> Stream.of(new JsonToken.BooleanToken(b));
            case Enum<?> e -> Stream.of(new JsonToken.StringToken(e.name()));
            case Optional<?> optional -> StreamUtil.lazyStream(() -> rootSerializer.serialize(optional.orElse(null)));
            case Collection<?> collection -> serializeCollection(rootSerializer, collection);
            case Object ignore when o.getClass().isArray() -> serializeArray(rootSerializer, o);
            case Object ignore when o.getClass().isRecord() -> serializeRecord(rootSerializer, o, recordCache);
            default -> null;
        };
        return Optional.ofNullable(tokenStream);
    }

    static Stream<JsonToken> serializeArray(JsonSerializer rootSerializer, Object array)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        builder.accept(Stream.of(JsonToken.BeginArrayToken.INSTANCE));
        boolean first = true;
        int length = Array.getLength(array);
        for (int i = 0; i < length; ++i) {
            if (i > 0) {
                builder.accept(Stream.of(JsonToken.ValueSeparatorToken.INSTANCE));
            }
            int index = i;
            builder.accept(StreamUtil.lazyStream(() -> rootSerializer.serialize(Array.get(array, index))));
        }
        builder.accept(Stream.of(JsonToken.EndArrayToken.INSTANCE));
        return builder.build().flatMap(Function.identity());
    }

    static Stream<JsonToken> serializeCollection(JsonSerializer rootSerializer, Collection<?> collection)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        builder.accept(Stream.of(new JsonToken.BeginArrayToken())); // we have to wrap the token in a stream as we want a stream of streams
        boolean first = true;
        for (Object value : collection) {
            if (first) {
                first = false;
            }
            else {
                builder.accept(Stream.of(new JsonToken.ValueSeparatorToken()));    // again, wrap the token in a stream
            }
            builder.accept(StreamUtil.lazyStream(() -> rootSerializer.serialize(value)));   // recursively serialize each value
        }
        builder.accept(Stream.of(new JsonToken.EndArrayToken()));   // again, wrap the token in a stream
        return builder.build().flatMap(Function.identity());    // flatten stream of streams into stream of tokens
    }

    record ObjectField(String name, Object value) {}

    static Stream<JsonToken> serializeObject(JsonSerializer rootSerializer, Collection<ObjectField> objectFields)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        builder.accept(Stream.of(JsonToken.BeginObjectToken.INSTANCE));
        boolean first = true;
        for (ObjectField objectField : objectFields) {
            if (first) {
                first = false;
            }
            else {
                builder.accept(Stream.of(JsonToken.ValueSeparatorToken.INSTANCE));
            }
            builder.accept(Stream.of(new JsonToken.ObjectNameToken(objectField.name())));
            builder.accept(rootSerializer.serialize(objectField.value()));
        }
        builder.accept(Stream.of(JsonToken.EndObjectToken.INSTANCE));
        return builder.build().flatMap(Function.identity());
    }

    static Stream<JsonToken> serializeRecord(JsonSerializer rootSerializer, Object record, Cache<Class<?>, RecordComponent[]> recordCache)
    {
        RecordComponent[] recordComponents = recordCache.computeIfAbsent(record.getClass(), Class::getRecordComponents);   // Java records include a complete specification of the record's components
        List<ObjectField> objectFields = Arrays.stream(recordComponents)
                .map(recordComponent -> {
                    try {
                        Object o = recordComponent.getAccessor().invoke(record);
                        return new ObjectField(recordComponent.getName(), o);
                    }
                    catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);  // TODO
                    }
                })
                .toList();
        return serializeObject(rootSerializer, objectFields);
    }

    private static JsonSerializer buildSerializer(List<PredicateSerializer> predicateSerializers)
    {
        return new JsonSerializer()
        {
            @Override
            public Stream<JsonToken> serialize(Object o)
            {
                return predicateSerializers.stream()
                        .flatMap(predicateSerializer -> predicateSerializer.maybeSerialize(this, o).stream())
                        .findFirst()
                        .orElseThrow(RuntimeException::new); // TODO
            }
        };
    }
}
