package io.starburst.json;

import io.starburst.json.Json;
import io.starburst.json.util.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlogExampleTest
{
    public enum Suit { Diamonds, Clubs, Spades, Hearts };
    public record Value(String name, int numericValue) {}
    public record Card(Value value, Suit suit) {}

    @Test
    public void testBlogExmple()
    {
        var json = Json.instance();

        TypeToken<List<Card>> typeToken = new TypeToken<>() {};

        List<Card> straight = List.of(
                new Card(new Value("A", 1), Suit.Spades),
                new Card(new Value("2", 2), Suit.Diamonds),
                new Card(new Value("3", 3), Suit.Clubs),
                new Card(new Value("4", 4), Suit.Hearts),
                new Card(new Value("5", 5), Suit.Diamonds));

        String straightJson = json.serializeToString(straight);
        List<Card> deserializedStraight = json.deserialize(typeToken, straightJson);
        assertEquals(straight, deserializedStraight);
    }
}
