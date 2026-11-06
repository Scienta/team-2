package no.scienta.cfp;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts only a JSON string, so a number or boolean isn't coerced to text (a 400 instead). JSON null still maps to
 * null, for bean validation to reject. Used on single fields, so the global Jackson settings stay as they are.
 */
public class StrictStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) {
        if (p.currentToken() == JsonToken.VALUE_STRING) {
            return p.getString();
        }
        return (String) ctxt.handleUnexpectedToken(String.class, p);
    }
}
