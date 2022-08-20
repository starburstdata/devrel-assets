[⬆️ Top](00-introduction.md) • [⬅️ Previous](05-deserialization-02.md)

# Bleeding Edge Java

_Note: this is Part 6 of this series. Please [start at the introduction](00-introduction.md) if you haven't already_.

## Part 6 - Conclusion

We now have the beginnings of a modern JSON serialization library. Note: a full featured version of this
library can be [found here](https://github.com/starburstdata/developer-blog-assets/tree/main/bleeding-edge-java/json).

Let's make one last helper to simplify the usage of the library. We'll create a high level API for our
library that exposes methods that are easier to use.

```java
public interface Json
{
    String serializeToString(Object o);

    void serializeToWriter(Object o, Writer writer);

    <T> T deserialize(Type type, String json);

    <T> T deserialize(Type type, Reader reader);
}
```

The implementation for it is straightforward:

```java
new Json() {
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
    public <T> T deserialize(Type type, String json)
    {
        return parser.parse(json.chars())
                .collect(JsonDeserializerCollector.deserializing(deserializer, type));
    }
    
    @Override
    public <T> T deserialize(Type type, Reader reader)
    {
        BufferedReader bufferedReader = new BufferedReader(reader);
        return parser.parse(bufferedReader.lines().flatMapToInt(String::chars))
                .collect(JsonDeserializerCollector.deserializing(deserializer, type));
    }
};
```

# Test it out for yourself!

Let's try the high-level API to serialize and deserialize in jshell.  This example will use these files:

- [TypeToken.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/TypeToken.java)
- [JsonToken.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonToken.java)
- [JsonSerializer.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonSerializer.java)
- [StringUtils.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/StringUtils.java)
- [JsonPrinter.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonPrinter.java)
- [JsonParser.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonParser.java)
- [JsonDeserializer.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializer.java)
- [JsonDeserializerCollector.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializerCollector.java)
- [Json.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/Json.java)

From a terminal with Java 19 installed, run:

```shell
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/TypeToken.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonToken.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonSerializer.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/StringUtils.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonPrinter.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonParser.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializer.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonDeserializerCollector.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/Json.java
jshell --enable-preview TypeToken.java JsonToken.java JsonSerializer.java StringUtils.java JsonPrinter.java JsonParser.java JsonDeserializer.java JsonDeserializerCollector.java Json.java
```

Try the following example or create your own.

```text
var json = Json.instance();

enum Suit { Diamonds, Clubs, Spades, Hearts };
record Value(String name, int value) {}
record Card(Value value, Suit suit) {}
TypeToken<List<Card>> typeToken = new TypeToken<>() {};

List<Card> straight = List.of(
    new Card(new Value("A", 1), Suit.Spades), 
    new Card(new Value("2", 2), Suit.Diamonds),
    new Card(new Value("3", 3), Suit.Clubs),
    new Card(new Value("4", 4), Suit.Hearts),
    new Card(new Value("5", 5), Suit.Diamonds));

String straightJson = json.serializeToString(straight);
List<Card> deserializedStraight = json.deserialize(typeToken, straightJson);

System.out.println();
System.out.println("RESULTS");
System.out.println("straight.equals(deserializedStraight)?");
System.out.println(straight.equals(deserializedStraight));
System.out.println();
System.out.println("JSON text:");
System.out.println();
System.out.println(straightJson);
```

# Summary

We have a simple JSON library and have learned about the very latest features of Java. We hope you've enjoyed this series. Look forward
to future posts that explore modern topics in software development.

# We're hiring

Want to be able to use the latest features of Java? [We're hiring!](https://www.starburst.io/careers/)

------------

_About the author:_

_Jordan Zimmerman is a Software Engineer working on [Starburst Galaxy](https://www.starburst.io/platform/starburst-galaxy/)_ 
