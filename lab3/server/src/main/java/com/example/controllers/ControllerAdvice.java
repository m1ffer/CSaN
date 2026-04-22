package com.example.controllers;

import com.example.service.FileService;
import com.example.service.ResourceLockedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;

@RestControllerAdvice
public class ControllerAdvice {
    @ExceptionHandler(NoSuchFileException.class)
    public ResponseEntity<String> handleNotFound(NoSuchFileException e){
        return ResponseEntity
                .status(404)
                .body("File " + FileService.unresolve(e.getFile()) + " not found");
    }

    @ExceptionHandler(FileAlreadyExistsException.class)
    public ResponseEntity<String> handleExists(FileAlreadyExistsException e){
        return ResponseEntity
                .status(409)
                .body("File " + FileService.unresolve(e.getFile()) + " already exists");
    }

    @ExceptionHandler(ResourceLockedException.class)
    public ResponseEntity<String> handleLocked(ResourceLockedException e){
        return ResponseEntity
                .status(423)
                .body("File " + FileService.unresolve(e.getMessage()) + " is busy");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurity(SecurityException e){
        return ResponseEntity
                .status(400)
                .body("Path " + e.getMessage() + " is invalid");
    }

    @ExceptionHandler(InvalidPathException.class)
    public ResponseEntity<String> handleInvalidPath(InvalidPathException e){
        return ResponseEntity
                .status(400)
                .body("Path " + e.getMessage() + " is invalid");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e){
        return ResponseEntity
                .badRequest()
                .body("Invalid request: " + e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIO(IOException e){
        return ResponseEntity
                .status(500)
                .body("IO error on server: " + e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<String> handleIO(UncheckedIOException e){
        return ResponseEntity
                .status(500)
                .body("IO error on server: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAll(Exception e){
        return ResponseEntity
                .status(500)
                .body("Internal server error: " + e.getMessage());
    }
}
