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
