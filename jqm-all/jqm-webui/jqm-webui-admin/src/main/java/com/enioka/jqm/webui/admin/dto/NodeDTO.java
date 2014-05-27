package com.enioka.jqm.webui.admin.dto;

import java.util.Calendar;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class NodeDTO
{
    private Integer id;
    private String name;
    private String dns;
    private Integer port;
    private String outputDirectory;
    private String jobRepoDirectory;
    private String rootLogLevel;
    private Calendar lastSeenAlive;
    private Integer jmxRegistryPort;
    private Integer jmxServerPort;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDns()
    {
        return dns;
    }

    public void setDns(String dns)
    {
        this.dns = dns;
    }

    public Integer getPort()
    {
        return port;
    }

    public void setPort(Integer port)
    {
        this.port = port;
    }

    public String getOutputDirectory()
    {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }

    public String getJobRepoDirectory()
    {
        return jobRepoDirectory;
    }

    public void setJobRepoDirectory(String jobRepoDirectory)
    {
        this.jobRepoDirectory = jobRepoDirectory;
    }

    public String getRootLogLevel()
    {
        return rootLogLevel;
    }

    public void setRootLogLevel(String rootLogLevel)
    {
        this.rootLogLevel = rootLogLevel;
    }

    public Calendar getLastSeenAlive()
    {
        return lastSeenAlive;
    }

    public void setLastSeenAlive(Calendar lastSeenAlive)
    {
        this.lastSeenAlive = lastSeenAlive;
    }

    public Integer getJmxRegistryPort()
    {
        return jmxRegistryPort;
    }

    public void setJmxRegistryPort(Integer jmxRegistryPort)
    {
        this.jmxRegistryPort = jmxRegistryPort;
    }

    public Integer getJmxServerPort()
    {
        return jmxServerPort;
    }

    public void setJmxServerPort(Integer jmxServerPort)
    {
        this.jmxServerPort = jmxServerPort;
    }
}
