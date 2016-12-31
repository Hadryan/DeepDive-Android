package com.nuvolect.deepdive.probe;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.googlecode.dex2jar.Method;
import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.reader.DexFileReader;
import com.googlecode.dex2jar.v3.Dex2jar;
import com.googlecode.dex2jar.v3.DexExceptionHandler;
import com.jaredrummler.apkparser.ApkParser;
import com.jaredrummler.apkparser.model.CertificateMeta;
import com.nuvolect.deepdive.ddUtil.CConst;
import com.nuvolect.deepdive.ddUtil.LogUtil;
import com.nuvolect.deepdive.ddUtil.OmniFile;
import com.nuvolect.deepdive.ddUtil.OmniHash;
import com.nuvolect.deepdive.ddUtil.OmniUtil;
import com.nuvolect.deepdive.ddUtil.OmniZip;
import com.nuvolect.deepdive.ddUtil.TimeUtil;
import com.nuvolect.deepdive.ddUtil.Util;
import com.nuvolect.deepdive.main.App;

import org.apache.commons.io.FilenameUtils;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.DumperFactoryImpl;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.immutable.ImmutableDexFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import jadx.api.JadxDecompiler;

//import net.dongliu.apk.parser.ApkParser;
//import net.dongliu.apk.parser.bean.CertificateMeta;
//import net.dongliu.apk.parser.bean.DexClass;

/**
 * This class represents an object to work with a single APK file.
 * Each object is specific to a user and a package.
 * Multiple users can process multiple apps at the same time.
 * Sub-folders are created for each app named with the package name
 * Sub-folders contain
 * 1. the APK file,
 * 2. the unpacked APK file and
 * 3. decompiled class files in subfolders src{cfr, jadx, fern}
 *
 * Notes:
 * _status is for 0/1 apk and dex file exists status
 * _thread status has states {running, stopped, null}
 * running: compile process is running
 * stopped: comple process has stopped, folder exists
 * null: compile process is not running, folder does not exist
 */
public class DecompileApk {

    private final Context m_ctx;
    private final String m_userId;
    private final String m_packageName;
    private final String m_volumeId;
    private final String m_userFolderPath;

    private OmniFile m_appFolder; // Includes package name for directory
    private OmniFile m_apkFile;
    private OmniFile m_dexFile;
    private OmniFile m_jarFile;
    private OmniFile m_optimizedDexFile;
    private OmniFile m_srcCfrFolder;
    private OmniFile m_srcFernFolder;
    private OmniFile m_srcJadxFolder;
    private ProgressStream m_progressStream;
    private String m_appApkPath;
    private String m_appFolderPath;
    private String m_appFolderUrl;
    private String m_dexPath;
    private String m_jarPath;
    private String m_optimizedDexPath;
    private String m_srcCfrFolderPath;
    private String m_srcFernFolderPath;
    private String m_srcJadxFolderPath;

    //    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    //    STACK_SIZE = Integer.valueOf(prefs.getString("thread_stack_size", String.valueOf(20 * 1024 * 1024)));
    //    IGNORE_LIBS = prefs.getBoolean("ignore_libraries", true);
    private int STACK_SIZE = 20 * 1024 * 1024;

    private String DEEPDIVE_THREAD_GROUP = "DeepDive Thread Group";
    private ThreadGroup m_threadGroup = new ThreadGroup(DEEPDIVE_THREAD_GROUP);
    private Thread m_unpackApkThread = null;
    private Thread m_optimizeDexThread = null;
    private Thread m_dex2jarThread = null;
    private Thread m_cfrThread = null;
    private Thread m_jadxThread = null;
    private Thread m_fernThread = null;
    private String UNZIP_APK_THREAD = "Unpack APK java thread";
    private String DEX2JAR_THREAD = "DEX to JAR java thread";
    private String JADX_THREAD = "Jadx jar to java thread";
    private String FERN_THREAD = "FernFlower jar to java thread";
    private List<String> ignoredLibs = new ArrayList();
    private String OPTIMIZED_CLASSES = "optimized_classes";
    private String OPTIMIZED_CLASSES_EXCLUSION_FILENAME = "/optimized_classes_exclusion.txt";
    private String[] m_dexFileNames = {}; // Generated list of candidate dex file names

    // Time when a process is started
    private long m_unpack_apk_time = 0;
    private long m_dex2jar_time = 0;
    private long m_optimize_dex_time = 0;
    private long m_cfr_time = 0;
    private long m_jadx_time = 0;
    private long m_fern_time = 0;
    private int m_active_threads;

