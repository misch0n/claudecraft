package pojlib.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.WeakReference;

/** Singleton class made to log on one file
 * The singleton part can be removed but will require more implementation from the end-dev
 */
// claudecraft: androidx @Keep removed. It only guides R8 when the AAR is built, has no runtime effect, and
// would make this patch depend on androidx being in the APK's classpath.
public class Logger {

    /* Instance variables */
    private final File mLogFile;
    private PrintStream mLogStream;
    private WeakReference<eventLogListener> mLogListenerWeakReference = null;

    /* No public construction */
    private Logger(){
        this("latestlog.txt");
    }

    private Logger(String fileName){
        mLogFile = new File(Constants.USER_HOME, fileName);
        // Make a new instance of the log file
        // claudecraft (log.keepPrevious, PLAN 1n): keep the previous run's log (often the crashed one)
        File kept = keepPreviousLog(fileName);
        mLogFile.delete();
        try {
            mLogFile.createNewFile();
            mLogStream = new PrintStream(mLogFile.getAbsolutePath());
        }catch (IOException e){e.printStackTrace();}
        if (kept != null && mLogStream != null) appendToLog("[claudecraft] previous launcher log kept as " + kept.getName());
    }

    /** Renames the existing log to name.prev.txt (replacing an older one) when log.keepPrevious is on.
     *  Uses ClaudecraftConfig.peekBoolean, which never calls back into the Logger. */
    private File keepPreviousLog(String fileName) {
        try {
            if (!mLogFile.isFile() || !ClaudecraftConfig.peekBoolean(ClaudecraftConfig.LOG_KEEP_PREVIOUS)) return null;
            String prevName = fileName.endsWith(".txt") ? fileName.substring(0, fileName.length() - 4) + ".prev.txt" : fileName + ".prev";
            File prev = new File(mLogFile.getParentFile(), prevName);
            prev.delete();
            return mLogFile.renameTo(prev) ? prev : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final class SLoggerSingletonHolder {
        static final Logger sLoggerSingleton = new Logger();
    }

    public static Logger getInstance(){
        return SLoggerSingletonHolder.sLoggerSingleton;
    }


    /** Print the text to the log file if not censored */
    public void appendToLog(String text){
        if(shouldCensorLog(text)) return;
        appendToLogUnchecked(text);
    }

    /** Print the text to the log file, no china censoring there */
    public void appendToLogUnchecked(String text){
        mLogStream.println(text);
        notifyLogListener(text);
    }

    /** Reset the log file, effectively erasing any previous logs */
    public void reset(){
        try{
            mLogFile.delete();
            mLogFile.createNewFile();
            mLogStream = new PrintStream(mLogFile.getAbsolutePath());
        }catch (IOException e){ e.printStackTrace();}
    }

    /** Disables the printing */
    public void shutdown(){
        mLogStream.close();
    }

    /**
     * Perform various checks to see if the log is safe to print
     * Subclasses may want to override this behavior
     * @param text The text to check
     * @return Whether the log should be censored
     */
    private static boolean shouldCensorLog(String text){
        return text.contains("Session ID is");
    }

    /** Small listener for anything listening to the log */
    public interface eventLogListener {
        void onEventLogged(String text);
    }

    /** Link a log listener to the logger */
    public void setLogListener(eventLogListener logListener){
        this.mLogListenerWeakReference = new WeakReference<>(logListener);
    }

    /** Notifies the event listener, if it exists */
    private void notifyLogListener(String text){
        if(mLogListenerWeakReference == null) return;
        eventLogListener logListener = mLogListenerWeakReference.get();
        if(logListener == null){
            mLogListenerWeakReference = null;
            return;
        }
        logListener.onEventLogged(text);
    }
}
