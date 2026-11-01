package mc.snakenest.launcher.news;

/** Checked on purpose: the News page must decide how to show a fetch failure. */
public final class NewsApiException extends Exception {

    public NewsApiException(String message) {
        super(message);
    }
}
