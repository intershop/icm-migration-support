package com.intershop.customization.migration.pfconfigurationfs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashMap;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.Migrator;

/**
 * helper class to Convert ICM 7.10 configuration .resource file into .properties file. They are used for
 * <ul>
 * <li>transport - name *_transport.resource</li>
 * <li>application preferencees - name *_appprfrnce.resource</li>
 * <li>user - name *_usr.resource</li>
 * <li>... w.i.p</li> <7ul> and converted into prooertty files.<br/>
 * 
 * <p/>
 * Bacgground: "When using the “Test System Configuration Solution Kit”, a rework is necessary according to
 * pf_configuration_fs versions for IS7.10 vs. for ICM11. See
 * <ul>
 * <li>"Cookbook - 7.10 Test System Configuration Solution" Kit vs.</li>
 * <li>"Cookbook - ICM 11 Test System Configuration Solution Kit"</li>.
 * </ul>
 * The *.resource files need to be migrated to *.properties files and wired in cartridge-specific configuration.xml
 * file."
 * 
 * <p/>
 * <b><u>.resource types</u><b>
 * <p/>
 * <u>usr  -* _transport.resource</u>
 * <p/>
 * <u>usr  -* _usr.resource</u>
 * <p/>
 * <u>usr  -* _usr.resource</u>
 * <p/>
 * 
 */
public class CfgResourceConverter
{

    private Path source;
    private Path target;
    private String resourceType;

    private String prefix = "";

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CfgResourceConverter.class);

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
            case "service":
                this.prefix = "pfconfigurationfs>mngdsrvc";
                break;
            case "domain":
