package com.zeekoltd.setat.pro;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SetInstaller {

    public interface InstallListener {
        void onProgress(String message);
        void onSuccess(String soundPrefix, String rhythmPrefix);
        void onError(String error);
    }

    public static void installFromZip(File zipFile, String setsFolderPath, String lang, InstallListener listener) {
        new Thread(() -> {
            try {
                // 1. تنظيف مسبق لأي ملفات .ogg أو .json قديمة عالقة في المجلد الرئيسي
                clearOldActiveFiles(setsFolderPath);

                // 2. فك الضغط *مباشرة* في المجلد الرئيسي setsFolderPath (بدون مجلد فرعي)
                boolean success = ZipUtils.unzipSetFromFile(zipFile, setsFolderPath);
                if (success) {
                    // 3. بما أن بعض السيتات قد تكون مضغوطة بداخلها مجلد، نقوم بتسطيحها لتصبح في الرئيسي
                    flattenDirectory(new File(setsFolderPath), setsFolderPath);

                    // 4. استخراج الـ Prefixes
                    String sPrefix = detectPrefix(setsFolderPath, "_01.ogg", "string");
                    String rPrefix = detectPrefix(setsFolderPath, "_a.ogg", "rhythm");
                    listener.onSuccess(sPrefix, rPrefix);
                } else {
                    listener.onError(lang.equals("en") ? "Failed to extract set" : "فشل في فك ضغط السيت");
                }
            } catch (Exception e) {
                listener.onError((lang.equals("en") ? "Unexpected error: " : "حدث خطأ غير متوقع: ") + e.getMessage());
            }
        }).start();
    }

    public static void downloadAndInstall(String fileUrl, String setName, String setsFolderPath, String lang, InstallListener listener) {
        new Thread(() -> {
            try {
                File goldenFolder = new File(setsFolderPath);
                if (!goldenFolder.exists()) goldenFolder.mkdirs();

                String targetUrl = fileUrl;
                if (targetUrl.contains("mediafire.com/file/")) {
                    listener.onProgress(lang.equals("en") ? "Preparing direct link..." : "جاري تجهيز الرابط المباشر للسيت...");
                    targetUrl = getMediaFireDirectLink(targetUrl);
                }

                URL url = new URL(targetUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                int fileLength = connection.getContentLength();
                InputStream input = new BufferedInputStream(connection.getInputStream());

                String validFileName = setName.replaceAll("[^a-zA-Z0-9أ-ي ]", "");
                File zipFile = new File(goldenFolder, validFileName + ".zip");
                OutputStream output = new FileOutputStream(zipFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        listener.onProgress(lang.equals("en") ? "Downloading... " + progress + "%\n" + setName : "جاري التحميل... " + progress + "%\n" + setName);
                    }
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();

                listener.onProgress(lang.equals("en") ? "Extracting & Installing..." : "جاري استخراج السيت وتركيبه...");

                // 1. تنظيف مسبق لأي ملفات .ogg أو .json قديمة عالقة
                clearOldActiveFiles(setsFolderPath);

                // 2. فك الضغط *مباشرة* في المجلد الرئيسي (بدون مجلد فرعي)
                boolean success = ZipUtils.unzipSetFromFile(zipFile, setsFolderPath);
                if (success) {
                    // 3. تسطيح الملفات (في حال كان السيت المضغوط يحتوي مجلداً داخله)
                    flattenDirectory(goldenFolder, setsFolderPath);

                    String sPrefix = detectPrefix(setsFolderPath, "_01.ogg", "string");
                    String rPrefix = detectPrefix(setsFolderPath, "_a.ogg", "rhythm");
                    listener.onSuccess(sPrefix, rPrefix);
                } else {
                    listener.onError(lang.equals("en") ? "Failed to extract set" : "فشل في فك ضغط السيت");
                }
            } catch (Exception e) {
                listener.onError(lang.equals("en") ? "Download failed! Check link or permissions." : "فشل التحميل! تأكد من الرابط أو الصلاحيات.");
            }
        }).start();
    }

    // اختراق ميديا فاير
    private static String getMediaFireDirectLink(String pageUrl) throws Exception {
        URL url = new URL(pageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        Pattern pattern = Pattern.compile("href=\"(https?://download[^\"']+)\"");
        while ((line = reader.readLine()) != null) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                reader.close();
                return matcher.group(1); 
            }
        }
        reader.close();
        return pageUrl; 
    }

    public static void restoreDefault(String setsFolderPath, String lang, InstallListener listener) {
        new Thread(() -> {
            clearOldActiveFiles(setsFolderPath);
            listener.onSuccess("string", "rhythm");
        }).start();
    }

    // 🚀 التعديل الأهم: دالة تنظيف تمسح ملفات الصوت و info.json في المجلد الرئيسي، *وتمسح أيضاً المجلدات الفرعية بالكامل* إن وجدت (باستثناء ملفات الـ zip)
    private static void clearOldActiveFiles(String setsFolderPath) {
        File dir = new File(setsFolderPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        String name = f.getName().toLowerCase();
                        if (name.endsWith(".ogg") || name.endsWith(".json")) {
                            f.delete();
                        }
                    } else if (f.isDirectory()) {
                        // إذا وجد مجلداً فرعياً (مثل "سيت 2727")، يمسح محتوياته ثم يمسحه هو
                        deleteDirectoryRecursive(f);
                    }
                }
            }
        }
    }

    // دالة مساعدة لمسح المجلدات الفرعية بالكامل
    private static void deleteDirectoryRecursive(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectoryRecursive(child);
                }
            }
        }
        dir.delete();
    }

    // 🚀 دالة التسطيح الذكية: تأخذ أي ملفات داخل المجلدات الفرعية (إن وجدت) وتضعها في المجلد الرئيسي، ثم تحذف المجلد الفرعي
    private static void flattenDirectory(File currentDir, String targetPath) {
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    flattenDirectory(f, targetPath);
                    f.delete(); // يحذف المجلد الفرعي بعد نقل ما بداخله
                } else {
                    // لا ننقل ملف الـ zip، ننقل فقط الأصوات وملف الأسماء
                    String name = f.getName().toLowerCase();
                    if (!f.getParent().equals(targetPath) && (name.endsWith(".ogg") || name.endsWith(".json"))) {
                        f.renameTo(new File(targetPath, f.getName()));
                    }
                }
            }
        }
    }

    public static String detectPrefix(String setsFolderPath, String suffix, String defaultPrefix) {
        File dir = new File(setsFolderPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(suffix)) {
                        return f.getName().substring(0, f.getName().lastIndexOf(suffix));
                    }
                }
            }
        }
        return defaultPrefix;
    }
}
