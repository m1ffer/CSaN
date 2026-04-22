package com.example.service;

import com.example.repository.Directory;
import com.example.repository.File;
import com.example.repository.FileRepository;
import com.example.repository.Resource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FileService {
    public static final String PATH_TO_BASE_DIRECTORY = "D:/lab_rep/";
    public static final Path BASE_DIR = Path.of(PATH_TO_BASE_DIRECTORY);
    public static final String DIRECTORY_ETAG = "\"Directory\"";
    private static Path resolve(String path){
        if (path.startsWith("/"))
            path = path.substring(1);
        Path resolved = BASE_DIR.resolve(Path.of(path)).normalize();
        if (!resolved.startsWith(BASE_DIR))
            throw new SecurityException(path);
        return resolved;
    }
    private static String unresolve(Path path) {
        return BASE_DIR.relativize(path).toString();
    }
    public static String unresolve(String path){
        return unresolve(Path.of(path));
    }
    public static String unresolve(File file){
        return unresolve(file.path()).replace("\\", "/");
    }
    public static String unresolve(Directory directory){
        return unresolve(directory.path()).replace("\\", "/") + "/";
    }
    public static String unresolve(Resource resource){
        return switch(resource){
            case File file -> unresolve(file);
            case Directory directory -> unresolve(directory);
        };
    }

    public Resource toResource(String requestPath){
        Path resolved = resolve(requestPath);
        if (requestPath.endsWith("/"))
            return new Directory(resolved);
        else
            return new File(resolved);
    }

    private final FileRepository repository;
    private final PathLocker locker;

    @PostConstruct
    public void init(){
        try {
            Directory baseDir = new Directory(BASE_DIR);
            if (!repository.exists(baseDir))
                this.repository.create(baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать базовую директорию", e);
        }
    }

    public String append(File file, Reader data) throws IOException {
        locker.lock(file);
        try(data; Writer writer = repository.appendWriter(file)){
            data.transferTo(writer);
        }
        finally{
            locker.unlock(file);
        }
        return getETag(file);
    }
    public String rewrite(File file, Reader data) throws IOException {
        locker.lock(file);
        try(data; Writer writer = repository.rewriteWriter(file)){
            data.transferTo(writer);
        }
        finally{
            locker.unlock(file);
        }
        return getETag(file);
    }

    public String create(Resource resource) throws IOException{
        locker.lock(resource);
        try {
            repository.create(resource);
            return getETag(resource);
        }
        finally{
            locker.unlock(resource);
        }
    }

    public String move(File source, Resource destination) throws IOException{
        locker.lock(source, destination);
        try{
            return switch(destination){
                case File destinationFile-> {
                    repository.move(source, destinationFile);
                    yield getETag(destinationFile);
                }
                case Directory(Path path) -> {
                    File destinationFile = new File(path.resolve(source.path().getFileName()));
                    repository.move(source, destinationFile);
                    yield getETag(destinationFile);
                }
            };
        }
        finally{
            locker.unlock(source, destination);
        }
    }
    public String copy(File source, Resource destination) throws IOException{
        locker.lock(source, destination);
        try{
            return switch(destination){
                case File destinationFile-> {
                    repository.copy(source, destinationFile);
                    yield getETag(destinationFile);
                }
                case Directory(Path path) -> {
                    File destinationFile = new File(path.resolve(source.path().getFileName()));
                    repository.copy(source, destinationFile);
                    yield getETag(destinationFile);
                }
            };
        }
        finally{
            locker.unlock(source, destination);
        }
    }

    public void delete(Resource resource) throws IOException {
        locker.lock(resource);
        try{
            repository.delete(resource);
        }
        finally{
            locker.unlock(resource);
        }
    }

    public Reader getFile(File file) throws IOException{
        return repository.getReader(file);
    }
    public List<String> getDirectory(Directory directory) throws IOException{
        try(Stream<Resource> stream = repository.getDirectory(directory)){
            return stream
                    .map(r -> switch(r){
                        case File file -> file.path().getFileName().toString();
                        case Directory dir -> dir.path().getFileName()
                                .toString().replace("\\", "/") + "/";
                    }).toList();
        }
    }

    public boolean exists(File file){
        return repository.exists(file);
    }
    public boolean exists(Directory directory){
        return repository.exists(directory);
    }
    public boolean exists(Resource resource){
        return repository.exists(resource);
    }

    public String getETag(File file) throws IOException {
        long size = repository.fileSize(file);
        long lastModified = repository.lastModified(file);
        return "\"" + size + "-" + lastModified + "\"";
    }
    public String getETag(Directory directory) {
        return DIRECTORY_ETAG;
    }
    public String getETag(Resource resource) throws IOException {
        return switch(resource){
            case File file -> getETag(file);
            case Directory directory -> getETag(directory);
        };
    }
}
