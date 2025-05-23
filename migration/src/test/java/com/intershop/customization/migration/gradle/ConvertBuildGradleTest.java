package com.intershop.customization.migration.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.intershop.customization.migration.utils.FileUtils;
import org.junit.jupiter.api.Test;

class ConvertBuildGradleTest
{
    private final ConvertBuildGradle underTest = new ConvertBuildGradle();

    @Test
    void testAll() throws IOException, URISyntaxException
    {
        List<String> lines = FileUtils.readAllLines(Paths.get(getResourceURI("ConvertBuildGradleTest.source").toURI()));
        List<String> expected = FileUtils.readAllLines(Paths.get(getResourceURI("ConvertBuildGradleTest.expected").toURI()));

        // split result into List of strings to handle different OS specific line endings
        List<String> result = Arrays.asList(underTest.migrate(lines).split("\\R"));
        assertEquals(expected, result);
    }

    @Test
    void testMapPlugins()
    {
        List<String> existing = Arrays.asList(
                        "java-cartridge",
                        "static-cartridge",
                        "com.intershop.gradle.cartridge-resourcelist",
                        "com.intershop.gradle.isml"
                        ); 
        List<String> expected = Arrays.asList(
                            "com.intershop.gradle.cartridge-resourcelist",
                            "com.intershop.gradle.isml",
                            //"com.intershop.icm.cartridge.external",
                            "com.intershop.icm.cartridge.product",
                            "java"
                        );
        assertEquals(expected, underTest.mapPlugins(existing));
    }

    @Test
    void testDependency()
    {
        List<String> existing = Arrays.asList(
                        "  compile 'commons-collections:commons-collections'",
                        "\tcompile group: 'com.intershop.platform', name: 'ui_web_library'",
                        " "
                        ); 
        List<String> expected = Arrays.asList(
                        "implementation 'commons-collections:commons-collections'",
                        "implementation 'com.intershop.platform:ui_web_library'",
                        ""
                        );
        assertEquals(expected, underTest.convertDependencies(existing));
    }

    private URL getResourceURI(String resourcePath)
    {
        return getClass().getResource(resourcePath);
    }

}
