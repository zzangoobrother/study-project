package com.example.webserver.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

public class FileSaveHelper {

    private static final String UPLOAD_DIR = System.getProperty("user.home") + "/uploads";

    public static String saveFile(byte[] fileContent, String originalFilename) throws IOException {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String extension = getFileExtension(originalFilename);
        String uuidFilename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        File file = new File(uploadDir, uuidFilename);

        try (OutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(fileContent);
        }

        return uuidFilename;
    }

    private static String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    public static boolean removeFile(String filename) {
        File file = new File(UPLOAD_DIR, filename);
        return file.delete();
    }
}
