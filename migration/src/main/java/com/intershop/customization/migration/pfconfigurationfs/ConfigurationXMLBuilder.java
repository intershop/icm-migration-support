package com.intershop.customization.migration.pfconfigurationfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final String FILTERE_RESOURCE            = "resource";
    private static final String FILTER_DOMAINE_RESOURCE     = "domain-resource";
    private static final Map<String, String> filters        = new HashMap<>();

    private static final String SCOPE_DOMAIN                = "domain";
    private static final String SCOPE_CLUSTER_SERVER_DOMAIN = "cluster,server,domain";
    private static final Map<String, String> scopes         = new HashMap<>();

    private static final String PLACEHOLDER_ENVIRONMENT     ="\\$\\{environment\\}";
    private static final Map<String, String> environments   = new HashMap<>();

    private static final String PLACEHOLDER_STAGING_SYSTEM_TYPE ="\\$\\{staging.system.type\\}";
    private static final Map<String, String> systemTypes        = new HashMap<>();

    private String lastDopmainName ="";
    private int resourceCfgEntryCounter = 0;

    private static final int MIN_PRIORITY = 61;
    int priority = MIN_PRIORITY;
    int priorityEMptyDOmain = MIN_PRIORITY;

    // vonfigure the mappings to config XML values
     {
        headerLines.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        headerLines.add("<configuration-setup>");
        headerLines.add("\t<sets>");
    
        footerLines.add("\t</sets>");
        footerLines.add("</configuration-setup>");
        
        // Initialize filters map
        filters.put(ResourceType.APPLICATION.getValue(), FILTERE_RESOURCE);
        filters.put(ResourceType.DMNPRFRNCE.getValue(),  FILTER_DOMAINE_RESOURCE);
        filters.put(ResourceType.MNGDSRVC.getValue(),    FILTERE_RESOURCE);
        filters.put(ResourceType.TRANSPORT.getValue(),   FILTER_DOMAINE_RESOURCE);
        filters.put(ResourceType.USR.getValue(),         FILTERE_RESOURCE);
        filters.put(ResourceType.UNKNOWN.getValue(),     FILTER_DOMAINE_RESOURCE);
    
        // Initialize scope map
        scopes.put(ResourceType.APPLICATION.getValue(), SCOPE_DOMAIN);
        scopes.put(ResourceType.DMNPRFRNCE.getValue(),  SCOPE_DOMAIN);
        scopes.put(ResourceType.MNGDSRVC.getValue(),    SCOPE_DOMAIN);
        scopes.put(ResourceType.TRANSPORT.getValue(),   SCOPE_DOMAIN);
        scopes.put(ResourceType.USR.getValue(),         SCOPE_CLUSTER_SERVER_DOMAIN);
        scopes.put(ResourceType.UNKNOWN.getValue(),     SCOPE_DOMAIN);

        // TODO issue: Are the keys used from here uniquely defined by ADO?

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
    public void addLine(String configType, String domainName, String cfgFileName) 
    {
        // if set and scope is "domai", add a domain name
        String xmlDomainName = "";
        if (domainName != null && !domainName.isEmpty() && (scopes.get(configType)).contains(SCOPE_DOMAIN) )
        {
            // add a domain if the configuration type requires one
            xmlDomainName = domainName;
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

        if (xmlDomainName.isEmpty())
        {
            if(!commondResources.contains(cfgFileName))
            {
                // count up priority for entries without a domain  starting with 60.
                commonLines.add( generateLine(
                    filters.get(configType),
                    scopes.get(configType), 
                    xmlDomainName, 
                    cfgFileName, 
                    priorityEMptyDOmain,
                    this.cartridgeName, 
                    false));
                commondResources.add(cfgFileName);
                priorityEMptyDOmain++;
            }
       }
       else
       {
            // count up priority by domain starting with 60 for each of them
             if (xmlDomainName.equals(lastDopmainName))
            {
                if(!domainResources.contains(cfgFileName))
                {   
                    lines.add(generateLine(
                        filters.get(configType),
                        scopes.get(configType), 
                        xmlDomainName, 
                        cfgFileName, 
                        priority,
                        this.cartridgeName, 
                        false));
                    priority++;
                    // to ensure the file name is unique in the domain
                    // (using variables they can get multiple)
                    domainResources.add(cfgFileName);
                }
            }
            else
            {
                lines.add("\t\t<!--- domain " + xmlDomainName + " -->");
                domainResources = new HashSet<>();
                priority = MIN_PRIORITY;
            }        
        } 

        lastDopmainName = xmlDomainName;
    }
    /** generates a configuration xml entry referring a configuration property file 
     *
     * @param filter - the filter type for the configuration
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
        String filter, 
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
        .append("  filter=\"").append(filter)
        .append("\"  scope=\"").append(scope)
        .append((!xmlDomainName.isEmpty()) ? "\" domain=\"" + xmlDomainName : "")
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
