package com.fast.loan.controller;

import com.fast.loan.dto.ApplicationDTO;
import com.fast.loan.dto.FileDTO;
import com.fast.loan.dto.ResponseDTO;
import com.fast.loan.service.ApplicationService;
import com.fast.loan.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@RequiredArgsConstructor
@RestController
@RequestMapping("/applications")
public class ApplicationController extends AbstractController {

    private final ApplicationService applicationService;
    private final FileStorageService fileStorageService;

    @PostMapping
    public ResponseDTO<ApplicationDTO.Response> create(@RequestBody ApplicationDTO.Request request) {
        return ok(applicationService.create(request));
    }

    @GetMapping("/{applicationId}")
    public ResponseDTO<ApplicationDTO.Response> get(@PathVariable Long applicationId) {
        return ok(applicationService.get(applicationId));
    }

    @PutMapping("/{applicationId}")
    public ResponseDTO<ApplicationDTO.Response> update(@PathVariable Long applicationId, @RequestBody ApplicationDTO.Request request) {
        return ok(applicationService.update(applicationId, request));
    }

    @DeleteMapping("/{applicationId}")
    public ResponseDTO<Void> delete(@PathVariable Long applicationId) {
        applicationService.delete(applicationId);
        return ok();
    }

    @PostMapping("/{applicationId}/terms")
    public ResponseDTO<Boolean> acceptTerms(@PathVariable Long applicationId, @RequestBody ApplicationDTO.AcceptTerms request) {
        return ok(applicationService.acceptTerms(applicationId, request));
    }

    @PostMapping("/files")
    public ResponseDTO<Void> upload(MultipartFile file) {
        fileStorageService.save(file);
        return ok();
    }

    @GetMapping("/files")
    public ResponseEntity<Resource> download(@RequestParam String fileName) {
        Resource file = fileStorageService.load(fileName);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").body(file);
    }

    @GetMapping("/files/info")
    public ResponseDTO<List<FileDTO>> getFileInfos() {
        List<FileDTO> fileInfos = fileStorageService.loadAll().map(path -> {
            String fileName = path.getFileName().toString();
            return FileDTO.builder()
                    .name(fileName)
                    .url(MvcUriComponentsBuilder.fromMethodName(ApplicationController.class, "download", fileName).build().toString())
                    .build();
        }).toList();

        return ok(fileInfos);
    }
}
