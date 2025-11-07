package com.tal.pro.service;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

@Service
public class FileStorageService {

    public String parseFile(MultipartFile file) throws IOException, TikaException, SAXException {
        // Create a parser that auto-detects the content type
        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 for no write limit
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = file.getInputStream()) {
            parser.parse(stream, handler, metadata, context);
            return handler.toString();
        }
    }

    public String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        return lastDotIndex == -1 ? "" : fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    public boolean isSupportedFileType(String fileExtension) {
        return fileExtension.matches("(?i)(pdf|doc|docx|txt|rtf|odt)");
    }
}
