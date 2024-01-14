package com.fast.loan.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.stream.Stream;

public interface FileStorageService {

    void save(MultipartFile file);

    Resource load(String fileName);

    Stream<Path> loadAll();

    void deleteAll();
}
