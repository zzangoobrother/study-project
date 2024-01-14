package com.fast.loan.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    void save(MultipartFile file);
}
