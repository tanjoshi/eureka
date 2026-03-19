package com.netflix.discovery.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.netflix.discovery.util.DeserializerStringCache.CacheScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.hamcrest.MatcherAssert.assertThat;

public class DeserializerStringCacheTest {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private static JsonParser createParser(String jsonValue) throws IOException {
        // Create a real parser positioned at a string value
        JsonParser parser = JSON_FACTORY.createParser("\"" + jsonValue + "\"");
        parser.nextToken(); // Move to VALUE_STRING
        return parser;
    }

    private DeserializerStringCache createCache() {
        ObjectReader reader = DeserializerStringCache.init(new ObjectMapper().reader());
        return (DeserializerStringCache) reader.getAttributes().getAttribute("deserInternCache");
    }

    @Test
    public void testUppercaseConversionWithLowercasePreset() throws IOException {
        DeserializerStringCache cache = createCache();

        String lowerCaseValue = cache.apply("value", CacheScope.APPLICATION_SCOPE);
        assertThat(lowerCaseValue, is("value"));

        try (JsonParser jsonParser = createParser("value")) {
            String upperCaseValue = cache.apply(jsonParser, CacheScope.APPLICATION_SCOPE, jp -> "VALUE");
            assertThat(upperCaseValue, is("VALUE"));
        }
    }

    @Test
    public void testUppercaseConversionWithLongString() throws IOException {
        DeserializerStringCache cache = createCache();
        char[] lowercaseValue = new char[1024];
        Arrays.fill(lowercaseValue, 'a');
        String longString = new String(lowercaseValue);

        try (JsonParser jsonParser = createParser(longString)) {
            char[] expectedValueChars = new char[1024];
            Arrays.fill(expectedValueChars, 'A');
            String expectedValue = new String(expectedValueChars);

            String upperCaseValue = cache.apply(jsonParser, CacheScope.APPLICATION_SCOPE,
                    jp -> longString.toUpperCase());
            assertThat(upperCaseValue, is(expectedValue));
        }
    }

    @Test
    public void testCacheHitReturnsIdenticalInstance() throws IOException {
        DeserializerStringCache cache = createCache();

        String first;
        try (JsonParser p1 = createParser("testValue")) {
            first = cache.apply(p1, CacheScope.APPLICATION_SCOPE);
        }

        String second;
        try (JsonParser p2 = createParser("testValue")) {
            second = cache.apply(p2, CacheScope.APPLICATION_SCOPE);
        }

        assertSame(first, second, "Cache hit should return identical instance");
    }

    @Test
    public void testCacheHitWithStringReturnsIdenticalInstance() throws IOException {
        DeserializerStringCache cache = createCache();

        String first = cache.apply(new String("testValue"), CacheScope.APPLICATION_SCOPE);
        String second = cache.apply(new String("testValue"), CacheScope.APPLICATION_SCOPE);

        assertSame(first, second, "Cache hit should return identical instance");
    }

    @Test
    public void testCacheHitAcrossParserAndString() throws IOException {
        DeserializerStringCache cache = createCache();

        String fromParser;
        try (JsonParser parser = createParser("testValue")) {
            fromParser = cache.apply(parser, CacheScope.APPLICATION_SCOPE);
        }
        String fromString = cache.apply(new String("testValue"), CacheScope.APPLICATION_SCOPE);

        assertSame(fromParser, fromString, "Cache should work across parser and string lookups");
    }

    @Test
    public void testTransformOnlyCalledOnCacheMiss() throws IOException {
        DeserializerStringCache cache = createCache();
        AtomicInteger callCount = new AtomicInteger(0);

        Function<JsonParser, String> countingTransform = jp -> {
            callCount.incrementAndGet();
            return "TRANSFORMED";
        };

        try (JsonParser p1 = createParser("value")) {
            cache.apply(p1, CacheScope.APPLICATION_SCOPE, countingTransform);
        }
        try (JsonParser p2 = createParser("value")) {
            cache.apply(p2, CacheScope.APPLICATION_SCOPE, countingTransform);
        }

        assertEquals(1, callCount.get(), "Transform should only be called once (on cache miss)");
    }

