package it.com.atlassian.labs.speakeasy;

import com.atlassian.pageobjects.Page;
import com.atlassian.pageobjects.ProductInstance;
import com.atlassian.pageobjects.TestedProduct;
import com.atlassian.pageobjects.binder.WaitUntil;
import com.atlassian.webdriver.AtlassianWebDriver;
import com.atlassian.webdriver.jira.JiraTestedProduct;
import com.google.common.base.Function;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 *
 */
public class SpeakeasyUserPage implements Page
{
    @Inject
    AtlassianWebDriver driver;

    @Inject
    ProductInstance productInstance;

    @FindBy(id = "pluginsTableBody")
    private WebElement pluginsTableBody;

    @FindBy(name = "pluginFile")
    private WebElement pluginFileUpload;

    @FindBy(id = "aui-message-bar")
    private WebElement messageBar;

    @Inject
    private TestedProduct testedProduct;

    @WaitUntil
    public void waitForTableLoad()
    {
        driver.waitUntil(new Function()
        {
            public Object apply(Object from)
            {
                return getPluginKeys().size() > 0;
            }
        });
    }

    public List<String> getPluginKeys()
    {
        List<String> pluginKeys = new ArrayList<String>();
        for (WebElement e : pluginsTableBody.findElements(By.tagName("tr")))
        {
            pluginKeys.add(e.getAttribute("data-pluginkey"));
        }
        return pluginKeys;
    }

    public Map<String, PluginRow> getPlugins()
    {
        Map<String,PluginRow> plugins = new LinkedHashMap<String,PluginRow>();
        for (WebElement e : pluginsTableBody.findElements(By.tagName("tr")))
        {
            PluginRow row = new PluginRow();
            final String key = e.getAttribute("data-pluginkey");
            row.setKey(key);
            row.setName(e.findElement(By.xpath("td[@headers='pluginName']")).getText());
            row.setDescription(e.findElement(By.xpath("td[@headers='pluginDescription']")).getText());
            row.setAuthor(e.findElement(By.xpath("td[@headers='pluginAuthor']")).getText());
            row.setVersion(e.findElement(By.xpath("td[@headers='pluginVersion']")).getText());
            plugins.put(key,row);
        }
        return plugins;
    }

    public String getUrl()
    {
        return "/plugins/servlet/speakeasy/user";
    }

    public void enablePlugin(String pluginKey)
    {
        clickToggleIf(pluginKey, "Enable");
        waitForMessages();
    }

    public void disablePlugin(String pluginKey)
    {
        clickToggleIf(pluginKey, "Disable");
        waitForMessages();
    }

    private void clickToggleIf(String pluginKey, String toggleText)
    {
        WebElement toggle = getPluginRow(pluginKey).findElement(By.className("pk_enable_toggle"));
        if (toggle.getText().contains(toggleText))
        {
            toggle.click();
        }
        else
        {
            throw new IllegalStateException("Cannot toggle");
        }
    }

    private WebElement getPluginRow(String key)
    {
        for (WebElement row : pluginsTableBody.findElements(By.tagName("tr")))
        {
            if (key.equals(row.getAttribute("data-pluginkey")))
            {
                return row;
            }
        }
        return null;
    }

    public SpeakeasyUserPage uploadPlugin(File jar)
    {
        pluginFileUpload.sendKeys(jar.getAbsolutePath());
        waitForMessages();
        return this;
    }

    private void waitForMessages()
    {
        driver.waitUntilElementIsVisibleAt(By.className("aui-message"), messageBar);
    }

    public List<String> getSuccessMessages()
    {
        List<String> messages = new ArrayList<String>();
        for (WebElement msg : messageBar.findElements(By.className("aui-message")))
        {
            if (msg.getAttribute("class").contains("success"))
            {
                messages.add(msg.getText().trim());
            }
        }
        return messages;
    }

    public File forkPlugin(String pluginKey) throws IOException
    {
        WebElement pluginRow = getPluginRow(pluginKey);
        WebElement downloadAction =  pluginRow.findElement(By.className("pk_fork"));
        String href = downloadAction.getAttribute("href");

        DefaultHttpClient httpclient = new DefaultHttpClient();
        httpclient.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("admin", "admin"));
        HttpGet get = new HttpGet("http://localhost:" + productInstance.getHttpPort() + href + "?os_username=admin&os_password=admin");
        HttpResponse res = httpclient.execute(get);
        File tmpFile = File.createTempFile("speakeasy-fork-", ".zip");
        FileOutputStream fout = new FileOutputStream(tmpFile);
        res.getEntity().writeTo(fout);
        fout.close();
        return tmpFile;
    }


    public SpeakeasyUserPage uninstallPlugin(String pluginKey)
    {
        WebElement uninstallLink = getUninstallLink(pluginKey);
        uninstallLink.click();
        waitForMessages();
        return this;
    }

    private WebElement getUninstallLink(String pluginKey)
    {
        WebElement pluginRow = getPluginRow(pluginKey);
        return pluginRow.findElement(By.className("pk_uninstall"));
    }

    public boolean canUninstall(String pluginKey)
    {
        try
        {
            getUninstallLink(pluginKey);
            return true;
        }
        catch (NoSuchElementException ex)
        {
            return false;
        }
    }

    public static class PluginRow
    {
        private String key;
        private String name;
        private String author;
        private String description;
        private String version;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getAuthor()
        {
            return author;
        }

        public void setAuthor(String author)
        {
            this.author = author;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }
    }
}
