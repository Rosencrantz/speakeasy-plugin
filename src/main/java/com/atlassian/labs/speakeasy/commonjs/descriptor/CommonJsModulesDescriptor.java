package com.atlassian.labs.speakeasy.commonjs.descriptor;

import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.descriptors.AbstractModuleDescriptor;
import com.atlassian.plugin.event.PluginEventManager;
import com.atlassian.plugin.osgi.factory.OsgiPlugin;
import com.atlassian.util.concurrent.NotNull;
import org.dom4j.Element;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.util.*;


/**
 *
 */
public class CommonJsModulesDescriptor extends AbstractModuleDescriptor<CommonJsModules>
{
    private String location = "/modules";
    private Set<String> dependencies = new HashSet<String>();

    private final BundleContext bundleContext;
    private final PluginEventManager pluginEventManager;
    private final PluginAccessor pluginAccessor;
    private final String state;
    private final List<String> users;
    private Bundle pluginBundle;
    private volatile CommonJsModules modules;
    private volatile GeneratedDescriptorsManager generatedDescriptorsManager;

    public CommonJsModulesDescriptor(BundleContext bundleContext, PluginEventManager pluginEventManager, PluginAccessor pluginAccessor,
                                     String state, List<String> users)
    {
        this.bundleContext = bundleContext;
        this.pluginEventManager = pluginEventManager;
        this.pluginAccessor = pluginAccessor;
        this.state = state;
        this.users = users;
    }


    @Override
    public void init(@NotNull Plugin plugin, @NotNull Element element) throws PluginParseException
    {
        super.init(plugin, element);

        if (element.attribute("location") != null)
        {
            location = element.attributeValue("location");
        }

        for (Element dep : new HashSet<Element>(element.elements("dependency")))
        {
            dependencies.add(dep.getTextTrim() + "-" + state);
        }
    }

    @Override
    public CommonJsModules getModule()
    {
        return modules;
    }

    @Override
    public void enabled()
    {
        super.enabled();
        pluginBundle = findBundleForPlugin(plugin);
        modules = new CommonJsModules(plugin, pluginBundle, location);
        generatedDescriptorsManager = new GeneratedDescriptorsManager(pluginBundle, modules, pluginAccessor, pluginEventManager, this);
    }

    @Override
    public void disabled()
    {
        super.disabled();
        if (generatedDescriptorsManager != null)
        {
            generatedDescriptorsManager.close();
        }
        generatedDescriptorsManager = null;
        pluginBundle = null;
    }

    private Bundle findBundleForPlugin(Plugin plugin)
    {
        for (Bundle bundle : bundleContext.getBundles())
        {
            if (plugin.getKey().equals(bundle.getHeaders().get(OsgiPlugin.ATLASSIAN_PLUGIN_KEY)))
            {
                return bundle;
            }
        }
        throw new PluginParseException("Cannot find bundle for plugin: " + plugin.getKey());
    }

    String getLocation()
    {
        return location;
    }

    Set<String> getDependencies()
    {
        return dependencies;
    }

    public List<String> getUsers()
    {
        return users;
    }

    public String getState()
    {
        return state;
    }
}
