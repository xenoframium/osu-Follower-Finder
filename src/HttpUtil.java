import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpUtil {
    public static class BodyEntry {
        public String key;
        public String value;

        BodyEntry(String k, String v){
            key=k;
            value=v;
        }
    }

    //Hey its my user agent
    public static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.84 Safari/537.36";

    public static String sendGet(HttpClient client, String url) {
        HttpGet request = new HttpGet(url);

        request.addHeader("User-Agent", USER_AGENT);

        boolean done = false;
        HttpResponse response = null;
        StringBuffer result = null;
        while (!done) {
            try {
                response = client.execute(request);
                if (response.getStatusLine().getStatusCode() >= 400) {
                    System.out.println("Received Code " + response + " while sending GET request, retrying in 1 second...");
                    Thread.sleep(1000);
                    continue;
                }

                done = true;

                BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                result = new StringBuffer();
                String line = "";
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }

                request.releaseConnection();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return result.toString();
    }

    public static String sendPost(HttpClient client, String url, List<NameValuePair> bodyEntries) {
        HttpPost post = new HttpPost(url);

        post.setHeader("User-Agent", HttpUtil.USER_AGENT);

        try {
            post.setEntity(new UrlEncodedFormEntity(bodyEntries));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        boolean done = false;
        HttpResponse response = null;
        String responseHTML = null;
        while (!done) {
            try {
                response = client.execute(post);
                if (response.getStatusLine().getStatusCode() >= 400) {
                    System.out.println("Received Code " + response + " while sending POST request, retrying in 1 second...");
                    Thread.sleep(1000);
                    continue;
                }

                done = true;

                BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

                StringBuffer result = new StringBuffer();
                String line = "";
                while ((line = rd.readLine()) != null) {
                    result.append(line);
                }

                responseHTML = result.toString();
                post.releaseConnection();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return responseHTML;
    }
}
