package com.nuvolect.deepdive.util;

import android.content.Context;
import android.util.Log;

import com.nuvolect.deepdive.R;
import com.nuvolect.deepdive.main.App;

import java.io.PrintWriter;
import java.io.StringWriter;

public class LogUtil {

    public static boolean DEBUG = true;
    public static boolean VERBOSE = DEBUG;
    public static final String TAG = App.getContext().getResources().getString(R.string.app_name);

    public enum LogType { NIL,
        ANALYTICS,
        APP_SURVEY,
        APP_SURVEY_FRAGMENT,
        BETTER_CRYPTO,
        CAMERA_CONTROLLER,
        CMD_DEBUG,
        CMD_DUPLICATE,
        CMD_EXTRACT,
        CMD_FILE,
        CMD_GET,
        CMD_IMAGE_QUERY,
        CMD_INFO,
        CMD_LOGIN,
        CMD_LS,
        CMD_MKDIR,
        CMD_MKFILE,
        CMD_OPEN,
        CMD_PARENTS,
        CMD_PASTE,
        CMD_PUT,
        CMD_RENAME,
        CMD_RESIZE,
        CMD_RM,
        CMD_SEARCH,
        CMD_TREE,
        CMD_UPLOAD,
        CMD_ZIPDL,
        CRYPT,
        CRYP_FILE,
        CRYP_SERVER,
        DO_INFO,
        DO_RM,
        FILE_OBJ,
        GALLERY,
        GALLERY_LONG_PRESS,
        INFO,
        LOCK_ACTIVITY,
        MAIN,
        MIME_UTIL,
        MY_WEB_VIEW_CLIENT,
        NFC_ACTIVITY,
        NFC_SESSION,
        OMNI_FILE,
        OMNI_FILES,
        OMNI_IMAGE,
        OMNI_ZIP,
        PHOTO,
        RESTFUL_HTM, REST,
        SCREEN_SLIDER,
        SERVE,
        SETTINGS,
        SETTINGS_ACTIVITY,
        SHOW_TIPS,
        SIZE,
        SYSTEM,
        USER_MANAGER,
        UTIL,
        VIDEO,
        VOL_UTIL,
        WEB_FRAGMENT,
        WEB_SERVER,
        WEB_SERVICE,
        ZIP_UTIL, DEEPDIVE, DECOMPILE, SEARCH, LUCENE, FERNFLOWER,
        }

    /**
     * Post a message to the developer console if VERBOSE is enabled.
     * @param log
     */
    public static void log(String log){

        if(LogUtil.VERBOSE)
            Log.v( TAG, log);
    }

    public static void log(LogType tag, String log){

        if(LogUtil.VERBOSE)
            Log.v( TAG+":"+tag.toString(), log);
    }

    public static void log(Class<?> clazz, String log) {

        if(LogUtil.VERBOSE)
            Log.v( TAG+":"+clazz.toString(), log);
    }

    /**
     * Put exception in Android LogCat and logDB.
     * @param ctx
     * @param clazz
     * @param e
     */
    public static void logException(Context ctx, Class<?> clazz, Exception e) {

        e.printStackTrace(System.err);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log( clazz,  "ERROR Exception: "+sw.toString());
    }
    /**
     * Put exception in Android LogCat and logDB.
     * @param clazz
     * @param e
     */
    public static void logException( Class<?> clazz, Exception e) {

        e.printStackTrace(System.err);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log( clazz,  "ERROR Exception: "+sw.toString());
    }
    /**
     * Put exception in Android LogCat and logDB.
     * @param clazz
     * @param e
     */
    public static void logException( Class<?> clazz, String note, Exception e) {

        e.printStackTrace(System.err);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log( clazz,  "ERROR Exception: "+note+sw.toString());
    }
    /**
     * Put exception in Android LogCat and logDB.
     * @param ctx
     * @param logType
     * @param e
     */
    public static void logException(Context ctx, LogType logType, Exception e) {

        e.printStackTrace(System.err);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log( logType,  "ERROR Exception: "+sw.toString());
    }
    public static String logException(LogType logType, Exception e) {

        e.printStackTrace(System.err);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log( logType,  "ERROR Exception: "+sw.toString());
        return  sw.toString();
    }

    public static void logException(LogType logType, String s, Exception e) {

        e.printStackTrace(System.err);
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        log( logType,  "ERROR Exception: "+s+sw.toString());
    }

    /**
     * Print simple log
     * @param tag
     * @param string
     */
    public static void e(String tag, String string) {

        if(LogUtil.VERBOSE)
            Log.e( tag, string);
    }

    public static void e(String tag, Exception e) {

        if(LogUtil.VERBOSE) {
            e.printStackTrace(System.err);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.v(tag, "ERROR Exception: " + sw.toString());
        }
    }

}
