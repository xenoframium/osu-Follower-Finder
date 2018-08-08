import org.apache.http.client.HttpClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FriendChecker {
    private HttpClient client;
    private String userId;
    private String sid;

    public FriendChecker(HttpClient client, String userId) {
        this.client = client;
        this.userId = userId;
    }

    public boolean checkIsFriend(){
        String response = tryAddFriend(userId);
        if (response != null) {
            String isFriendPattern = "<a href='\\/u\\/.+?\\?a=remove&localUserCheck=.+?' class=\"btn\" style='background:#ef77af;'><i class=\"icon-heart\"><\\/i> Mutual Friend<\\/a>";
            Pattern p = Pattern.compile(isFriendPattern);
            Matcher m = p.matcher(response);
            if (m.find()) {
                tryRemoveFriend(userId);
                return true;
            }
            tryRemoveFriend(userId);
        }
        return false;
    }

    String tryAddFriend(String userId){
        String responseHTML = HttpUtil.sendGet(client, "https://osu.ppy.sh/u/"+userId);
        String addFriendMatcher = "<a href='(\\/u\\/.+?\\?a=add&localUserCheck=.+?)'";

        Pattern p = Pattern.compile(addFriendMatcher);
        Matcher m = p.matcher(responseHTML);
        if (m.find()) {
            String url = "https://osu.ppy.sh"+m.group(1);
            responseHTML = HttpUtil.sendGet(client, url);
            return responseHTML;
        } else {
            return null;
        }
    }

    boolean tryRemoveFriend(String userId){
        String responseHTML = HttpUtil.sendGet(client, "https://osu.ppy.sh/u/"+userId);
        String removeFriendMatcher = "<a href='(\\/u\\/.+?\\?a=remove&localUserCheck=.+?)'";

        Pattern p = Pattern.compile(removeFriendMatcher);
        Matcher m = p.matcher(responseHTML);
        if (m.find()) {
            String url = "https://osu.ppy.sh"+m.group(1);
            responseHTML = HttpUtil.sendGet(client, url);
            return true;
        } else {
            return false;
        }
    }
}
