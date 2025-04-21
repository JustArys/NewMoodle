package com.example.newmoodle.service;


import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.Locale;
import java.util.Map; // Import Map
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-duration:15}")
    private long presignedUrlDurationMinutes;

    // Map for extensions to MIME types (add more as needed)
    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp"
            // Add other types if you support them
    );

    // --- uploadFile, downloadFileAsStream, deleteFile remain the same ---
    public String uploadFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        // Улучшено удаление недопустимых символов
        String safeOriginalName = originalFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String key = UUID.randomUUID().toString() + "_" + safeOriginalName;


        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType()) // Важно для правильной работы pre-signed URL
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
            logger.info("File uploaded successfully to R2/S3 with key: {}", key);
            return key;
        } catch (S3Exception e) {
            logger.error("S3 Error uploading file with key {}: {}", key, e.awsErrorDetails().errorMessage(), e);
            throw new IOException("Failed to upload file to R2/S3: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("IO Error uploading file with key {}: {}", key, e.getMessage(), e);
            throw e; // Пробрасываем дальше
        }
    }

    public ResponseInputStream<GetObjectResponse> downloadFileAsStream(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            logger.debug("Requesting input stream for key: {}", key);
            return s3Client.getObject(getObjectRequest);
        } catch (NoSuchKeyException e) {
            logger.warn("File not found in R2/S3 with key: {}", key);
            return null;
        } catch (S3Exception e) {
            logger.error("S3 Error downloading file stream for key {}: {}", key, e.awsErrorDetails().errorMessage(), e);
            return null;
        }
    }

    public void deleteFile(String key) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            logger.info("Deleted object from R2/S3 with key: {}", key);
        } catch (S3Exception e) {
            logger.error("S3 Error deleting file with key {}: {}", key, e.awsErrorDetails().errorMessage(), e);
            // Можно добавить throw new RuntimeException(...) если удаление критично
        }
    }
    // --- loadFileAsResource can remain if needed elsewhere, but not for OpenAI image call ---
    public Resource loadFileAsResource(String key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(presignedUrlDurationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(presignRequest);
            URL url = presignedGetObjectRequest.url();
            logger.info("Generated pre-signed URL for key: {}, expires: {}", key, presignedGetObjectRequest.expiration());

            // Wrap in UrlResource, but don't rely on .exists() for pre-signed URLs immediately
            Resource resource = new UrlResource(url);
            return resource;

        } catch (S3Exception e) {
            logger.error("S3 Error generating pre-signed URL for key {}: {}", key, e.awsErrorDetails().errorMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error generating pre-signed URL or Resource for key {}: {}", key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Downloads a file from S3/R2 and returns its content as a byte array.
     * @param key The key of the object in the bucket.
     * @return Byte array of the file content.
     * @throws IOException If the file is not found or an error occurs during download/reading.
     */
    public byte[] downloadFileAsBytes(String key) throws IOException {
        logger.info("Attempting to download file as bytes for key: {}", key);
        try (ResponseInputStream<GetObjectResponse> s3ObjectStream = downloadFileAsStream(key)) {
            if (s3ObjectStream == null) {
                // downloadFileAsStream already logs warnings/errors
                throw new IOException("File not found or could not be accessed in S3/R2 with key: " + key);
            }
            byte[] content = s3ObjectStream.readAllBytes();
            logger.info("Successfully downloaded {} bytes for key: {}", content.length, key);
            return content;
        } catch (IOException e) {
            logger.error("IOException while reading bytes for key {}: {}", key, e.getMessage(), e);
            throw e; // Re-throw IOExceptions
        } catch (Exception e) {
            // Catch other potential exceptions during stream processing
            logger.error("Unexpected error downloading file as bytes for key {}: {}", key, e.getMessage(), e);
            throw new IOException("Failed to download file as bytes for key " + key + ": " + e.getMessage(), e);
        }
    }

    /**
     * Determines the MIME type based on the file extension in the key.
     * @param key The object key (e.g., "abc.png").
     * @return The corresponding MIME type (e.g., "image/png") or a default if unknown.
     */
    public String getMimeType(String key) {
        if (key == null || !key.contains(".")) {
            return "application/octet-stream"; // Default binary type
        }
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return EXTENSION_TO_MIME_TYPE.getOrDefault(ext, "application/octet-stream");
    }


    // --- extractText and helper methods remain the same ---
    public String extractText(String key) throws Exception {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("R2/S3 object key cannot be null or empty");
        }

        String fileExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT) : "";
        logger.info("Attempting text extraction for key: {}, detected extension: '{}'", key, fileExtension);

        try (InputStream inputStream = downloadFileAsStream(key)) {
            if (inputStream == null) {
                logger.error("Could not get input stream for key '{}'. File might not exist or S3 error occurred.", key);
                throw new IOException("Failed to get file stream from R2/S3 for key: " + key);
            }

            switch (fileExtension) {
                case "pdf":
                    return extractTextFromPdf(inputStream);
                case "docx":
                    return extractTextFromDocx(inputStream);
                case "txt":
                    return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                case "jpg":
                case "png":
                case "jpeg":
                case "gif":
                case "webp":
                    logger.warn("Text extraction from images ('{}') is not supported by this method.", fileExtension);
                    throw new IllegalArgumentException("Text extraction not supported for image type: " + fileExtension + ". Use direct image processing.");
                default:
                    logger.warn("Unsupported file type for text extraction: {}", fileExtension);
                    throw new IllegalArgumentException("Unsupported file type for text extraction: " + fileExtension);
            }
        } catch (IOException ioException) {
            // Логгируем и пробрасываем дальше
            logger.error("IOException during text extraction process for key {}: {}", key, ioException.getMessage(), ioException);
            throw ioException;
        } catch (IllegalArgumentException illegalArgEx) {
            // Логгируем и пробрасываем дальше (это наши ожидаемые ошибки для неподдерживаемых типов)
            logger.warn("IllegalArgumentException during text extraction for key {}: {}", key, illegalArgEx.getMessage());
            throw illegalArgEx;
        } catch (Exception e) {
            // Логгируем неожиданные ошибки
            logger.error("Unexpected error during text extraction for key {}: {}", key, e.getMessage(), e);
            throw new Exception("Failed to extract text from file (key: " + key + "): " + e.getMessage(), e);
        }
    }

    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        // PDFBox может требовать чтения всего потока в память
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            if (document.isEncrypted()) {
                logger.warn("Attempting to extract text from an encrypted PDF. Result may be empty or partial.");
                // Можно добавить попытку снять защиту с пустым паролем: document.setAllSecurityToBeRemoved(true);
            }
            PDFTextStripper textStripper = new PDFTextStripper();
            String text = textStripper.getText(document);
            logger.debug("Successfully extracted text from PDF");
            return text;
        } catch (Exception e) {
            logger.error("Error extracting text from PDF: {}", e.getMessage(), e);
            throw new IOException("Failed to parse PDF content: " + e.getMessage(), e);
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws Exception {
        try {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(inputStream);
            MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
            // Correctly extract text content from docx4j object model
            org.docx4j.wml.Document wmlDocumentEl = documentPart.getContents();
            javax.xml.bind.JAXBElement<?> body = (javax.xml.bind.JAXBElement<?>) wmlDocumentEl.getBody().getContent().get(0); // Example access, might need refinement
            String text = org.docx4j.TextUtils.getText(body); // Use TextUtils
            logger.debug("Successfully extracted text from DOCX");
            return text != null ? text : "";
        } catch (Exception e) {
            logger.error("Error extracting text from DOCX: {}", e.getMessage(), e);
            throw new Exception("Failed to parse DOCX content: " + e.getMessage(), e);
        }
    }
}