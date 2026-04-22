package com.example.service;

import com.example.repository.Resource;

import java.io.IOException;
import java.nio.file.Path;

public class ResourceLockedException extends IOException{

    public ResourceLockedException(Resource resource) {
        super(resource.path().toString());
    }
}
