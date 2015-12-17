package com.fenix.DTBot;

import javafx.concurrent.Task;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Bot class can log into Digital Tutors and neatly retrieve all the videos of a given tutorial. The tutorials are
 * inputted by the form of a list of URLs in a text file that the Bot iterates through. Proper directory structure is
 * also generated.
 */
public class Bot extends Task {

    private Logger log; // logger to output status of this bot

    private String user, pass; // login information
    private File tutDir, tutReferenceDir; // directories for each tutorial
    private File queue, outputDir; // critical information for the operation of this bot
    private int maxProgress; // when bot reaches this progress, it completed it's task
    private int baseProgress, tutProgress;

    private URL tutURL; // URL of the tutorial
    private boolean dtCloudPlayerEnabled; // tracks what player is used on DT

    private List<String> tutList; // list of all tutorial URLs to traverse
    private Iterator<String> tutIterator; // iterator for tutList


    /**
     * Generates a new instance of Bot allowing for the download of tutorials from Digital Tutors
     *
     * @param user      login string for the user field
     * @param pass      login string for the pass field
     * @param outputDir directory to which will be downloaded
     * @param queue     text file containing list of tutorial URLs
     * @param handler   implements status update handling
     */
    public Bot(String user, String pass, File outputDir, File queue, Handler handler) {
        super();
        this.user = user;
        this.pass = pass;
        this.outputDir = outputDir;
        this.queue = queue;

        tutList = new ArrayList<>();
        log = Logger.getLogger(Bot.class.getName()); // name logger the same as class name
        log.addHandler(handler);
        log.setLevel(Level.FINEST); // the lowest log level is FINEST

        // initialize progress fields
        maxProgress = 0;
        baseProgress = 0;
        updateTitle("0/0");
    }


    /**
     * Increments the progress by a factor of 100 - the designated amount of units per tutorial. This method does not
     * use the <code>tutProgress</code> field to ensure that each time <code>Bot</code> moves to a new tutorial, the
     * progress is updated in equal amounts
     */
    private void incrementGlobalProgress() {
        baseProgress += 100; // each tutorial is worth 100 units and this resets
        if (baseProgress > maxProgress) {
            // cap the progress to the max
            baseProgress = maxProgress;
        }
        tutProgress = baseProgress; // reset task progress
        // set the new progress string
        updateTitle(Integer.toString(baseProgress / 100) + "/" + Integer.toString(tutList.size()));
        // using baseProgress ensures that the progress bar remains consistent regardless of tut progress
        updateProgress(baseProgress, maxProgress);
    }

    /**
     * Increments the individual progress for each tutorial. If this method exceeds more than 100 units of progress,
     * {@link #incrementGlobalProgress() incrementGlobalProgress} rounds it off.
     *
     * @param amt amount to increment by
     */
    private void incrementTutProgress(int amt) {
        tutProgress += amt;
        updateProgress(tutProgress, maxProgress);
    }

    /**
     * Verifies whether this bot is ready to start
     *
     * @return <code>true</code> if the bot has the necessary parameters to start; <code>false</code> otherwise.
     */
    public boolean hasParameters() {
        return user != null && pass != null && outputDir != null && queue != null;
    }

    /**
     * Opens the {@link #queue} file and adds the URLs contained in it to memory if they are valid.
     *
     * @throws IOException if the queue was not found or could not be opened
     */
    private void populateTutList() throws IOException {
        log.info("[*] Populating tutorial queue");
        BufferedReader in = new BufferedReader(new FileReader(queue));

        // read each line from the file and add it to the list
        String line;
        while ((line = in.readLine()) != null) {
            tutList.add(line.trim());
        }
        tutIterator = tutList.iterator(); // global iterator is useful
        in.close();
        log.finer("Added " + tutList.size() + " tutorial URLs to queue");

        // set max progress to reference on progress updates
        maxProgress = tutList.size() * 100;
        updateTitle("0/" + tutList.size()); // update label with correct total
    }

    /**
     * Logs into the Digital Tutors web page with stored credentials
     *
     * @param driver    browser to perform the action with
     * @param keepLogin whether to login in persistently
     */
    private void login(WebDriver driver, boolean keepLogin) {
        log.info("[*] Logging in");

        // Go to Login page
        driver.get("https://www.digitaltutors.com");
        WebElement link = waitForElement(driver, // sign in button
                By.xpath("//*[@id='ulLoggedInStatus']/li[3]/div/a"));

        link.click();
        waitForTitle(driver, "login");

        // Find elements
        WebElement button = driver.findElement(By.xpath("//button"));
        WebElement emailField = driver.findElement(By.name("p_email"));
        WebElement passField = driver.findElement(By.name("p_password"));

        // Log in
        emailField.sendKeys(user);
        passField.sendKeys(pass);

        // Persistent Log in
        if (keepLogin) {
            WebElement stayLoggedInBox = driver.findElement(By.name("p_keep_logged_in"));
            stayLoggedInBox.click();
        }

        button.click();
        waitForTitle(driver, "digital-tutors");
    }

