package com.example.server;

import com.example.service.FileService;
import com.example.service.PathLocker;
import com.example.repository.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final String TEST_PREFIX = "api-it-";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FileService service;

    @Autowired
    private PathLocker locker;

    @AfterEach
    void cleanupTestRoots() throws IOException {
        Path base = FileService.BASE_DIR;
        if (!Files.exists(base)) {
            return;
        }
        try (Stream<Path> children = Files.list(base)) {
            children
                    .filter(p -> p.getFileName().toString().startsWith(TEST_PREFIX))
                    .forEach(this::deleteRecursivelyQuietly);
        }
    }

    @Test
    void postDirectoryCreatesDirectory() throws Exception {
        String root = newRoot();

        mvc.perform(post(dir(root)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void getDirectoryReturnsListing() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "a.txt"));
        mvc.perform(post(dir(root + "/sub")))
                .andExpect(status().isCreated());

        MvcResult result = mvc.perform(get(dir(root)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a.txt")))
                .andExpect(content().string(containsString("sub/")));
    }

    @Test
    void postWithoutBodyCreatesFile() throws Exception {
        String root = newRoot();
        createDirectory(root);

        mvc.perform(post(file(root, "a.txt")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"));
    }

    @Test
    void getFileReturnsContentSuccessfully() throws Exception {
        String root = newRoot();
        createDirectory(root);

        mvc.perform(post(file(root, "a.txt"))
                        .contentType("text/plain; charset=UTF-8")
                        .content("hello"))
                .andExpect(status().isCreated());

        mvc.perform(get(file(root, "a.txt")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/plain")))
                .andExpect(content().string("hello"));
    }

    @Test
    void postWithBodyCreatesNewFileWithoutIfMatch() throws Exception {
        String root = newRoot();
        createDirectory(root);
        String filePath = file(root, "a.txt");

        mvc.perform(post(filePath)
                        .contentType("text/plain; charset=UTF-8")
                        .content("abc"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"));

        assertFileContent(filePath, "abc");
    }

    @Test
    void postWithBodyToExistingFileWithoutIfMatchReturns409() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "a.txt"));

        mvc.perform(post(file(root, "a.txt"))
                        .contentType("text/plain; charset=UTF-8")
                        .content("x"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    void postWithBodyAndMatchingIfMatchAppends() throws Exception {
        String root = newRoot();
        createDirectory(root);
        String filePath = file(root, "a.txt");
        createFileWithBody(filePath, "a");
        String etag = currentFileEtag(filePath);

        mvc.perform(post(filePath)
                        .header("If-Match", etag)
                        .contentType("text/plain; charset=UTF-8")
                        .content("b"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("ETag"));

        assertFileContent(filePath, "ab");
    }

    @Test
    void postWithBodyAndMismatchedIfMatchReturns412() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createFileWithBody(file(root, "a.txt"), "a");

        mvc.perform(post(file(root, "a.txt"))
                        .header("If-Match", "\"wrong\"")
                        .contentType("text/plain; charset=UTF-8")
                        .content("b"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().string(containsString("If-Match does not match")));
    }

    @Test
    void postBodyToDirectoryReturns400() throws Exception {
        String root = newRoot();
        createDirectory(root);

        mvc.perform(post(dir(root))
                        .contentType("text/plain; charset=UTF-8")
                        .content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Body is not allowed for directory resource")));
    }

    @Test
    void putWithoutBodyReturns400() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "a.txt"));

        mvc.perform(put(file(root, "a.txt")).header("If-Match", "*"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("must contain a body")));
    }

    @Test
    void putWithoutIfMatchReturns428() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "a.txt"));

        mvc.perform(put(file(root, "a.txt"))
                        .contentType("text/plain; charset=UTF-8")
                        .content("x"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().string(containsString("If-Match header is required")));
    }

    @Test
    void putForDirectoryReturns400() throws Exception {
        String root = newRoot();
        createDirectory(root);

        mvc.perform(put(dir(root))
                        .header("If-Match", "*")
                        .contentType("text/plain; charset=UTF-8")
                        .content("x"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("PUT is allowed only for file resources")));
    }

    @Test
    void putWithIfMatchRewritesContent() throws Exception {
        String root = newRoot();
        createDirectory(root);
        String filePath = file(root, "a.txt");
        createFileWithBody(filePath, "old");
        String etag = currentFileEtag(filePath);

        mvc.perform(put(filePath)
                        .header("If-Match", etag)
                        .contentType("text/plain; charset=UTF-8")
                        .content("new"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("ETag"));

        assertFileContent(filePath, "new");
    }

    @Test
    void deleteWithoutIfMatchReturns428() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "a.txt"));

        mvc.perform(delete(file(root, "a.txt")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().string(containsString("If-Match header is required")));
    }

    @Test
    void deleteWithMismatchedIfMatchReturns412() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "a.txt"));

        mvc.perform(delete(file(root, "a.txt"))
                        .header("If-Match", "\"wrong\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().string(containsString("If-Match does not match")));
    }

    @Test
    void deleteWithIfMatchDeletesResource() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "a.txt"));

        mvc.perform(delete(file(root, "a.txt"))
                        .header("If-Match", "*"))
                .andExpect(status().isNoContent());

        mvc.perform(get(file(root, "a.txt")))
                .andExpect(status().isNotFound());
    }

    @Test
    void copyWithoutSourceIfMatchReturns428() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createFileWithBody(file(root, "src.txt"), "hello");

        mvc.perform(request(HttpMethod.valueOf("COPY"), file(root, "src.txt"))
                        .header("Destination", file(root, "dst.txt")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().string(containsString("Source-If-Match header is required")));
    }

    @Test
    void copyWithSourceMismatchReturns412() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createFileWithBody(file(root, "src.txt"), "hello");

        mvc.perform(request(HttpMethod.valueOf("COPY"), file(root, "src.txt"))
                        .header("Destination", file(root, "dst.txt"))
                        .header("Source-If-Match", "\"wrong\""))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().string(containsString("Source-If-Match does not match")));
    }

    @Test
    void copyToNewDestinationReturns201() throws Exception {
        String root = newRoot();
        createDirectory(root);
        String srcPath = file(root, "src.txt");
        String dstPath = file(root, "dst.txt");
        createFileWithBody(srcPath, "hello");

        mvc.perform(request(HttpMethod.valueOf("COPY"), srcPath)
                        .header("Destination", dstPath)
                        .header("Source-If-Match", "*"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"));

        assertFileContent(dstPath, "hello");
    }

    @Test
    void copyToExistingDestinationWithDestinationIfMatchReturns204() throws Exception {
        String root = newRoot();
        createDirectory(root);
        String srcPath = file(root, "src.txt");
        String dstPath = file(root, "dst.txt");
        createFileWithBody(srcPath, "hello");
        createFileWithBody(dstPath, "old");
        String dstEtag = currentFileEtag(dstPath);

        mvc.perform(request(HttpMethod.valueOf("COPY"), srcPath)
                        .header("Destination", dstPath)
                        .header("Source-If-Match", "*")
                        .header("Destination-If-Match", dstEtag))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("ETag"));

        assertFileContent(dstPath, "hello");
    }

    @Test
    void moveToNewDestinationReturns201AndRemovesSource() throws Exception {
        String root = newRoot();
        createDirectory(root);
        String srcPath = file(root, "src.txt");
        String dstPath = file(root, "dst.txt");
        createFileWithBody(srcPath, "hello");

        mvc.perform(request(HttpMethod.valueOf("MOVE"), srcPath)
                        .header("Destination", dstPath)
                        .header("Source-If-Match", "*"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"));

        mvc.perform(get(srcPath))
                .andExpect(status().isNotFound());
        assertFileContent(dstPath, "hello");
    }

    @Test
    void moveToExistingDestinationWithDestinationIfMatchReturns204() throws Exception {
        String root = newRoot();
        createDirectory(root);
        String srcPath = file(root, "src.txt");
        String dstPath = file(root, "dst.txt");
        createFileWithBody(srcPath, "hello");
        createFileWithBody(dstPath, "old");
        String dstEtag = currentFileEtag(dstPath);

        mvc.perform(request(HttpMethod.valueOf("MOVE"), srcPath)
                        .header("Destination", dstPath)
                        .header("Source-If-Match", "*")
                        .header("Destination-If-Match", dstEtag))
                .andExpect(status().isNoContent())
                .andExpect(header().exists("ETag"));

        mvc.perform(get(srcPath))
                .andExpect(status().isNotFound());
        assertFileContent(dstPath, "hello");
    }

    @Test
    void invalidDestinationPathReturns400() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createFileWithBody(file(root, "src.txt"), "hello");

        mvc.perform(request(HttpMethod.valueOf("COPY"), file(root, "src.txt"))
                        .header("Destination", "?:/bad")
                        .header("Source-If-Match", "*"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("invalid")));
    }

    @Test
    void lockedResourceReturns423() throws Exception {
        String root = newRoot();
        createDirectory(root);
        createEmptyFile(file(root, "lock.txt"));
        File locked = (File) service.toResource(file(root, "lock.txt"));

        locker.lock(locked);
        try {
            mvc.perform(delete(file(root, "lock.txt"))
                            .header("If-Match", "*"))
                    .andExpect(status().isLocked())
                    .andExpect(content().string(containsString("is busy")));
        } finally {
            locker.unlock(locked);
        }
    }

    @Test
    void getNonExistingFileReturns404() throws Exception {
        String root = newRoot();
        createDirectory(root);

        mvc.perform(get(file(root, "no.txt")))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEmptyDirectoryReturnsEmptyJson() throws Exception {
        String root = newRoot();
        createDirectory(root);

        MvcResult result = mvc.perform(get(dir(root))).andReturn();

        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void postDirectoryWithBodyReturns400() throws Exception {
        String root = newRoot();
        createDirectory(root);

        mvc.perform(post(dir(root))
                        .contentType("text/plain")
                        .content("data"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postExistingFileWithoutIfMatchReturns409() throws Exception {
        String root = newRoot();
        createDirectory(root);

        createEmptyFile(file(root, "a.txt"));

        mvc.perform(post(file(root, "a.txt"))
                        .content("data"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRemovesFile() throws Exception {
        String root = newRoot();
        createDirectory(root);

        createEmptyFile(file(root, "a.txt"));

        String etag = mvc.perform(get(file(root, "a.txt")))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mvc.perform(delete(file(root, "a.txt"))
                        .header("If-Match", etag))
                .andExpect(status().isNoContent());

        mvc.perform(get(file(root, "a.txt")))
                .andExpect(status().isNotFound());
    }

    @Test
    void copyToExistingWithoutDestinationIfMatchReturns409() throws Exception {
        String root = newRoot();
        createDirectory(root);

        createEmptyFile(file(root, "a.txt"));
        createEmptyFile(file(root, "b.txt"));

        String etag = mvc.perform(get(file(root, "a.txt")))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mvc.perform(MockMvcRequestBuilders.request(
                                HttpMethod.valueOf("COPY"),
                                file(root, "a.txt")
                        )
                        .header("Destination", file(root, "b.txt"))
                        .header("Source-If-Match", etag))
                .andExpect(status().isConflict());
    }

    @Test
    void moveRemovesSourceFile() throws Exception {
        String root = newRoot();
        createDirectory(root);

        createEmptyFile(file(root, "a.txt"));

        String etag = mvc.perform(get(file(root, "a.txt")))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mvc.perform(MockMvcRequestBuilders.request(
                                HttpMethod.valueOf("MOVE"),
                                file(root, "a.txt"))
                        .header("Destination", file(root, "b.txt"))
                        .header("Source-If-Match", etag))
                .andExpect(status().isCreated());

        mvc.perform(get(file(root, "a.txt")))
                .andExpect(status().isNotFound());

        mvc.perform(get(file(root, "b.txt")))
                .andExpect(status().isOk());
    }

    @Test
    void pathTraversalReturns400() throws Exception {
        mvc.perform(get("/../../evil.txt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void etagChangesAfterModification() throws Exception {
        String root = newRoot();
        createDirectory(root);

        mvc.perform(post(file(root, "a.txt"))
                        .content("hello"))
                .andExpect(status().isCreated());

        String etag1 = mvc.perform(get(file(root, "a.txt")))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        mvc.perform(post(file(root, "a.txt"))
                        .header("If-Match", etag1)
                        .content("world"))
                .andExpect(status().isNoContent());

        String etag2 = mvc.perform(get(file(root, "a.txt")))
                .andReturn()
                .getResponse()
                .getHeader("ETag");

        assertNotEquals(etag1, etag2);
    }



    private String newRoot() {
        return TEST_PREFIX + UUID.randomUUID();
    }

    private String dir(String root) {
        return "/" + root + "/";
    }

    private String file(String root, String name) {
        return "/" + root + "/" + name;
    }

    private void createDirectory(String root) throws Exception {
        mvc.perform(post(dir(root)))
                .andExpect(status().isCreated());
    }

    private void createEmptyFile(String path) throws Exception {
        mvc.perform(post(path))
                .andExpect(status().isCreated());
    }

    private String createFileWithBody(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path)
                        .contentType("text/plain; charset=UTF-8")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("ETag"))
                .andReturn();
        return result.getResponse().getHeader("ETag");
    }

    private String currentFileEtag(String path) throws IOException {
        File file = (File) service.toResource(path);
        return service.getETag(file);
    }

    private void assertFileContent(String path, String expected) throws IOException {
        Path diskPath = FileService.BASE_DIR.resolve(path.substring(1));
        String actual = Files.readString(diskPath, StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private void deleteRecursivelyQuietly(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }


}
