package io.starburst.json;

public sealed interface JsonToken
{
    record BeginArrayToken()
            implements JsonToken
    {
        public static final BeginArrayToken INSTANCE = new BeginArrayToken();
    }

    record EndArrayToken()
            implements JsonToken
    {
        public static final EndArrayToken INSTANCE = new EndArrayToken();
    }

    record BeginObjectToken()
            implements JsonToken
    {
        public static final BeginObjectToken INSTANCE = new BeginObjectToken();
    }

    record EndObjectToken()
            implements JsonToken
    {
        public static final EndObjectToken INSTANCE = new EndObjectToken();
    }

    record ObjectNameToken(String name)
            implements JsonToken
    {
    }

    record ValueSeparatorToken()
            implements JsonToken
    {
        public static final ValueSeparatorToken INSTANCE = new ValueSeparatorToken();
    }

    record StringToken(String value)
            implements JsonToken, JsonValue<String>
    {
    }

    record NumberToken(Number value)
            implements JsonToken, JsonValue<Number>
    {
    }

    record BooleanToken(boolean booleanValue)
            implements JsonToken, JsonValue<Boolean>
    {
        public static final BooleanToken TRUE = new BooleanToken(true);
        public static final BooleanToken FALSE = new BooleanToken(false);

        @Override
        public Boolean value()
        {
            return booleanValue;
        }
    }

    record NullToken()
            implements JsonToken, JsonValue<Object>
    {
        public static final NullToken INSTANCE = new NullToken();

        @Override
        public Object value()
        {
            return null;
        }
    }
}
