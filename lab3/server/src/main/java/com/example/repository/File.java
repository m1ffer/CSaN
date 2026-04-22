package com.example.repository;

import java.nio.file.Path;

public record File(Path path) implements Resource{
}
