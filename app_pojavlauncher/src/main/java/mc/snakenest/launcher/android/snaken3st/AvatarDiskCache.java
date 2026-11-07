package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Sha256;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

/** Last good avatar, scoped to the site and player. Contains no session credentials. */
final class AvatarDiskCache {
    private static Path file(Context context, String site, long playerId) {
        String key=Sha256.hex((site.replaceAll("/+$", "")+"\n"+playerId).getBytes(StandardCharsets.UTF_8));
        return context.getNoBackupFilesDir().toPath().resolve("snake-n3st/public-icons/avatar-"+key+".png");
    }
    static Bitmap read(Context context, String site, long playerId) {
        if(playerId<=0) return null;
        try {
            Path file=file(context,site,playerId);
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>1024*1024) return null;
            byte[] data=Files.readAllBytes(file);
            BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(data,0,data.length,bounds);
            if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>256 || bounds.outHeight>256) return null;
            return BitmapFactory.decodeByteArray(data,0,data.length);
        } catch(Exception ignored) { return null; }
    }
    static void save(Context context, String site, long playerId, Bitmap bitmap) {
        if(playerId<=0 || bitmap==null || bitmap.getWidth()>256 || bitmap.getHeight()>256) return;
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes) && bytes.size()<=1024*1024) AtomicFiles.write(file(context,site,playerId),bytes.toByteArray());
        } catch(Exception ignored) { }
    }
}
