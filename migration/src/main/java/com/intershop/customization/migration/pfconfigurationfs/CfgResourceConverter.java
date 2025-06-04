package com.intershop.customization.migration.pfconfigurationfs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.intershop.customization.migration.utils.FileUtils;
import org.slf4j.LoggerFactory;

/**
 * Helper class to Convert ICM 7.10 configuration .resource file to .properties file. They are used for
 * <ul>
 *     <li>transport settings - * _transport.resource</li>
 *     <li>application - * _appprfrnce.resource </li>
 *     <li>user credentials - * _usr.resource </li>
 *     <li>manages services - *_mngdsrvc.resource</li> 
•	   <li> domain preferences - * _dmnprfrnce.resource </li> 
•	</7ul> and converted into property files.<br/>
 * 
 * <p/>
 * background: "When using the “Test System Configuration Solution Kit”, a rework is necessary according to
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

    /**
     * The resource type to be converted, mapping to the resource file name.
     * <ul>
     * <li>transport - name *_transport.resource</li>
     * <li>application preferencees - name *_appprfrnce.resource</li>
     * <li>usr - name *_usr.resource</li>
     * <li>mngdsrvc - name *_mngdsrvc.resource</li>
     * <li>dmnprfrnce - name *_dmnprfrnce.resource</li>
     * </ul>
     */
    public enum ResourceType
    {
        TRANSPORT("transport"), 
        APPLICATION("application"), 
        USR("usr"), 
        MNGDSRVC("mngdsrvc"), 
        DMNPRFRNCE("dmnprfrnce"), 
        UNKNOWN("");;

        private final String value;

        // Constructor
        ResourceType(String value)
        {
            this.value = value;
        }

        // Getter method
        public String getValue()
        {
            return value;
        }

        public static ResourceType fromValue(String input) {
            for (ResourceType type : ResourceType.values()) {
                if (type.getValue().equals(input)) {
                    return type;
                }
            }
            return UNKNOWN;
        }

        public String getPrefix()
        {
            return ResourceType.UNKNOWN.getValue().equals(this.value) ? "" : "pfconfigurationfs>" + value;
        }
    }

    private Path source;
    private Path target;
    private String resourceType;

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(CfgResourceConverter.class);

    /**
     * constructor<br/>
     * 
     * @param resourceType the type of the resource, e.g. "transport", prefixed as "pfconfigurationfs>transport"
     * @param source the source file to convert
     * @param target the target file to write the converted content
     */
    public CfgResourceConverter(String resourceType, Path source, Path target)
    {
        this.source = source;
        this.target = target;

        this.resourceType = ResourceType.fromValue(resourceType).getValue();


    }

    /**
     * convert am ICM 7.10 reaource configuration to an ICM 11+ property file
     */
    public void convertResource() throws IOException
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
            List<String> lines = FileUtils.readAllLines(source);
            List<String> targetLines = new ArrayList<>();

            if (ResourceType.TRANSPORT.getValue().equals(this.resourceType)
                || ResourceType.APPLICATION.getValue().equals(this.resourceType))
            {
                targetLines = migrateTransportCfg(lines);
            }
            else if (ResourceType.USR.getValue().equals(this.resourceType)
                     || ResourceType.DMNPRFRNCE.getValue().equals(this.resourceType))
            {
                targetLines = migrateSimpleCfg(lines);
            }
            else if (ResourceType.MNGDSRVC.getValue().equals(this.resourceType))
            {
                targetLines = migrateManagedServiceCfg(lines);
            }
            else
            {
                LOGGER.debug("Cannot convert file {}", source);
            }

            FileUtils.writeLines(target, targetLines);
            // at least for local development
            LOGGER.debug("Converted file {} ==>  {}.", source, target);
        }
        catch(IOException e)
        {
            LOGGER.error("Converting failed for file {} ==>  {}.", source, target);
            e.printStackTrace();
            throw new IOException("Error reading file: " + source, e);
        }

    }

    /**
     * Similar for user credentials and domain preferences
     * <p/>
     * The ICm 7.10 configuration:<br/>
     * #ParameterName# = #Value# InactivityPeriod = 0 <br/>
     * gets converted to ICM11+:<br/>
     * pfconfigurationfs>dmnprfrnce>#ParameterName# = #Value#<br/>
     * pfconfigurationfs>dmnprfrnce>InactivityPeriod = 0
     * 
     * @param lines the lines of the source file
     * @return targetLines the lines of the target file
     */
    private ArrayList<String> migrateSimpleCfg(List<String> lines)
    {
        String targetLine = "";
        ArrayList<String> targetLines = new ArrayList<>();

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
                targetLine = ResourceType.fromValue(this.resourceType).getPrefix() + ">" + line.trim();
                targetLines.add(targetLine);
            }
        }
        return targetLines;
    }

    /**
     * similar for configurations of #type# transport and application
     * <p/>
     * The ICM 7.10 configuration:<br/>
     * <br/>
     * gets converted to ICM11+:<br/>
     * pfconfigurationfs>#type#>#UrlIdentifier#>#ParameterName# = #Value#<br/>
     * pfconfigurationfs>#type#>rest>ExternalApplicationBaseURL = https://int-live-connect.roehm.com<br/>
     * whereby<br/>
     * #ParameterName# to #Value# for the application determined by <site = processed domain>&#UrlIdentifier#.<br/>
     * 
     * @param lines the lones of the source file
     * @return targetLines the lines of the target file
     */
    private ArrayList<String> migrateTransportCfg(List<String> lines)
    {
        ArrayList<String> targetLines = new ArrayList<>();

        // Process and write lines to another file
        String targetLine = "";
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
                        cfgKey = cfgKey.substring(cfgKey.indexOf("."), cfgKey.length()).trim();
                    }
                }

                // gather configuration paremeters

                List<String> sourceEentry = Arrays.asList(cfgGroup, cfgKey, cfgValue);
                if (tartEntry.size() < 3)
                {
                    tartEntry.add(sourceEentry.get(2).trim());
                }
                if (tartEntry.size() == 3)
                {
                    String groupStr = tartEntry.get(0).trim();
                    if (ResourceType.APPLICATION.getValue().equals(this.resourceType))
                    {
                        groupStr = cfgDomainDir + ">" + groupStr;
                    }
                    targetLine = ResourceType.fromValue(this.resourceType).getPrefix() + ">" + groupStr + ">" + tartEntry.get(1).trim() + " = "
                                    + tartEntry.get(2).trim();
                    if (!targetLine.endsWith(" = n/a"))
                    {
                        targetLines.add(targetLine);
                    }
                    tartEntry = new ArrayList<String>();
                }
            }
        }
        return targetLines;
    }

    /**
     * The ICm 7.10 configuration:<br/>
     * ConfigItemX.ServiceDefinitionID=...<br/>
     * ConfigItemX.ServiceConfigurationName=...<br/>
     * ConfigItemX.ParameterName=...<br/>
     * ConfigItemX.Value=...<br/>
     * <br/>
     * gets converted to ICM11+:<br/>
     * pfconfigurationfs>mngdsrvc>#ServiceDefinitionID#>#ServiceConfigurationName#>#ParameterName# = #Value#b#<r/>
     * ...with the 4 values IS7.10
     */
    private ArrayList<String> migrateManagedServiceCfg(List<String> lines)
    {
        ArrayList<String> targetLines = new ArrayList<>();

        // Process and write lines to another file
        String targetLine = "";
        HashMap<String, String> taretEntry = new HashMap<>();

        // String cfgDomainDir = source.getParent().toFile().getName();

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
                if (taretEntry.size() < 4)
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
                if (taretEntry.size() == 4)
                {
                    if (0 >= cfgKey.indexOf("."))
                    {
                        cfgKey = cfgKey.substring(cfgKey.indexOf(".") + 1, cfgKey.length());
                    }
                    cfgGroup = cfgKey.substring(0, cfgKey.indexOf("."));
                    StringBuffer bTargetLine
                    = new StringBuffer().append(ResourceType.fromValue(this.resourceType).getPrefix())
                      .append(">")
                      .append(taretEntry.get( cfgGroup + ".ServiceDefinitionID"))
                      .append(">").append(taretEntry.get(cfgGroup + ".ServiceConfigurationName"))
                      .append(">").append(taretEntry.get(cfgGroup + ".ParameterName"))
                      .append(" = ").append(taretEntry.get(cfgGroup + ".Value"));

                    targetLine = bTargetLine.toString();
                    targetLines.add(targetLine);

                    taretEntry.clear();
                }
            }
        }
        return targetLines;
    }
}
