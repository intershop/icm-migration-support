package com.intershop.customization.migration.utils;

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
        assert OsCheck.isWindows();
    }

    @Test
    void testIsMac()
    {
        System.setProperty(OS_NAME_PROPERTY, "Mac OS X");
        assert OsCheck.isMac();
    }

    @Test
    void testIsLinux()
    {
        System.setProperty(OS_NAME_PROPERTY, "Linux");
        assert OsCheck.isLinux();
    }

    @Test
    void testIsSolaris()
    {
        System.setProperty(OS_NAME_PROPERTY, "SunOS");
        assert OsCheck.isSolaris();
    }
}