    public enum THREAD_ID { unpack_apk, dex2jar, cfr, jadx, fern_flower, optimize_dex};

    public DecompileApk(Context ctx, String userId, String packageName) {

        m_ctx = ctx;
        m_userId = userId;
        m_packageName = packageName;
        m_userFolderPath = App.getUser().getUserFolderPath();
        m_volumeId = App.getUser().getDefaultVolumeId();
        m_progressStream = new ProgressStream();
        m_progressStream.putStream("Decompiler ready");

        /*
         * Generate a list of candidate dex file names
         * {classes, classes2..classes64}
         */
        List<String> tmp = new ArrayList<String>();
        tmp.add("classes");
        for( int i = 2; i <=64; i++)
            tmp.add("classes"+i);

        m_dexFileNames = new String[ tmp.size()];
        m_dexFileNames = tmp.toArray(m_dexFileNames);

        m_appFolderPath = (CConst.USER_FOLDER_PATH + m_packageName + "/").replace("//","/");
        m_appFolder = new OmniFile( m_volumeId, m_appFolderPath);
        if (!m_appFolder.exists()) {
            m_appFolder.mkdir();
        }
        m_appFolderUrl = OmniHash.getHashedServerUrl(m_ctx,
                m_volumeId, m_appFolderPath);

        m_appApkPath = m_appFolderPath+m_packageName+".apk";
        m_apkFile           = new OmniFile( m_volumeId, m_appApkPath);
        m_dexFile           = new OmniFile( m_volumeId, m_appFolderPath+"/classes.dex");
        m_optimizedDexFile  = new OmniFile( m_volumeId, m_appFolderPath+"/optimized_classes.dex");
        m_jarFile           = new OmniFile( m_volumeId, m_appFolderPath+"/classes.jar");
        m_srcCfrFolderPath  = m_appFolderPath+"/srcCfr";
        m_srcCfrFolder      = new OmniFile( m_volumeId, m_srcCfrFolderPath);
        m_srcJadxFolderPath = m_appFolderPath+"/srcJadx";
        m_srcJadxFolder     = new OmniFile( m_volumeId, m_srcJadxFolderPath);
        m_srcFernFolderPath = m_appFolderPath+"/srcFern";
        m_srcFernFolder     = new OmniFile( m_volumeId, m_srcFernFolderPath);
    }

