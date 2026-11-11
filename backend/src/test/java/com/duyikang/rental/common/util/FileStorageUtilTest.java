package com.duyikang.rental.common.util;

import com.duyikang.rental.common.config.FileStorageProperties;
import com.duyikang.rental.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FileStorageUtil} 单元测试。
 *
 * <p>纯 JUnit：手工 new 出 properties 与 util，<b>不启 Spring 上下文</b>——
 * 上传逻辑与容器无关，跑得快，也不会碰到本机 JDK 的 Mockito agent 问题。
 * 落盘目录用 {@link TempDir}，测试结束自动清理。</p>
 */
class FileStorageUtilTest {

    private static final long MAX_SIZE_MB = 5L;

    @TempDir
    Path tempDir;

    private FileStorageUtil util;

    @BeforeEach
    void setUp() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setRoot(tempDir.toString());
        properties.setAccessPath("/uploads");
        properties.setMaxSizeMb(MAX_SIZE_MB);
        properties.setAllowedExt(List.of("jpg", "jpeg", "png", "webp"));
        util = new FileStorageUtil(properties);
    }

    @Test
    void store_extensionNotAllowed_throwsFileTypeNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "image/png", bytes(16));

        assertThatThrownBy(() -> util.store(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(2004));
    }

    @Test
    void store_emptyFile_throwsFileTypeNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> util.store(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(2004));
    }

    @Test
    void store_oversize_throwsFileTooLarge() {
        long oversize = MAX_SIZE_MB * 1024 * 1024 + 1;
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", new byte[(int) oversize]);

        assertThatThrownBy(() -> util.store(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(2005));
    }

    @Test
    void store_nonImageContentType_throwsFileTypeNotAllowed() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "text/plain", bytes(32));

        assertThatThrownBy(() -> util.store(file))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getCode()).isEqualTo(2004));
    }

    @Test
    void store_normalPng_savesUnderRootAndReturnsUrl() throws Exception {
        byte[] content = bytes(64);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", content);

        FileStorageUtil.StoredFile stored = util.store(file);

        assertThat(stored.url()).matches("/uploads/\\d{6}/[0-9a-f]{32}\\.png");
        assertThat(stored.size()).isEqualTo(64);
        assertThat(stored.path()).exists();
        assertThat(stored.path().startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.readAllBytes(stored.path())).isEqualTo(content);
    }

    @Test
    void store_filenameWithTraversal_savedInsideRootWithoutDotDot() {
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.png", "image/png", bytes(8));

        FileStorageUtil.StoredFile stored = util.store(file);

        assertThat(stored.url()).doesNotContain("..");
        assertThat(stored.path().startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
        assertThat(stored.path().getFileName().toString()).doesNotContain("..");
    }

    @Test
    void store_uppercaseExtension_normalizedToLowercase() {
        MockMultipartFile file = new MockMultipartFile("file", "A.JPG", "image/jpeg", bytes(8));

        FileStorageUtil.StoredFile stored = util.store(file);

        assertThat(stored.url()).endsWith(".jpg");
        assertThat(stored.path().getFileName().toString()).endsWith(".jpg");
    }

    private static byte[] bytes(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 127);
        }
        return data;
    }
}
