package io.starburst.json;

import io.starburst.json.JsonToken.BeginArrayToken;
import io.starburst.json.JsonToken.BeginObjectToken;
import io.starburst.json.JsonToken.BooleanToken;
import io.starburst.json.JsonToken.EndArrayToken;
import io.starburst.json.JsonToken.EndObjectToken;
import io.starburst.json.JsonToken.NullToken;
import io.starburst.json.JsonToken.NumberToken;
import io.starburst.json.JsonToken.ObjectNameToken;
import io.starburst.json.JsonToken.StringToken;
import io.starburst.json.JsonToken.ValueSeparatorToken;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.stream.IntStream;

import static io.starburst.json.EdgeCases.FLOATS;
import static io.starburst.json.EdgeCases.UNICODE_ESCAPE;
import static io.starburst.json.JsonAssertions.assertNoToken;
import static io.starburst.json.JsonAssertions.assertToken;

public class TestParsing
{
    @Test
    public void testUnicode()
    {
        String json = "{\"text\":\"\uD83D\uDE03\"}";
        Iterator<JsonToken> iterator = JsonParser.instance().parse(json.chars()).iterator();
        assertToken(BeginObjectToken.class, iterator);
        assertToken(ObjectNameToken.class, iterator, objectNameToken -> objectNameToken.name().equals("text"));
        assertToken(StringToken.class, iterator, stringToken -> stringToken.value().equals("\uD83D\uDE03"));
        assertToken(EndObjectToken.class, iterator);
        assertNoToken(iterator);

        iterator = JsonParser.instance().parse(UNICODE_ESCAPE.chars()).iterator();
        StringToken stringToken = assertToken(StringToken.class, iterator);
        assertNoToken(iterator);
        iterator = JsonParser.instance().parse(stringToken.value().chars()).iterator();
        assertToken(BeginObjectToken.class, iterator);
        assertToken(ObjectNameToken.class, iterator, o -> o.name().equals("text"));
        assertToken(StringToken.class, iterator, s -> s.value().equals("\uD83D\uDE03"));
        assertToken(EndObjectToken.class, iterator);
        assertNoToken(iterator);

        iterator = JsonParser.instance().parse("[\"\\u00411234\"]".chars()).iterator();
        assertToken(BeginArrayToken.class, iterator);
        assertToken(StringToken.class, iterator, s -> s.value().equals("A1234"));
        assertToken(EndArrayToken.class, iterator);
        assertNoToken(iterator);
    }

    @Test
    public void testNumbers()
    {
        String json = "1 2\t3\r4\n5\r\n6\r\n   7";
        Iterator<JsonToken> iterator = JsonParser.instance().parse(json.chars()).iterator();
        IntStream.range(1, 8).forEach(i -> assertToken(NumberToken.class, iterator, n -> (n.value() instanceof Long) && (n.value().intValue() == i)));
        assertNoToken(iterator);

        long qty = FLOATS.chars().filter(i -> i == ',').count();
        Iterator<JsonToken> floatsIterator = JsonParser.instance().parse(FLOATS.chars()).iterator();
        assertToken(BeginArrayToken.class, floatsIterator);
        IntStream.range(0, (int) qty + 1).forEach(i -> {
            if (i > 0) {
                assertToken(ValueSeparatorToken.class, floatsIterator);
            }
            assertToken(NumberToken.class, floatsIterator, n -> n.value() instanceof Double);
        });
        assertToken(EndArrayToken.class, floatsIterator);
        assertNoToken(iterator);
    }

    @Test
    public void testBooleans()
    {
        String json = "true false\ttrue\rfalse\ntrue\r\nfalse\r\n   true";
        Iterator<JsonToken> iterator = JsonParser.instance().parse(json.chars()).iterator();
        IntStream.range(1, 8).forEach(i -> assertToken(BooleanToken.class, iterator, b -> ((i & 1) == 1) == b.booleanValue()));
        assertNoToken(iterator);
    }

    @Test
    public void testNulls()
    {
        String json = "null null\tnull\rnull\nnull\r\nnull\r\n   null";
        Iterator<JsonToken> iterator = JsonParser.instance().parse(json.chars()).iterator();
        IntStream.range(1, 8).forEach(i -> assertToken(NullToken.class, iterator));
        assertNoToken(iterator);
    }

    @Test
    public void testEscapes()
    {
        String DOC = "["
                +"\"LF=\\n\""
                +"]";
        Iterator<JsonToken> iterator = JsonParser.instance().parse(DOC.chars()).iterator();
        assertToken(BeginArrayToken.class, iterator);
        assertToken(StringToken.class, iterator, s -> s.value().equals("LF=\n"));
        assertToken(EndArrayToken.class, iterator);
        assertNoToken(iterator);

        iterator = JsonParser.instance().parse("[\"NULL:\\u0000!\"]".chars()).iterator();
        assertToken(BeginArrayToken.class, iterator);
        assertToken(StringToken.class, iterator, s -> s.value().equals("NULL:\0!"));
        assertToken(EndArrayToken.class, iterator);
        assertNoToken(iterator);

        iterator = JsonParser.instance().parse("[\"\\u0123\"]".chars()).iterator();
        assertToken(BeginArrayToken.class, iterator);
        assertToken(StringToken.class, iterator, s -> s.value().equals("\u0123"));
        assertToken(EndArrayToken.class, iterator);
        assertNoToken(iterator);

        iterator = JsonParser.instance().parse("[\"\\u0041\\u0043\"]".chars()).iterator();
        assertToken(BeginArrayToken.class, iterator);
        assertToken(StringToken.class, iterator, s -> s.value().equals("AC"));
        assertToken(EndArrayToken.class, iterator);
        assertNoToken(iterator);
    }

    @Test
    public void testSimpleNameEscaping()
    {
        for (int i = 0; i < 16; ++i) {
            final String base = "1234567890abcdef".substring(0, i);
            final String inputKey = '"' + base + "\\\"" + '"';
            final String expKey = base + "\"";
            Iterator<JsonToken> iterator = JsonParser.instance().parse(("{"+inputKey+" : 123456789}       ").chars()).iterator();
            assertToken(BeginObjectToken.class, iterator);
            assertToken(ObjectNameToken.class, iterator, objectNameToken -> objectNameToken.name().equals(expKey));
            assertToken(NumberToken.class, iterator, numberToken -> numberToken.value().intValue() == 123456789);
            assertToken(EndObjectToken.class, iterator);
            assertNoToken(iterator);
        }
    }
}