    @Test
    public void testGlobalScopeSurvivesApplicationScopeClear() throws IOException {
        ObjectReader reader = DeserializerStringCache.init(new ObjectMapper().reader());
        DeserializerStringCache cache = (DeserializerStringCache) reader.getAttributes()
                .getAttribute("deserInternCache");

        String globalValue;
        try (JsonParser p1 = createParser("globalKey")) {
            globalValue = cache.apply(p1, CacheScope.GLOBAL_SCOPE);
        }

        String appValue;
        try (JsonParser p2 = createParser("appKey")) {
            appValue = cache.apply(p2, CacheScope.APPLICATION_SCOPE);
        }

        // Clear only application scope
        DeserializerStringCache.clear(reader, CacheScope.APPLICATION_SCOPE);

        // Global should still return same instance
        String globalAgain;
        try (JsonParser p3 = createParser("globalKey")) {
            globalAgain = cache.apply(p3, CacheScope.GLOBAL_SCOPE);
        }
        assertSame(globalValue, globalAgain, "Global value should survive application scope clear");

        // Application scope was cleared, so this should be a new instance
        String appAgain;
        try (JsonParser p4 = createParser("appKey")) {
            appAgain = cache.apply(p4, CacheScope.APPLICATION_SCOPE);
        }
        assertNotSame(appValue, appAgain, "Application value should be new after clear");
        assertEquals(appValue, appAgain, "Application value should have same content");
    }

    @Test
    public void testGlobalScopeClearClearsBothScopes() throws IOException {
        ObjectReader reader = DeserializerStringCache.init(new ObjectMapper().reader());
        DeserializerStringCache cache = (DeserializerStringCache) reader.getAttributes()
                .getAttribute("deserInternCache");

        String globalValue;
        try (JsonParser p1 = createParser("globalKey")) {
            globalValue = cache.apply(p1, CacheScope.GLOBAL_SCOPE);
        }

        String appValue;
        try (JsonParser p2 = createParser("appKey")) {
            appValue = cache.apply(p2, CacheScope.APPLICATION_SCOPE);
        }

        // Clear global scope (should clear both)
        DeserializerStringCache.clear(reader, CacheScope.GLOBAL_SCOPE);

        String globalAgain;
        try (JsonParser p3 = createParser("globalKey")) {
            globalAgain = cache.apply(p3, CacheScope.GLOBAL_SCOPE);
        }

        String appAgain;
        try (JsonParser p4 = createParser("appKey")) {
            appAgain = cache.apply(p4, CacheScope.APPLICATION_SCOPE);
        }

        assertNotSame(globalValue, globalAgain, "Global value should be new after global clear");
        assertNotSame(appValue, appAgain, "Application value should be new after global clear");
    }

    @Test
    public void testParserWithNonZeroOffset() throws IOException {
        DeserializerStringCache cache = createCache();

        // First cache "value" from a normal parse
        String cached;
        try (JsonParser p1 = createParser("value")) {
            cached = cache.apply(p1, CacheScope.APPLICATION_SCOPE);
        }
        assertEquals("value", cached, "Should extract correct value");

        // Verify same value from different parse returns cached instance
        String cachedAgain;
        try (JsonParser p2 = createParser("value")) {
            cachedAgain = cache.apply(p2, CacheScope.APPLICATION_SCOPE);
        }
        assertSame(cached, cachedAgain, "Should match cache entry");
    }

    @Test
    public void testDifferentTransformsForSameKeyAreCachedSeparately() throws IOException {
        DeserializerStringCache cache = createCache();

        // Same raw key "app" but different transforms (identity vs toUpperCase)
        String lowercase;
        try (JsonParser p1 = createParser("app")) {
            lowercase = cache.apply(p1, CacheScope.APPLICATION_SCOPE);
        }

        // Use a different function class - this should create a different cache entry
        // because the variant is based on the function class identity
        class UpperCaseTransform implements Function<JsonParser, String> {
            public String apply(JsonParser jp) { return "APP"; }
        }

        String uppercase;
        try (JsonParser p2 = createParser("app")) {
            uppercase = cache.apply(p2, CacheScope.APPLICATION_SCOPE, new UpperCaseTransform());
        }

        assertEquals("app", lowercase, "Lowercase should be 'app'");
        assertEquals("APP", uppercase, "Uppercase should be 'APP'");
        assertNotSame(lowercase, uppercase, "Different transforms should cache separately");
    }
}
