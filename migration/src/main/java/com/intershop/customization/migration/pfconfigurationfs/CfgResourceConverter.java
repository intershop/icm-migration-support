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
    public static final String PROPERTY_KEY_SEPARATOR = ">";
    public static final String PROPERTY_KEY_PREFIX = "pfconfigurationfs";
    public static final String PROPERTY_KEY_OBJECT_TYPE_APPLICATION = "appprfrnce";
    public static final String PROPERTY_KEY_OBJECT_TYPE_DOMAIN      = "dmnprfrnce";
    public static final String PROPERTY_KEY_OBJECT_TYPE_JOB         = "job";
    public static final String PROPERTY_KEY_OBJECT_TYPE_SERVICE     = "mngdsrvc";
    public static final String PROPERTY_KEY_OBJECT_TYPE_PAYMENT     = "pmntsrvc";
    public static final String PROPERTY_KEY_OBJECT_TYPE_TRANSPORT   = "transport";
    public static final String PROPERTY_KEY_OBJECT_TYPE_USER        = "usr";
    public static final String DEFAULT_APPLICATION_URL_IDENTIFIER = "rest";
    
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
        APPPRFRNC("appprfrnc"),  // usually *.resource files
        APPPRFRNCE("appprfrnce"),  // usually *.resource files
        APPDMNPRF("appdmnprf"),  // usually *.properties files
        USR("usr"), 
        MNGDSRVC("mngdsrvc"), 
        PMTSRVC("pmntsrvc"), 
        DMNPRFRNCE("dmnprfrnce"), 
        JOB("job"),
        UNKNOWN("");

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
            String propertyKeyPrefix = PROPERTY_KEY_PREFIX;
            switch (this)
            {
                case TRANSPORT:
                    propertyKeyPrefix += PROPERTY_KEY_SEPARATOR + PROPERTY_KEY_OBJECT_TYPE_TRANSPORT;
                    break;
                case USR:
                    propertyKeyPrefix += PROPERTY_KEY_SEPARATOR + PROPERTY_KEY_OBJECT_TYPE_USER;
                    break;
                case MNGDSRVC:
                    propertyKeyPrefix += PROPERTY_KEY_SEPARATOR + PROPERTY_KEY_OBJECT_TYPE_SERVICE;
                    break;
                case PMTSRVC:
                    propertyKeyPrefix += PROPERTY_KEY_SEPARATOR + PROPERTY_KEY_OBJECT_TYPE_PAYMENT;
                    break;
                case DMNPRFRNCE:
                    propertyKeyPrefix += PROPERTY_KEY_SEPARATOR + PROPERTY_KEY_OBJECT_TYPE_DOMAIN;
                    break;
                case JOB:
                    propertyKeyPrefix += PROPERTY_KEY_SEPARATOR + PROPERTY_KEY_OBJECT_TYPE_JOB;
                    break;
                case APPLICATION:
                case APPPRFRNC:
                case APPPRFRNCE:
                case APPDMNPRF:
                    propertyKeyPrefix += PROPERTY_KEY_SEPARATOR + PROPERTY_KEY_OBJECT_TYPE_APPLICATION;
                    break;
                default:
                    LOGGER.error("Unknown resource type. Unable to determine the correct property key prefix.");
            }

            return propertyKeyPrefix;
        }
    }

    private Path source;
    private Path target;
    private ResourceType resourceType;

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

        this.resourceType = ResourceType.fromValue(resourceType);


    }

    /**
     * convert am ICM 7.10 reaource configuration to an ICM 11+ property file
     */
    public void convertResource() throws IOException
    {
        if (ResourceType.UNKNOWN == this.resourceType)
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

            if ( ResourceType.TRANSPORT == this.resourceType)
            {
                targetLines = migrateTransportCfg(lines);
            }
            else if (ResourceType.APPLICATION == this.resourceType
                || ResourceType.APPPRFRNC == this.resourceType
                || ResourceType.APPPRFRNCE == this.resourceType
                || ResourceType.APPDMNPRF == this.resourceType)  // ResourceType.APPDMNPRF ("appdmnprf") is usually a *.properties file and not a *.resource file!
            {
                targetLines = migrateApplicationCfg(lines);
            }
            else if (ResourceType.USR  ==this.resourceType
                     || ResourceType.DMNPRFRNCE == this.resourceType)
            {
                targetLines = migrateSimpleCfg(lines);
            } 
            else if ( ResourceType.MNGDSRVC == this.resourceType)
            {
                targetLines = migrateManagedServiceCfg(lines);
            }
            else if ( ResourceType.PMTSRVC == this.resourceType)
            {
                targetLines = migratePaymantServiceCfg(lines);
            }
            else if ( ResourceType.JOB == this.resourceType)
            {
                targetLines = migrateJobCfg(lines);
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
                targetLine = this.resourceType.getPrefix() + PROPERTY_KEY_SEPARATOR + line.trim();
                targetLines.add(targetLine);
            }
        }
        return targetLines;
    }

    /**
     * Application domain preferences contained in a *.resource file.
     * Usually *_appprfrnc.resource or *_appprfrnce.resource.
     * By default, these files do not contain an explicit application (URL identifier),
     * but a default application is not supported by ICM 11+ pf_configuration_fs yet.
     * Using constant DEFAULT_APPLICATION_URL_IDENTIFIER = "rest".
     * 
     * @param lines the lines of the source file
     * @return targetLines the lines of the target file
     */
    private ArrayList<String> migrateApplicationCfg(List<String> lines)
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
                targetLine = this.resourceType.getPrefix() + PROPERTY_KEY_SEPARATOR + DEFAULT_APPLICATION_URL_IDENTIFIER + PROPERTY_KEY_SEPARATOR + line.trim();
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
     * pfconfigurationfs>#type#>rest>ExternalApplicationBaseURL = https://int-live-connect.example.com<br/>
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

        // fuill target line
        ArrayList<String> targetEntry = new ArrayList<>();

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
                String cfgKey = "";
                String cfgValue = "";
                if (entry.length == 2)
                {
                    cfgKey = entry[0].trim();
                    cfgValue = entry[1].trim();
                }

                // gather configuration paremeters

                String sourceValue = cfgValue;
                if (targetEntry.size() < 3)
                {
                    targetEntry.add(sourceValue);
                }
                if (targetEntry.size() == 3)
                {
                    String groupStr = targetEntry.get(0).trim();
                    targetLine = this.resourceType.getPrefix() + PROPERTY_KEY_SEPARATOR + groupStr + PROPERTY_KEY_SEPARATOR + targetEntry.get(1).trim() + " = "
                                    + targetEntry.get(2).trim();
                    if (!targetLine.endsWith(" = n/a"))
                    {
                        targetLines.add(targetLine);
                    }
                    targetEntry = new ArrayList<String>();
                }
            }
        }

        if (targetEntry.size() > 0)
        {
            LOGGER.error("Incomplete configuration entry found in file {}, expecting 3 lines per configuration", source);
        }

        return targetLines;
    }

    /**
     * Similar to configurations of #type# transport and application,
     * but EnabledFlag=true/false needs to be converted to Enabled = "true"|"false"
     * and spaces in job names need to be escaped with a backslash.
     * <p/>
     * The ICM 7.10 configuration:<br/>
     * <br/>
     * gets converted to ICM11+:<br/>
     * pfconfigurationfs>job>#JobName#>#AttributeName# = #Value#<br/>
     * pfconfigurationfs>job>Regular\ Replication\ Process>ReplicationProcessID = MyReplicationProcess<br/>
     * pfconfigurationfs>job>Regular\ Replication\ Process>Enabled = false<br/>
     * 
     * @param lines the lines of the source file
     * @return targetLines the lines of the target file
     */
    private ArrayList<String> migrateJobCfg(List<String> lines)
    {
        ArrayList<String> targetLines = new ArrayList<>();

        // Process and write lines to another file
        String targetLine = "";

        // fuill target line
        ArrayList<String> targetEntry = new ArrayList<>();

        for (String line : lines)
        {
            line = line.trim();

            // job resource file
            if (line.isEmpty() || (line.startsWith("#")))
            {
                targetLine = line;
                targetLines.add(targetLine);
            }
            else
            {
                String[] entry = line.split("=");

                // scan input line
                String cfgJobName = "";
                String cfgKey = "";
                String cfgValue = "";
                if (entry.length == 2)
                {
                    cfgKey = entry[0].trim();
                    cfgValue = entry[1].trim();
                }

                // gather configuration parameters

                String sourceValue = cfgValue;
                if (targetEntry.size() < 3)
                {
                    targetEntry.add(sourceValue);
                }
                if (targetEntry.size() == 3)
                {
                    String jobName       = targetEntry.get(0);
                    String attributeName = targetEntry.get(1);
                    jobName       = quotePropertyKey(jobName); // Escape spaces in job name, because it is used as part of a property key in a properties file
                    attributeName = quotePropertyKey(attributeName); // Escape spaces in attribute name, because it is used as part of a property key in a properties file
                    if (attributeName.equalsIgnoreCase("EnabledFlag"))
                    {
                        attributeName = "Enabled";
                        if (targetEntry.get(2).equalsIgnoreCase("true"))
                        {
                            targetEntry.set(2, "true");
                        }
                        else if (targetEntry.get(2).equalsIgnoreCase("false"))
                        {
                            targetEntry.set(2, "false");
                        }
                        else
                        {
                            // leave targetEntry as-is, but log a warning
                            LOGGER.warn("Unexpected value for job config '{}' (job name '{}') parameter 'EnabledFlag': {}. Expected 'true' or 'false'.", cfgJobName, jobName, targetEntry.get(2));
                        }
                    }
                    targetLine = this.resourceType.getPrefix() + PROPERTY_KEY_SEPARATOR + jobName + PROPERTY_KEY_SEPARATOR + attributeName + " = "
                                    + targetEntry.get(2).trim();
                    if (!targetLine.endsWith(" = n/a"))
                    {
                        targetLines.add(targetLine);
                    }
                    targetEntry = new ArrayList<String>();
                }
            }
        }

        if (targetEntry.size() > 0)
        {
            LOGGER.error("Incomplete configuration entry found in file {}, expecting 3 lines per job configuration", source);
        }

        return targetLines;
    }

    /**
     * Property keys in properties files have some restrictions (see JavaDoc of java.util.Properties):
     * Do not use spaces in the keys! Not even around the > separators!
     * In keys you need to quote:
     * - Space ('\ ', '\u0020')
     * - Tab ('\t', '\u0009')
     * - Form feed ('\f', '\u000C')
     * - Equals sign ('\=')
     * - Colon ('\:')
     * Unfortunately you cannot quote a > inside a key/name, because pf_configuration_fs does not support any quoting. (Only java.util.Properties supports it, before handing over the keys to the application.)
     * Example quoting:
     * - "My Parameter Name" --> "My\ Parameter\u0020Name"
     * 
     * @param key the property key to quote
     * @return the quoted property key
     */
    public static String quotePropertyKey(String key) {
        if (key == null || key.isEmpty() || !key.contains(" ")) {
            return key; // Nothing to change
        }

        StringBuffer quotedKey = new StringBuffer(key.length() * 2); // Allocate enough space for the worst case
        char lastChar = '\0';
        for (char c : key.toCharArray()) {
            if (c == ' ' && lastChar != '\\') {
                quotedKey.append('\\');
            }
            quotedKey.append(c);
            lastChar = c;
        }

        return quotedKey.toString();
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
        HashMap<String, String> targetEntry = new HashMap<>();

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
                if (targetEntry.size() < 4)
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
                    targetEntry.put(cfgKey, cfgValue);
                }
                // all values found - build and add the target line and
                // reset the source data
                if (targetEntry.size() == 4)
                {
                    if (0 >= cfgKey.indexOf("."))
                    {
                        cfgKey = cfgKey.substring(cfgKey.indexOf(".") + 1, cfgKey.length());
                    }
                    cfgGroup = cfgKey.substring(0, cfgKey.indexOf("."));
                    StringBuffer bTargetLine
                    = new StringBuffer().append(this.resourceType.getPrefix())
                      .append(PROPERTY_KEY_SEPARATOR)
                      .append(targetEntry.get( cfgGroup + ".ServiceDefinitionID"))
                      .append(PROPERTY_KEY_SEPARATOR).append(targetEntry.get(cfgGroup + ".ServiceConfigurationName"))
                      .append(PROPERTY_KEY_SEPARATOR).append(targetEntry.get(cfgGroup + ".ParameterName"))
                      .append(" = ").append(targetEntry.get(cfgGroup + ".Value"));

                    targetLine = bTargetLine.toString();
                    targetLines.add(targetLine);

                    targetEntry.clear();
                }
            }
        }

        if (targetEntry.size() > 0)
        {
            LOGGER.error("Incomplete configuration entry found in file {}, expecting 4 lines per managed service configuration", source);
        }

        return targetLines;
    }

    /**
     * The ICm 7.10 configuration:<br/>
     * ConfigItemX.PaymentServiceID=...<br/>
     * ConfigItemX.PaymentServiceConfigurationID=...<br/>
     * ConfigItemX.ParameterName=...<br/>
     * ConfigItemX.Value=...<br/>
     * <br/>
     * gets converted to ICM11+:<br/>
     * pfconfigurationfs>mngdsrvc>#PaymentServiceID#>#PaymentServiceConfigurationID#>#ParameterName# = #Value#b#<r/>
     * ...with the 4 values IS7.10
     */
    private ArrayList<String> migratePaymantServiceCfg(List<String> lines)
    {
        ArrayList<String> targetLines = new ArrayList<>();

        // Process and write lines to another file
        String targetLine = "";
        HashMap<String, String> targetEntry = new HashMap<>();

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
                if (targetEntry.size() < 4)
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
                    targetEntry.put(cfgKey, cfgValue);
                }
                // all values found - build and add the target line and
                // reset the source data
                if (targetEntry.size() == 4)
                {
                    if (0 >= cfgKey.indexOf("."))
                    {
                        cfgKey = cfgKey.substring(cfgKey.indexOf(".") + 1, cfgKey.length());
                    }
                    cfgGroup = cfgKey.substring(0, cfgKey.indexOf("."));
                    StringBuffer bTargetLine
                    = new StringBuffer().append(this.resourceType.getPrefix())
                      .append(PROPERTY_KEY_SEPARATOR)
                      .append(targetEntry.get( cfgGroup + ".PaymentServiceID"))
                      .append(PROPERTY_KEY_SEPARATOR).append(targetEntry.get(cfgGroup + ".PaymentServiceConfigurationID"))
                      .append(PROPERTY_KEY_SEPARATOR).append(targetEntry.get(cfgGroup + ".ParameterName"))
                      .append(" = ").append(targetEntry.get(cfgGroup + ".Value"));

                    targetLine = bTargetLine.toString();
                    targetLines.add(targetLine);

                    targetEntry.clear();
                }
            }
        }

        if (targetEntry.size() > 0)
        {
            LOGGER.error("Incomplete configuration entry found in file {}, expecting 4 lines per payment service configuration", source);
        }

        return targetLines;
    }
}
