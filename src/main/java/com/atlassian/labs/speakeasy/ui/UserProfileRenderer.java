package com.atlassian.labs.speakeasy.ui;

import com.atlassian.labs.speakeasy.SpeakeasyManager;
import com.atlassian.labs.speakeasy.data.SpeakeasyData;
import com.atlassian.labs.speakeasy.install.PluginManager;
import com.atlassian.labs.speakeasy.model.RemotePlugin;
import com.atlassian.labs.speakeasy.model.UserPlugins;
import com.atlassian.labs.speakeasy.product.ProductAccessor;
import com.atlassian.plugin.webresource.UrlMode;
import com.atlassian.plugin.webresource.WebResourceManager;
import com.atlassian.plugins.rest.common.json.JaxbJsonMarshaller;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.templaterenderer.annotations.HtmlSafe;
import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.Map;

/**
 *
 */
public class UserProfileRenderer
{

    private final TemplateRenderer templateRenderer;
    private final SpeakeasyManager speakeasyManager;
    private final UserManager userManager;
    private final WebResourceManager webResourceManager;
    private final JaxbJsonMarshaller jaxbJsonMarshaller;
    private final PluginManager pluginManager;
    private final ProductAccessor productAccessor;
    private final SpeakeasyData data;

    public UserProfileRenderer(TemplateRenderer templateRenderer, SpeakeasyManager speakeasyManager, UserManager userManager, WebResourceManager webResourceManager, JaxbJsonMarshaller jaxbJsonMarshaller, PluginManager pluginManager, ProductAccessor productAccessor, SpeakeasyData data)
    {
        this.templateRenderer = templateRenderer;
        this.speakeasyManager = speakeasyManager;
        this.userManager = userManager;
        this.webResourceManager = webResourceManager;
        this.jaxbJsonMarshaller = jaxbJsonMarshaller;
        this.pluginManager = pluginManager;
        this.productAccessor = productAccessor;
        this.data = data;
    }

    public void render(HttpServletRequest req, Writer output, boolean useUserProfileDecorator) throws UnauthorizedAccessException, IOException
    {
        String user = userManager.getRemoteUsername(req);
        if (user == null)
        {
            throw new UnauthorizedAccessException("Unauthorized - must be a valid user");
        }

        webResourceManager.requireResource("com.atlassian.auiplugin:ajs");
        webResourceManager.requireResource("com.atlassian.labs.speakeasy-plugin:user");
        webResourceManager.requireResourcesForContext("speakeasy.user-profile");
        boolean devMode = data.isDeveloperModuleEnabled(user);
        if (devMode)
        {
            webResourceManager.requireResource("com.atlassian.labs.speakeasy-plugin:ide");
        }

        final UserPlugins plugins = speakeasyManager.getUserAccessList(user);
        final String pluginJson = jaxbJsonMarshaller.marshal(plugins);
        render("templates/user" + (useUserProfileDecorator ? "-with-decorator" : "") + ".vm", ImmutableMap.<String,Object>builder().
                put("accessList", speakeasyManager.getUserAccessList(user)).
                put("user", user).
                put("contextPath", req.getContextPath()).
                put("plugins", plugins.getPlugins()).
                put("rowRenderer", new RowRenderer()).
                put("installAllowed", pluginManager.canUserInstallPlugins(user)).
                put("staticResourcesPrefix", webResourceManager.getStaticResourcePrefix(UrlMode.RELATIVE)).
                put("product", productAccessor.getSdkName()).
                put("devmode", devMode).
                build(),
                output);
    }

    protected void render(final String template, final Map<String, Object> renderContext,
                          final Writer output)
            throws IOException
    {
        templateRenderer.render(template, renderContext, output);
    }

    public static class RowRenderer
    {
        private final Template rowTemplate;

        public RowRenderer()
        {
            InputStream in = null;
            try
            {
                in = getClass().getClassLoader().getResourceAsStream("user/row.mu");
                this.rowTemplate = Mustache.compiler().compile(new InputStreamReader(in));
            }
            finally
            {
                IOUtils.closeQuietly(in);
            }
        }

        @HtmlSafe
        @com.atlassian.velocity.htmlsafe.HtmlSafe
        public String render(RemotePlugin plugin)
        {
            return rowTemplate.execute(plugin);
        }
    }
}
