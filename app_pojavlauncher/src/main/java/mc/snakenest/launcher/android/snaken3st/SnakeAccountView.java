package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import mc.snakenest.launcher.android.R;
import java.lang.ref.WeakReference;
import java.util.Objects;

/** Amethyst toolbar label with an optional square Snake N3st avatar. */
public final class SnakeAccountView extends AppCompatTextView {
    private String requestedImage;
    private String requestedBase;
    private boolean requestSet;
    private long generation;

    public SnakeAccountView(Context context) { this(context, null); }
    public SnakeAccountView(Context context, AttributeSet attrs) { this(context, attrs, android.R.attr.textViewStyle); }
    public SnakeAccountView(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setCompoundDrawablePadding(dp(10));
        display(null);
    }

    /** A stable URL is requested once, including when the server returns an error. */
    public void setAvatar(String imageUrl, String baseUrl) {
        if (requestSet && Objects.equals(requestedImage, imageUrl) && Objects.equals(requestedBase, baseUrl)) return;
        requestSet = true; requestedImage = imageUrl; requestedBase = baseUrl;
        long current = ++generation;
        display(null);
        if (imageUrl == null || imageUrl.isBlank()) return;
        WeakReference<SnakeAccountView> reference = new WeakReference<>(this);
        RemoteIconLoader.load(getContext(), imageUrl, baseUrl, drawable -> {
            SnakeAccountView view = reference.get();
            if (view != null && view.generation == current) view.display(drawable);
        });
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void display(Drawable drawable) {
        if (drawable instanceof BitmapDrawable bitmap) {
            bitmap.setGravity(android.view.Gravity.FILL);
            bitmap.setFilterBitmap(false);
        }
        if (drawable == null) drawable = ContextCompat.getDrawable(getContext(), R.drawable.sn3_account_avatar);
        if (drawable != null) drawable.setBounds(0, 0, dp(32), dp(32));
        Drawable[] existing = getCompoundDrawablesRelative();
        setCompoundDrawablesRelative(drawable, existing[1], existing[2], existing[3]);
    }
}
