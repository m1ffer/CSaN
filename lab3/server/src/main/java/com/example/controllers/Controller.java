package com.example.controllers;

import com.example.extension.annotation.HttpMethod;
import com.example.repository.Directory;
import com.example.repository.File;
import com.example.repository.Resource;
import com.example.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;

@RestController
@RequiredArgsConstructor
public class Controller {

    private final FileService service;
    private final ObjectMapper objectMapper;

    @GetMapping("/**")
    public ResponseEntity<StreamingResponseBody> get(HttpServletRequest request) throws IOException {
        String path = extractPath(request);
        System.out.println("Пришел гет запрос к файлу: " + path);

        Resource resource = service.toResource(path);

        return switch (resource) {

            case File file -> {
                Reader reader = service.getFile(file);
                String etag = service.getETag(file);

                StreamingResponseBody stream = outputStream -> {
                    try (reader;
                         Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                        reader.transferTo(writer);
                    }
                };

                yield ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .header("ETag", etag)
                        .body(stream);
            }

            case Directory directory -> {
                Object dto = service.getDirectory(directory);

                StreamingResponseBody stream = outputStream -> {
                    try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                        objectMapper.writeValue(writer, dto);
                    }
                };

                yield ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(stream);
            }
        };
    }

    @PostMapping("/**")
    public ResponseEntity<?> post(HttpServletRequest request,
                                  @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                  Reader body)
                        throws IOException{
        String path = extractPath(request);
        System.out.println("Пришел пост запрос к файлу " + path);
        Resource resource = service.toResource(path);
        File file;
        boolean hasBody = body != null &&
                (request.getContentLengthLong() > 0 ||
                        request.getHeader("Transfer-Encoding") != null ||
                        "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding")));

        String ETag;
        if(!hasBody) {
            ETag = service.create(resource);
            return ResponseEntity
                    .created(URI.create(path))
                    .header("ETag", ETag)
                    .build();
        }
        if (resource instanceof File f)
            file = f;
        else return ResponseEntity
                .badRequest()
                .body("Body is not allowed for directory resource");
        if (ifMatch != null){
                if (!service.exists(file))
                    throw new NoSuchFileException(file.path().toString());
                if ((ifMatch.equals("*") || ifMatch.equals(service.getETag(file)))) {
                    ETag = service.append(file, body);
                    return ResponseEntity
                            .noContent()
                            .header("ETag", ETag)
                            .build();
                }
                else return ResponseEntity
                        .status(412)
                        .body("If-Match does not match the current ETag");
        } else{
            if(service.exists(file))
                throw new FileAlreadyExistsException(file.path().toString());
            ETag = service.append(file, body);
            return ResponseEntity
                    .created(URI.create(path))
                    .header("ETag", ETag)
                    .build();
        }
    }

    @PutMapping("/**")
    public ResponseEntity<?> put(HttpServletRequest request,
                      @RequestHeader(value = "If-Match", required = false) String ifMatch,
                      Reader body) throws IOException {
        String path = extractPath(request);
        System.out.println("Пришел пут запрос к файлу " + path);
        Resource resource = service.toResource(path);

        boolean hasBody = body != null &&
                (request.getContentLengthLong() > 0 ||
                        request.getHeader("Transfer-Encoding") != null ||
                        "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding")));

        String ETag;
        if(!hasBody) {
            return ResponseEntity
                    .badRequest()
                    .body("PUT request must contain a body");
        }
        else if (ifMatch != null){
            if (resource instanceof File file){
                if ((ifMatch.equals("*") || ifMatch.equals(service.getETag(file)))) {
                    ETag = service.rewrite(file, body);
                    return ResponseEntity
                            .noContent()
                            .header("ETag", ETag)
                            .build();
                }
                else return ResponseEntity
                        .status(412)
                        .body("If-Match does not match the current ETag");
            }
            else return ResponseEntity
                    .badRequest()
                    .body("PUT is allowed only for file resources");
        }
        else return ResponseEntity
                    .status(428)
                    .body("If-Match header is required");
    }

    @DeleteMapping("/**")
    public ResponseEntity<?> delete(HttpServletRequest request,
                         @RequestHeader(value = "If-Match", required = false) String ifMatch) throws IOException {
        String path = extractPath(request);
        System.out.println("Пришел делит запрос к файлу " + path);
        Resource resource = service.toResource(path);

        if (ifMatch != null){
            if (ifMatch.equals("*") || ifMatch.equals(service.getETag(resource))) {
                service.delete(resource);
                return ResponseEntity
                        .noContent()
                        .build();
            }
            else return ResponseEntity
                    .status(412)
                    .body("If-Match does not match the current ETag");
        }
        else return ResponseEntity
                .status(428)
                .body("If-Match header is required");
    }

    @HttpMethod("COPY")
    @RequestMapping("/**")
    public ResponseEntity<?> copy(HttpServletRequest request,
                       @RequestHeader("Destination") String destination,
                       @RequestHeader(value = "Source-If-Match", required = false) String sourceIfMatch,
                       @RequestHeader(value = "Destination-If-Match", required = false) String destinationIfMatch) throws IOException {
        String source = extractPath(request);
        System.out.println("Пришел копи запрос к файлу " + source + " в файл " + destination);
        Resource srcResource = service.toResource(source);
        File srcFile;
        Resource destResource = service.toResource(destination);

        if (srcResource instanceof File file)
            srcFile = file;
        else
            return ResponseEntity
                    .badRequest()
                    .body("COPY source must be a file resource");

        if (sourceIfMatch == null)
            return ResponseEntity
                    .status(428)
                    .body("Source-If-Match header is required");

        if (!sourceIfMatch.equals("*") && !sourceIfMatch.equals(service.getETag(srcFile)))
            return ResponseEntity
                    .status(412)
                    .body("Source-If-Match does not match source ETag");

        String ETag;
        if (destinationIfMatch != null) {
            if (!service.exists(destResource))
                throw new NoSuchFileException(destResource.path().toString());
            if (!destinationIfMatch.equals("*") && !destinationIfMatch.equals(service.getETag(destResource)))
                return ResponseEntity
                        .status(412)
                        .body("Destination-If-Match does not match destination ETag");

            ETag = service.copy(srcFile, destResource);

            return ResponseEntity
                    .noContent()
                    .header("ETag", ETag)
                    .build();
        } else{
            if (service.exists(destResource))
                throw new FileAlreadyExistsException(destResource.path().toString());

            ETag = service.copy(srcFile, destResource);

            return ResponseEntity
                    .created(URI.create(destination))
                    .header("ETag", ETag)
                    .build();
        }
    }

    @HttpMethod("MOVE")
    @RequestMapping("/**")
    public ResponseEntity<?> move(HttpServletRequest request,
                       @RequestHeader("Destination") String destination,
                       @RequestHeader(value = "Source-If-Match", required = false) String sourceIfMatch,
                       @RequestHeader(value = "Destination-If-Match", required = false) String destinationIfMatch) throws IOException {
        String source = extractPath(request);
        System.out.println("Пришел мув запрос к файлу " + source + " в файл " + destination);
        Resource srcResource = service.toResource(source);
        File srcFile;
        Resource destResource = service.toResource(destination);

        if (srcResource instanceof File file)
            srcFile = file;
        else
            return ResponseEntity
                    .badRequest()
                    .body("MOVE source must be a file resource");

        if (sourceIfMatch == null)
            return ResponseEntity
                    .status(428)
                    .body("Source-If-Match header is required");

        if (!sourceIfMatch.equals("*") && !sourceIfMatch.equals(service.getETag(srcFile)))
            return ResponseEntity
                    .status(412)
                    .body("Source-If-Match does not match source ETag");

        String ETag;
        if (destinationIfMatch != null) {
            if (!service.exists(destResource))
                throw new NoSuchFileException(destResource.path().toString());
            if (!destinationIfMatch.equals("*") && !destinationIfMatch.equals(service.getETag(destResource)))
                return ResponseEntity
                        .status(412)
                        .body("Destination-If-Match does not match destination ETag");

            ETag = service.move(srcFile, destResource);

            return ResponseEntity
                    .noContent()
                    .header("ETag", ETag)
                    .build();
        } else{
            if (service.exists(destResource))
                throw new FileAlreadyExistsException(destResource.path().toString());

            ETag = service.move(srcFile, destResource);

            return ResponseEntity
                    .created(URI.create(destination))
                    .header("ETag", ETag)
                    .build();
        }
    }

    private String extractPath(HttpServletRequest request){
        return (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
        );
    }
}
