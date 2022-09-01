import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.stream.Collectors;

public interface Json
{
    static Json instance()
    {
        return new Json() {
            private final JsonPrinter printer = JsonPrinter.instance();
            private final JsonParser parser = JsonParser.instance();
            private final JsonDeserializer deserializer = JsonDeserializer.instance();
            private final JsonSerializer serializer = JsonSerializer.instance();

            @Override
            public String serializeToString(Object o)
            {
                return serializer.serialize(o)
                        .map(printer::print)
                        .collect(Collectors.joining());
            }

            @Override
            public void serializeToWriter(Object o, Writer writer)
            {
                serializer.serialize(o)
                        .map(printer::print)
                        .forEach(str -> {
                            try {
                                writer.append(str);
                            }
                            catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }

            @Override
            public <T> T deserialize(TypeToken<T> type, String json)
            {
                return deserialize(type.type(), json);
            }

            @Override
            public <T> T deserialize(TypeToken<T> type, Reader reader)
            {
                return deserialize(type.type(), reader);
            }

            @Override
            public <T> T deserialize(Class<T> type, String json)
            {
                return deserialize((Type) type, json);
            }

            @Override
            public <T> T deserialize(Class<T> type, Reader reader)
            {
                return deserialize((Type) type, reader);
            }

            @SuppressWarnings("unchecked")
            private <T> T deserialize(Type type, String json)
            {
                return (T) parser.parse(json.chars())
                        .collect(JsonDeserializerCollector.deserializing(deserializer, type));
            }

            @SuppressWarnings("unchecked")
            private <T> T deserialize(Type type, Reader reader)
            {
                BufferedReader bufferedReader = new BufferedReader(reader);
                return (T) parser.parse(bufferedReader.lines().flatMapToInt(String::chars))
                        .collect(JsonDeserializerCollector.deserializing(deserializer, type));
            }
        };
    }

    String serializeToString(Object o);

    void serializeToWriter(Object o, Writer writer);

    <T> T deserialize(TypeToken<T> type, String json);

    <T> T deserialize(Class<T> type, String json);

    <T> T deserialize(TypeToken<T> type, Reader reader);

    <T> T deserialize(Class<T> type, Reader reader);
}
