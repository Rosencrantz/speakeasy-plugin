package com.atlassian.labs.speakeasy;

import com.atlassian.labs.speakeasy.data.SpeakeasyData;
import com.atlassian.labs.speakeasy.install.PluginManager;
import com.atlassian.labs.speakeasy.install.PluginOperationFailedException;
import com.atlassian.labs.speakeasy.model.RemotePlugin;
import com.atlassian.labs.speakeasy.model.UserPlugins;
import com.atlassian.labs.speakeasy.product.ProductAccessor;
import com.atlassian.plugin.ModuleDescriptor;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.plugin.descriptors.UnrecognisedModuleDescriptor;
import com.atlassian.plugin.event.PluginEventListener;
import com.atlassian.plugin.event.PluginEventManager;
import com.atlassian.plugin.event.events.PluginDisabledEvent;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.hostcontainer.HostContainer;
import com.atlassian.plugin.osgi.external.ListableModuleDescriptorFactory;
import com.atlassian.plugin.osgi.external.SingleModuleDescriptorFactory;
import com.atlassian.plugin.osgi.factory.OsgiPlugin;
import com.atlassian.plugin.util.WaitUntil;
import com.atlassian.plugin.webresource.transformer.WebResourceTransformerModuleDescriptor;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.springframework.beans.factory.DisposableBean;

import javax.ws.rs.core.Response;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.atlassian.labs.speakeasy.util.BundleUtil.findBundleForPlugin;
import static java.util.Arrays.asList;

/**
 *
 */
public class SpeakeasyManager
{
    private final PluginAccessor pluginAccessor;
    private final SpeakeasyData data;
    private final PluginManager pluginManager;
    private final ProductAccessor productAccessor;
    private final DescriptorGeneratorManager descriptorGeneratorManager;


    public SpeakeasyManager(PluginAccessor pluginAccessor,
                            SpeakeasyData data, PluginManager pluginManager, ProductAccessor productAccessor, DescriptorGeneratorManager descriptorGeneratorManager)
    {
        this.descriptorGeneratorManager = descriptorGeneratorManager;
        this.pluginAccessor = pluginAccessor;
        this.data = data;
        this.pluginManager = pluginManager;
        this.productAccessor = productAccessor;
    }

    public UserPlugins getUserAccessList(String userName, String... modifiedKeys)
    {
        return getUserAccessList(userName, asList(modifiedKeys));
    }
    public UserPlugins getUserAccessList(String userName, List<String> modifiedKeys)
    {
        List<RemotePlugin> plugins = getAllRemoteSpeakeasyPlugins(userName);
        UserPlugins userPlugins = new UserPlugins(plugins);
        userPlugins.setUpdated(modifiedKeys);
        return userPlugins;
    }

    private List<RemotePlugin> getAllRemoteSpeakeasyPlugins(final String userName)
    {
        final List<Plugin> rawPlugins = getAllSpeakeasyPlugins();
        return Lists.transform(rawPlugins, new Function<Plugin,RemotePlugin>()
        {
            public RemotePlugin apply(Plugin from)
            {
                return getRemotePlugin(from.getKey(), userName, rawPlugins);
            }
        });
    }

    private List<Plugin> getAllSpeakeasyPlugins()
    {
        List<Plugin> plugins = new ArrayList<Plugin>();
        for (Plugin plugin : pluginAccessor.getPlugins())
        {
            for (ModuleDescriptor moduleDescriptor : plugin.getModuleDescriptors())
            {
                if (moduleDescriptor instanceof DescriptorGenerator)
                {
                    plugins.add(plugin);
                    break;
                }
            }
        }
        return plugins;
    }

    public RemotePlugin getRemotePlugin(String pluginKey, String userName) throws PluginOperationFailedException
    {
        return getRemotePlugin(pluginKey, userName, getAllSpeakeasyPlugins());
    }
    private RemotePlugin getRemotePlugin(String pluginKey, String userName, Iterable<Plugin> speakeasyPlugins) throws PluginOperationFailedException
    {
        final Plugin plugin = pluginAccessor.getPlugin(pluginKey);
        if (plugin == null)
        {
            throw new PluginOperationFailedException("Plugin not found: " + pluginKey);
        }

        RemotePlugin remotePlugin = new RemotePlugin(plugin);
        remotePlugin.setAuthor(getPluginAuthor(plugin));
        List<String> accessList = data.getUsersList(plugin.getKey());
        remotePlugin.setEnabled(accessList.contains(userName));
        remotePlugin.setNumUsers(accessList.size());
        boolean isAuthor = userName.equals(remotePlugin.getAuthor());
        boolean pureSpeakeasy = onlyContainsSpeakeasyModules(plugin);
        boolean canUninstall = isAuthor && pureSpeakeasy;
        remotePlugin.setFork(remotePlugin.getForkedPluginKey() != null);
        remotePlugin.setCanUninstall(canUninstall);
        remotePlugin.setCanEdit(isAuthor && pureSpeakeasy);
        remotePlugin.setCanFork(!remotePlugin.isFork() && pureSpeakeasy && !isAuthor);
        remotePlugin.setCanEnable(!remotePlugin.isEnabled());
        remotePlugin.setCanDisable(remotePlugin.isEnabled());
        remotePlugin.setCanDownload(pureSpeakeasy);

        // if the user has already forked this, don't let them fork again
        if (!remotePlugin.isFork())
        {
            for (Plugin plug : speakeasyPlugins)
            {
                if (remotePlugin.getKey().equals(RemotePlugin.getForkedPluginKey(plug.getKey())) && userName.equals(getPluginAuthor(plug)))
                {
                    remotePlugin.setCanFork(false);
                }
            }
        }
        return remotePlugin;
    }

