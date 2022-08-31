/*
 * Copyright 2022 Starburst Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.starburst.json;

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
