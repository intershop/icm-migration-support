package com.intershop.customization.migration.utils;

/**
 * This enum is used to check the operating system of the current environment.
 * It provides methods to determine if the current OS is Windows, Mac, Linux, or Solaris.
 */
public enum OsCheck
{
    WINDOWS("win"),
    MAC("mac"),
    LINUX("nix", "nux", "aix"),
    SOLARIS("sunos");

    private final String[] osNames;

    OsCheck(String... osNames)
    {
        this.osNames = osNames;
    }

    private static boolean isOs(OsCheck osCheck)
    {
        String os = System.getProperty("os.name").toLowerCase();
        for (String name : osCheck.osNames)
        {
            if (os.contains(name))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the current operating system is Windows.
     *
     * @return true if the OS is Windows, false otherwise
     */
    public static boolean isWindows()
    {
        return isOs(WINDOWS);
    }

    /**
     * Checks if the current operating system is Mac.
     *
     * @return true if the OS is Mac, false otherwise
     */
    public static boolean isMac()
    {
        return isOs(MAC);
    }

    /**
     * Checks if the current operating system is Linux.
     *
     * @return true if the OS is Linux, false otherwise
     */
    public static boolean isLinux()
    {
        return isOs(LINUX);
    }

    /**
     * Checks if the current operating system is Solaris.
     *
     * @return true if the OS is Solaris, false otherwise
     */
    public static boolean isSolaris()
    {
        return isOs(SOLARIS);
    }
}