    private String getPluginAuthor(Plugin plugin)
    {
        String author = data.getPluginAuthor(plugin.getKey());
        if (author == null)
        {
            author = plugin.getPluginInformation().getVendorName();
        }
        return author;
    }

    private boolean onlyContainsSpeakeasyModules(Plugin plugin)
    {
        String stateIdentifier = String.valueOf(data.getPluginStateIdentifier(plugin.getKey()));
        for (ModuleDescriptor descriptor : plugin.getModuleDescriptors())
        {
            if (!(descriptor instanceof DescriptorGenerator) && !descriptor.getKey().endsWith(stateIdentifier))
            {
                return false;
            }
        }
        return true;
    }

    public List<String> allowUserAccess(final String pluginKey, final String user)
    {
        List<String> affectedPluginKeys = new ArrayList<String>();
        List<String> accessList = data.getUsersList(pluginKey);
        if (!accessList.contains(user))
        {
            accessList.add(user);
            data.saveUsersList(pluginKey, accessList);
            descriptorGeneratorManager.updateModuleDescriptorsForPlugin(pluginKey, accessList);
            affectedPluginKeys.add(pluginKey);
        }

        // clear other allowed forks
        RemotePlugin targetPlugin = getRemotePlugin(pluginKey, user);
        String parentKey = targetPlugin.getForkedPluginKey() != null ? targetPlugin.getForkedPluginKey() : targetPlugin.getKey();

        for (Plugin plugin : getAllSpeakeasyPlugins())
        {
            if (!plugin.getKey().equals(targetPlugin.getKey()) && (plugin.getKey().equals(parentKey)
                    || parentKey.equals(RemotePlugin.getForkedPluginKey(plugin.getKey()))))
            {
                if (removeFromAccessList(plugin.getKey(), user) != null)
                {
                    affectedPluginKeys.add(plugin.getKey());
                }
            }
        }

        sendEnabledEmail(pluginKey, user);
        return affectedPluginKeys;
    }

    private void sendEnabledEmail(final String pluginKey, final String user)
    {
        final String userFullName = productAccessor.getUserFullName(user);
        String pluginAuthor = data.getPluginAuthor(pluginKey);
        if (pluginAuthor != null && !user.equals(pluginAuthor))
        {
            final Set<RemotePlugin> commonExtensions = new HashSet<RemotePlugin>();
            final Set<RemotePlugin> suggestedExtensions = new HashSet<RemotePlugin>();
            for (RemotePlugin plugin : getAllRemoteSpeakeasyPlugins(user))
            {
                if (plugin.isEnabled())
                {
                    List<String> accessList = data.getUsersList(plugin.getKey());
                    if (accessList.contains(pluginAuthor))
                    {
                        commonExtensions.add(plugin);
                    }
                    else
                    {
                        suggestedExtensions.add(plugin);
                    }
                }

            }
            productAccessor.sendEmail(pluginAuthor, "email/enabled-subject.vm", "email/enabled-body.vm", new HashMap<String,Object>() {{
                put("plugin", getRemotePlugin(pluginKey, user));
                put("enablerFullName", userFullName);
                put("enabler", user);
                put("commonExtensions", commonExtensions);
                put("suggestedExtensions", suggestedExtensions);
                put("enabledTotal", data.getUsersList(pluginKey).size());
            }});
        }
    }

    private void sendForkedEmail(final String pluginKey, final String forkedPluginKey, final String user)
    {
        final String userFullName = productAccessor.getUserFullName(user);
        String pluginAuthor = data.getPluginAuthor(pluginKey);
        if (pluginAuthor != null && !user.equals(pluginAuthor))
        {
            final Set<RemotePlugin> otherForkedExtensions = new HashSet<RemotePlugin>();
            for (RemotePlugin plugin : getAllRemoteSpeakeasyPlugins(user))
            {
                if (user.equals(plugin.getAuthor()) && plugin.getForkedPluginKey() != null && !forkedPluginKey.equals(plugin.getKey()))
                {
                    otherForkedExtensions.add(getRemotePlugin(plugin.getForkedPluginKey(), user));
                }

            }
            productAccessor.sendEmail(pluginAuthor, "email/forked-subject.vm", "email/forked-body.vm", new HashMap<String,Object>() {{
                RemotePlugin originalPlugin = getRemotePlugin(pluginKey, user);
                put("plugin", originalPlugin);
                put("productAccessor", productAccessor);
                put("forkedPlugin", getRemotePlugin(forkedPluginKey, user));
                put("forkerFullName", userFullName);
                put("forker", user);
                put("otherForkedExtensions", otherForkedExtensions);
            }});
        }
    }

