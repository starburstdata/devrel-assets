import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface JsonSerializer
{
    static JsonSerializer instance()
    {
        return new JsonSerializer() {};
    }

    default Stream<JsonToken> serialize(Object o)
    {
        return switch (o) {
            case null -> Stream.of(new JsonToken.NullToken());
            case String str -> Stream.of(new JsonToken.StringToken(str));
            case Number n -> Stream.of(new JsonToken.NumberToken(n));
            case Boolean b -> Stream.of(new JsonToken.BooleanToken(b));
            case Enum<?> e -> Stream.of(new JsonToken.StringToken(e.name()));
            case Optional<?> optional -> lazySerialize(optional.orElse(null));
            case Collection<?> collection -> serializeCollection(collection);
            case Object __ when o.getClass().isRecord() -> serializeRecord(o);
            default -> throw new IllegalArgumentException();    // we don't support this type
        };
    }

    default Stream<JsonToken> serializeCollection(Collection<?> collection)
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
            builder.accept(lazySerialize(value));   // recursively serialize each value
        }
        builder.accept(Stream.of(new JsonToken.EndArrayToken()));   // again, wrap the token in a stream
        return builder.build().flatMap(Function.identity());    // flatten stream of streams into stream of tokens
    }

    default Stream<JsonToken> serializeRecord(Object record)
    {
        RecordComponent[] recordComponents = record.getClass().getRecordComponents();   // Java records include a complete specification of the record's components
        Stream.Builder<Stream<JsonToken>> builder = Stream.builder();
        builder.accept(Stream.of(new JsonToken.BeginObjectToken()));    // again, wrap the token in a stream
        boolean first = true;
        for (RecordComponent recordComponent : recordComponents) {
            if (first) {
                first = false;
            }
            else {
                builder.accept(Stream.of(new JsonToken.ValueSeparatorToken())); // again, wrap the token in a stream
            }
            builder.accept(Stream.of(new JsonToken.ObjectNameToken(recordComponent.getName())));    // for now, use the record name - in future versions we might re-format the name
            try {
                Object o = recordComponent.getAccessor().invoke(record);    // use the record's accessor to get its value via reflection
                builder.accept(lazySerialize(o));      // recursively serialize each value
            }
            catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        builder.accept(Stream.of(new JsonToken.EndObjectToken()));
        return builder.build().flatMap(Function.identity());
    }

    private Stream<JsonToken> lazySerialize(Object o)
    {
        // use an iterator to delay the serialization until the stream is processed
        // this prevents large objects from creating an unnecessarily large chain of token streams
        Iterator<Stream<JsonToken>> tokenStreamIterator = new Iterator<>()
        {
            private boolean hasNext = true;

            @Override
            public boolean hasNext()
            {
                return hasNext;
            }

            @Override
            public Stream<JsonToken> next()
            {
                if (hasNext) {
                    hasNext = false;
                    return serialize(o);
                }
                throw new NoSuchElementException();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(tokenStreamIterator, 0), false).flatMap(Function.identity());
    }
}
