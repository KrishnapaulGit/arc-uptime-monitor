package arcmonitor;

import com.twilio.Twilio;
import com.twilio.type.PhoneNumber;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;
import javax.mail.internet.*;
import javax.mail.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Properties;


public class ArcAlert {

    public static final Dotenv dotenv = Dotenv.load();

    // Twilio credentials
    public static final String ACCOUNT_SID = dotenv.get("TWILIO_ACCOUNT_SID");
    public static final String AUTH_TOKEN = dotenv.get("TWILIO_AUTH_TOKEN");
    public static final String FROM_PHONE = dotenv.get("TWILIO_FROM_PHONE");
    public static final String TO_PHONE = dotenv.get("TWILIO_TO_PHONE");

    // Gmail SMTP credentials
    public static final String GMAIL_USERNAME = dotenv.get("GMAIL_USERNAME");
    public static final String GMAIL_APP_PASSWORD = dotenv.get("GMAIL_APP_PASSWORD");
    public static final String TO_EMAIL = dotenv.get("TO_EMAIL");

    // ARC Facilities login info
    public static final String LOGIN_URL = dotenv.get("LOGIN_URL");
    public static final String ARC_EMAIL = dotenv.get("ARC_EMAIL");
    public static final String ARC_PASSWORD = dotenv.get("ARC_PASSWORD");

    // Thresholds
    public static final long LOAD_TIME_THRESHOLD = 40000;

    public static void main(String[] args) {
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        WebDriverManager.chromedriver().setup();

        while (true) {
            WebDriver driver = getDriver();
            driver.manage().window().setSize(new Dimension(1200,720));
            log("=======Execution Start======");

            long totalLoadStart = System.currentTimeMillis();

            try {
                driver.get(LOGIN_URL);

                boolean loginSuccessful = attemptLogin(driver);
                String currentUrl = safeGetUrl(driver);
                boolean isSessionExpired = checkSessionExpired(driver);
                boolean isCorrectUrl = currentUrl.contains("campuslist");

                long totalLoadEnd = System.currentTimeMillis();
                long totalLoadTime = totalLoadEnd - totalLoadStart;

                log("Total load time: " + totalLoadTime + " ms");

                if (totalLoadTime > LOAD_TIME_THRESHOLD) {
                    String body = "ARC Facilities High Load Time Alert! Total Load time: " + (totalLoadTime / 1000.0) + " seconds\nURL: " + currentUrl;
                    handleAlert("ARC FACILITIES 2.0 : High Load Time", body, driver, currentUrl);
                    Thread.sleep(20000);
                    driver.quit();
                    continue;
                }

                if (!isCorrectUrl || !loginSuccessful || isSessionExpired) {
                    String reason = isSessionExpired ? "Session expired" : "Login failed or URL mismatch";
                    String body = "ARC Facilities Down: " + reason + "\nURL: " + currentUrl+"\n\n\n NOTE: Total Load Time: " + (totalLoadTime / 1000.0) + " seconds";
                    handleAlert("ARC FACILITIES 2.0: Down", body, driver, currentUrl);
                } else {
                    String msg = "ARC Facilities is UP\nURL: " + currentUrl + "\n\n\n NOTE: Total Load Time: " + (totalLoadTime / 1000.0) + " seconds";
                    sendEmail("ARC FACILITIES 2.0 : Up", msg, null);
                    // sendSms(msg);
                    log(msg);
                    log("Total Execution Time : "+(totalLoadTime/1000.0) +" seconds");
                }

            } catch (TimeoutException tex) {
                String url = safeGetUrl(driver);
                String msg = "TimeoutException occurred while loading: " + tex.getMessage() + "\nURL: " + url;
                handleAlert("ARC FACILITIES 2.0: Timeout Occurs", msg, driver, url);
                tex.printStackTrace();
            } catch (Exception ex) {
                String url = safeGetUrl(driver);
                String msg = "ARC Facilities Error: " + ex.getMessage() + "\nURL: " + url;
                handleAlert("ARC FACILITIES 2.0: Crash", msg, driver, url);
                ex.printStackTrace();
            } finally {
                driver.quit();
            }

            try {
                Thread.sleep(30000);
            } catch (InterruptedException ignored) {}
        }

    }

    private static WebDriver getDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--incognito", "--headless=new");

        return new ChromeDriver(options);
    }

    private static boolean attemptLogin(WebDriver driver) {
        try {
            driver.findElement(By.id("email")).sendKeys(ARC_EMAIL);
            driver.findElement(By.id("send")).click();

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.elementToBeClickable(By.id("link2"))).click();

            driver.findElement(By.id("password")).sendKeys(ARC_PASSWORD);
            driver.findElement(By.id("send")).click();

            Thread.sleep(10000); // Allow time for redirection
            return true;
        } catch (Exception e) {
            log("Login failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean checkSessionExpired(WebDriver driver) {
        try {
            WebElement expired = driver.findElement(By.xpath("//h2[contains(text(), 'session expired')]"));
            return expired.isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private static void handleAlert(String subject, String body, WebDriver driver, String url) {
        log(body);
        String screenshot = takeScreenshot(driver, url);
        sendEmail(subject, body, screenshot);
// sendSms(body);
    }

    private static String safeGetUrl(WebDriver driver) {
        try {
            return driver.getCurrentUrl();
        } catch (Exception e) {
            return "Unable to fetch URL";
        }
    }

    private static String takeScreenshot(WebDriver driver, String url) {
        try {
            TakesScreenshot ts = (TakesScreenshot) driver;
            File src = ts.getScreenshotAs(OutputType.FILE);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String host = new URL(url).getHost().replaceAll("[^a-zA-Z0-9.-]", "_");

            File dir = new File("screenshots");
            if (!dir.exists()) dir.mkdirs();

            String filename = timestamp + "_" + host + ".png";
            File dest = new File(dir, filename);
            Files.copy(src.toPath(), dest.toPath());
            log("Screenshot saved: " + dest.getAbsolutePath());

            return filename;
        } catch (Exception e) {
            log("Screenshot failed: " + e.getMessage());
            return null;
        }
    }

    private static void sendEmail(String subject, String body, String screenshotFileName) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(GMAIL_USERNAME, GMAIL_APP_PASSWORD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(GMAIL_USERNAME));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL));
            message.setSubject(subject);

            if (screenshotFileName != null) {
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(body);

                MimeBodyPart attachmentPart = new MimeBodyPart();
                attachmentPart.attachFile(new File("screenshots/" + screenshotFileName));

                Multipart multipart = new MimeMultipart();
                multipart.addBodyPart(textPart);
                multipart.addBodyPart(attachmentPart);

                ((MimeMessage) message).setContent(multipart);
            } else {
                message.setText(body);
            }

            Transport.send(message);
            log("Email sent via Gmail.");
        } catch (Exception e) {
            log("Email failed: " + e.getMessage());
        }
    }

    private static void sendSms(String body) {
        try {
            com.twilio.rest.api.v2010.account.Message message = com.twilio.rest.api.v2010.account.Message.creator(
                    new PhoneNumber(TO_PHONE),
                    new PhoneNumber(FROM_PHONE),
                    body
            ).create();
            log("SMS sent. SID: " + message.getSid());
        } catch (Exception e) {
            log("SMS failed: " + e.getMessage());
        }
    }


    private static void log(String message) {
        System.out.println("[ArcAlert] " + message);
    }
}
