package org.springframework.vault.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that specifies how to map property names received from Vault to Spring environment.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface PropertyMapping {

    /**
     * Property name received from Vault.
     */
    String from();

    /**
     * Property name to be set to Spring environment.
     */
    String to();
}
