import java.util.Iterator;
import java.util.LinkedList;
import java.util.PrimitiveIterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface JsonParser
{
    static JsonParser instance()
    {
        return new JsonParser() {};
    }

    default Stream<JsonToken> parse(IntStream stream)
    {
        Iterator<JsonToken> tokenIterator = new Iterator<>() {
            private final PrimitiveIterator.OfInt iterator = stream.iterator();
            private final StringBuilder builder = new StringBuilder();
            private final LinkedList<JsonToken> tokens = new LinkedList<>();
            private int pushedBack = -1;

            {
                advanceToNextToken();
            }

            @Override
            public boolean hasNext()
            {
                return !tokens.isEmpty();
            }

            @Override
            public JsonToken next()
            {
                if (tokens.isEmpty()) {
                    throw new RuntimeException();
                }
                JsonToken result = tokens.removeFirst();
                advanceToNextToken();
                return result;
            }

            private void advanceToNextToken()
            {
                if (pushedBack < 0) {
                    pushedBack = advanceWhitespace(iterator);
                }

                String possibleObjectName = null;
                JsonToken nextToken = null;
                while ((nextToken == null) && ((pushedBack >= 0) || iterator.hasNext())) {
                    char c = (char) (((pushedBack >= 0) ? pushedBack : iterator.nextInt()) & 0xffff);
                    pushedBack = -1;
                    nextToken = switch (c) {
                        case '{' -> new JsonToken.BeginObjectToken();
                        case '}' -> new JsonToken.EndObjectToken();
                        case '[' -> new JsonToken.BeginArrayToken();
                        case ']' -> new JsonToken.EndArrayToken();
                        case ',' -> new JsonToken.ValueSeparatorToken();
                        case '"' -> {
                            if (possibleObjectName != null) {
                                throw new RuntimeException();   // can't have more than one string
                            }
                            possibleObjectName = StringUtils.parseString(builder, iterator);    // it may be an object name so we have to keep parsing
                            yield null;
                        }
                        case 't' -> parseLiteral(iterator, "rue", new JsonToken.BooleanToken(true));
                        case 'f' -> parseLiteral(iterator, "alse", new JsonToken.BooleanToken(false));
                        case 'n' -> parseLiteral(iterator, "ull", new JsonToken.NullToken());
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', '.', 'e', 'E' -> new JsonToken.NumberToken(StringUtils.parseNumber(builder, c, iterator, i -> pushedBack = i));
                        case ':' -> {
                            if (possibleObjectName == null) {
                                throw new RuntimeException();
                            }
                            JsonToken.ObjectNameToken objectNameToken = new JsonToken.ObjectNameToken(possibleObjectName);
                            possibleObjectName = null;
                            yield objectNameToken;
                        }
                        default -> {
                            if (StringUtils.isWhitespace(c)) {
                                yield null; // ignore whitespace between tokens
                            }
                            throw new RuntimeException();   // unexpected character
                        }
                    };
                }
                if (possibleObjectName != null) {
                    // wasn't an object name - we now have to emit this string token first
                    tokens.add(new JsonToken.StringToken(possibleObjectName));
                }
                if (nextToken != null) {
                    tokens.add(nextToken);
                }
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(tokenIterator, 0), false);
    }

    private static JsonToken parseLiteral(PrimitiveIterator.OfInt iterator, String remaining, JsonToken token)
    {
        for (int i = 0; i < remaining.length(); ++i) {
            char c = (char) (iterator.nextInt() & 0xffff);
            if (c != remaining.charAt(i)) {
                throw new RuntimeException();
            }
        }
        return token;
    }

    private static int advanceWhitespace(PrimitiveIterator.OfInt iterator)
    {
        while (iterator.hasNext()) {
            int i = iterator.nextInt();
            char c = (char) (i & 0xffff);
            if (!StringUtils.isWhitespace(c)) {
                return i;
            }
        }
        return -1;
    }
}
