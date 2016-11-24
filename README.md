# DTBot
**Note: Since Digital Tutors has now refactored their webpage to Plural Sight, the web element paths are now obsolete**

Bot for downloading video tutorial series from Digital Tutors webpage. Uses a queue file such that multiple video tutorial series can be downloaded in sequence and outputs in neatly categorized folders and named mp4 files.

![interface image after start](http://i.imgur.com/2sec3RJ.png)

## Usage
The main function is located in `Main.java`

1. Create a text file where each line is a URL to the main tutorial webpage.
2. Hit the "queue" button and navigate to queue file created in step 1
3. Hit the "output" button and navigate to the folder where you want each series to be downloaded (subfolders will automatically be created)
4. Enter your login info into the text fields above the "queue" and "output" buttons
5. Hit the "start" button

The application will now begin to download your video tutorial series and provide colorful feedback in the pane to the right of the login. The bottom bar is a progress bar that shows the amount of tutorials completed and how many are left.

## Backend
One noteworthy feature of this bot is its logging system. It uses a specialized `ListView` that interacts with a custom log handler to output messages with different formatting to the GUI based on thier level.

The application uses JavaFX for its GUI, and `selenium` as an interface to the web.