    /**
     * Update member variables paths, and files. Return thread status and URLs.
     * @return
     */
    public JSONObject getStatus() {

        /**
         * Update status on tasks performed
         */
        boolean apkFileExists = m_apkFile.exists();
        boolean dexFileExists = m_dexFile.exists();
        boolean optimizedDexExists = m_optimizedDexFile.exists();
        boolean jarFileExists = m_jarFile.exists();
        boolean cfrFolderExists = m_srcCfrFolder.exists();
        boolean jadxFolderExists = m_srcJadxFolder.exists();
        boolean fernFolderExists = m_srcFernFolder.exists();
        JSONObject wrapper = new JSONObject();

        try {

            wrapper.put("copy_apk_status", apkFileExists ?1:0);
            wrapper.put("app_folder_url", m_appFolderUrl);
            wrapper.put("app_folder_path", m_appFolderPath);

            if(cfrFolderExists){
                String url = OmniHash.getHashedServerUrl(m_ctx,
                        m_volumeId, m_srcCfrFolderPath);
                wrapper.put("cfr_url", url);
            }
            else
                wrapper.put("cfr_url", m_appFolderUrl);

            if(jadxFolderExists){
                String url = OmniHash.getHashedServerUrl(m_ctx,
                        m_volumeId, m_srcJadxFolderPath);
                wrapper.put("jadx_url", url);
            }
            else
                wrapper.put("jadx_url", m_appFolderUrl);

            if(fernFolderExists){
                String url = OmniHash.getHashedServerUrl(m_ctx,
                        m_volumeId, m_srcFernFolderPath);
                wrapper.put("fern_url", url);
            }
            else
                wrapper.put("fern_url", m_appFolderUrl);

            wrapper.put("optimize_dex_status", optimizedDexExists?1:0);

            m_active_threads = 0;
            wrapper.put("unpack_apk_thread",  getThreadStatus( dexFileExists, m_unpackApkThread));
            wrapper.put("dex2jar_thread",     getThreadStatus( jarFileExists, m_dex2jarThread));
            wrapper.put("optimize_dex_thread",getThreadStatus( optimizedDexExists, m_optimizeDexThread));
            wrapper.put("cfr_thread",         getThreadStatus( cfrFolderExists, m_cfrThread));
            wrapper.put("jadx_thread",        getThreadStatus( jadxFolderExists, m_jadxThread));
            wrapper.put("fern_thread",        getThreadStatus( fernFolderExists, m_fernThread));
            wrapper.put("active_threads",     m_active_threads);

            wrapper.put("unpack_apk_time",    getThreadTime( m_unpack_apk_time ));
            wrapper.put("dex2jar_time",       getThreadTime( m_dex2jar_time ));
            wrapper.put("optimize_dex_time",  getThreadTime( m_optimize_dex_time ));
            wrapper.put("cfr_time",           getThreadTime( m_cfr_time ));
            wrapper.put("jadx_time",          getThreadTime( m_jadx_time ));
            wrapper.put("fern_time",          getThreadTime( m_fern_time ));

            wrapper.put("upload_url",         "/probe/upload_apk");
            wrapper.put("log",                getStream());

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return wrapper;
    }

    /**
     * Dispatch to perform the assigned action
     * @param action
     * @return
     */
    public JSONObject startThread(String action) {

        THREAD_ID action_id = null;
        try {
            action_id = THREAD_ID.valueOf( action);
        } catch (IllegalArgumentException e) {
            LogUtil.log( DecompileApk.class, "Error, invalid command: "+action);
        }

        switch( action_id){

            case unpack_apk:
                return unpackApk();
            case optimize_dex:
                return optimizeDex();
            case dex2jar:
                return dex2jar();
            case cfr:
                return cfr();
            case jadx:
                return jadx();
            case fern_flower:
                return fern_flower();
            default:
                return null;
        }
    }

    /**
     * Return the status of a compile process. The status can be one of three states:
     * running: compile process is running
     * stopped: comple process has stopped, folder exists
     * empty: compile process is not running, folder does not exist
     *
     * @param folderExists
     * @param aThread
     * @return
     *
     * The member variable {@link #m_active_threads} is bumped for each active thread
     */
    private String getThreadStatus(boolean folderExists, Thread aThread) {

        if( aThread != null && aThread.isAlive()){

            ++m_active_threads;
            return "running";
        }

        if( folderExists)
            return "stopped";

        return "empty";
    }

    private String getThreadTime(long startTime){

        if( startTime == 0)
            return "";

        return TimeUtil.deltaTimeHrMinSec( startTime);
    }


    /**
     * Copy the specific APK to working folder.
     * Return a link to the parent folder.
     * @return
     */
    public JSONObject copyApk() {

        JSONObject wrapper = new JSONObject();

        try {
            wrapper.put("copy_apk_status", 0);// 0==Start with failed file copy
            m_progressStream.putStream("Copy APK starting");

            PackageManager pm = m_ctx.getPackageManager();
            ApplicationInfo applicationInfo = pm.getApplicationInfo( m_packageName, PackageManager.GET_META_DATA);

            java.io.File inputFile = new File( applicationInfo.publicSourceDir);
            InputStream inputStream = new FileInputStream( inputFile );

            OutputStream outputStream = m_apkFile.getOutputStream();
            int bytes_copied = Util.copyFile( inputStream, outputStream);
            String formatted_count = NumberFormat.getNumberInstance(Locale.US).format(bytes_copied);

            m_progressStream.putStream("Copy APK complete. Copied: "+formatted_count);

            wrapper.put("copy_apk_status", 1); // Change to success if we get here
            wrapper.put("copy_apk_url", m_appFolderUrl);

        } catch (PackageManager.NameNotFoundException | JSONException | IOException e) {
            LogUtil.logException(LogUtil.LogType.DECOMPILE, e);
            m_progressStream.putStream(e.toString());
            m_progressStream.putStream("Copy APK failed");
        }

        return wrapper;
    }

    private JSONObject unpackApk() {

        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {

                LogUtil.log( DecompileApk.class, "Uncaught exception: "+e.toString());
                m_progressStream.putStream("Uncaught exception: "+t.getName());
                m_progressStream.putStream("Uncaught exception: "+e.toString());
            }
        };

        m_unpack_apk_time = System.currentTimeMillis();  // Save start time for tracking

        m_unpackApkThread = new Thread( m_threadGroup, new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                try {

                    m_progressStream.putStream("Unpack APK starting");
                    if( m_apkFile.exists() && m_apkFile.isFile()){

                        // Extract all files except for XML, to be extracted later
                        success = ApkZipUtil.unzipAllExceptXML(m_apkFile, m_appFolder, m_progressStream);

                        ApkParser apkParser = ApkParser.create( m_apkFile.getStdFile());

                        // Get a list of all files in the APK and iterate and extract by type
                        List<String> paths = OmniZip.getFilesList( m_apkFile);
                        for( String path : paths){

                            OmniFile file = new OmniFile( m_volumeId,m_appFolderPath+"/"+path);
                            OmniUtil.forceMkdirParent( file);

                            String extension = FilenameUtils.getExtension( path);

                            if( extension.contentEquals("xml")){

                                String xml = apkParser.transBinaryXml(path);
                                OmniUtil.writeFile( file, xml);
                                m_progressStream.putStream( "Translated: "+path);
                            }
                        }
                        // Write over manifest with unencoded version
                        String manifestXml = apkParser.getManifestXml();
                        OmniFile manifestFile = new OmniFile( m_volumeId, m_appFolderPath+"/AndroidManifest.xml");
                        OmniUtil.writeFile(manifestFile, manifestXml);
                        m_progressStream.putStream("Translated and parsed: "+"AndroidManifest.xml");

                        // Uses original author CaoQianLi's apk-parser
                        // compile 'net.dongliu:apk-parser:2.1.7'
//                        for( CertificateMeta cm : apkParser.getCertificateMetaList()){
//
//                            m_progressStream.putStream("Certficate base64 MD5: "+cm.getCertBase64Md5());
//                            m_progressStream.putStream("Certficate MD5: "+cm.getCertMd5());
//                            m_progressStream.putStream("Sign algorithm OID: "+cm.getSignAlgorithmOID());
//                            m_progressStream.putStream("Sign algorithm: "+cm.getSignAlgorithm());
//                        }

                        CertificateMeta cm = null;
                        try {
                            cm = apkParser.getCertificateMeta();
                            m_progressStream.putStream("Certficate base64 MD5: "+cm.certBase64Md5);
                            m_progressStream.putStream("Certficate MD5: "+cm.certMd5);
                            m_progressStream.putStream("Sign algorithm OID: "+cm.signAlgorithmOID);
                            m_progressStream.putStream("Sign algorithm: "+cm.signAlgorithm);

                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }

                        m_progressStream.putStream("ApkSignStatus: "+ apkParser.verifyApk());

                        /**
                         * Create a file for the user to include classes to omit in the optimize DEX task.
                         */
                        OmniFile optimizedDex = new OmniFile( m_volumeId,m_appFolderPath+ OPTIMIZED_CLASSES_EXCLUSION_FILENAME);
                        if( ! optimizedDex.exists()){

                            OmniUtil.writeFile( optimizedDex, "");
                            m_progressStream.putStream("File created: "+OPTIMIZED_CLASSES_EXCLUSION_FILENAME);
                        }
                    }else{

                        m_progressStream.putStream("APK not found. Select Copy APK.");
                    }

                } catch (Exception | StackOverflowError e) {
                    m_progressStream.putStream(e.toString());
                }
                String time = TimeUtil.deltaTimeHrMinSec(m_unpack_apk_time);
                m_unpack_apk_time = 0;
                if( success)
                    m_progressStream.putStream("Unpack APK complete: "+time);
                else
                    m_progressStream.putStream("Unpack APK failed: "+time);
            }
        }, UNZIP_APK_THREAD, STACK_SIZE);

        m_unpackApkThread.setPriority(Thread.MAX_PRIORITY);
        m_unpackApkThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        m_unpackApkThread.start();

        final JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("unpack_apk_thread", getThreadStatus( true, m_unpackApkThread));

        } catch (JSONException e) {
            LogUtil.logException(LogUtil.LogType.DECOMPILE, e);
        }

        return wrapper;
    }
    /**
     * Build a new DEX file excluding classes in the OPTIMIZED_CLASS_EXCLUSION file
     * @return
     */
    private JSONObject optimizeDex() {

        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {

                LogUtil.log( DecompileApk.class, "Uncaught exception: "+e.toString());
                m_progressStream.putStream("Uncaught exception: "+t.getName());
                m_progressStream.putStream("Uncaught exception: "+e.toString());
            }
        };

        m_optimize_dex_time = System.currentTimeMillis();  // Save start time for tracking

        m_optimizeDexThread = new Thread(m_threadGroup, new Runnable() {
            @Override
            public void run() {

                List<ClassDef> classes = new ArrayList<>();
                m_progressStream.putStream("Optimizing classes, reference: "+ OPTIMIZED_CLASSES_EXCLUSION_FILENAME);

                Scanner s = null;
                try {
                    s = new Scanner( new File(m_appFolderPath+ OPTIMIZED_CLASSES_EXCLUSION_FILENAME));
                    while (s.hasNext()){
                        String excludeClass = s.next();
                        ignoredLibs.add(excludeClass);
                        m_progressStream.putStream("Exclude class: "+excludeClass);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if( s != null)
                    s.close();

                /**
                 * FIXME: Current solution reads dex from the APK.  It appears to only read the first classes.dex.
                 * The problem is the classes{n}.dex files are ignored and not all classes can be decompiled.
                 */
//                File dFile = new File( m_appFolderPath +"/classes.dex" );
//                try {
//                    DexFile df = new DexFile( dFile);
//                    Enumeration<String> e = df.entries();
//
//                    LogUtil.log( e.toString());
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }

                org.jf.dexlib2.iface.DexFile dexFile = null;
                try {
                    dexFile = DexFileFactory.loadDexFile( m_appApkPath, 19);
                } catch( Exception e) {
                    m_progressStream.putStream("The app DEX file cannot be decompiled.");
                }

                Set<? extends ClassDef> classSet = dexFile.getClasses();

                for (org.jf.dexlib2.iface.ClassDef classDef : classSet) {
                    if (!isIgnored(classDef.getType())) {
                        final String currentClass = classDef.getType();
                        m_progressStream.putStream("Optimizing_class: " + currentClass);
                        classes.add(classDef);
                    }
                }

                m_progressStream.putStream("Merging classes #"+classSet.size());
                dexFile = new ImmutableDexFile( classes);

                try {
                    m_progressStream.putStream("Writing optimized_classes.dex");
                    DexFileFactory.writeDexFile(m_appFolder+"/optimized_classes.dex", dexFile);
                } catch( Exception e) {
                    m_progressStream.putStream("The app DEX file cannot be decompiled.");
                }
                m_progressStream.putStream("Optimize DEX complete: "
                        +TimeUtil.deltaTimeHrMinSec(m_optimize_dex_time));
                m_optimize_dex_time = 0;
            }
        }, UNZIP_APK_THREAD, STACK_SIZE);

        m_optimizeDexThread.setPriority(Thread.MAX_PRIORITY);
        m_optimizeDexThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        m_optimizeDexThread.start();

        return new JSONObject();
    }

    private boolean isIgnored(String className) {
        for (String ignoredClass : ignoredLibs) {
            if (className.startsWith(ignoredClass)) {
                return true;
            }
        }
        return false;
    }

    private JSONObject dex2jar() {

        // DEX 2 JAR CONFIGS
        final boolean reuseReg = false; // reuse register while generate java .class file
        final boolean topologicalSort1 = false; // same with --topological-sort/-ts
        final boolean topologicalSort = false; // sort block by topological, that will generate more readable code
        final boolean verbose = true; // show progress
        final boolean debugInfo = false; // translate debug info
        final boolean printIR = false; // print ir to System.out
        final boolean optimizeSynchronized = true; // Optimise-synchronised

        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {

                LogUtil.log( DecompileApk.class, "Uncaught exception: "+e.toString());
                m_progressStream.putStream("Uncaught exception: "+t.getName());
                m_progressStream.putStream("Uncaught exception: "+e.toString());
            }
        };

        m_dex2jar_time = System.currentTimeMillis();  // Save start time for tracking

        m_dex2jarThread = new Thread( m_threadGroup, new Runnable() {
            @Override
            public void run() {

                boolean success = false;
                OmniFile dexFile = null;
                OmniFile jarFile = null;
                m_progressStream.putStream("DEX to JAR starting");

                for( String fileName : m_dexFileNames){

                    dexFile = new OmniFile( m_volumeId, m_appFolderPath + "/" + fileName+".dex");

                    if( dexFile.exists() && dexFile.isFile()){

                        String size = NumberFormat.getNumberInstance(Locale.US).format(dexFile.length());
                        m_progressStream.putStream("DEX to JAR processing: "+dexFile.getName()+", "+size);

                        DexExceptionHandlerMod dexExceptionHandlerMod = new DexExceptionHandlerMod();
                        jarFile = new OmniFile( m_volumeId, m_appFolderPath + "/" + fileName + ".jar");

                        if( jarFile.exists())
                            jarFile.delete();

                        try {
                            DexFileReader reader = new DexFileReader(dexFile.getStdFile());
                            Dex2jar dex2jar = Dex2jar
                                    .from(reader)
                                    .reUseReg(reuseReg)
                                    .topoLogicalSort(topologicalSort || topologicalSort1)
                                    .skipDebug(!debugInfo)
                                    .optimizeSynchronized(optimizeSynchronized)
                                    .printIR(printIR)
                                    .verbose(verbose);
                            dex2jar.setExceptionHandler(dexExceptionHandlerMod);
                            dex2jar.to(jarFile.getStdFile());
                            success = true;
                        } catch ( Exception e) {
                            String ex = LogUtil.logException(LogUtil.LogType.DECOMPILE, e);
                            m_progressStream.putStream(ex);
                            success = false;
                        }
                        if( success ){

                            size = NumberFormat.getNumberInstance(Locale.US).format(jarFile.length());
                            m_progressStream.putStream("DEX to JAR succeeded: "+jarFile.getName()+", "+size);
                        }
                        else
                            m_progressStream.putStream("Exception thrown, file cannot be decompiled: "+dexFile.getPath());
                    }
                }
                if( jarFile == null)
                    m_progressStream.putStream("No DEX file found: "+ m_dexFileNames);

                m_progressStream.putStream("DEX to JAR complete: "
                        +TimeUtil.deltaTimeHrMinSec(m_dex2jar_time));
                m_dex2jar_time = 0;
            }

        }, DEX2JAR_THREAD, STACK_SIZE);

        m_dex2jarThread.setPriority(Thread.MAX_PRIORITY);
        m_dex2jarThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        m_dex2jarThread.start();

        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("dex2jar_thread", getThreadStatus( true, m_dex2jarThread));

        } catch (JSONException e) {
            LogUtil.logException(LogUtil.LogType.DECOMPILE, e);
        }

        return wrapper;
    }

    private JSONObject cfr() {

        m_srcCfrFolder.mkdirs();

        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {

                LogUtil.log( DecompileApk.class, "Uncaught exception: "+e.toString());
                m_progressStream.putStream("Uncaught exception: "+t.getName());
                m_progressStream.putStream("Uncaught exception: "+e.toString());
            }
        };

        m_cfr_time = System.currentTimeMillis();  // Save start time for tracking

        m_cfrThread = new Thread(m_threadGroup, new Runnable() {
            @Override
            public void run() {

                m_progressStream.putStream("CFR starting");
                OmniFile jarFile = null;
                try {
                    for (String fileName : m_dexFileNames) {

                        jarFile = new OmniFile( m_volumeId, m_appFolderPath + "/" + fileName + ".jar");

                        if( jarFile.exists() && jarFile.isFile()){

                            String[] args = {
                                    jarFile.getStdFile().toString(),
                                    "--outputdir",
                                    m_srcCfrFolder.getStdFile().toString()
                            };
                            GetOptParser getOptParser = new GetOptParser();

                            org.benf.cfr.reader.util.getopt.Options options =
                                    getOptParser.parse(args, OptionsImpl.getFactory());

                            if (!options.optionIsSet(OptionsImpl.HELP) && options.getOption(OptionsImpl.FILENAME) != null) {

                                m_progressStream.putStream("CFR starting from DEX: "+fileName);

                                ClassFileSourceImpl classFileSource = new ClassFileSourceImpl(options);
                                final DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
                                final String path = options.getOption(OptionsImpl.FILENAME);
                                DumperFactoryImpl dumperFactory = new DumperFactoryImpl(options);
                                org.benf.cfr.reader.Main.doJar( dcCommonState, path, dumperFactory);
                                m_progressStream.putStream("See srcCfr/summary.txt");
                                m_progressStream.putStream("CFR from DEX complete: "+fileName);
                            }
                        }
                    }

                }catch(Exception | StackOverflowError e){
                    m_progressStream.putStream(e.toString());
                }
                m_progressStream.putStream("CFR complete: "+TimeUtil.deltaTimeHrMinSec(m_cfr_time));
                m_cfr_time = 0;
            }
        }, DEEPDIVE_THREAD_GROUP, STACK_SIZE);

        m_cfrThread.setPriority(Thread.MAX_PRIORITY);
        m_cfrThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        m_cfrThread.start();

