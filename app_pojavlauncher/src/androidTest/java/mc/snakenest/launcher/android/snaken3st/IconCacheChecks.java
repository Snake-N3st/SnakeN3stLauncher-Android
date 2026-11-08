package mc.snakenest.launcher.android.snaken3st;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Bundle;
import java.net.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated fake avatar identity: no session access, no activity or game launch. */
final class IconCacheChecks {
    static void run(Instrumentation instrumentation) {
        Bundle result=new Bundle(); int checks=0;
        String site="https://offline-icon-fixture.invalid"; long player=Long.MAX_VALUE-17;
        ProxySelector previous=ProxySelector.getDefault(); AtomicInteger requests=new AtomicInteger();
        Path file=null; boolean owned=false;
        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) { requests.incrementAndGet(); throw new IllegalStateException("Network forbidden by cache test"); }
                @Override public void connectFailed(URI uri,SocketAddress address,IOException error) { }
            });
            var context=instrumentation.getTargetContext();
            Method path=AvatarDiskCache.class.getDeclaredMethod("file",android.content.Context.class,String.class,long.class); path.setAccessible(true);
            file=(Path)path.invoke(null,context,site,player);
            if(Files.exists(file)) throw new AssertionError("Fixture identity already in use");
            owned=true;
            Bitmap source=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888); source.eraseColor(0xff52c030);
            AvatarDiskCache.save(context,site,player,source); source.recycle();
            Bitmap restored=AvatarDiskCache.read(context,site,player);
            require(restored!=null && restored.getPixel(0,0)==0xff52c030,"disk-round-trip"); checks++; restored.recycle();
            require(AvatarDiskCache.read(context,site,player-1)==null,"account-isolation"); checks++;
            require(AvatarDiskCache.read(context,"https://other-icon-fixture.invalid",player)==null,"site-isolation"); checks++;
            Files.writeString(file,"corrupt optional image");
            require(AvatarDiskCache.read(context,site,player)==null,"corrupt-cache-fallback"); checks++;
            mc.snakenest.launcher.android.Tools.initStorageConstants(context);
            var catalogue=new mc.snakenest.launcher.modpack.LocalPackCatalogStore(context.getNoBackupFilesDir().toPath().resolve("snake-n3st/local-pack-catalog.json"),mc.snakenest.launcher.android.BuildConfig.SN3_BASE_URL);
            int[] cachedPacks={0};
            for(var pack:catalogue.loadSummaries()) instrumentation.runOnMainSync(()->SnakePackIcons.loadLocal(context,pack,icon->{if(icon!=null)cachedPacks[0]++;}));
            result.putInt("nativePackIcons",cachedPacks[0]);
            require(requests.get()==0,"zero-network"); result.putBoolean("success",true);
        } catch(Throwable failure) {
            result.putBoolean("success",false); result.putString("message",failure.getClass().getSimpleName()+": "+failure.getMessage());
        } finally {
            try { if(owned && file!=null) Files.deleteIfExists(file); } catch(Exception cleanup) { result.putBoolean("success",false); }
            ProxySelector.setDefault(previous);
            result.putInt("checks",checks); result.putInt("httpRequests",requests.get());
            instrumentation.finish(result.getBoolean("success")?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    private static void require(boolean valid,String name) { if(!valid) throw new AssertionError(name); }
}
