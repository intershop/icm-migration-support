package com.intershop.customization.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.LoggerFactory;

/**
 * helper class  to  Convert ICM 7.10 configuration .resource file into .properties file.
 * @see https://intershop.atlassian.net/wiki/spaces/INTTCHNL/pages/49623072922/Tools+for+ICM+N+Migration+Draft#*.resource-Files-in-share/system/config/domains
 * Abstract:
 * "When using the “Test System Configuration Solution Kit”, a rework is necessary 
 * according to pf_configuration_fs versions for IS7.10 vs. for ICM11. 
 * See 
 * <ul>
 *   <li><a href="https://intershop.atlassian.net/wiki/spaces/ENFDEVDOC/pages/1895598402">Cookbook - 7.10 Test System Configuration Solution Kit</a> vs. </li>
 *   <li><a href="https://intershop.atlassian.net/wiki/pages/createpage.action?spaceKey=ENFDEVDOC&title=Cookbook%20-%20ICM%2011%20Test%20System%20Configuration%20Solution%20Kit">Cookbook - ICM 11 Test System Configuration Solution Kit</a></li>. 
 * </ul>
 * The *.resource files need to be migrated to *.properties files and 
 * wired in cartridge-specific configuration.xml file."
 * 
 */
public class CfgResourceConverter {
    private Path source;
    private Path target;
    private String resourceType = "";

    private String prefix="";

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Migrator.class);

    /**
     * @param resourceType the type of the resource, e.g. "transport", prefixed as  "pfconfigurationfs>transport"
     * @param source the source file to convert
     * @param target the target file to write the converted content
     */
    public CfgResourceConverter(String resourceType, Path source, Path target) {
        this.source = source;
        this.target = target;
        switch (resourceType)
        {
            case "transport":
                this.prefix = "pfconfigurationfs>transport";
                break;
            case "application":
                this.prefix = "pfconfigurationfs>appprfrnce" ;
                break;
            case "user":
                this.prefix = "pfconfigurationfs>usr" ;
                break;
            default:
                LOGGER.error("Unknown resource type: {}", resourceType);
                return;
        }
        this.resourceType = resourceType;
    }

    /** onvert a transport configuration
     */
    public void convertTransportResource() {
        if(resourceType.isEmpty())
        {
            LOGGER.error("No resource type set. Can't convert file {}.", source);
            return;
        }
        if( ! source.toFile().getName().endsWith(".resource")) {
            
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

            // Process and write lines to another file
            String lastKey = "";
            String targetLine = "";
            ArrayList<String> tartEntry = new ArrayList<>();

            ArrayList<String> targetLines = new ArrayList<>();
            for (String line : lines) {
                line = line.trim();

                // transport resource file
                String key = "";
                if(line.isEmpty() || (line.startsWith("#")))
                {
                    targetLine = line;
                    targetLines.add(targetLine);
                }
                else
                {
                    if("user".equals(this.resourceType))
                    {
                        targetLine = this.prefix+">"+line.trim();
                        targetLines.add(targetLine);
                    }
                    else
                    {
                        String entry[] = line.split("=");

                        // scam inputz lin
                        String cfgGroup = "";
                        String cfgKey = "";
                        String cfgValue = "";
                        if (entry.length == 2) 
                        {
                            cfgKey = entry[0].trim();
                            cfgValue = entry[1].trim();
                            if((!"user".equals(this.resourceType)) && (0 >= cfgKey.indexOf(".")))
                            {
                                cfgGroup = cfgKey.substring(0,cfgKey.indexOf(".")-1);
                                cfgKey = cfgKey.substring(cfgKey.indexOf("."), cfgKey.length()).trim();
                            }
                            else
                            {
                                cfgGroup = "N/A";
                            }
                        }
                        // fuill target line
                        if(!cfgGroup.isEmpty() && !cfgKey.isEmpty() && !cfgValue.isEmpty())
                        {
                            List<String> sourceEentry = Arrays.asList(cfgGroup,  cfgKey,  cfgValue);
                            key = sourceEentry.get(0).trim();
                            if (sourceEentry.size() == 3)
                            {
                                if(tartEntry.size() < 3)
                                {
                                    tartEntry.add(sourceEentry.get(2).trim());
                                } 
                                if(tartEntry.size() ==  3) 
                                {
                                    String groupStr = tartEntry.get(0).trim();
                                    if("application".equals(this.resourceType))
                                    {
                                        groupStr = cfgDomainDir+">"+groupStr;
                                    }
                                    targetLine = this.prefix
                                    + ">" + groupStr 
                                    + ">" + tartEntry.get(1).trim() 
                                    + " = " + tartEntry.get(2).trim();
                                    if(! targetLine.endsWith(" = n/a")) {
                                        targetLines.add(targetLine);
                                    }
                                    tartEntry = new ArrayList<String>();
                                }
                            }
                        }
                    }
                }
                lastKey = key;
            }
            Files.write(target, targetLines);
        }
        catch (IOException e) {
            LOGGER.error("Error reading file: " + source, e);
        }
    }
}
