package com.intershop.customization.migration.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.junit.jupiter.api.Test;

class MigrationStepFolderTest
{

    private static final String RESOURCE_PACKAGE = "com.intershop.customization.migration.common";
    private final MigrationStepFolder underTest = MigrationStepFolder.valueOf(RESOURCE_PACKAGE);
    
    @Test
    void testURIs() throws IOException, URISyntaxException
    {
        List<URI> resources = MigrationStepFolder.getURIs(RESOURCE_PACKAGE);
        assertEquals(1, resources.size());
        assertTrue(resources.get(0).toString().endsWith("MigrationStepTest.yml"));
        assertEquals(1, underTest.getSteps().size());
   }
}
