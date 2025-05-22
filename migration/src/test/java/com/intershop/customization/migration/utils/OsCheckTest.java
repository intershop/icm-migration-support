package com.intershop.customization.migration.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OsCheckTest
{

    private String oldValue;

    private static final String OS_NAME_PROPERTY = "os.name";

    @BeforeEach
    void setup()
    {
        // Store the original value of the system property
        oldValue = System.getProperty(OS_NAME_PROPERTY);
    }

    @AfterEach
    void teardown()
    {
        // Restore the original value of the system property
        System.setProperty(OS_NAME_PROPERTY, oldValue);
    }

    @Test
    void testIsWindows()
    {
        System.setProperty(OS_NAME_PROPERTY, "Windows 11");
        assertTrue(OsCheck.isWindows());
    }

    @Test
    void testIsMac()
    {
        System.setProperty(OS_NAME_PROPERTY, "Mac OS X");
        assertTrue(OsCheck.isMac());
    }

    @Test
    void testIsLinux()
    {
        System.setProperty(OS_NAME_PROPERTY, "Linux");
        assertTrue(OsCheck.isLinux());
    }

    @Test
    void testIsSolaris()
    {
        System.setProperty(OS_NAME_PROPERTY, "SunOS");
        assertTrue(OsCheck.isSolaris());
    }
}
