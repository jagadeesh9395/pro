package com.tal.pro.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Component
public class FileUploadUtil {

    private final Path fileStorageLocation;
    private final long maxFileSize;
    private final String[] allowedExtensions;

    public FileUploadUtil(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${file.max-size:10485760}") long maxFileSize,
            @Value("${file.allowed-extensions}") String[] allowedExtensions) throws IOException {
        
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
        this.allowedExtensions = allowedExtensions;
        
        // Create upload directory if it doesn't exist
        Files.createDirectories(this.fileStorageLocation);
    }

    public String storeFile(MultipartFile file) throws IOException {
        // Validate file
        validateFile(file);

        // Generate a unique file name
        String originalFileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = "";
        
        if (originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        
        String fileName = UUID.randomUUID().toString() + fileExtension;
        
        // Copy file to the target location
        Path targetLocation = this.fileStorageLocation.resolve(fileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        
        return fileName;
    }

    private void validateFile(MultipartFile file) {
        // Check if file is empty
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds the maximum limit");
        }

        // Check file extension
        String originalFileName = file.getOriginalFilename();
        if (originalFileName != null) {
            String fileExtension = "";
            if (originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
            }
            
            if (!Arrays.asList(allowedExtensions).contains(fileExtension.toLowerCase())) {
                throw new IllegalArgumentException("File type not allowed. Allowed types: " + 
                        String.join(", ", allowedExtensions));
            }
        }
    }

    public Path loadFile(String filename) {
        return fileStorageLocation.resolve(filename).normalize();
    }

    public void deleteFile(String filename) throws IOException {
        Path filePath = loadFile(filename);
        Files.deleteIfExists(filePath);
    }

    public String getContentType(String filename) {
        try {
            return Files.probeContentType(loadFile(filename));
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