//                processStatus = getThreadStatus( true, m_cfrThread);
//                url = OmniHash.getHashedServerUrl(m_ctx, m_volumeId, m_srcCfrFolderPath);
        String processKey = "cfr_thread";
        String urlKey = "cfr_url";

        return processWrapper( processKey, urlKey);
    }

    /**
     * Jadx converts a DEX file directly into Java files.  It does not input JAR files.
     */
    private JSONObject jadx() {

        m_srcJadxFolder.mkdirs();
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {

                LogUtil.log( DecompileApk.class, "Uncaught exception: "+e.toString());
                m_progressStream.putStream("Uncaught exception: "+t.getName());
                m_progressStream.putStream("Uncaught exception: "+e.toString());
            }
        };

        m_jadx_time = System.currentTimeMillis();  // Save start time for tracking

        m_jadxThread = new Thread( m_threadGroup, new Runnable() {
            @Override
            public void run() {

                m_progressStream.putStream("Jadx starting");
                        /*
                         * Type File require, versus OmniFile, in order to provide loadFiles
                         * a list of <File>.
                         */
                List<File> dexList = new ArrayList<>();
                JadxDecompiler jadx = new JadxDecompiler();
                jadx.setOutputDir(m_srcJadxFolder.getStdFile());
                String loadingNames = "";
                String spacer = "";

                for (String fileName : m_dexFileNames) {

                    OmniFile dexFile = new OmniFile( m_volumeId, m_appFolderPath + "/" + fileName + ".dex");

                    if( dexFile.exists() && dexFile.isFile()) {

                        dexList.add( dexFile.getStdFile() );
                        loadingNames += spacer + dexFile.getName();
                        spacer = ", ";

                        if( fileName.contentEquals( OPTIMIZED_CLASSES))
                            break;
                    }
                }
                try {
                    m_progressStream.putStream("Loading: "+loadingNames);
                    jadx.loadFiles(dexList);
                    m_progressStream.putStream("Load complete");
                } catch (Exception e) {
                    LogUtil.logException(LogUtil.LogType.DECOMPILE, e);
                    m_progressStream.putStream(e.toString());
                }
                try {
                    m_progressStream.putStream("Jadx saveSources start");
                    jadx.saveSources();
                    m_progressStream.putStream("Jadx saveSources complete");
                } catch (Exception e) {
                    LogUtil.logException(LogUtil.LogType.DECOMPILE, e);
                    m_progressStream.putStream(e.toString());
                }

                m_progressStream.putStream("Jadx complete: "+TimeUtil.deltaTimeHrMinSec(m_jadx_time));
                m_jadx_time = 0;
            }
        }, JADX_THREAD, STACK_SIZE);

        m_jadxThread.setPriority(Thread.MAX_PRIORITY);
        m_jadxThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        m_jadxThread.start();

        String processKey = "jadx_thread";
