package com.atlassian.labs.speakeasy.model;

import com.atlassian.plugin.Plugin;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@XmlRootElement(name = "plugin")
public class RemotePlugin
{
    private String key;

    private String name;

    private String author;

    private String version;

    private String description;

    private int numUsers = 0;

    private boolean enabled;

    private boolean canUninstall;

    private HashMap<String,String> params;

    public RemotePlugin()
    {}

    public RemotePlugin(Plugin plugin)
    {
        key = plugin.getKey();
        name = plugin.getName() != null ? plugin.getName() : plugin.getKey();
        description = plugin.getPluginInformation().getDescription();
        version = plugin.getPluginInformation().getVersion();
        params = new HashMap<String,String>(plugin.getPluginInformation().getParameters());
    }

    @XmlElement
    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    @XmlElement
    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    @XmlElement
    public HashMap<String, String> getParams()
    {
        return params;
    }

    public void setParams(HashMap<String, String> params)
    {
        this.params = params;
    }


    @XmlElement
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    @XmlElement
    public String getAuthor()
    {
        return author;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    @XmlElement
    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    @XmlElement
    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    @XmlElement
    public int getNumUsers() {
        return numUsers;
    }

    public void setNumUsers(int numUsers) {
        this.numUsers = numUsers;
    }

    @XmlElement
    public boolean isCanUninstall()
    {
        return canUninstall;
    }

    public void setCanUninstall(boolean canUninstall)
    {
        this.canUninstall = canUninstall;
    }

    public void setForkedPluginKey(String ntohing)
    {
    }

    @XmlElement
    public String getForkedPluginKey()
    {
        if (key != null && key.contains("-fork-"))
        {
            return key.substring(0, key.indexOf("-fork-"));
        }
        return null;
    }

}
