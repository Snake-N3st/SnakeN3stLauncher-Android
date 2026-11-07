package mc.snakenest.launcher.android.snaken3st;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;
import mc.snakenest.launcher.android.Tools;
import mc.snakenest.launcher.android.utils.DownloadUtils;

/** Fake HTTP connections exercise real streaming and atomic replacement without network access. */
final class DownloadIoChecks {
    static int run(Context context)throws Exception {
        Path root=Files.createTempDirectory(context.getCacheDir().toPath(),"sn3-transfer-fixture-");
        File target=root.resolve("file.bin").toFile();int checks=0;
        Method method=DownloadUtils.class.getDeclaredMethod("downloadFileMonitored",URL.class,File.class,byte[].class,Tools.DownloaderFeedback.class);method.setAccessible(true);
        try {
            byte[] original={1,2,3};Files.write(target.toPath(),original);
            AtomicInteger progress=new AtomicInteger();
            transfer(method,url(200,new byte[]{4,5,6,7},4,false),target,(current,total)->progress.set(current));
            require(java.util.Arrays.equals(Files.readAllBytes(target.toPath()),new byte[]{4,5,6,7})&&progress.get()==4);checks++;
            Files.write(target.toPath(),original);
            require(failure(method,url(200,new byte[]{8,9},100,false),target) instanceof EOFException);
            require(java.util.Arrays.equals(Files.readAllBytes(target.toPath()),original));checks++;
            require(failure(method,url(500,new byte[]{8},1,false),target) instanceof IOException);
            require(java.util.Arrays.equals(Files.readAllBytes(target.toPath()),original));checks++;
            require(failure(method,url(404,new byte[]{8},1,false),target) instanceof FileNotFoundException);checks++;
            require(failure(method,url(200,new byte[50],50,true),target) instanceof IOException);
            require(java.util.Arrays.equals(Files.readAllBytes(target.toPath()),original));checks++;
            try {
                Thread.currentThread().interrupt();
                require(failure(method,url(200,new byte[50],50,false),target) instanceof InterruptedIOException);
            }finally{Thread.interrupted();}
            require(java.util.Arrays.equals(Files.readAllBytes(target.toPath()),original));
            try(var paths=Files.list(root)){require(paths.noneMatch(p->p.getFileName().toString().endsWith(".part")));}checks++;
            Files.write(target.toPath(),new byte[0]);AtomicInteger downloaded=new AtomicInteger();
            DownloadUtils.ensureSha1(target,null,()->{downloaded.incrementAndGet();Files.write(target.toPath(),original);return null;});
            require(downloaded.get()==1);checks++;
            return checks;
        }finally{try(var paths=Files.list(root)){for(Path p:paths.toList())Files.delete(p);}Files.delete(root);}
    }
    private static void transfer(Method method,URL source,File target,Tools.DownloaderFeedback monitor)throws Exception {
        try{method.invoke(null,source,target,new byte[8],monitor);}catch(InvocationTargetException failure){Throwable cause=failure.getCause();if(cause instanceof Exception e)throw e;throw new AssertionError(cause);}
    }
    private static Throwable failure(Method method,URL source,File target) {
        try{transfer(method,source,target,(current,total)->{});return null;}catch(Throwable failure){return failure;}
    }
    private static URL url(int status,byte[] body,long length,boolean interruptedStream)throws Exception {
        return new URL(null,"https://no-network.invalid/fixture",new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
                @Override public void connect(){}@Override public void disconnect(){}@Override public boolean usingProxy(){return false;}
                @Override public int getResponseCode(){return status;}@Override public long getContentLengthLong(){return length;}
                @Override public InputStream getInputStream(){
                    if(!interruptedStream)return new ByteArrayInputStream(body);
                    return new InputStream(){int count;@Override public int read()throws IOException{if(count++>=10)throw new IOException("Fixture stream failure");return 8;}};
                }
            };}
        });
    }
    private static void require(boolean condition){if(!condition)throw new AssertionError("Download I/O assertion failed");}
}