    /**
     * Moves the bot to the next tutorial page and creates necessary directory structure.
     *
     * @param driver browser to perform the action with
     * @return URL to the next tutorial
     * @throws IOException if directory setup failed
     */
    private String nextTut(WebDriver driver) throws IOException {

        while (tutIterator.hasNext()) {
            log.info("[*] Going to next tutorial");
            String nextURL = tutIterator.next(); // URLs were read in by
            // populateTutList()

            // Check if URL is usable
            try {
                if (!isTutURL(nextURL))
                    throw new MalformedURLException();
            } catch (MalformedURLException e) {
                log.warning("Bad URL: " + nextURL);
                incrementGlobalProgress(); // update progress
                continue; // go to next URL in list
            }

            driver.get(nextURL);

            tutURL = new URL(driver.getCurrentUrl()); // store for later
            // get the name of the tutorial series and create folder
            String[] splitTitle = driver.getTitle().split(">");
            for (int i = 0; i < splitTitle.length; i++) {
                splitTitle[i] = splitTitle[i].trim();
            }
            log.info("[*] Tutorial Name: " + splitTitle[2]);
            tutDir = new File(outputDir, splitTitle[2]);
            tutReferenceDir = new File(tutDir, "References");

            // check whether tutorial was already downloaded
            if (tutDir.exists()) {
                log.warning("Tutorial folder already exists");
                incrementGlobalProgress(); // update progress
                continue; // go to next URL in list
            }

            // create directories
            log.finer("Creating folder");
            //noinspection ResultOfMethodCallIgnored
            tutDir.mkdir();
            //noinspection ResultOfMethodCallIgnored
            tutReferenceDir.mkdir();

            return nextURL;
        }
        log.info("[*] Finished URL queue");
        return null; // iterator has run out of stuff;

    }

    @SuppressWarnings("unused")
    private void downloadReferences(WebDriver driver) {
        // TODO implement this
    }

    /**
     * The work horse of the bot class. Concurrently downloads all the videos from the tutorial page.
     *
     * @param driver browser to perform the action with
     * @throws IOException if something went wrong downloading the files
     */
    private void downloadVideos(WebDriver driver) throws IOException {
        // Navigate to the video player
        log.info("[*] Going to video player");
        WebElement link = driver.findElement(By.xpath("//a[contains(@href, 'play-')]"));
        link.click();

        // Switching needs to be done before finding elements since page is
        // refreshed
        // Figure out what player we are using and switch to cloud player if
        // needed/possible
        dtCloudPlayerEnabled = existsElement(driver, By.xpath("//a[@title='Pause']"));
        if (!dtCloudPlayerEnabled) { // no cloud player => switch to it
            log.warning("Could not find cloud player. Trying to switch");
            switchPlayer(driver);

            // check if we have cloud player, if not switch to frame
            if (!existsElement(driver, By.xpath("//a[@title='Pause']"))) {
                log.warning("Switching to cloud player failed. Using frame player");
                dtCloudPlayerEnabled = false; // error will pop up later if
                // unsuccessful => irrelevant
            } else {
                log.finer("Cloud player found");
                dtCloudPlayerEnabled = true;
            }
        } else {
            log.finer("Cloud player found");
        }

        log.info("[*] Downloading videos");

        // Go through the list extract the divs for each video
        List<WebElement> videoList = waitForAllElements(driver,
                By.xpath("//div[@class='scrollable_container']/div[contains(@id, 'divLesson')]"));

        Iterator<WebElement> itr = videoList.iterator();
        // video URLs are based on appending the video id to a base URL
        String baseURL = tutURL.toString() + "#play-"; // to append to

        String videoTitle;
        String videoNumber;
        String videoID;
        String videoLink;
        File video; // pointer to local file

        WebElement element; // takes on each video div
        ExecutorService pool = Executors.newFixedThreadPool(5); // downloads are concurrent

        // set up progress so that each video download takes an even piece of 100 units
        int videoProgress = 100 / videoList.size();

        while (itr.hasNext()) {

            element = itr.next();

            // 1. Get relevant data
            videoTitle = element.getAttribute("data-title");
            videoNumber = element.getAttribute("data-position");
            videoID = element.getAttribute("data-lesson_id");

            // 2. Navigate to the video page
            log.fine("<> Navigating to video page: (" + videoNumber + ") " + videoTitle);
            driver.get(baseURL + videoID);

            // 3. Get the link to the video
            videoLink = getVideoLink(driver);
            if (videoLink == null) {
                continue;
            }

            // 4. Download video with correct name
            video = new File(tutDir, videoNumber + " - " + videoTitle + ".mp4");
            pool.submit(new DownloadTask(videoLink, video));
            log.finer("Downloading");
            incrementTutProgress(videoProgress); // update progress
        }
        pool.shutdown();
        incrementGlobalProgress(); // update progress
    }

