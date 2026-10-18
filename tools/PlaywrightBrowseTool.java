package tools;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.net.URI;

public class PlaywrightBrowseTool {

    public static String browse(String url) {
        try {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("URL must not be empty");
            }
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("URL must be an absolute HTTP or HTTPS URL");
            }

            try (Playwright playwright = Playwright.create()) {
                Browser browser = playwright.chromium().launch(
                        new BrowserType.LaunchOptions().setHeadless(true));
                try {
                    BrowserContext context = browser.newContext(
                            new Browser.NewContextOptions().setUserAgent(
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                            + "AppleWebKit/537.36"));
                    Page page = context.newPage();
                    page.navigate(url, new Page.NavigateOptions().setTimeout(20_000));
                    page.waitForTimeout(2_000);

                    Object result = page.evaluate(
                            "() => { const main = document.querySelector"
                                    + "('main, article, [role=\"main\"]') || document.body;"
                                    + " return main ? main.innerText : ''; }");
                    return result == null ? "" : result.toString().trim();
                } finally {
                    browser.close();
                }
            }
        } catch (Exception e) {
            return "[PLAYWRIGHT ERROR] " + e.getMessage();
        }
    }
}
