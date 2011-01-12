package it.com.atlassian.labs.speakeasy;

import com.atlassian.pageobjects.Page;
import com.atlassian.pageobjects.PageBinder;
import com.atlassian.pageobjects.ProductInstance;
import com.atlassian.pageobjects.TestedProduct;
import com.atlassian.pageobjects.binder.WaitUntil;
import com.atlassian.webdriver.AtlassianWebDriver;
import com.atlassian.webdriver.utils.Check;
import com.google.common.base.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.RenderedWebElement;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.lang.Integer.parseInt;

/**
 *
 */
public class SpeakeasyUserPage implements Page
{
    @Inject
    AtlassianWebDriver driver;

    @Inject
    ProductInstance productInstance;

    @Inject
    PageBinder pageBinder;

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
            WebElement nameTd = e.findElement(By.xpath("td[@headers='pluginName']"));
            row.setName(nameTd.findElement(By.className("pluginName")).getText());
            row.setDescription(nameTd.findElement(By.className("pluginDescription")).getText());
            row.setAuthor(e.findElement(By.xpath("td[@headers='pluginAuthor']")).getText());
            row.setUsers(parseInt(e.findElement(By.xpath("td[@headers='pluginUsers']")).getText()));
            row.setVersion(e.findElement(By.xpath("td[@headers='pluginVersion']")).getText());
            plugins.put(key,row);
        }
        return plugins;
    }

    public String getUrl()
    {
        return "/plugins/servlet/speakeasy/user";
    }

    public SpeakeasyUserPage enablePlugin(String pluginKey)
    {
        clickToggleIf(pluginKey, false);
        waitForMessages();
        return this;
    }

    public SpeakeasyUserPage disablePlugin(String pluginKey)
    {
        clickToggleIf(pluginKey, true);
        waitForMessages();
        return this;
    }

    public boolean isPluginEnabled(String pluginKey)
    {
        WebElement toggle = getEnableToggleLink(pluginKey);
        return toggle.getText().contains("Disable");
    }

    private WebElement getEnableToggleLink(String pluginKey)
    {
        return getPluginRow(pluginKey).findElement(By.className("pk_enable_toggle"));
    }

    private void clickToggleIf(String pluginKey, boolean enabled)
    {
        if (isPluginEnabled(pluginKey) == enabled)
        {
            getEnableToggleLink(pluginKey).click();
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

    public SpeakeasyUserPage waitForMessages()
    {
        driver.waitUntilElementIsVisibleAt(By.className("aui-message"), messageBar);
        return this;
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

    public DownloadDialog openDownloadDialog(String pluginKey) throws IOException
    {
        WebElement pluginRow = getPluginRow(pluginKey);
        WebElement downloadAction =  pluginRow.findElement(By.className("pk_download"));
        downloadAction.click();

        return pageBinder.bind(DownloadDialog.class, pluginKey);
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

    public List<String> getErrorMessages()
    {
        List<String> messages = new ArrayList<String>();
        for (WebElement msg : messageBar.findElements(By.className("aui-message")))
        {
            if (msg.getAttribute("class").contains("error"))
            {
                messages.add(msg.getText().trim());
            }
        }
        return messages;
    }

    public IdeDialog openEditDialog(String pluginKey)
    {
        WebElement pluginRow = getPluginRow(pluginKey);
        WebElement editAction =  pluginRow.findElement(By.className("pk_edit"));
        editAction.click();

        return pageBinder.bind(IdeDialog.class, pluginKey);

    }

    public SpeakeasyUserPage fork(String pluginKey)
    {
        WebElement pluginRow = getPluginRow(pluginKey);
        pluginRow.findElement(By.className("pk_fork")).click();
        waitForMessages();
        return this;
    }

    public static class PluginRow
    {
        private String key;
        private String name;
        private String author;
        private int users;
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

        public int getUsers()
        {
            return users;
        }

        public void setUsers(int users)
        {
            this.users = users;
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
