package com.example.repository;

import org.springframework.stereotype.Repository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Repository
public class FileRepository {
    public Reader getReader(File file) throws IOException {
        return Files.newBufferedReader(file.path(), StandardCharsets.UTF_8);
    }
    public Stream<Resource> getDirectory(Directory directory) throws IOException {
        return Files.list(directory.path())
                .filter(p -> Files.isRegularFile(p) || Files.isDirectory(p))
                .map(path -> Files.isRegularFile(path) ? new File(path) : new Directory(path));
    }

    public void create(File file) throws IOException {
        Files.createFile(file.path());
    }
    public void create(Directory directory) throws IOException {
        Path path = directory.path();
        if (Files.exists(path))
            throw new FileAlreadyExistsException(path.toString());
        Files.createDirectories(path);
    }
    public void create(Resource resource) throws IOException {
        switch(resource){
            case File file -> create(file);
            case Directory directory -> create(directory);
        }
    }

    public boolean exists(File file){
        return Files.isRegularFile(file.path());
    }
    public boolean exists(Directory directory){
        return Files.isDirectory(directory.path());
    }
    public boolean exists(Resource resource) {
        return switch(resource){
            case File file -> exists(file);
            case Directory directory -> exists(directory);
        };
    }

    public Writer appendWriter(File file) throws IOException {
        return Files.newBufferedWriter(
                file.path(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
    public Writer rewriteWriter(File file) throws IOException {
        return Files.newBufferedWriter(
                file.path(),
                StandardCharsets.UTF_8,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    public void move(File source, File destination) throws IOException{
        Files.move(source.path(), destination.path(),
                StandardCopyOption.REPLACE_EXISTING);
    }
    public void copy(File source, File destination) throws IOException{
        Files.copy(source.path(), destination.path(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    public void delete(File file) throws IOException {
        Files.delete(file.path());
    }
    public void delete(Directory directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory.path())) {
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
    public void delete(Resource resource) throws IOException {
        switch(resource){
            case File file -> delete(file);
            case Directory directory -> delete(directory);
        }
    }

    public long lastModified(Resource resource) throws IOException {
        return Files.getLastModifiedTime(resource.path()).toMillis();
    }
    public long fileSize(Resource resource) throws IOException {
        return Files.size(resource.path());
    }
}