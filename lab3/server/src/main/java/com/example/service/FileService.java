package com.example.service;

import com.example.repository.FileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FileService {
    public static final String PATH_TO_BASE_DIRECTORY = "D:/lab_rep";
    public static final Path BASE_DIR = Path.of(PATH_TO_BASE_DIRECTORY);
    private static Path resolve(String path){
        Path resolved = BASE_DIR.resolve(Path.of(path)).normalize();
        if (!resolved.startsWith(BASE_DIR))
            throw new SecurityException("Invalid path");
        return resolved;
    }

    private final FileRepository repository;
    @PostConstruct
    public void init(){
        try {
            this.repository.createDirectory(Path.of(PATH_TO_BASE_DIRECTORY));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать базовую директорию", e);
        }
    }

    public void append(String stringPath, Reader data) throws IOException {
        Path path = resolve(stringPath);
        if(!repository.exists(path))
            repository.createFile(path);
        try(data; Writer writer = repository.appendWriter(path)){
            data.transferTo(writer);
        }
    }
    public void rewrite(String stringPath, Reader data) throws IOException {
        Path path = resolve(stringPath);
        try(data; Writer writer = repository.rewriteWriter(path)){
            data.transferTo(writer);
        }
    }

    public void createDirectory(String stringPath) throws IOException {
        Path path = resolve(stringPath);
        if(repository.exists(path))
            throw new FileAlreadyExistsException(
                    "Directory " + stringPath + " already exists"
            );
        repository.createDirectory(path);
    }
    public void createFile(String stringPath) throws IOException {
        Path path = resolve(stringPath);
        repository.createFile(path);
    }

    public void move(String stringSource,
                     String stringDestination) throws IOException {
        Path source = resolve(stringSource);
        Path destination = resolve(stringDestination);
        if (!repository.isFile(source))
            throw new FileNotFoundException("File " + stringSource + " not found");
        if (repository.isFile(destination))
            repository.moveFileToFile(source, destination);
        else if (repository.isDirectory(destination))
            repository.moveFileToDirectory(source, destination);
        else
            throw new FileNotFoundException("Destination " + stringDestination + " not found");
    }

    public void delete(String stringPath) throws IOException {
        Path path = resolve(stringPath);
        if (repository.isFile(path))
            repository.deleteFile(path);
        else if (repository.isDirectory(path))
            repository.deleteDirectory(path);
        else
            throw new FileNotFoundException(stringPath + " not found");
    }

    public void copy(String stringSource,
                       String stringDestination) throws IOException{
        Path source = resolve(stringSource);
        Path destination = resolve(stringDestination);
        if (!repository.isFile(source))
            throw new FileNotFoundException("File " + stringSource + " not found");
        if (repository.isFile(destination))
            repository.copyFileToFile(source, destination);
        else if (repository.isDirectory(destination))
            repository.copyFileToDirectory(source, destination);
        else
            throw new FileNotFoundException("Destination " + stringDestination + " not found");
    }

    public Reader get(String stringPath) throws IOException{
        Path path = resolve(stringPath);
        return repository.getReader(path);
    }
}