//                this.prefix = "pfconfigurationfs>dmnprfrnc";
                return;
            default:
                LOGGER.warn("Unknown resource type: {}", resourceType);
                return;
        }
        this.resourceType = resourceType;
    }

    /**
     * onvert a transport configuration
     */
    public void convertResource()
    {
        if (this.resourceType.isEmpty())
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

        try
        {
            // Read lines from a file
            List<String> lines = Files.readAllLines(source);
            ArrayList<String> targetLines = new ArrayList<>();

            if("application".equals(this.resourceType) || "transport".equals(this.resourceType))
            {
                targetLines = migrateTransportCfg(lines);
            }
            else if ("user".equals(this.resourceType))
            {
                targetLines = migrateUserCfg(lines);
            }
            else if ("service".equals(this.resourceType))
            {
                targetLines = migrateManagedServiceCfg(lines);
            }
            else
            {
                //return;
            }

            Files.write(target, targetLines);
            LOGGER.debug("Convered file {} ==>  {}.", source, target);
        }
        catch(IOException e)
        {
            LOGGER.error("Error reading file: " + source, e);
        }

    }

    private ArrayList<String> migrateUserCfg(List<String> lines)
     {
        String targetLine = "";
        ArrayList<String> targetLines = new ArrayList<>();

        for (String line : lines)
        {
            line = line.trim();
            String key = "";

            // transport resource file
            if (line.isEmpty() || (line.startsWith("#")))
            {
                targetLine = line;
                targetLines.add(targetLine);
            }
            else
            {
                    targetLine = this.prefix + ">" + line.trim();
                    targetLines.add(targetLine);
            }
        }
        return targetLines;
    }

    private ArrayList<String> migrateTransportCfg(List<String> lines) {
        ArrayList<String> targetLines = new ArrayList<>();

        // Process and write lines to another file
        String lastKey = "";
        String targetLine = "";
        String cfgDomainDir = source.getParent().toFile().getName();

        // fuill target line
        ArrayList<String> tartEntry = new ArrayList<>();

        for (String line : lines)
        {
            line = line.trim();
            String key = "";

            // transport resource file
            if (line.isEmpty() || (line.startsWith("#")))
            {
                targetLine = line;
                targetLines.add(targetLine);
                lastKey = "";
            }
            else
            {
                String[] entry = line.split("=");

                // scam inputz lin
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
                        lastKey = cfgGroup;
                        cfgKey = cfgKey.substring(cfgKey.indexOf("."), cfgKey.length()).trim();
                    }
                }

                // gather configuration paremeters

                List<String> sourceEentry = Arrays.asList(cfgGroup, cfgKey, cfgValue);
                key = sourceEentry.get(0).trim();
                if (sourceEentry.size() == 3)
                {
                    if (tartEntry.size() < 3)
                    {
                        tartEntry.add(sourceEentry.get(2).trim());
                    }
                    if (tartEntry.size() == 3)
                    {
                    
                        String groupStr = tartEntry.get(0).trim();
                        if ("application".equals(this.resourceType))
                        {
                            groupStr = cfgDomainDir + ">" + groupStr;
                        }
                        targetLine = this.prefix 
                        + ">" + groupStr
                        + ">" + tartEntry.get(1).trim() 
                        + " = " + tartEntry.get(2).trim();
                        if (!targetLine.endsWith(" = n/a"))
                        {
                            targetLines.add(targetLine);
                        }
                        tartEntry = new ArrayList<String>();
                    }
                }
            }
        }
        return targetLines;
    }

    /**
     * The ICm 7.10 configuration<br/>  
     * ConfigItemX.ServiceDefinitionID=...br/> 
     * ConfigItemX.ServiceConfigurationName=...br/> 
     * ConfigItemX.ParameterName=...br/> 
     * ConfigItemX.Value=...br/> 
     * br/> 
     * gets converted to  ICM11+:br/> 
     * pfconfigurationfs>mngdsrvc>#ServiceDefinitionID#>#ServiceConfigurationName#>#ParameterName# = #Value#br/> 
     * ...with the 4 values IS7.10
     */
    private ArrayList<String> migrateManagedServiceCfg(List<String> lines) {
        ArrayList<String> targetLines = new ArrayList<>();


        // Process and write lines to another file
        String lastKey = "";
        String targetLine = "";
        HashMap<String, String> taretEntry = new HashMap<>();

        String cfgDomainDir = source.getParent().toFile().getName();

        // fuill target line
        ArrayList<String> tartEntry = new ArrayList<>();

        for (String line : lines)
        {
            line = line.trim();

            // transport resource file
            if (line.isEmpty() || (line.startsWith("#")))
            {
                targetLine = line;
                targetLines.add(targetLine);
            }
            else
            {
                // scan input line 

                String cfgGroup = "";
                String cfgKey = "";
                String cfgValue = "";

                // gather the source data
                if(taretEntry.size() < 4)
                {
                    // scan the source line
                    String[] entry = line.split("=");
                    if (entry.length == 2)
                    {
                        cfgKey = entry[0].trim();
                        cfgValue = entry[1].trim();
                        if (0 >= cfgKey.indexOf("."))
                        {
                            cfgGroup = cfgKey.substring(0, cfgKey.indexOf(".") - 1);
                            cfgKey = cfgKey.substring(cfgKey.indexOf("."), cfgKey.length()).trim();
                        }
                    }
                    taretEntry.put(cfgKey, cfgValue);
                }
                // all values found - build and add the target line and 
                // reset the source data
                if(taretEntry.size() == 4)
                {
                    String keys="";for(String k : taretEntry.keySet()) 
                    { 
                        cfgGroup = cfgKey.substring(0, cfgKey.indexOf("."));
                        break;
                    }
                    StringBuffer bTargetLine = new StringBuffer()
                    .append(this.prefix).append(">")
                    .append(taretEntry.get(cfgGroup+".ServiceDefinitionID")).append(">")
                    .append(taretEntry.get(cfgGroup+".ServiceConfigurationName")).append(">")
                    .append(taretEntry.get(cfgGroup+".ParameterName")).append(" = ")
                    .append(taretEntry.get(cfgGroup+".Value"));
                    
                    targetLine = bTargetLine.toString();
                    targetLines.add(targetLine);
                    
                    taretEntry.clear();
                }

                lastKey = cfgGroup;
            }
        }
        return targetLines;
    }
}