    /**
     * Obtains the link to the video source on the current video page.
     *
     * @param driver browser to perform the action with
     * @return URL to the video source
     */
    private String getVideoLink(WebDriver driver) {
        String videoLink = null;

        // these will alternate back and forth if something is wrong with the
        // page

        if (dtCloudPlayerEnabled)
            try {
                // try to get link from cloud player
                videoLink = _getVideoLinkFromCloudPlayer(driver);
            } catch (TimeoutException e) {
                log.warning("Could not find cloud player. Trying frame player");
                dtCloudPlayerEnabled = false; // no cloud player => straight to
                // frame next time
                try {
                    // try to get link from frame player
                    videoLink = _getVideoLinkFromFrame(driver);
                } catch (TimeoutException b) {
                    // give up and return null
                    log.warning("Could not find player");
                }
            }
        else
            try {
                // try to get link from frame player
                videoLink = _getVideoLinkFromFrame(driver);
            } catch (TimeoutException e) {
                log.warning("Could not find frame player. Trying cloud player");
                dtCloudPlayerEnabled = true; // no frame player => straight to
                // cloud next time
                try {
                    // try to get link from cloud player
                    videoLink = _getVideoLinkFromCloudPlayer(driver);
                } catch (TimeoutException b) {
                    // give up and return null
                    log.warning("Could not find player");
                }
            }

        return videoLink;
    }

    /**
     * Helper class for {@link #getVideoLink(WebDriver) getVideoLink}. Gets the video source link if the cloud player is
     * enabled.
     *
     * @param driver browser to perform the action with
     * @return URL to the video source
     * @throws TimeoutException if required web elements could not be found, likely due to having navigated to the wrong
     *                          page
     */
    private String _getVideoLinkFromCloudPlayer(WebDriver driver) throws TimeoutException {
        waitForElement(driver, By.xpath("//a[@title='Pause']"), 25);
        return driver.findElement(By.xpath("//source")).getAttribute("src");
    }

    /**
     * Helper class for {@link #getVideoLink(WebDriver) getVideoLink}. Gets the video source link if the frame player is
     * enabled.
     *
     * @param driver browser to perform the action with
     * @return URL to the video source
     * @throws TimeoutException if required web elements could not be found, likely due to having navigated to the wrong
     *                          page.
     */
    private String _getVideoLinkFromFrame(WebDriver driver) throws TimeoutException {
        WebElement videoFrame = waitForElement(driver, By.id("myExperience"), 25);
        driver.switchTo().frame(videoFrame);
        driver.findElement(By.id("$bc14")).click();
        log.finer("Waiting on video");
        WebElement videoPlayer = waitForElement(driver, By.id("bcVideo"));
        String videoLink = videoPlayer.getAttribute("src");
        driver.switchTo().parentFrame();
        return videoLink;
    }

