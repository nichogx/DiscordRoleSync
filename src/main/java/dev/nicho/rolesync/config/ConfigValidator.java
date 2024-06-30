package dev.nicho.rolesync.config;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;

import java.util.Set;

public class ConfigValidator {

    private final JsonSchema schema;

    /**
     * Creates a config validator from a JsonSchema.
     *
     * @param schema the already loaded JsonSchema to use
     */
    public ConfigValidator(JsonSchema schema) {
        this.schema = schema;
    }

    /**
     * Validates a YAML file from a String against the schema.
     *
     * @param yaml The YAML file as a String
     * @return A set of validation messages in case validations fail,
     *  null otherwise.
     */
    public Set<ValidationMessage> validateYaml(String yaml) {
        Set<ValidationMessage> errors = this.schema.validate(yaml, InputFormat.YAML);

        if (errors == null || errors.isEmpty()) {
            // Don't return empty errors to make it easier to parse this output
            return null;
        }

        return errors;
    }
}
