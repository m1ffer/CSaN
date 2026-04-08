package com.example.repository;

import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

@Repository
public class FileRepository {

    public Reader getReader(Path path) throws IOException {
        return Files.newBufferedReader(path, StandardCharsets.UTF_8);
    }

    public InputStream getInputStream(Path path) throws IOException {
        return new BufferedInputStream(Files.newInputStream(path));
    }

    public void createFile(Path path) throws IOException {
        Files.createFile(path);
    }

    public void createDirectory(Path path) throws IOException {
        Files.createDirectories(path);
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }

    public Writer appendWriter(Path path) throws IOException {
        return Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    public Writer rewriteWriter(Path path) throws IOException {
        return Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    public boolean isFile(Path path) {
        return Files.isRegularFile(path);
    }

    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    public void moveFileToDirectory(Path source, Path destinationDir) throws IOException {
        Path target = destinationDir.resolve(source.getFileName());
        moveFileToFile(source, target);
    }

    public void moveFileToFile(Path source, Path target) throws IOException{
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void deleteFile(Path path) throws IOException {
        Files.delete(path);
    }

    public void deleteDirectory(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    public void copyFileToFile(Path source, Path target) throws IOException{
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void copyFileToDirectory(Path source, Path destinationDir) throws IOException{
        Path target = destinationDir.resolve(source.getFileName());
        copyFileToFile(source, target);
    }
}