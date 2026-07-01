package com.vikisol.one.common.service;

import com.vikisol.one.common.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    private Path fileStoragePath;

    @PostConstruct
    public void init() {
        fileStoragePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(fileStoragePath);
            Files.createDirectories(fileStoragePath.resolve("profiles"));
            Files.createDirectories(fileStoragePath.resolve("resumes"));
            Files.createDirectories(fileStoragePath.resolve("documents"));
            Files.createDirectories(fileStoragePath.resolve("assets"));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directories", e);
        }
    }

    public String storeFile(MultipartFile file, String subDirectory) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String storedFileName = UUID.randomUUID() + extension;

        try {
            Path targetDir = fileStoragePath.resolve(subDirectory);
            Files.createDirectories(targetDir);
            Path targetPath = targetDir.resolve(storedFileName);

            if (!targetPath.getParent().equals(targetDir)) {
                throw new BadRequestException("Invalid file path");
            }

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return "/files/" + subDirectory + "/" + storedFileName;
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    public byte[] loadFile(String subDirectory, String fileName) {
        try {
            Path filePath = fileStoragePath.resolve(subDirectory).resolve(fileName).normalize();
            if (!filePath.startsWith(fileStoragePath)) {
                throw new BadRequestException("Invalid file path");
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Could not read file", e);
        }
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith("/files/")) return;
        try {
            String relativePath = fileUrl.substring("/files/".length());
            Path filePath = fileStoragePath.resolve(relativePath).normalize();
            if (filePath.startsWith(fileStoragePath)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException e) {
            log.warn("Could not delete file: {}", fileUrl, e);
        }
    }
}
