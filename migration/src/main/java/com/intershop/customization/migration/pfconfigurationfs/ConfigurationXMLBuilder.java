package com.intershop.customization.migration.pfconfigurationfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.intershop.customization.migration.pfconfigurationfs.CfgResourceConverter.ResourceType;

/**
 * This method would contain logic to build the XML configuration
 * based on the cartridge name and the provided configuration XML.
* The implementation details are not provided in the original code snippet.
*/

public class ConfigurationXMLBuilder {

    private String cartridgeName = "";

    private List<String> headerLines = new ArrayList<>();
    private List<String> footerLines = new ArrayList<>();
    private List<String> lines = new ArrayList<>();
    private List<String> commonLines = new ArrayList<>();

    // keep the file names already added to the XML
    private Set  <String> domainResources = new HashSet<>();
    private Set  <String> commondResources = new HashSet<>();

    // vonfigure the mappings to config XML values - see the tati blok
    private static final String FINDER_DOMAIN_RESOURCE      = "domain-resource";

    private static final String SCOPE_DOMAIN                = "domain";

    private static final String PLACEHOLDER_ENVIRONMENT     ="\\$\\{environment\\}";
    // LinkedHashMap to keep the order of replacements - e.g. the issue with (pre)production
    private static final Map<String, String> environments   = new LinkedHashMap<>();

    private static final String PLACEHOLDER_STAGING_SYSTEM_TYPE ="\\$\\{staging.system.type\\}";
    private static final Map<String, String> systemTypes        = new HashMap<>();

    private String lastDopmainName ="";
    private int resourceCfgEntryCounter = 0;

    private static final int MIN_PRIORITY = 60;
    int priority = MIN_PRIORITY;
    int priorityEMptyDOmain = MIN_PRIORITY;

    // vonfigure the mappings to config XML values
     {
        headerLines.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        headerLines.add("<configuration-setup>");
        headerLines.add("\t<sets>");
    
        footerLines.add("\t</sets>");
        footerLines.add("</configuration-setup>");
        
        // environments
        environments.put("development",     PLACEHOLDER_ENVIRONMENT);        
        environments.put("integration",     PLACEHOLDER_ENVIRONMENT);        
        environments.put("preproduction",   PLACEHOLDER_ENVIRONMENT);        
        environments.put("production",      PLACEHOLDER_ENVIRONMENT);        

        // staging system types
        systemTypes.put("editing",  PLACEHOLDER_STAGING_SYSTEM_TYPE);
        systemTypes.put("live",     PLACEHOLDER_STAGING_SYSTEM_TYPE);
    
    }

    /**
     * Constructor for ConfigurationXMLBuilder.
     * Initializes the cartridge name and clears the (domain specific) and the (not domain related) common lines configuration XML lines 
     *
     * @param cartridgeName - the name of the cartridge for which the configuration XML is being built
     */
    ConfigurationXMLBuilder(String cartridgeName) {

        lines.clear();
        commonLines.clear();
        this.cartridgeName = cartridgeName;
    }
    /** gather the data and add a line to the list of configuration xml entries
     * 
     * @param configType - the type of the configuration (e.g. application, transport, etc.)
     * @param domainName - the name of the domain for the configuration, if applicable
     * @param cfgFileName - the name of the configuration file to be added
     * 
     */
    public boolean addLine(String configType, String domainName, String cfgFileName) 
    {
        // if set and scope is "domai", add a domain name
        String xmlDomainName = "";
        if (domainName != null && !domainName.isEmpty() )
        {
            // add a domain if the configuration type requires one
            xmlDomainName = domainName;
        }
        else{
            return false;
        }

        // resource file name relative to the cartridge name
        cfgFileName = cfgFileName.replace("\\", "/")
        .replaceFirst( "^.*" + this.cartridgeName+".config", "config");

        // set variables or environment and staging system type in the file name
        for(Map.Entry<String, String> env: environments.entrySet())
        {
            cfgFileName = cfgFileName.replaceAll(env.getKey(), env.getValue());
        }
        for(Map.Entry<String, String> sys: systemTypes.entrySet())
        {
            cfgFileName = cfgFileName.replaceAll(sys.getKey(), sys.getValue());
        }

        // count up priority by domain starting with 60 for each of them
         if (!xmlDomainName.equals(lastDopmainName))
        {
            lines.add("\t\t<!--- domain " + xmlDomainName + " -->");
            domainResources = new HashSet<>();
            priority = MIN_PRIORITY;
        }        

        if(!domainResources.contains(cfgFileName))
        {   
            priority = calcPriority(cfgFileName, priority);
            lines.add(generateLine(
                FINDER_DOMAIN_RESOURCE,
                SCOPE_DOMAIN, 
                xmlDomainName, 
                cfgFileName, 
                priority,
                this.cartridgeName, 
                false));
            // to ensure the file name is unique in the domain
            // (using variables they can get multiple)
            domainResources.add(cfgFileName);
        }

        lastDopmainName = xmlDomainName;
        return true;
    }

