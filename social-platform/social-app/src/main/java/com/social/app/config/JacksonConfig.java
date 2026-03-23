package com.social.app.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;

/**
 * Configures Jackson to serialize large long values (GlobalIds) as JSON strings.
 * Values above Number.MAX_SAFE_INTEGER (2^53 - 1) are serialized as strings
 * to prevent JavaScript precision loss. Small values remain as numbers.
 */
@Configuration
public class JacksonConfig {

    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();

        SimpleModule longModule = new SimpleModule("SafeLong");
        JsonSerializer<Long> safeLongSerializer = new JsonSerializer<Long>() {
            @Override
            public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                if (value != null && (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER)) {
                    gen.writeString(value.toString());
                } else {
                    gen.writeNumber(value);
                }
            }
        };
        longModule.addSerializer(Long.class, safeLongSerializer);
        longModule.addSerializer(Long.TYPE, safeLongSerializer);
        mapper.registerModule(longModule);

        return mapper;
    }
}
