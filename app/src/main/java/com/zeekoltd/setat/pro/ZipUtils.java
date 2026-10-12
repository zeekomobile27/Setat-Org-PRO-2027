package com.zeekoltd.setat.pro;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ZipUtils {

    public static boolean unzipSetFromUri(Context context, Uri zipUri, String targetDirectory) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(zipUri);
            if (inputStream == null) return false;
            return extractZipStream(inputStream, targetDirectory);
        } catch (Exception e) {
            Log.e("ZeekoAudio", "فشل فك الضغط من المسار", e);
            return false;
        }
    }

    // بناء محرك فك ضغط قوي جداً يعتمد على ZipFile لتخطي كل مشاكل الاستخراج
    public static boolean unzipSetFromFile(File zipFile, String targetDirectory) {
        if (zipFile == null || !zipFile.exists() || !zipFile.canRead()) {
            Log.e("ZeekoAudio", "الملف غير موجود أو لا يمكن قراءته: " + (zipFile != null ? zipFile.getAbsolutePath() : "null"));
            return false;
        }
        
        File targetDirFile = new File(targetDirectory);
        if (!targetDirFile.exists()) targetDirFile.mkdirs();

        boolean extractedAtLeastOneFile = false;
        
        try {
            ZipFile zip = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[8192];

            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String filename = ze.getName();

                if (filename.contains("..")) continue;

                File file = new File(targetDirectory, filename);
                if (ze.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    InputStream in = zip.getInputStream(ze);
                    FileOutputStream fout = new FileOutputStream(file);
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);
                    }
                    fout.flush();
                    fout.close();
                    in.close();
                    
                    extractedAtLeastOneFile = true;
                }
            }
            zip.close();
            return extractedAtLeastOneFile;
            
        } catch (Exception e) {
            Log.e("ZeekoAudio", "محاولة بديلة لفك الضغط بسبب صيغة الملف", e);
            try {
                InputStream inputStream = new FileInputStream(zipFile);
                return extractZipStream(inputStream, targetDirectory);
            } catch (Exception ex) {
                return false;
            }
        }
    }

    private static boolean extractZipStream(InputStream inputStream, String targetDirectory) {
        File targetDirFile = new File(targetDirectory);
        if (!targetDirFile.exists()) targetDirFile.mkdirs();
        
        boolean extractedAtLeastOneFile = false;
        try {
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(inputStream));
            ZipEntry ze;
            byte[] buffer = new byte[8192];
            
            while ((ze = zis.getNextEntry()) != null) {
                String filename = ze.getName();
                if (filename.contains("..")) {
                    zis.closeEntry();
                    continue;
                }
                File file = new File(targetDirectory, filename);
                if (ze.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    FileOutputStream fout = new FileOutputStream(file);
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);
                    }
                    fout.close();
                    extractedAtLeastOneFile = true;
                }
                zis.closeEntry();
            }
            zis.close();
            return extractedAtLeastOneFile;
        } catch (Exception e) {
            return false;
        }
    }
}
