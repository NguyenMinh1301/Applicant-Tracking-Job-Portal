package com.vietrecruit.common.util;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.vietrecruit.common.enums.ApiErrorCode;
import com.vietrecruit.common.exception.ApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates uploaded files using magic-byte signatures rather than trusting Content-Type headers.
 */
@Slf4j
@Component
public class FileValidator {

    private static final long MAX_CV_SIZE = 5L * 1024 * 1024; // 5MB
    private static final long MAX_AVATAR_SIZE = 2L * 1024 * 1024; // 2MB
    private static final long MAX_BANNER_SIZE = 3L * 1024 * 1024; // 3MB

    // Magic bytes
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] DOCX_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // PK (ZIP)
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] WEBP_LABEL = {0x57, 0x45, 0x42, 0x50}; // "WEBP" at offset 8

    public void validateCv(MultipartFile file) {
        validateSize(file, MAX_CV_SIZE);
        byte[] header = readHeader(file, 12);
        if (!isPdf(header) && !isDocx(header) && !isJpeg(header) && !isPng(header)) {
            throw new ApiException(ApiErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    public void validateAvatar(MultipartFile file) {
        validateSize(file, MAX_AVATAR_SIZE);
        byte[] header = readHeader(file, 12);
        if (!isJpeg(header) && !isPng(header) && !isWebp(header)) {
            throw new ApiException(ApiErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    public void validateBanner(MultipartFile file) {
        validateSize(file, MAX_BANNER_SIZE);
        byte[] header = readHeader(file, 12);
        if (!isJpeg(header) && !isPng(header) && !isWebp(header)) {
            throw new ApiException(ApiErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private void validateSize(MultipartFile file, long maxSize) {
        if (file.getSize() > maxSize) {
            throw new ApiException(ApiErrorCode.FILE_TOO_LARGE);
        }
    }

    private byte[] readHeader(MultipartFile file, int length) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[length];
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                throw new ApiException(ApiErrorCode.FILE_TYPE_NOT_ALLOWED);
            }
            return header;
        } catch (IOException e) {
            log.error("Failed to read file header: {}", e.getMessage());
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to read uploaded file");
        }
    }

    private boolean isPdf(byte[] header) {
        return startsWith(header, PDF_MAGIC);
    }

    private boolean isDocx(byte[] header) {
        return startsWith(header, DOCX_MAGIC);
    }

    private boolean isJpeg(byte[] header) {
        return startsWith(header, JPEG_MAGIC);
    }

    private boolean isPng(byte[] header) {
        return startsWith(header, PNG_MAGIC);
    }

    private boolean isWebp(byte[] header) {
        // RIFF....WEBP — "WEBP" label at bytes 8-11
        if (header.length < 12) return false;
        for (int i = 0; i < WEBP_LABEL.length; i++) {
            if (header[8 + i] != WEBP_LABEL[i]) return false;
        }
        return true;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
