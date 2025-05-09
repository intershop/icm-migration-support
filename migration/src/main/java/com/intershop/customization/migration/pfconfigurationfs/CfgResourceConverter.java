package com.intershop.customization.migration.pfconfigurationfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.LoggerFactory;

/**
 * helper class to Convert ICM 7.10 configuration .resource file into .properties file. They are used for
 * <ul>
 * <li>transport - name *_transport.resource</li>
 * <li>application preferencees - name *_appprfrnce.resource</li>
 * <li>user - name *_usr.resource</li>
 * <li>... w.i.p</li> <7ul> and converted into property files.<br/>
 * 
 * <p/>
 * Background: "When using the “Test System Configuration Solution Kit”, a rework is necessary according to
 * pf_configuration_fs versions for IS7.10 vs. for ICM11. See
 * <ul>
 * <li>"Cookbook - 7.10 Test System Configuration Solution" Kit vs.</li>
 * <li>"Cookbook - ICM 11 Test System Configuration Solution Kit"</li>.
 * </ul>
 * The *.resource files need to be migrated to *.properties files and wired in cartridge-specific configuration.xml
 * file."
 * 
 */
public class CfgResourceConverter
{

    private final Path source;
    private final Path target;
    private String resourceType;

    private String prefix = "";

    public final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(getClass());

    /**
     * @param resourceType the type of the resource, e.g. "transport", prefixed as "pfconfigurationfs>transport"
     * @param source the source file to convert
     * @param target the target file to write the converted content
     */
    public CfgResourceConverter(String resourceType, Path source, Path target)
    {
        this.source = source;
        this.target = target;

        this.resourceType = "";
        switch(resourceType)
        {
            case "transport":
                this.prefix = "pfconfigurationfs>transport";
                break;
            case "application":
                this.prefix = "pfconfigurationfs>appprfrnce";
                break;
            case "user":
                this.prefix = "pfconfigurationfs>usr";
                break;
            default:
                LOGGER.error("Unknown resource type: {}", resourceType);
                return;
        }
        this.resourceType = resourceType;
    }

    /**
     * convert a transport configuration
     */
    public void convertTransportResource()
    {
        if (resourceType.isEmpty())
        {
            LOGGER.error("No resource type set. Can't convert file {}.", source);
            return;
        }
        if (!source.toFile().getName().endsWith(".resource"))
        {

            LOGGER.error("Wrong file name - not a resource file? - {}", source.toFile().getName());
            return;
        }
        if (!source.toFile().exists())
        {
            LOGGER.error("Source file {} does not exist.", source);
            return;
        }

        String cfgDomainDir = source.getParent().toFile().getName();

        try
        {
            // Read lines from a file
            List<String> lines = Files.readAllLines(source);

            // Source file contains 3 lines belonging together. values are collected in targetEntry
            ArrayList<String> targetEntry = new ArrayList<>();

            // Lines to write another file
            ArrayList<String> targetLines = new ArrayList<>();
            for (String line : lines)
            {
                line = line.trim();

                // transport resource file
                if (line.isEmpty() || (line.startsWith("#")))
                {
                    targetLines.add(line);
                    continue;
                }

                if ("user".equals(this.resourceType))
                {
                    targetLines.add(this.prefix + ">" + line.trim());
                }
                else
                {
                    String[] entry = line.split("=");

                    // scan input line
                    String cfgGroup = "";
                    String cfgKey = "";
                    String cfgValue = "";
                    if (entry.length == 2)
                    {
                        cfgKey = entry[0].trim();
                        cfgValue = entry[1].trim();
                        if (0 >= cfgKey.indexOf("."))
                        {
                            cfgGroup = cfgKey.substring(0, cfgKey.indexOf(".") - 1);
                            cfgKey = cfgKey.substring(cfgKey.indexOf(".")).trim();
                        }
                        else
                        {
                            cfgGroup = "N/A";
                        }
                    }

                    // fill target line
                    if (!cfgGroup.isEmpty() && !cfgKey.isEmpty() && !cfgValue.isEmpty())
                    {
                        List<String> sourceEntry = Arrays.asList(cfgGroup, cfgKey, cfgValue);

                        if (targetEntry.size() < 3)
                        {
                            targetEntry.add(sourceEntry.get(2).trim());
                        }
                        if (targetEntry.size() == 3)
                        {
                            String groupStr = targetEntry.get(0).trim();

                            if ("application".equals(this.resourceType))
                            {
                                groupStr = cfgDomainDir + ">" + groupStr;
                            }

                            String targetLine = this.prefix
                                                    + ">" + groupStr
                                                    + ">" + targetEntry.get(1).trim()
                                                    + " = " + targetEntry.get(2).trim();

                            if (!targetLine.endsWith(" = n/a"))
                            {
                                targetLines.add(targetLine);
                            }
                            targetEntry = new ArrayList<>();
                        }
                    }
                }
            }
            Files.write(target, targetLines);
        }
        catch(IOException e)
        {
            LOGGER.error("Error reading file: " + source, e);
        }
    }
}
