package io.starburst.json;

import io.starburst.json.util.StreamUtil;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public sealed interface JsonValue<T>
        permits JsonToken.NumberToken, JsonToken.StringToken, JsonToken.BooleanToken, JsonToken.NullToken, JsonValue.JsonObject, JsonValue.JsonArray
{
    T value();

    record JsonObject(Map<String, ? extends JsonValue<?>> value)
            implements JsonValue<Map<String, ? extends JsonValue<?>>> {}

    record JsonArray(List<? extends JsonValue<?>> value)
            implements JsonValue<List<? extends JsonValue<?>>> {}

    static JsonClass instance()
    {
        return new JsonClass() {
            @Override
            public String toString()
            {
                return "JsonClass-instance";
            }

            @Override
            public Optional<JsonDeserializer.TypedDeserializer> maybeDeserialize(JsonDeserializer rootDeserializer, JsonDeserializer.TypedDeserializer parentTypedDeserializer, Type type)
            {
                return switch (type) {
                    case Class<?> clazz when JsonValue.class.isAssignableFrom(clazz) -> Optional.of(deserializeJsonValue(rootDeserializer, parentTypedDeserializer));
                    default -> Optional.empty();
                };
            }

            @Override
            public Optional<Stream<JsonToken>> maybeSerialize(JsonSerializer rootSerializer, Object o)
            {
                return switch (o) {
                    case JsonValue<?> jsonValue -> Optional.of(StreamUtil.lazyStream(() -> serializeJsonValue(jsonValue)));
                    case null -> Optional.empty();
                    default -> Optional.empty();
                };
            }
        };
    }

    private static Stream<JsonToken> serializeJsonValue(JsonValue<?> jsonValue)
    {
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        switch (jsonValue) {
            case JsonObject jsonObject -> {
                builder.accept(Stream.of(JsonToken.BeginObjectToken.INSTANCE));
                boolean first = true;
                for (Map.Entry<String, ? extends JsonValue<?>> entry : jsonObject.value.entrySet()) {
                    if (first) {
                        first = false;
                    }
                    else {
                        builder.accept(Stream.of(JsonToken.ValueSeparatorToken.INSTANCE));
                    }
                    builder.accept(Stream.of(new JsonToken.ObjectNameToken(entry.getKey())));
                    builder.accept(StreamUtil.lazyStream(() -> serializeJsonValue(entry.getValue())));
                }
                builder.accept(Stream.of(JsonToken.EndObjectToken.INSTANCE));
            }
            case JsonArray jsonArray -> {
                builder.accept(Stream.of(JsonToken.BeginArrayToken.INSTANCE));
                boolean first = true;
                for (JsonValue<?> value : jsonArray.value()) {
                    if (first) {
                        first = false;
                    }
                    else {
                        builder.accept(Stream.of(JsonToken.ValueSeparatorToken.INSTANCE));
                    }
                    builder.accept(StreamUtil.lazyStream(() -> serializeJsonValue(value)));
                }
                builder.accept(Stream.of(JsonToken.EndArrayToken.INSTANCE));
            }
            case JsonToken.NumberToken token -> builder.accept(Stream.of(token));
            case JsonToken.StringToken token -> builder.accept(Stream.of(token));
            case JsonToken.BooleanToken token -> builder.accept(Stream.of(token));
            case JsonToken.NullToken token -> builder.accept(Stream.of(token));
        }
        return builder.build().flatMap(Function.identity());
    }

    private static JsonDeserializer.TypedDeserializer deserializeJsonValue(JsonDeserializer rootDeserializer, JsonDeserializer.TypedDeserializer parentTypedDeserializer)
    {
        return new JsonDeserializer.TypedDeserializer() {
            private Supplier<JsonValue<?>> rootValueSupplier;
            private JsonValue<?> rootValue;

            @Override
            public String toString()
            {
                return "deserializeJsonValue";
            }

            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public JsonDeserializer.TypedDeserializer accept(JsonToken jsonToken)
            {
                JsonDeserializer.TypedDeserializer nextTypedDeserializer = parentTypedDeserializer;
                switch (jsonToken) {
                    case JsonToken.StringToken stringToken -> rootValue = stringToken;
                    case JsonToken.NumberToken numberToken -> rootValue = numberToken;
                    case JsonToken.BooleanToken booleanToken -> rootValue = booleanToken;
                    case JsonToken.NullToken nullToken -> rootValue = nullToken;
                    case JsonToken.BeginArrayToken __ -> {
                        JsonDeserializer.TypedDeserializer collectorTypedDeserializer = JsonDeserializer.collectionTypedDeserializer(rootDeserializer, parentTypedDeserializer, List.class, JsonValue.class);
                        nextTypedDeserializer = collectorTypedDeserializer.accept(jsonToken);
                        rootValueSupplier = () -> new JsonArray((List<? extends JsonValue<?>>) collectorTypedDeserializer.value());
                    }
                    case JsonToken.BeginObjectToken __ -> {
                        Function<Map<String, Object>, Object> builder = map -> new JsonObject((Map)map);
                        JsonDeserializer.TypedDeserializer objectTypedDeserializer = JsonDeserializer.objectTypedDeserializer(rootDeserializer, parentTypedDeserializer, ___ -> JsonValue.class, builder);
                        nextTypedDeserializer = objectTypedDeserializer.accept(jsonToken);
                        rootValueSupplier = () -> (JsonObject) objectTypedDeserializer.value();
                    }
                    default -> throw new RuntimeException();    // TODO
                }
                return nextTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if ((rootValueSupplier == null) && (rootValue == null)) {
                    throw new RuntimeException();   // TODO
                }
                if (rootValue != null) {
                    return rootValue;
                }
                return rootValueSupplier.get();
            }
        };
    }
}
