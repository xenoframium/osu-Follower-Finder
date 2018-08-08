import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    volatile static String username;
    volatile static String password;
    volatile static String verificationCode = null;
    static String sid = "";
    static String userId = "";
    static String country = "";
    static boolean hasVerified = false;

    static BasicCookieStore baseCookieStore = new BasicCookieStore();

    private static HttpClient originalClient = null;

    private static JTextArea textArea = null;
    private static JScrollPane scroll = null;
    private static JPanel panel = null;
    private static JLabel progressLabel = null;
    private static JLabel stepLabel = null;
    private static JProgressBar progressBar = null;
    private static JTextField startFromUserField = null;

    private static int startFromUser = 0;
    private static boolean hasLoggedIn = false;

    private static CountDownLatch verifyLatch = new CountDownLatch(1);

    private static class LoginFailException extends RuntimeException {}

    private static void printToTextArea(String text) {
        textArea.append(text + "\n");
    }

    private static void printToStepLabel(String text) {
        stepLabel.setText("Current Step:" + text);
    }

    private static void printToProgressLabel(String text) {
        progressLabel.setText(text);
    }

    private static void updateProgress(double percent) {
        progressBar.setValue((int) percent);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("osu! Follower Finder");

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent windowEvent){
                System.exit(0);
            }
        });

        panel = new JPanel();
        panel.setLayout(new GridBagLayout());

        frame.add(panel);

        GridBagConstraints c = new GridBagConstraints();

        JLabel usernameLabel = new JLabel("Username:");
        c.gridx = 0;
        c.gridy = 0;
        panel.add(usernameLabel, c);

        JTextField usernameField = new JTextField(10);
        c.gridx = 1;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(usernameField, c);

        JLabel passwordLabel = new JLabel("Password:");
        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.NONE;
        panel.add(passwordLabel, c);

        JPasswordField passwordField = new JPasswordField(10);
        c.gridx = 1;
        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(passwordField, c);

        JButton runButton = new JButton("Find Followers!");
        c.gridx = 2;
        c.gridy = 0;
        c.gridheight = 3;
        c.fill = GridBagConstraints.BOTH;
        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(hasLoggedIn) return;
                progressLabel.setText("Authenticating...");
                username = usernameField.getText();
                password = new String(passwordField.getPassword());
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startFromUser = Integer.valueOf(startFromUserField.getText());
                            if (startFromUser < 1) {
                                //Please forgive me lmao
                                throw new NumberFormatException();
                            }
                        } catch(NumberFormatException ex) {
                            progressLabel.setText("'" + startFromUserField.getText() + "' Is Not a Valid Rank");
                            return;
                        }
                        try {
                            beginQuery();
                        } catch(LoginFailException ex) {
                            ex.printStackTrace();
                            progressLabel.setText("Failed To Login, Try Again.");
                        }
                    }
                }).start();
            }
        });
        panel.add(runButton, c);

        JLabel startFromUser = new JLabel("Start From Rank #:");
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.fill = GridBagConstraints.NONE;
        panel.add(startFromUser, c);

        startFromUserField = new JTextField("1", 10);
        c.gridx = 1;
        c.gridy = 2;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(startFromUserField, c);

        stepLabel = new JLabel("Current Step:", SwingConstants.CENTER);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 3;
        c.gridheight = 1;
        panel.add(stepLabel, c);

        progressLabel = new JLabel("Enter Credentials", SwingConstants.CENTER);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 3;
        c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(progressLabel, c);

        progressBar = new JProgressBar(0, 10);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 3;
        c.gridheight = 1;
        panel.add(progressBar, c);

        JLabel foundFriends = new JLabel("Found Followers:", SwingConstants.CENTER);
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 3;
        c.gridheight = 1;
        panel.add(foundFriends, c);

        textArea = new JTextArea(20, 0);
        textArea.setEditable(false);
        ((DefaultCaret)textArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        scroll = new JScrollPane(textArea);
        c.gridx = 0;
        c.gridy = 7;
        c.gridwidth = 3;
        c.gridheight = 1;
        panel.add(scroll, c);

        frame.pack();
        frame.setResizable(false);

        frame.setVisible(true);
    }

    private static void verifyUser() {
        String responseHtml = HttpUtil.sendGet(originalClient, "https://osu.ppy.sh/p/verify");

        String vPattern = "<input type='hidden' name='v' value='(.+?)'\\/>";
        Pattern p = Pattern.compile(vPattern);
        Matcher m = p.matcher(responseHtml);
        if (m.find()) {
            boolean done = false;

            JFrame verifyFrame = new JFrame("Account Verification");
            JPanel panel = new JPanel();
            verifyFrame.add(panel);

            panel.setLayout(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();

            JLabel verificationNotice = new JLabel("Please Enter The Verification Code Sent To Your Email");
            c.gridx = 0;
            c.gridy = 0;
            c.gridwidth = 2;
            panel.add(verificationNotice, c);

            JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
            c.gridx = 0;
            c.gridy = 1;
            c.gridwidth = 2;
            panel.add(statusLabel, c);

            JLabel enterVerificationLabel = new JLabel("Code:", SwingConstants.CENTER);
            c.gridx = 0;
            c.gridy = 2;
            c.gridwidth = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(enterVerificationLabel, c);

            JTextField textField = new JTextField(9);
            c.gridx = 1;
            c.gridy = 2;
            c.fill = GridBagConstraints.BOTH;
            panel.add(textField, c);

            verifyFrame.pack();

            List<NameValuePair> parameters = new ArrayList<NameValuePair>();
            String v = m.group(1);
            parameters.add(new BasicNameValuePair("v", v));
            parameters.add(new BasicNameValuePair("check", "forgot"));
            HttpUtil.sendPost(originalClient, "https://osu.ppy.sh/p/verify", parameters);

            textField.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    statusLabel.setText("Verifying...");
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            verificationCode = textField.getText();

                            List<NameValuePair> parameters = new ArrayList<>();
                            parameters.clear();
                            parameters.add(new BasicNameValuePair("check", "verify"));
                            parameters.add(new BasicNameValuePair("v", v));
                            parameters.add(new BasicNameValuePair("value", verificationCode));
                            parameters.add(new BasicNameValuePair("checkonly", "true"));
                            String responseHTML = HttpUtil.sendPost(originalClient, "https://osu.ppy.sh/p/verify", parameters);
                            if (responseHTML.equals("")) {
                                parameters.remove(parameters.size() - 1);
                                HttpUtil.sendPost(originalClient, "https://osu.ppy.sh/p/verify", parameters);

                                hasVerified = true;
                                verifyFrame.dispose();
                                verifyLatch.countDown();
                            } else {
                                statusLabel.setText("Wrong Verification Code, Try Again.");
                            }
                        }
                    }).start();
                }
            });

            verifyFrame.addWindowListener(new WindowListener() {
                @Override
                public void windowOpened(WindowEvent e) {}

                @Override
                public void windowClosing(WindowEvent e) {
                    if (!hasVerified) {
                        System.exit(0);
                    }
                }

                @Override
                public void windowClosed(WindowEvent e) {}

                @Override
                public void windowIconified(WindowEvent e) {}

                @Override
                public void windowDeiconified(WindowEvent e) {}

                @Override
                public void windowActivated(WindowEvent e) {}

                @Override
                public void windowDeactivated(WindowEvent e) {}
            });
            verifyFrame.setVisible(true);
        } else {
            hasVerified = true;
            verifyLatch.countDown();
        }
    }

    public static void beginQuery() {
        BasicClientCookie cookie = new BasicClientCookie("osu_site_v", "old");
        cookie.setDomain(".osu.ppy.sh");
        cookie.setPath("/");
        baseCookieStore.addCookie(cookie);

        originalClient = HttpClientBuilder.create().setDefaultCookieStore(baseCookieStore).build();

        progressLabel.setText("Authenticating...");

        login();
        getCountry();

        verifyUser();

        try {
            verifyLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        //Lol concurrent arrays dont kill me please (although theres so many other things wrong with this code in general)
        List<String> userIds = Collections.synchronizedList(new ArrayList<>());
        List<String> userNames = Collections.synchronizedList(new ArrayList<>());

        printToStepLabel(" Scanning Country Player Pages...");

        //Lol apparently ip limited so it doesnt even matter if you have 1 or 10 threads, maybe ill bother with vpn someday who knows
        AtomicInteger pageCounter = new AtomicInteger(0);
        progressBar.setMinimum(1);

        String res = HttpUtil.sendGet(originalClient, "https://osu.ppy.sh/p/pp/?m=0&c="+country);
        Pattern p = Pattern.compile("<div class=\"pagination\">Displaying 1 to [0-9]+ of (.+?) results\\.<br\\/>");
        Matcher m = p.matcher(res);
        m.find();
        int numUsers = Integer.parseInt(m.group(1));
        int numPages = (numUsers-1)/50+1;

        progressBar.setMaximum(numPages);
        for (int i = (startFromUser-1)/50 + 1; i <= numPages; i++) {
            updateProgress(i);
            printToProgressLabel(String.format("Scanning Page (%d/%d)",i, numPages));
            try {
                scanUsers(i, userIds, userNames);
            } catch(Exception e) {
                e.printStackTrace();
                i--;
                continue;
            }
        }

        List<String> newFriends = Collections.synchronizedList(new ArrayList<>());
        pageCounter.set(0);

        printToStepLabel(" Checking Players For Friends...");

        progressBar.setMinimum(0);
        progressBar.setMaximum(userIds.size()-1);
        int counter = startFromUser;
        for (int i = (startFromUser-1)%50; i < userNames.size(); i++, counter++) {
            try {
                updateProgress(i);
                printToProgressLabel(String.format("Checking Player '%s' (%d/%d)", userNames.get(i), counter, numUsers));
                FriendChecker checker = new FriendChecker(createClient(), userIds.get(i));
                boolean isNewFriend = checker.checkIsFriend();
                if (isNewFriend) {
                    printToTextArea("https://osu.ppy.sh/u/" + userNames.get(i));
                }
                newFriends.add(userNames.get(i));
            } catch (Exception e) {
                i--;
                counter--;
                e.printStackTrace();
                continue;
            }
        }

        logout();

        stepLabel.setText("Complete!");
        progressLabel.setText("Links to Followers are Below.");
    }

    private static void scanUsers(int page, List<String> userIds, List<String> usernames) {
        String url = "https://osu.ppy.sh/p/pp/?c="+country+"&m=0&s=3&o=1&f=&page="+page;
        String responseHTML = HttpUtil.sendGet(originalClient, url);

        String userPattern = "href='\\/u\\/(.+?)'>(.+?)<\\/a><\\/td>";

        Pattern p = Pattern.compile(userPattern);
        Matcher m = p.matcher(responseHTML);

        while (m.find()) {
            userIds.add(m.group(1));
            usernames.add(m.group(2));
        }
    }

    private static BasicClientCookie copyCookie(Cookie cookie) {
        BasicClientCookie cookie2 = new BasicClientCookie(cookie.getName(), cookie.getValue());
        cookie2.setDomain(cookie.getDomain());
        cookie2.setPath(cookie.getPath());
        return cookie2;
    }

    private static BasicCookieStore copyCookieStore(CookieStore store) {
        BasicCookieStore store2 = new BasicCookieStore();
        for (Cookie cookie : store.getCookies()) {
            store2.addCookie(copyCookie(cookie));
        }
        return store2;
    }

    private static HttpClient createClient() {
        return HttpClientBuilder.create().setDefaultCookieStore(copyCookieStore(baseCookieStore)).build();
    }

    private static void getCountry() {
        String responseHTML = HttpUtil.sendGet(originalClient, "https://osu.ppy.sh/u/"+userId);

        String countryPattern = "<a href='\\/p\\/pp\\?s=3&o=1&c=NZ&find="+username+"#jumpto'><img class='flag' title='.+?' src=\"\\/\\/s\\.ppy\\.sh\\/images\\/flags\\/(.+?)\\.gif\"";
        Pattern r = Pattern.compile(countryPattern);
        Matcher m = r.matcher(responseHTML);
        m.find();
        country = m.group(1);
    }

    private static void login() {
        String url = "https://osu.ppy.sh/forum/ucp.php?mode=login";

        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair("username", Main.username));
        parameters.add(new BasicNameValuePair("password", Main.password));
        parameters.add(new BasicNameValuePair("login", "login"));
        parameters.add(new BasicNameValuePair("sid", ""));
        String responseHTML = HttpUtil.sendPost(originalClient, url, parameters);

        String sidPattern = "<a href=\"\\/forum\\/ucp\\.php\\?mode=logout&sid=(.+?)\">Logout<\\/a>";

        // Create a Pattern object
        Pattern r = Pattern.compile(sidPattern);

        // Now create matcher object.
        Matcher m = r.matcher(responseHTML);
        m.find();
        try {
            sid = m.group(1);
        } catch(IllegalStateException e) {
            throw new LoginFailException();
        }

        String userIDPattern = "<div style=\"float:right; color: #666666;\">Welcome, <b><a href=\"\\/u\\/(.+?)\">";
        r = Pattern.compile(userIDPattern);
        m = r.matcher(responseHTML);
        m.find();
        userId = m.group(1);

        hasLoggedIn = true;
        password = null;
    }

    private static void logout() {
        HttpUtil.sendGet(originalClient, "https://osu.ppy.sh/forum/ucp.php?mode=logout&sid="+sid);
    }
}
