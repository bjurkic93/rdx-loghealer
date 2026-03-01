package com.reddiax.loghealer.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.Instant;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
        
        SimpleModule instantModule = new SimpleModule();
        instantModule.addDeserializer(Instant.class, new InstantDeserializer());
        instantModule.addSerializer(Instant.class, new InstantSerializer());
        objectMapper.registerModule(instantModule);
        
        return objectMapper;
    }

    public static class InstantDeserializer extends JsonDeserializer<Instant> {
        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken().isNumeric()) {
                long value = p.getLongValue();
                if (value > 1_000_000_000_000L) {
                    return Instant.ofEpochMilli(value);
                } else {
                    return Instant.ofEpochSecond(value);
                }
            } else {
                String text = p.getText();
                try {
                    long millis = Long.parseLong(text);
                    if (millis > 1_000_000_000_000L) {
                        return Instant.ofEpochMilli(millis);
                    } else {
                        return Instant.ofEpochSecond(millis);
                    }
                } catch (NumberFormatException e) {
                    return Instant.parse(text);
                }
            }
        }
    }

    public static class InstantSerializer extends JsonSerializer<Instant> {
        @Override
        public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value != null) {
                gen.writeNumber(value.toEpochMilli());
            }
        }
    }
}
