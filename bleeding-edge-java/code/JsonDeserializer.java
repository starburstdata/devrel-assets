import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface JsonDeserializer
{
    static JsonDeserializer instance()
    {
        return new JsonDeserializer() {};
    }
    
    interface TypedDeserializer
    {
        TypedDeserializer accept(JsonToken t);

        Object value();
    }

    default TypedDeserializer deserializerFor(TypedDeserializer parentTypedDeserializer, Type type)
    {
        return switch (type) {
            case Class<?> clazz when clazz.equals(byte.class) || clazz.equals(Byte.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().byteValue());
            case Class<?> clazz when clazz.equals(short.class) || clazz.equals(Short.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().shortValue());
            case Class<?> clazz when clazz.equals(int.class) || clazz.equals(Integer.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().intValue());
            case Class<?> clazz when clazz.equals(long.class) || clazz.equals(Long.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().longValue());
            case Class<?> clazz when clazz.equals(float.class) || clazz.equals(Float.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().floatValue());
            case Class<?> clazz when clazz.equals(double.class) || clazz.equals(Double.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, numberToken -> numberToken.value().doubleValue());
            case Class<?> clazz when clazz.equals(boolean.class) || clazz.equals(Boolean.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.BooleanToken.class, JsonToken.BooleanToken::value);
            case Class<?> clazz when Number.class.isAssignableFrom(clazz) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.NumberToken.class, JsonToken.NumberToken::value);
            case Class<?> clazz when clazz.equals(String.class) -> simpleTypedDeserializer(parentTypedDeserializer, JsonToken.StringToken.class, JsonToken.StringToken::value);
            case ParameterizedType parameterizedType when (parameterizedType.getRawType() instanceof Class<?> clazz) && Collection.class.isAssignableFrom(clazz) -> collectionTypedDeserializer(parentTypedDeserializer, clazz, parameterizedType.getActualTypeArguments()[0]);
            case ParameterizedType parameterizedType when (parameterizedType.getRawType() instanceof Class<?> clazz) && Optional.class.isAssignableFrom(clazz) -> optionalTypedDeserializer(parentTypedDeserializer, parameterizedType.getActualTypeArguments()[0]);
            case Class<?> clazz when clazz.isRecord() -> recordTypedDeserializer(parentTypedDeserializer, clazz);
            case Class<?> clazz when clazz.isEnum() -> enumTypedDeserializer(parentTypedDeserializer, clazz);
            default -> throw new RuntimeException();
        };
    }

    default <T extends JsonToken> TypedDeserializer simpleTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<T> tokenClass, Function<T, Object> valueProvider)
    {
        return new TypedDeserializer()
        {
            private Object value;
            private boolean valueIsSet;

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                if (valueIsSet) {
                    throw new RuntimeException();
                }
                switch (jsonToken) {
                    case JsonToken.NullToken __ -> {
                        value = null;
                        valueIsSet = true;
                    }
                    case JsonToken __ when tokenClass.isAssignableFrom(jsonToken.getClass()) -> {
                        value = valueProvider.apply(tokenClass.cast(jsonToken));
                        valueIsSet = true;
                    }
                    default -> throw new RuntimeException();
                }
                return parentTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if (!valueIsSet) {
                    throw new RuntimeException();
                }
                return value;
            }
        };
    }

    default TypedDeserializer optionalTypedDeserializer(TypedDeserializer parentTypedDeserializer, Type componentType)
    {
        return new TypedDeserializer()
        {
            private final TypedDeserializer valueDeserializer = deserializerFor(parentTypedDeserializer, componentType);

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                return valueDeserializer.accept(jsonToken);
            }

            @Override
            public Object value()
            {
                return Optional.ofNullable(valueDeserializer.value());
            }
        };
    }

    default TypedDeserializer enumTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<?> enumClass)
    {
        return simpleTypedDeserializer(parentTypedDeserializer, JsonToken.StringToken.class, stringToken ->
                Stream.of(enumClass.getEnumConstants()).filter(c -> c.toString().equals(stringToken.value())).findFirst().orElseThrow(RuntimeException::new));
    }

    default TypedDeserializer collectionTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<?> collectionClass, Type componentType)
    {
        return new TypedDeserializer()
        {
            private final List<TypedDeserializer> values = new ArrayList<>();
            private boolean started;
            private boolean isDone;
            private boolean expectingValue;

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                TypedDeserializer nextTypedDeserializer = this;
                if (expectingValue) {
                    expectingValue = false;
                    switch (jsonToken) {
                        case JsonToken.EndArrayToken __ -> {
                            nextTypedDeserializer = accept(jsonToken);    // it's an empty array
                        }
                        default -> {
                            TypedDeserializer valueTypedDeserializer = deserializerFor(this, componentType);
                            values.add(valueTypedDeserializer);
                            nextTypedDeserializer = valueTypedDeserializer.accept(jsonToken);
                        }
                    }
                }
                else {
                    switch (jsonToken) {
                        case JsonToken.BeginArrayToken __ -> {
                            if (started) {
                                throw new RuntimeException();
                            }
                            started = true;
                            expectingValue = true;
                        }
                        case JsonToken.EndArrayToken __ -> {
                            if (!started || isDone) {
                                throw new RuntimeException();
                            }
                            nextTypedDeserializer = parentTypedDeserializer;
                            isDone = true;
                        }
                        case JsonToken.ValueSeparatorToken __ -> {
                            if (!started) {
                                throw new RuntimeException();
                            }
                            expectingValue = true;
                        }
                        default -> throw new RuntimeException();
                    }
                }
                return nextTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if (!isDone) {
                    throw new RuntimeException();
                }
                // get the final value from each of the stored deserializers
                Stream<Object> valueStream = values.stream().map(TypedDeserializer::value);
                // create either a set or a list depending on the collection class
                return Set.class.isAssignableFrom(collectionClass) ? valueStream.collect(Collectors.toSet()) : valueStream.toList();
            }
        };
    }

    default TypedDeserializer recordTypedDeserializer(TypedDeserializer parentTypedDeserializer, Class<?> recordClass)
    {
        RecordComponent[] recordComponents = recordClass.getRecordComponents();
        Map<String, RecordComponent> recordComponentMap = Stream.of(recordComponents).collect(Collectors.toMap(RecordComponent::getName, Function.identity()));

        return new TypedDeserializer() {
            private final Map<String, TypedDeserializer> valuesMap = new HashMap<>();
            private String currentName;
            private boolean started;
            private boolean isDone;

            @Override
            public TypedDeserializer accept(JsonToken jsonToken)
            {
                TypedDeserializer nextTypedDeserializer = this;
                switch (jsonToken) {
                    case JsonToken.BeginObjectToken __ -> {
                        if (started) {
                            throw new RuntimeException();
                        }
                        else {
                            started = true;
                        }
                    }
                    case JsonToken.ObjectNameToken(var name) -> {
                        if (!started || (currentName != null)) {
                            throw new RuntimeException();
                        }
                        currentName = name;
                        RecordComponent recordComponent = recordComponentMap.get(currentName);
                        if (recordComponent == null) {
                            throw new RuntimeException();
                        }
                        TypedDeserializer typedDeserializer = deserializerFor(this, recordComponent.getGenericType());
                        valuesMap.put(currentName, typedDeserializer);
                        nextTypedDeserializer = typedDeserializer;
                    }
                    case JsonToken.EndObjectToken __ -> {
                        if (!started || isDone) {
                            throw new RuntimeException();
                        }
                        nextTypedDeserializer = parentTypedDeserializer;
                        isDone = true;
                    }
                    case JsonToken.ValueSeparatorToken __ -> {
                        if (!started || (currentName == null)) {
                            throw new RuntimeException();
                        }
                        currentName = null;
                    }
                    default -> throw new RuntimeException();
                }
                return nextTypedDeserializer;
            }

            @Override
            public Object value()
            {
                if (!isDone) {
                    throw new RuntimeException();
                }
                Class<?>[] argumentTypes = new Class[recordComponents.length];
                Object[] arguments = new Object[recordComponents.length];
                for (int i = 0; i < recordComponents.length; ++i) {
                    RecordComponent recordComponent = recordComponents[i];
                    argumentTypes[i] = recordComponent.getType();
                    TypedDeserializer valueTypedDeserializer = valuesMap.get(recordComponent.getName());
                    if (valueTypedDeserializer == null) {
                        if (Optional.class.isAssignableFrom(recordComponent.getType())) {
                            arguments[i] = Optional.empty();
                        }
                        // otherwise leave it null
                    }
                    else {
                        arguments[i] = valueTypedDeserializer.value();
                    }
                }
                try {
                    return recordClass.getConstructor(argumentTypes).newInstance(arguments);
                }
                catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | IllegalArgumentException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
