package io.starburst.json.models;

import java.util.List;

public record RecordWithGenerics<T>(T thing, List<String> names)
{
}
