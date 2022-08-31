package io.starburst.json;

import io.starburst.json.models.GlossaryContainer;
import io.starburst.json.models.MenuContainer;
import io.starburst.json.models.RecordWithGenerics;
import io.starburst.json.util.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestComplete
{
    @Test
    public void testJsonExamples()
    {
        Json json = Json.instance();
        MenuContainer menuContainer = json.deserialize(MenuContainer.class, JsonExamples.MENU);
        String menuContainerJson = json.serializeToString(menuContainer);
        assertEquals(dePretty(JsonExamples.MENU), menuContainerJson);

        GlossaryContainer glossaryContainer = json.deserialize(GlossaryContainer.class, JsonExamples.GLOSSARY);
        String glossaryContainerJson = json.serializeToString(glossaryContainer);
        assertEquals(dePretty(JsonExamples.GLOSSARY), glossaryContainerJson);
    }

    @Test
    public void testGenerics()
    {
        JsonClass jsonClass = JsonClass.<RecordWithGenerics<Long>>builder(new TypeToken<>() {})
                .addTypeVariableField("thing", RecordWithGenerics::thing, Long.class)
                .addField("names", RecordWithGenerics::names, new TypeToken<>() {})
                .build();
        Json json = Json.builder().addSerializer(jsonClass).addDeserializer(jsonClass).build();

        RecordWithGenerics<Long> r = new RecordWithGenerics<>(123456789123456789L, List.of("a", "b", "c"));
        String jsonText = json.serializeToString(r);
        RecordWithGenerics<Long> deserialized = json.deserialize(new TypeToken<>() {}, jsonText);
        assertEquals(deserialized, r);
    }

/* arrays currently not working due to javac internal error
    @Test
    public void testArrays()
    {
        Json json = Json.instance();
        int[] deserialized = json.deserialize(int[].class, "[1, 2, 3]");
        String intListJson = json.serializeToString(deserialized);
        assertEquals("[1,2,3]", intListJson);

        Optional<String>[] optionalArray = json.deserialize(new TypeToken<Optional<String>[]>() {}, "[null, \"hey\", \"there\"]");
        String optionalArrayJson = json.serializeToString(optionalArray);
        assertEquals("[null,\"hey\",\"there\"]", optionalArrayJson);
    }
*/

    private static String dePretty(String str)
    {
        return Stream.of(str.split("\\n"))
                .map(String::trim)
                .map(s -> s.replace(": ", ":"))
                .map(s -> s.replace("\", ", "\","))
                .collect(Collectors.joining());
    }
}