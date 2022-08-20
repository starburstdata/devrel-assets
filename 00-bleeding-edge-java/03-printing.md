[⬆️ Top](00-introduction.md) • [⬅️ Previous](02-serialization.md) • [Next ➡️](04-parsing.md)

# Bleeding Edge Java

_Note: this is Part 3 of this series. Please [start at the introduction](00-introduction.md) if you haven't already_.

## Part 3 - Printing

Printing is the process of converting a stream of JSON tokens into JSON text. For our library, our printer only needs to
print a single JSON token at a time. Our serializer already produces a stream of JSON tokens and the JDK has a large
set of existing methods for managing streams. What we need is a mapping function that can be passed to the JDK Stream's
`map()` method. We can define a Java interface for this:

```java
public interface JsonPrinter
{
    CharSequence print(JsonToken jsonToken);
}
```

With an instance of this interface we can serialize from a Java object to a stream of JSON tokens to JSON text like this:

```java
String jsonText = serializer.serialize(object)
    .map(printer::print)
    .collect(Collectors.joining());
```

The implementation for our printer is very straightforward (note the code uses a StringUtils utility - see the link at the end of this article):

```java
CharSequence print(JsonToken jsonToken)
{
    return switch (jsonToken) {
        case JsonToken.NumberToken numberToken -> numberToken.value().toString();
        case JsonToken.StringToken stringToken -> StringUtils.quoteAndEscape(stringToken.value());
        case JsonToken.BooleanToken booleanToken -> booleanToken.value() ? "true" : "false";
        case JsonToken.NullToken ignore -> "null";
        case JsonToken.BeginArrayToken ignore -> "[";
        case JsonToken.EndArrayToken ignore -> "]";
        case JsonToken.BeginObjectToken ignore -> "{";
        case JsonToken.EndObjectToken ignore -> "}";
        case JsonToken.ObjectNameToken objectNameToken -> StringUtils.quoteAndEscape(objectNameToken.name()) + ":";
        case JsonToken.ValueSeparatorToken ignore -> ",";
    };
}
```

Here again we take advantage of enhanced switch and pattern matching (see [Serialization](02-serialization.md) for more details). Notice here that our
`switch` statement does not need a `default` case. We have `case` statements for all possible implementations in the JsonToken sealed hierarchy. The Java compiler
knows this and doesn't require a `default`. If in the future we add a new JsonToken type this code would no longer compile.

# Test it out for yourself!

In the prior article we developed a serializer that can serialize a Java record into a stream of JSON tokens. We now have a printer that can
map a JSON token into JSON text. Let's put this together in jshell. This example
will use these files:

- [TypeToken.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/TypeToken.java)
- [JsonToken.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonToken.java)
- [JsonSerializer.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonSerializer.java)
- [StringUtils.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/StringUtils.java) (utility class)
- [JsonPrinter.java](https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonPrinter.java)

From a terminal with Java 19 installed, run:

```shell
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/TypeToken.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonToken.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonSerializer.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/StringUtils.java
wget -nc https://raw.githubusercontent.com/starburstdata/developer-blog-assets/main/bleeding-edge-java/code/JsonPrinter.java
jshell --enable-preview TypeToken.java JsonToken.java JsonSerializer.java StringUtils.java JsonPrinter.java
```

Inside jshell let's serialize a Java record into JSON text:

```text
var serializer = JsonSerializer.instance();
var printer = JsonPrinter.instance();

record Person(String name, int age) {}
var person = new Person("someone", 28);

String jsonText = serializer.serialize(person)  // serialize record to stream of tokens
    .map(printer::print)                        // map each JsonToken to a String (as a CharSequence)
    .collect(Collectors.joining());             // collect into a String
System.out.println(jsonText);
```

# Summary

We can now serialize a Java record into JSON text. 

# We're hiring

Want to be able to use the latest features of Java? [We're hiring!](https://www.starburst.io/careers/)

------------

_About the author:_

_Jordan Zimmerman is a Software Engineer working on [Starburst Galaxy](https://www.starburst.io/platform/starburst-galaxy/)_ 
