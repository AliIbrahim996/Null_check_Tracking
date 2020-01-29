package tracking.pkgnull.check;

import java.util.ArrayList;
import java.util.Scanner;

public class Message {

    String msg;
    String Msg;
    String mSg;
    ArrayList<String> messages;

    Message() {
        msg = "";
        messages = null;

    }

    public Object getMessage(String msg2) {

        for (String msg3 : messages) {
            mSg = "5";
            if (msg3 != null) {
                if (msg2 != null) {
                    if ((msg2 == mSg) || (mSg != null)) {
                        if (msg2.equals(msg)) {
                            return true;
                        }
                    }
                }
            }
        }
        return null;
    }

    public Object Join(String message) {
        if (msg != null) {
            msg += message;
            return msg;
        }
        return null;
    }

    public String print() {
        return "****";
    }

    public static void main(String[] args) {
        Message aMessage = new Message();
        Message bMessage = new Message();
        Scanner in = new Scanner(System.in);
        String firstMessage = in.next();
        String secondMessage = in.next();
        String thirdMessage = in.next();
        String forthMessage = in.next();
        aMessage.Msg = firstMessage;
        aMessage.Msg = bMessage.print();
        if ((aMessage.Msg != null) && (null == aMessage.Join(""))) {
            if ((bMessage.mSg == null) || (aMessage.getMessage(firstMessage) != null)) {
                aMessage.Join(firstMessage + secondMessage);
            }
        }
        bMessage.Msg = (String) aMessage.Join(forthMessage);
        if ((bMessage.msg != null) || (bMessage.getMessage(secondMessage) == null)) {
            if (bMessage.messages != null) {
                bMessage.Join(thirdMessage + forthMessage);
            }
        }
    }

}
