package org.scenarioservice.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Strict JSON types (D8, D19 #6, D20 #4; as auth-service's {@code StrictJsonStringsConfiguration}): a JSON number,
 * fraction or boolean is never a string, and an integer field accepts only a JSON integer (no string, fraction,
 * boolean or empty string; a value outside int32 fails as an overflow). Each becomes 400 MALFORMED_REQUEST. A JSON
 * {@code null} is not coerced; it reaches validation.
 */
@Configuration
public class StrictJsonConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer strictJsonTypes() {
        return builder -> builder
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Integer, config -> config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