//                processStatus = getThreadStatus( true, m_jadxThread);
        String urlKey = "jadx_url";
//                url = OmniHash.getHashedServerUrl( m_ctx, m_volumeId, m_srcJadxFolderPath);

        return processWrapper( processKey, urlKey);
    }

    /**
     * FernFlower converts JAR files to a zipped decompiled JAR file
     */
    private JSONObject fern_flower() {// https://github.com/fesh0r/fernflower

        m_srcFernFolder.mkdirs();

        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {

                LogUtil.log( DecompileApk.class, "Uncaught exception: "+e.toString());
                m_progressStream.putStream("Uncaught exception: "+t.getName());
                m_progressStream.putStream("Uncaught exception: "+e.toString());
            }
        };

        m_fern_time = System.currentTimeMillis();  // Save start time for tracking

        m_fernThread = new Thread( m_threadGroup, new Runnable() {
            @Override
            public void run() {

                File javaOutputDir = m_srcFernFolder.getStdFile();

                File jarFile = null;
                String jarFileName = "";

                for(int i = 1; i < m_dexFileNames.length; i++) {

                    jarFileName = m_dexFileNames[i]+".jar";
                    jarFile = new File(m_appFolderPath + "/" + jarFileName);

                    if( jarFile.exists() && jarFile.isFile()) {

                        boolean success = true;
                        try {
                            m_progressStream.putStream("FernFlower starting: "+ jarFileName);
                            PrintStream printStream = new PrintStream(m_progressStream);
                            System.setErr(printStream);
                            System.setOut(printStream);
                            PrintStreamLogger logger = new PrintStreamLogger(printStream);

                            final Map<String, Object> mapOptions = new HashMap<>();
                            ConsoleDecompiler decompiler = new ConsoleDecompiler(
                                    m_srcFernFolder.getStdFile() , mapOptions, logger);
                            decompiler.addSpace( jarFile, true);

                            m_progressStream.putStream("FernFlower decompiler.addSpace complete: "+jarFileName);
                            decompiler.decompileContext();
                            m_progressStream.putStream("FernFlower decompiler.decompileContext complete: "+jarFileName);

                            String decompiledJarFileName = m_appFolderPath + ".jar_"+jarFileName;
                            OmniFile decompiledJarFile = new OmniFile( m_volumeId,  decompiledJarFileName);
                            success = OmniZip.unzipFile( decompiledJarFile, m_srcFernFolder, null, null);

                            if (success)
                                m_progressStream.putStream("FernFlower decompiler.unpack complete: "+jarFileName);
                            else
                                m_progressStream.putStream("FernFlower decompiler.unpack failed: "+jarFileName);
                        } catch (Exception e) {
                            String str = LogUtil.logException(LogUtil.LogType.FERNFLOWER, e);
                            m_progressStream.putStream("FernFlower exception "+jarFileName);
                            m_progressStream.putStream(str);
                            success = false;
                        }
                        /**
                         * Look for the classes.jar file and unzip it
                         */
                        if( ! success ){

                            OmniFile of = new OmniFile( m_volumeId, m_srcFernFolderPath+"/classes.jar");
                            if( of.exists()){

                                ApkZipUtil.unzip( of, m_srcFernFolder, m_progressStream);
                                m_progressStream.putStream("FernFlower utility unzip complete with errors: "+jarFileName);
                            }
                            else
                                m_progressStream.putStream("File does not exist: "+of.getAbsolutePath());
                        }
                    }
                }
                m_progressStream.putStream( "FernFlower complete: "+TimeUtil.deltaTimeHrMinSec(m_fern_time));
                m_fern_time = 0;
            }
        }, FERN_THREAD, STACK_SIZE);

        m_fernThread.setPriority(Thread.MAX_PRIORITY);
        m_fernThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);
        m_fernThread.start();