    /**
     * returna the increased priority
     * considering the predefind values for 
     * <ul>
     *   <li>${environment}_*.properties: priority="62"</li>
     *   <li>${environment}_${staging.system.type}_*.properties" priority="64"</li>
     *   <li>increasinv value otherwise</li>
     * </ul>
     * 
     * @param fileName propery file name to be checked
     * @param givenpriority the current domain's or oman independent priority
     * @return the increades priority vallue considering the system type values
     */
    private int calcPriority(String fileName, int givenpriority)
    {
        int priority = givenpriority +1;

        // ommit pre-defined values
        if((62 == priority) || (64 == priority)) priority++;

        // set pre-defined vales
        if(fileName.contains("staging.system.type"))
        {
            priority = 64;
        }
        else if(fileName.contains("environment"))
        {
            priority = 62;
        }

        return priority;
    }
    /** generates a configuration xml entry referring a configuration property file 
     *
     * @param finder - the filter type for the configuration
     * @param scope - the scope of the configuration
     * @param xmlDomainName - the domain name for the configuration if given
     * @param fileName - the name of the configuration file
     * @param priority - the priority of the configuration by domain
     * @param cartridgeName - the name of the cartridge
     * @param required - whether the configuration is required
     * 
     * @return the configuration xml entry
     *  
    */
    private String generateLine(    
        String finder, 
        String scope, 
        String xmlDomainName, 
        String fileName, 
        int priority,
        String cartridgeName, 
        boolean required)
    {
        // count xml entries to ensure there are convertes resource files
        resourceCfgEntryCounter++;
        // combine the xml entry
        return new StringBuffer( "\t\t<set")
        .append(" finder=\"").append(finder)
        .append("\" scope=\"").append(scope)
        .append("\" domainName=\"" + xmlDomainName)
        .append("\" resourceName=\"").append(fileName)
        .append("\" priority=\"").append(priority)
        .append("\" cartridge=\"").append(cartridgeName)
        .append("\" required=\"").append(required)
        .append("\"/>").toString();
    }

    /**
     * Generates the configuration XML as a list of strings.
     * This method combines the header, common, domain specific and footer lines
     * with reach line referring a configuration property file.
     *
     * @return a list of strings representing the configuration XML
     */
    public List<String>  generateConfigXML() 
    {
        ArrayList<String>  xmlLines = new ArrayList<>();
        xmlLines.addAll(headerLines);
        xmlLines.addAll(commonLines);
        xmlLines.addAll(lines);
        xmlLines.addAll(footerLines);

        return xmlLines;
    }

    /**
     * Returns the number of generated configuration entries.
     * This there are none, no configuration.xml is needed
     *
     * @return the count of generated configuration entries
     */
    public int getGeneratedEntriesCount() {
        return resourceCfgEntryCounter;
    }

}
