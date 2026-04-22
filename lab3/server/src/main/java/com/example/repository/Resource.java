package com.example.repository;

import java.nio.file.Path;

public sealed interface Resource
permits File, Directory{
    Path path();
}
