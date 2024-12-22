package com.example.webserver.http;

import java.util.HashMap;
import java.util.Map;

public enum Mime {
    TEXT_HTML("html", "text/html", "UTF-8"),
    TEXT_CSS("css", "text/css", "UTF-8"),
    TEXT_PLAIN("txt", "text/plain", "UTF-8"),
    TEXT_XML("xml", "text/xml", "UTF-8"),
    TEXT_CSV("csv", "text/csv", "UTF-8"),

    // Image Types
    IMAGE_ICO("ico", "image/x-icon"),
    IMAGE_PNG("png", "image/png"),
    IMAGE_JPEG("jpeg", "image/jpeg"),
    IMAGE_BMP("bmp", "image/bmp"),
    IMAGE_SVG("svg", "image/svg+xml", "UTF-8"),
    IMAGE_JPG("jpg", "image/jpg"),
    IMAGE_GIF("gif", "image/gif"),
    IMAGE_TIFF("tiff", "image/tiff"),
    IMAGE_WEBP("webp", "image/webp"),

    // Application Types
    APPLICATION_JSON("json", "application/json", "UTF-8"),
    APPLICATION_XML("xml", "application/xml", "UTF-8"),
    APPLICATION_JAVASCRIPT("js", "application/javascript", "UTF-8"),
    APPLICATION_PDF("pdf", "application/pdf"),
    APPLICATION_ZIP("zip", "application/zip"),
    APPLICATION_GZIP("gz", "application/gzip"),
    APPLICATION_RAR("rar", "application/vnd.rar"),
    APPLICATION_TAR("tar", "application/x-tar"),
    APPLICATION_WWW_FORM_URLENCODED("urlencoded", "application/x-www-form-urlencoded", "UTF-8"),
    APPLICATION_OCTET_STREAM("bin", "application/octet-stream");

    private final String extension;
    private final String type;
    private final String charset;

    private static final Map<String, Mime> mimeTypes = new HashMap<>();
    private static final Map<String, Mime> extensionTypes = new HashMap<>();

    static {
        for (Mime mime : values()) {
            mimeTypes.put(mime.getType(), mime);
            extensionTypes.put(mime.getExtension(), mime);
        }
    }

    Mime(String extension, String type) {
        this(extension, type, null);
    }

    Mime(String extension, String type, String charset) {
        this.extension = extension;
        this.type = type;
        this.charset = charset;
    }

    public static Mime ofFilePath(String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("확장자는 cannot be null");
        }

        String extension = filePath.substring(filePath.lastIndexOf(".") + 1);

        if (!extensionTypes.containsKey(extension)) {
            return TEXT_HTML;
        }

        return extensionTypes.get(extension);
    }

    public String getExtension() {
        return extension;
    }

    public String getType() {
        return type;
    }

    public String getCharset() {
        return charset;
    }
}
