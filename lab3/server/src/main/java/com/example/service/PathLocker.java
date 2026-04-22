package com.example.service;

import com.example.repository.Directory;
import com.example.repository.File;
import com.example.repository.FileRepository;
import com.example.repository.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class PathLocker {
    private final TreeSet<Path> locked = new TreeSet<>();

    private boolean tryLock(Resource resource) {
        switch(resource){
            case Directory(Path path) ->{
                if (isNotLockedByAncestor(path)) {
                    Path next = locked.higher(path);
                    if (next == null || !next.startsWith(path))
                        return locked.add(path);
                    else
                        return false;
                }
                return false;
            }
            case File(Path path) ->{
                if (isNotLockedByAncestor(path))
                    return locked.add(path);
                return false;
            }
        }
    }

    private boolean isNotLockedByAncestor(Path path){
        for(Path current = path; current != null; current = current.getParent())
            if (locked.contains(current))
                return false;
        return true;
    }

    public synchronized void unlock(Resource... paths){
        Arrays.stream(paths)
                .map(Resource::path)
                .forEach(locked::remove);
    }

    public synchronized void lock(Resource... resources) throws ResourceLockedException, NoSuchFileException {
        for (int i = 0; i < resources.length; i++)
            if (!tryLock(resources[i])){
                rollback(resources, i);
                throw new ResourceLockedException(resources[i]);
            }
    }

    private void rollback(Resource[] paths, int failed){
        for (int i = 0; i < failed; i++)
            locked.remove(paths[i].path());
    }
}