//                String processStatus = getThreadStatus( true, m_fernThread);
//                String url = OmniHash.getHashedServerUrl( m_ctx, m_volumeId, m_srcFernFolderPath);

        String processKey = "fern_thread";
        String urlKey = "fern_url";

        return processWrapper( processKey, urlKey);
    }

    private JSONObject processWrapper( String processKey, String urlKey){

        JSONObject wrapper = new JSONObject();
//        String url = "";
//        String processKey = "undef";
//        String processStatus = null;
//        String urlKey = "";

        try {
            wrapper.put("url", m_appFolderUrl);
            wrapper.put(processKey, null);
            wrapper.put( urlKey, "");

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return wrapper;
    }

    public JSONObject stopThread(String threadId){

        THREAD_ID thread = THREAD_ID.valueOf(threadId);
        Thread myThread = null;

        switch(thread){

            case unpack_apk:
                myThread = m_unpackApkThread;
                break;
            case dex2jar:
                myThread = m_dex2jarThread;
                break;
            case optimize_dex:
                myThread = m_optimizeDexThread;
                break;
            case cfr:
                myThread = m_cfrThread;
                break;
            case jadx:
                myThread = m_jadxThread;
                break;
            case fern_flower:
                myThread = m_fernThread;
                break;
        }

        if( myThread != null){
            if( myThread.isInterrupted())
                myThread.currentThread().stop();
            else {
                myThread.currentThread().interrupt();
            }
        }

        return getStatus();
//
//        if( myThread != null) {
//            if( myThread.isInterrupted())
//                m_progressStream.putStream( "Process is interrupted: "+ myThreadName);
//            else
//            if( myThread.isAlive())
//                m_progressStream.putStream( "Process is alive: "+ myThreadName);
//            else
//                m_progressStream.putStream( "Process is not alive: "+ myThreadName);
//        }
//        else
//            m_progressStream.putStream( "Process is null: "+ myThreadName);
//
//        JSONObject status = new JSONObject();
//        try {
//            status.put("stop", myThread != null && myThread.isAlive()?1:0);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//        return status;
    }

    private class DexExceptionHandlerMod implements DexExceptionHandler {
        @Override
        public void handleFileException(Exception e) {
            LogUtil.logException(LogUtil.LogType.DECOMPILE, "Dex2Jar Exception", e);
        }

        @Override
        public void handleMethodTranslateException(Method method, IrMethod irMethod, MethodNode methodNode, Exception e) {
            LogUtil.logException(LogUtil.LogType.DECOMPILE, "Dex2Jar Exception", e);
        }
    }

    public void clearStream() {

        m_progressStream.init();
    }

    public JSONArray getStream() {

        return m_progressStream.getStream();
    }

}