package mc.snakenest.launcher.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Thin facade over {@code java.util.logging} so call sites never touch
 * {@link Logger} directly. Deliberately minimal: one rotating file under
 * {@link AppDirs#logs()}, plus console output.
 *
 * <h2>Hard rule</h2>
 * Never pass a private key seed, a signature, or a full URL with a query
 * string to any method here. Query strings on this API always carry
 * {@code signature}/{@code publicKey}; logging them would leak the same
 * information a stolen log file could be replayed with. Log the request
 * path only.
 */
public final class Log {

    private static volatile boolean initialized;

    private Log() {
    }

    public static synchronized void initialize(AppDirs dirs) {
        if (initialized) {
            return;
        }
        try {
            Files.createDirectories(dirs.logs());
            Logger root = Logger.getLogger("mc.snakenest.launcher");
            root.setUseParentHandlers(false);
            root.setLevel(Level.ALL);

            FileHandler fileHandler = new FileHandler(dirs.logs().resolve("launcher-%g.log").toString(), 5 * 1024 * 1024, 5, true);
            fileHandler.setFormatter(new PlainFormatter());
            root.addHandler(fileHandler);

            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new PlainFormatter());
            root.addHandler(consoleHandler);

            initialized = true;
        } catch (IOException e) {
            System.err.println("Could not initialize file logging: " + e.getMessage());
        }
    }

    public static void info(Class<?> source, String message) {
        logger(source).info(message);
    }

    public static void warn(Class<?> source, String message) {
        logger(source).warning(message);
    }

    public static void error(Class<?> source, String message, Throwable cause) {
        logger(source).log(Level.SEVERE, message, cause);
    }

    private static Logger logger(Class<?> source) {
        return Logger.getLogger("mc.snakenest.launcher." + source.getSimpleName());
    }

    private static final class PlainFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String thrown = "";
            if (record.getThrown() != null) {
                var writer = new java.io.StringWriter();
                record.getThrown().printStackTrace(new java.io.PrintWriter(writer));
                thrown = System.lineSeparator() + writer;
            }
            return String.format("%s [%s] %s: %s%s%n",
                    java.time.Instant.ofEpochMilli(record.getMillis()),
                    record.getLevel(),
                    record.getLoggerName(),
                    record.getMessage(),
                    thrown
            );
        }
    }
}
