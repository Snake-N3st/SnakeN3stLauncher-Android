package mc.snakenest.launcher.android.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.concurrent.Callable;

import mc.snakenest.launcher.android.*;
import org.apache.commons.io.*;

@SuppressWarnings("IOStreamConstructor")
public class DownloadUtils {
    public static final String USER_AGENT = Tools.APP_NAME;
    private static final int TIME_OUT = 10000;

    public static void download(String url, OutputStream os) throws IOException {
        download(new URL(url), os);
    }

    public static void download(URL url, OutputStream os) throws IOException {
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        try {
            configure(connection);checkStatus(connection);
            try(InputStream input=connection.getInputStream()) { copy(input,os,connection.getContentLengthLong(),new byte[65536],null); }
        } finally { connection.disconnect(); }
    }
    private static void configure(HttpURLConnection connection) {
        connection.setRequestProperty("User-Agent",USER_AGENT);
        connection.setRequestProperty("Accept-Encoding","identity");
        connection.setConnectTimeout(TIME_OUT);connection.setReadTimeout(TIME_OUT);
    }
    private static void checkStatus(HttpURLConnection connection) throws IOException {
        int status=connection.getResponseCode();
        if(status==404)throw new FileNotFoundException("HTTP 404");
        if(status!=200)throw new IOException("Download returned HTTP "+status);
    }
    private static void copy(InputStream input,OutputStream output,long expected,byte[] buffer,@Nullable Tools.DownloaderFeedback monitor) throws IOException {
        long overall=0,lastReport=0;
        while(true) {
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download cancelled");
            int count=input.read(buffer);if(count<0)break;
            output.write(buffer,0,count);overall+=count;
            long now=System.nanoTime();
            if(monitor!=null && (now-lastReport>=50_000_000L || overall==expected)) {
                monitor.updateProgress((int)Math.min(Integer.MAX_VALUE,overall),(int)Math.min(Integer.MAX_VALUE,expected));lastReport=now;
            }
        }
        if(expected>=0 && overall!=expected)throw new EOFException("Incomplete download");
        if(monitor!=null)monitor.updateProgress((int)Math.min(Integer.MAX_VALUE,overall),(int)Math.min(Integer.MAX_VALUE,expected));
    }

    public static String downloadString(String url) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        download(url, bos);
        bos.close();
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void downloadFile(String url, File out) throws IOException {
        downloadFileMonitored(url,out,null,(current,total)->{});
    }

    public static void downloadFileMonitored(String urlInput, File outputFile, @Nullable byte[] buffer,
                                             Tools.DownloaderFeedback monitor) throws IOException {
        downloadFileMonitored(new URL(urlInput),outputFile,buffer,monitor);
    }

    static void downloadFileMonitored(URL source, File outputFile, @Nullable byte[] buffer,
                                      Tools.DownloaderFeedback monitor) throws IOException {
        FileUtils.ensureParentDirectory(outputFile);
        java.nio.file.Path part=java.nio.file.Files.createTempFile(outputFile.getAbsoluteFile().getParentFile().toPath(),".sn3-download-",".part");
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)source.openConnection();configure(connection);checkStatus(connection);
            try(InputStream input=connection.getInputStream();OutputStream output=java.nio.file.Files.newOutputStream(part)) {
                copy(input,output,connection.getContentLengthLong(),buffer==null?new byte[65536]:buffer,monitor);
            }
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download cancelled");
            java.nio.file.Files.move(part,outputFile.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } finally {
            if(connection!=null)connection.disconnect();
            java.nio.file.Files.deleteIfExists(part);
        }
    }

    public static <T> T downloadStringCached(String url, String cacheName, ParseCallback<T> parseCallback) throws IOException, ParseException{
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/"+cacheName);
        if(cacheDestination.isFile() &&
                cacheDestination.canRead() &&
                System.currentTimeMillis() < (cacheDestination.lastModified() + 86400000)) {
            try {
                String cachedString = Tools.read(new FileInputStream(cacheDestination));
                return parseCallback.process(cachedString);
            }catch(IOException e) {
                Log.i("DownloadUtils", "Failed to read the cached file", e);
            }catch (ParseException e) {
                Log.i("DownloadUtils", "Failed to parse the cached file", e);
            }
        }
        String urlContent = DownloadUtils.downloadString(url);
        // if we download the file and fail parsing it, we will yeet outta there
        // and not cache the unparseable sting. We will return this after trying to save the downloaded
        // string into cache
        T parseResult = parseCallback.process(urlContent);

        boolean tryWriteCache;
        if(cacheDestination.exists()) {
            tryWriteCache = cacheDestination.canWrite();
        } else {
            tryWriteCache = FileUtils.ensureParentDirectorySilently(cacheDestination);
        }

        if(tryWriteCache) try {
            Tools.write(cacheDestination.getAbsolutePath(), urlContent);
        }catch(IOException e) {
            Log.i("DownloadUtils", "Failed to cache the string", e);
        }
        return parseResult;
    }

    private static <T> T downloadFile(Callable<T> downloadFunction) throws IOException{
        try {
            return downloadFunction.call();
        } catch (IOException e){
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyFile(File file, String sha1) {
        return file.exists() && Tools.compareSHA1(file, sha1);
    }

    public static <T> T ensureSha1(File outputFile, @Nullable String sha1, Callable<T> downloadFunction) throws IOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Download cancelled");
        // Skip if needed
        if(sha1 == null) {
            // If the file exists and we don't know it's SHA1, don't try to redownload it.
            if(outputFile.isFile() && outputFile.length()>0) return null;
            else return downloadFile(downloadFunction);
        }

        int attempts = 0;
        boolean fileOkay = verifyFile(outputFile, sha1);
        T result = null;
        while (attempts < 5 && !fileOkay){
            attempts++;
            downloadFile(downloadFunction);
            fileOkay = verifyFile(outputFile, sha1);
        }
        if(!fileOkay) throw new SHA1VerificationException("SHA1 verifcation failed after 5 download attempts");
        return result;
    }

    /**
     * Get the content length for a given URL.
     * @param url the URL to get the length for
     * @return the length in bytes or -1 if not available
     * @throws IOException if an I/O error occurs.
     */
    public static long getContentLength(String url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();
        urlConnection.setRequestMethod("HEAD");
        urlConnection.setDoInput(false);
        urlConnection.setDoOutput(false);
        urlConnection.connect();
        int responseCode = urlConnection.getResponseCode();
        if(responseCode >= 200 && responseCode <= 299) return urlConnection.getContentLength();
        return -1;
    }

    public interface ParseCallback<T> {
        T process(String input) throws ParseException;
    }
    public static class ParseException extends Exception {
        public ParseException(Exception e) {
            super(e);
        }
    }

    public static class SHA1VerificationException extends IOException {
        public SHA1VerificationException(String message) {
            super(message);
        }
    }
}

