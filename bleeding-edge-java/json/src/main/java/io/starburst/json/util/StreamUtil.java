package io.starburst.json.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface StreamUtil
{
    static <T> Stream<T> lazyStream(Supplier<Stream<T>> streamSupplier)
    {
        // use an iterator to delay the stream generation until the stream is processed
        Iterator<Stream<T>> streamIterator = new Iterator<>()
        {
            private boolean hasNext = true;

            @Override
            public boolean hasNext()
            {
                return hasNext;
            }

            @Override
            public Stream<T> next()
            {
                if (hasNext) {
                    hasNext = false;
                    return streamSupplier.get();
                }
                throw new NoSuchElementException();
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(streamIterator, 0), false)
                .flatMap(Function.identity());
    }
}
