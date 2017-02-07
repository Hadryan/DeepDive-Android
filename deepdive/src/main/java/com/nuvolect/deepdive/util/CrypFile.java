package com.nuvolect.deepdive.util;//

import android.content.Context;

import com.nuvolect.deepdive.R;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import info.guardianproject.iocipher.File;


//TODO create class description
//
public class CrypFile {

    public static void createDemoFile(Context ctx){

        File sample = new info.guardianproject.iocipher.File("/alberti_cipher_disk.png");
        if (!sample.exists()) {
            try {
                InputStream in = ctx.getResources().openRawResource(R.raw.alberti_cipher_disk);
                OutputStream out = new info.guardianproject.iocipher.FileOutputStream(sample);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                in.close();
                out.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create the default camera director if necessary.
     * Return false if the directory cannot be created.
     * @param ctx
     * @return
     */
    public static boolean createCameraFolder(Context ctx) {

        boolean success =true;

        OmniFile file = new OmniFile(Omni.cryptoVolumeId, "/DCIM/Camera");
        if( ! file.exists())
            success = file.mkdirs();

        return success;
    }
}