    public String disallowUserAccess(String pluginKey, String user)
    {
        return removeFromAccessList(pluginKey, user);
    }

    private String removeFromAccessList(String pluginKey, String user)
    {
        List<String> accessList = data.getUsersList(pluginKey);
        if (accessList.contains(user))
        {
            accessList.remove(user);
            data.saveUsersList(pluginKey, accessList);

            descriptorGeneratorManager.updateModuleDescriptorsForPlugin(pluginKey, accessList);
            return pluginKey;
        }
        return null;
    }

    public void disallowAllPluginAccess(String pluginKey)
    {
        List<String> accessList = data.getUsersList(pluginKey);
        accessList.clear();
        data.saveUsersList(pluginKey, accessList);
        descriptorGeneratorManager.updateModuleDescriptorsForPlugin(pluginKey, accessList);
    }

    public boolean hasAccess(String pluginKey, String remoteUser)
    {
        return data.getUsersList(pluginKey).contains(remoteUser);
    }

    public void disallowAllUserAccess(String user)
    {
        for (Plugin plugin : pluginAccessor.getEnabledPlugins())
        {
            if (data.getUsersList(plugin.getKey()).contains(user))
            {
                disallowUserAccess(plugin.getKey(), user);
            }
            descriptorGeneratorManager.unregisterGeneratedDescriptorsForPlugin(plugin.getKey());
        }
    }

    public UserPlugins uninstallPlugin(String pluginKey, String user)
            throws PluginOperationFailedException
    {
        List<String> keysModified = new ArrayList<String>();
        RemotePlugin plugin = getRemotePlugin(pluginKey, user);
        if (plugin == null || !plugin.isCanUninstall())
        {
            throw new PluginOperationFailedException("Not authorized to install " + pluginKey);
        }
        String originalKey = plugin.getForkedPluginKey();
        if (originalKey != null && pluginAccessor.getPlugin(originalKey) != null)
        {
            if (hasAccess(pluginKey, user))
            {
                keysModified.add(originalKey);
                allowUserAccess(originalKey, user);

            }
        }
        disallowAllPluginAccess(pluginKey);
        pluginManager.uninstall(pluginKey, user);
        return getUserAccessList(user, keysModified);
    }

    public UserPlugins fork(String pluginKey, String remoteUser, String description)
            throws PluginOperationFailedException
    {
        RemotePlugin plugin = getRemotePlugin(pluginKey, remoteUser);
        if (!plugin.isCanFork())
        {
            throw new PluginOperationFailedException("Not authorized to fork " + pluginKey);
        }
        String forkedPluginKey = pluginManager.forkAndInstall(pluginKey, remoteUser, description);
        List<String> modifiedKeys = new ArrayList<String>();
        modifiedKeys.add(forkedPluginKey);
        if (hasAccess(pluginKey, remoteUser))
        {
            modifiedKeys.add(pluginKey);
            allowUserAccess(forkedPluginKey, remoteUser);
        }
        if (forkedPluginKey != null)
        {
            sendForkedEmail(pluginKey, forkedPluginKey, remoteUser);
        }
        return getUserAccessList(remoteUser, modifiedKeys);
    }

    public File getPluginFileAsProject(String pluginKey, String user)
    {
        RemotePlugin plugin = getRemotePlugin(pluginKey, user);
        if (!plugin.isCanDownload())
        {
            throw new PluginOperationFailedException("Not authorized to download " + pluginKey);
        }
        return pluginManager.getPluginFileAsProject(pluginKey);
    }

    public List<String> getPluginFileNames(String pluginKey, String user)
    {
        RemotePlugin plugin = getRemotePlugin(pluginKey, user);
        if (!plugin.isCanEdit())
        {
            throw new PluginOperationFailedException("Not authorized to view " + pluginKey);
        }
        return pluginManager.getPluginFileNames(pluginKey);
    }

    public Object getPluginFile(String pluginKey, String fileName, String user)
    {
        RemotePlugin plugin = getRemotePlugin(pluginKey, user);
        if (!plugin.isCanEdit())
        {
            throw new PluginOperationFailedException("Not authorized to view " + pluginKey);
        }
        return pluginManager.getPluginFile(pluginKey, fileName);
    }

    public RemotePlugin saveAndRebuild(String pluginKey, String fileName, String contents, String user)
    {
        RemotePlugin plugin = getRemotePlugin(pluginKey, user);
        if (!plugin.isCanEdit())
        {
            throw new PluginOperationFailedException("Not authorized to edit " + pluginKey);
        }
        String installedPluginKey = pluginManager.saveAndRebuild(pluginKey, fileName, contents, user);
        return getRemotePlugin(installedPluginKey, user);
    }

    public UserPlugins installPlugin(File uploadedFile, String user)
    {
        if (!pluginManager.canUserInstallPlugins(user))
        {
            throw new PluginOperationFailedException("Not authorized to install plugins");
        }

        String pluginKey = pluginManager.install(uploadedFile, user);
        return getUserAccessList(user, pluginKey);
    }
}
