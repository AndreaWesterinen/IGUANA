package org.aksw.iguana.commons.streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Helper functions to work with streams.
 */
public class Streams {
    /**
     * Fastest way to serialize a stream to a UTF-8 string according to https://stackoverflow.com/a/35446009/6800941
     *
     * @param inputStream stream to be serialized
     * @return content of the inputStream as string
     * @throws IOException forwarded exception from InputStream.read()
     */
    static public String inputStream2String(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1)
            result.write(buffer, 0, length);
        return result.toString(StandardCharsets.UTF_8.name());
    }
}
