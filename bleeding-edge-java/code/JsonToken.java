public sealed interface JsonToken
{
    record BeginArrayToken()
            implements JsonToken {}

    record EndArrayToken()
            implements JsonToken {}

    record BeginObjectToken()
            implements JsonToken {}

    record EndObjectToken()
            implements JsonToken {}

    record ObjectNameToken(String name)
            implements JsonToken {}

    record ValueSeparatorToken()
            implements JsonToken {}

    record StringToken(String value)
            implements JsonToken {}

    record NumberToken(Number value)
            implements JsonToken {}

    record BooleanToken(boolean value)
            implements JsonToken {}

    record NullToken()
            implements JsonToken {}
}