    /**
     * Checks for the existence of an element on the current page.
     *
     * @param driver browser to perform action with
     * @param by     locator for the element
     * @return {@code true} if the element was found; {@code false} otherwise
     */
    private boolean existsElement(WebDriver driver, By by) {
        try {
            waitForElement(driver, by);
        } catch (TimeoutException e) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unused")
    private void writeHTMLtoFile(WebDriver driver, String fileName) throws FileNotFoundException {
        writeHTMLtoFile(driver.getPageSource(), fileName);
    }

    private void writeHTMLtoFile(String html, String fileName) throws FileNotFoundException {
        PrintWriter out = new PrintWriter(fileName);
        out.print(html);
        out.close();
    }

    /**
     * Wait for the current page to produce the correct title.
     *
     * @param driver browser to perform the action with
     * @param title  title to wait for
     */
    private void waitForTitle(WebDriver driver, String title) {
        (new WebDriverWait(driver, 10)).until((WebDriver d) -> {
            return d.getTitle().toLowerCase().startsWith(title.toLowerCase());
        });
    }

    /**
     * Wait for an web element to appear on the page.
     *
     * @param driver browser to perform the action with
     * @param by     locator for the element
     * @return element found
     */
    private WebElement waitForElement(WebDriver driver, By by) {
        return waitForElement(driver, by, 10);
    }

    private WebElement waitForElement(WebDriver driver, By by, int timeOutInSeconds) {
        return (new WebDriverWait(driver, timeOutInSeconds)).until(ExpectedConditions.presenceOfElementLocated(by));
    }

    /**
     * Wait for web elements to appear on the page.
     *
     * @param driver browser to perform the action with
     * @param by     locator for the elements
     * @return elements found
     */
    private List<WebElement> waitForAllElements(WebDriver driver, By by) {
        return (new WebDriverWait(driver, 10).until(ExpectedConditions
                .presenceOfAllElementsLocatedBy(by)));
    }

    @SuppressWarnings("unused")
    private void displayCookies(WebDriver driver) {
        Set<Cookie> allCookies = driver.manage().getCookies();
        for (Cookie cookie : allCookies) {
            log.finer(String.format("%S -> %S", cookie.getName(), cookie.getValue()));
        }
    }

    /**
     * {@link #isTutURL(URL)}
     *
     * @param url string form of the URL to verify
     * @return {@code true} if URL is valid, {@code false} otherwise
     * @throws MalformedURLException if string could not be interpreted as a URL
     */
    private boolean isTutURL(String url) throws MalformedURLException {
        return isTutURL(new URL(url));
    }

    /**
     * Verifies that the given URL is a valid Digital Tutors tutorial page.
     *
     * @param url URL to verify
     * @return {@code true} if URL is valid, {@code false} otherwise
     */
    private boolean isTutURL(URL url) {
        boolean hostCorrect = url.getHost().contains("digitaltutors");
        boolean pathCorrect = url.getPath().contains("tutorial");
        return hostCorrect && pathCorrect;
    }

    /**
     * Switches the current video player from cloud to frame and vice versa.
     *
     * @param driver browser to perform action with
     * @throws NoSuchElementException if required web elements could not be found (e.g. neither players)
     */
    private void switchPlayer(WebDriver driver) throws NoSuchElementException {
        driver.findElement(By.xpath("//a[@id='lnkVideoHelp']")).click();

        List<WebElement> allElements = waitForAllElements(driver,
                By.xpath("//a[@class='lnkSwitchPlayers']"));
        boolean success = false;

        // ensures that we find the visible link since 2 out of 3 are hidden
        for (WebElement element : allElements) {
            try {
                element.click();
                success = true;
            } catch (ElementNotVisibleException e) {
                // this is one of the inactive ones
            }
        }

        if (!success)
            throw new ElementNotVisibleException("All switch player links are not visible");
    }


    /**
     * A runnable task that represents a single video download. This class is instantiated whenever a new background
     * thread to download a video is required and is part of a larger system of concurrent downloads.
     */
    private class DownloadTask implements Runnable {

        private URL link; // source to download from
        private File file; // file to do download too

        public DownloadTask(String link, File file) throws MalformedURLException {
            this(new URL(link), file);

        }

        public DownloadTask(URL url, File file) {
            this.link = url;
            this.file = file;
        }

        @Override
        public void run() {
            for (int i = 0; i < 3; i++)
                try {
                    FileUtils.copyURLToFile(link, file, 1000, 1000); // download the video
                    break;
                } catch (Exception e) {
                    if (i < 2) // retries to download video twice
                        log.warning("Retrying video download: " + file.getName());
                    else
                        log.warning("Could not download video: " + file.getName());
                }
        }
    }

    /**
     * Awakens the bot and initiates the login and download procedures
     *
     * @return always returns {@code null} as required by the super class in {@link #call() call}.
     */
    public Void start() {
        WebDriver driver = new FirefoxDriver();

        try {
            populateTutList();
            login(driver, true);
            while (true) {
                if (nextTut(driver) == null) {
                    // ran out of tutorials in the queue
                    break;
                }
                downloadVideos(driver);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, e.getMessage());
        }

        return null; // necessary for Task class call() signature
    }

    @Override
    protected Void call() throws Exception {
        if (!hasParameters()) {
            throw new IllegalStateException("This bot object does not have parameters to operate on");
        }
        return start();
    }
}