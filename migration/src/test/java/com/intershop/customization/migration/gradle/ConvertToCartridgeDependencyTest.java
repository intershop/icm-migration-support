package com.intershop.customization.migration.gradle;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;

import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.utils.FileUtils;
import org.junit.jupiter.api.Test;

class ConvertToCartridgeDependencyTest
{
    private final ConvertToCartridgeDependency underTest = new ConvertToCartridgeDependency();

    @Test
    void testAll() throws IOException, URISyntaxException
    {
        List<String> lines = FileUtils.readAllLines(Paths.get(getResourceURI("ConvertToCartridgeDependencyTest.source")));
        String expected = FileUtils.readString(Paths.get(getResourceURI("ConvertToCartridgeDependencyTest.expected")));
        MigrationStep step = MigrationStep.valueOf(getGlobalResourceURI(
                        "migration/001_migration_7x10_to_11/020_ConvertToCartridgeDependency.yml"));
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
