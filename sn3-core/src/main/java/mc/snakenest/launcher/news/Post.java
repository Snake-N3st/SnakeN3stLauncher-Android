package mc.snakenest.launcher.news;

import com.google.gson.annotations.SerializedName;

/**
 * Shape of Azuriom core's {@code PostResource} (verified against
 * {@code app/Http/Resources/PostResource.php} on the local instance, not
 * just documentation): {@code /api/posts} omits {@code content} for nothing
 * but this class covers both the list and the single-post shape, since
 * Gson simply leaves unset fields {@code null} rather than erroring.
 */
public record Post(
        long id,
        String title,
        String description,
        String slug,
        String url,
        String content,
        Author author,
        @SerializedName("published_at") String publishedAt,
        String image
) {
    public record Author(long id, String name) {
    }
}
