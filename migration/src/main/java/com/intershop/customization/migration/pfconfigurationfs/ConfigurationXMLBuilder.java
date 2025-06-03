package com.intershop.customization.migration.pfconfigurationfs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.intershop.customization.migration.pfconfigurationfs.CfgResourceConverter.ResourceType;

/**
 * This method would contain logic to build the XML configuration
 * based on the cartridge name and the provided configuration XML.
* The implementation details are not provided in the original code snippet.
*/

public class ConfigurationXMLBuilder {

    private String cartridgeName = "";

    private ArrayList<String> headerLines = new ArrayList<>();
    private ArrayList<String> footerLines = new ArrayList<>();
    private ArrayList<String> lines = new ArrayList<>();
    private ArrayList<String> commonLines = new ArrayList<>();

    // keep the file names already added to the XML
    private HashSet <String> domainResources = new HashSet<>();
    private HashSet <String> commondResources = new HashSet<>();

    // vonfigure the mappings to config XML values - see the tati blok
    private HashMap<String, String> filters = new HashMap<>();
    private HashMap<String, String> scopes = new HashMap<>();
    private HashMap<String, String> environments = new HashMap<>();
    private HashMap<String, String> systemTypes = new HashMap<>();

    private String lastDopmainName ="";

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
        filters.put(ResourceType.APPLICATION.getValue(), "resource");
        filters.put(ResourceType.DMNPRFRNCE.getValue(),  "domain-resource");
        filters.put(ResourceType.MNGDSRVC.getValue(),    "resource");
        filters.put(ResourceType.TRANSPORT.getValue(),   "domain-resource");
        filters.put(ResourceType.USR.getValue(),         "resource");
        filters.put(ResourceType.UNKNOWN.getValue(),     "domain-resource");
    
        // Initialize scope map
        scopes.put(ResourceType.APPLICATION.getValue(), "domain");
        scopes.put(ResourceType.DMNPRFRNCE.getValue(),  "domain");
        scopes.put(ResourceType.MNGDSRVC.getValue(),    "domain");
        scopes.put(ResourceType.TRANSPORT.getValue(),   "domain");
        scopes.put(ResourceType.USR.getValue(),         "cluster,server,domain");
        scopes.put(ResourceType.UNKNOWN.getValue(),     "domain");

        // TODO issue: Are the keys used from here uniquely defined by ADO?

        // environments
        environments.put("development",     "\\$\\{environment\\}");        
        environments.put("integration",     "\\$\\{environment\\}");        
        environments.put("preproduction",   "\\$\\{environment\\}");        
        environments.put("production",      "\\$\\{environment\\}");        

        // staging system types
        systemTypes.put("editing",  "\\$\\{staging.system.type\\}");
        systemTypes.put("live",     "\\$\\{staging.system.type\\}");
    
    }
    ConfigurationXMLBuilder(String cartridgeName) {

        lines.clear();
        commonLines.clear();
        this.cartridgeName = cartridgeName;
    }

    public void addLine(String configType, String domainName, String cfgFileName) 
    {
        // if set and scope is "domai", add a domain name
        String xmlDomainName = "";
        if (domainName != null && !domainName.isEmpty() && (0 <= (scopes.get(configType)).indexOf("domain") ))
        {
            // add a domain if the configuration type requires one
            xmlDomainName = domainName;
        }

        // resource file name relative to the cartridge name
        cfgFileName = cfgFileName.replace("\\", "/")
        .replaceFirst( "^.*" + this.cartridgeName+".config", "config");

        // set variables or environment and staging system type in the file name
        for(String key: environments.keySet())
        {
            cfgFileName = cfgFileName.replaceAll(key, environments.get(key));
            // break;
        }
        for(String key: systemTypes.keySet())
        {
            cfgFileName = cfgFileName.replaceAll(key, systemTypes.get(key));
            // break;
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

    private String generateLine(    
        String filter, 
        String scope, 
        String xmlDomainName, 
        String fileName, 
        int priority,
        String cartridgeName, 
        boolean required)
    {
        return new StringBuffer( "\t\t<set")
        .append("  filter=\"").append(scope)
        .append("  scope=\"").append(scope)
        .append((!xmlDomainName.isEmpty()) ? "\" domain=\"" + xmlDomainName : "")
        .append("\" resourceName=\"").append(fileName)
        .append("\" priority=\"").append(priority)
        .append("\" cartridge=\"").append(cartridgeName)
        .append("\" required=\"").append(required)
        .append("\"/>").toString();
    }

    public ArrayList<String>  generateConfigXML() 
    {
        ArrayList<String>  xmlLines = new ArrayList<>();
        for (String line : headerLines) xmlLines.add((line));
        
        for (String line : commonLines) xmlLines.add((line));
        for (String line : lines) xmlLines.add((line));

        for (String line : footerLines) xmlLines.add((line));

        return xmlLines;
    }

}
