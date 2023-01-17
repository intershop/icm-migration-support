package com.intershop.customization.migration.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.intershop.customization.migration.gradle.ConvertToCartridgeDependency;

class MigrationStepTest
{
    private static final Charset DEFAULT_CHARSET = Charset.forName("utf-8");
    private static final String KEY_CARTRIDGE_DEPENDENCY_GROUPS = ConvertToCartridgeDependency.YAML_KEY_CARTRIDGE_DEPENDENCY;
    MigrationStep underTest = new MigrationStep();

    @SuppressWarnings("unchecked")
    @Test
    void testOptionLoading() throws IOException, URISyntaxException
    {
        List<String> lines = Files.readAllLines(
                        Paths.get(getResourceURI("MigrationStepTest.yml").toURI()), DEFAULT_CHARSET);
        Map<String, Object> expected = Map.of(
                        "type", "specs.intershop.com/v1beta/migrate",
                        "migrator", "com.intershop.customization.migration.gradle.ConvertToCartridgeDependency",
                        "options", Map.of(KEY_CARTRIDGE_DEPENDENCY_GROUPS, List.of("com.intershop.platform", "com.intershop.content", "com.intershop.business", "com.intershop.b2b")));
        Map<String, Object> was = underTest.importOptions(String.join(System.lineSeparator(), lines));
        assertEquals(expected.keySet(), was.keySet());
        assertEquals(expected.get("type"), was.get("type"));
        assertEquals(expected.get("migrator"), was.get("migrator"));

        Map<String, Object> optionsExpected = (Map<String, Object>) expected.get("options");
        Map<String, Object> optionsWas = (Map<String, Object>) was.get("options");
        assertEquals(optionsExpected.keySet(), optionsWas.keySet());

        List<String> cartridgeExpected = (List<String>) optionsExpected.get(KEY_CARTRIDGE_DEPENDENCY_GROUPS);
        List<String> cartridgeWas = (List<String>) optionsWas.get(KEY_CARTRIDGE_DEPENDENCY_GROUPS);
        assertEquals(cartridgeExpected, cartridgeWas);
        // to summarize it
        assertEquals(expected, was);
    }
    
    @Test
    void testLoader() throws IOException, URISyntaxException
    {
        List<String> lines = Files.readAllLines(
                        Paths.get(getResourceURI("MigrationStepTest.yml").toURI()), DEFAULT_CHARSET);
        underTest.importOptions(String.join(System.lineSeparator(), lines));
        
        MigrationPreparer migrator = underTest.getMigrator();
        assertTrue(migrator instanceof ConvertToCartridgeDependency);
    }

    @Test
    void testGetVars() throws IOException, URISyntaxException
    {
        List<String> lines = Files.readAllLines(
                        Paths.get(getResourceURI("MigrationStepTest.yml").toURI()), DEFAULT_CHARSET);
        underTest.importOptions(String.join(System.lineSeparator(), lines));
        
        List<String> pkgs = underTest.getOption(KEY_CARTRIDGE_DEPENDENCY_GROUPS);
        assertEquals(4, pkgs.size());
    }

    private URL getResourceURI(String resourcePath)
    {
        return getClass().getResource(resourcePath);
    }

}
