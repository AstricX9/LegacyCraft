package net.minecraft.testing;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class that compares LegacyCraft with a real 1.8.9 world
 * ({@link VanillaWorld}). These are the VERIFIED_VANILLA evidence; they are
 * slow and need the world, so {@code gradlew test} leaves them out and
 * {@code gradlew oracleTest} runs them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("oracle")
public @interface Oracle {}
