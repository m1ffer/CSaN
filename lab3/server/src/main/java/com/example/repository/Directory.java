package com.example.repository;

import java.nio.file.Path;

public record Directory(Path path) implements Resource{
}
