package com.intershop.customization.migration.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.intershop.customization.migration.common.MigrationStep;

public class ConvertToCartridgeDependencyTest
{
    private static final Charset BUILD_GRADLE_CHARSET = Charset.forName("utf-8");
    private final ConvertToCartridgeDependency underTest = new ConvertToCartridgeDependency();

    @Test
    void testAll() throws IOException, URISyntaxException
    {
        List<String> lines = Files.readAllLines(Paths.get(getResourceURI("ConvertToCartridgeDependencyTest.source")), BUILD_GRADLE_CHARSET);
        String expected = Files.readString(Paths.get(getResourceURI("ConvertToCartridgeDependencyTest.expected")), BUILD_GRADLE_CHARSET);
        MigrationStep step = MigrationStep.valueOf(getGlobalResourceURI("migration/001_migration_7.10-11.0.8/003_ConvertToCartridgeDependency.yml"));
        underTest.setStep(step);
        String result = underTest.migrate(lines);
        assertEquals(expected, result);
    }

    private URI getResourceURI(String resourcePath) throws URISyntaxException
    {
        return getClass().getResource(resourcePath).toURI();
    }

    private URI getGlobalResourceURI(String resourcePath) throws URISyntaxException
    {
        return getClass().getClassLoader().getResource(resourcePath).toURI();
    }
}
