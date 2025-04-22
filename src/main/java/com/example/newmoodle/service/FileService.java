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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    // ... (Existing fields and methods: logger, s3Client, s3Presigner, bucketName, etc.) ...
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-duration:15}")
    private long presignedUrlDurationMinutes;

    private static final Map<String, String> EXTENSION_TO_MIME_TYPE = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "webp", "image/webp",
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "txt", "text/plain"
            // Add other types if you support them
    );

    // Define sets for quick type checking
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private static final Set<String> TEXT_EXTRACTABLE_EXTENSIONS = Set.of("pdf", "docx", "txt");


    // --- uploadFile, downloadFileAsStream, deleteFile, loadFileAsResource, downloadFileAsBytes remain the same ---
    public String uploadFile(MultipartFile file) throws IOException {
        // ... (implementation unchanged)
        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String safeOriginalName = originalFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String key = UUID.randomUUID().toString() + "_" + safeOriginalName;


        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
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
            throw e;
        }
    }

    public ResponseInputStream<GetObjectResponse> downloadFileAsStream(String key) {
        // ... (implementation unchanged)
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
        // ... (implementation unchanged)
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            logger.info("Deleted object from R2/S3 with key: {}", key);
        } catch (S3Exception e) {
            logger.error("S3 Error deleting file with key {}: {}", key, e.awsErrorDetails().errorMessage(), e);
        }
    }

    public Resource loadFileAsResource(String key) {
        // ... (implementation unchanged)
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


    public byte[] downloadFileAsBytes(String key) throws IOException {
        // ... (implementation unchanged)
        logger.info("Attempting to download file as bytes for key: {}", key);
        try (ResponseInputStream<GetObjectResponse> s3ObjectStream = downloadFileAsStream(key)) {
            if (s3ObjectStream == null) {
                throw new IOException("File not found or could not be accessed in S3/R2 with key: " + key);
            }
            byte[] content = s3ObjectStream.readAllBytes();
            logger.info("Successfully downloaded {} bytes for key: {}", content.length, key);
            return content;
        } catch (IOException e) {
            logger.error("IOException while reading bytes for key {}: {}", key, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error downloading file as bytes for key {}: {}", key, e.getMessage(), e);
            throw new IOException("Failed to download file as bytes for key " + key + ": " + e.getMessage(), e);
        }
    }

    public String getMimeType(String key) {
        // ... (implementation unchanged)
        if (key == null || !key.contains(".")) {
            return "application/octet-stream";
        }
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return EXTENSION_TO_MIME_TYPE.getOrDefault(ext, "application/octet-stream");
    }

    // --- Text Extraction Logic ---
    public String extractText(String key) throws Exception {
        // ... (implementation largely unchanged, but uses isTextExtractableFile)
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("R2/S3 object key cannot be null or empty");
        }

        String fileExtension = getExtension(key);
        logger.info("Attempting text extraction for key: {}, detected extension: '{}'", key, fileExtension);

        if (!isTextExtractableFileExtension(fileExtension)) {
            if (isImageFileExtension(fileExtension)) {
                logger.warn("Text extraction from images ('{}') is not supported by this method.", fileExtension);
                throw new IllegalArgumentException("Text extraction not supported for image type: " + fileExtension + ". Use direct image processing.");
            } else {
                logger.warn("Unsupported file type for text extraction: {}", fileExtension);
                throw new IllegalArgumentException("Unsupported file type for text extraction: " + fileExtension);
            }
        }

        try (InputStream inputStream = downloadFileAsStream(key)) {
            if (inputStream == null) {
                logger.error("Could not get input stream for key '{}'. File might not exist or S3 error occurred.", key);
                throw new IOException("Failed to get file stream from R2/S3 for key: " + key);
            }

            return switch (fileExtension) {
                case "pdf" -> extractTextFromPdf(inputStream);
                case "docx" -> extractTextFromDocx(inputStream);
                case "txt" -> new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                default -> { // Should not be reached due to check above, but as failsafe
                    logger.error("Reached default case in extractText unexpectedly for extension: {}", fileExtension);
                    throw new IllegalArgumentException("File type check failed for text extraction: " + fileExtension);
                }
            };
        } catch (IOException ioException) {
            logger.error("IOException during text extraction process for key {}: {}", key, ioException.getMessage(), ioException);
            throw ioException;
        } catch (IllegalArgumentException illegalArgEx) {
            logger.warn("IllegalArgumentException during text extraction for key {}: {}", key, illegalArgEx.getMessage());
            throw illegalArgEx;
        } catch (Exception e) {
            logger.error("Unexpected error during text extraction for key {}: {}", key, e.getMessage(), e);
            throw new Exception("Failed to extract text from file (key: " + key + "): " + e.getMessage(), e);
        }
    }
    // --- extractTextFromPdf, extractTextFromDocx remain the same ---
    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        // ... (implementation unchanged)
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            if (document.isEncrypted()) {
                logger.warn("Attempting to extract text from an encrypted PDF. Result may be empty or partial.");
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
        // ... (implementation unchanged)
        try {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(inputStream);
            MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
            org.docx4j.wml.Document wmlDocumentEl = documentPart.getContents();
            javax.xml.bind.JAXBElement<?> body = (javax.xml.bind.JAXBElement<?>) wmlDocumentEl.getBody().getContent().get(0);
            String text = org.docx4j.TextUtils.getText(body);
            logger.debug("Successfully extracted text from DOCX");
            return text != null ? text : "";
        } catch (Exception e) {
            logger.error("Error extracting text from DOCX: {}", e.getMessage(), e);
            throw new Exception("Failed to parse DOCX content: " + e.getMessage(), e);
        }
    }

    // --- Helper methods for file type checking ---

    /**
     * Extracts the file extension from a key.
     * @param key The S3 object key.
     * @return The lower-case file extension without the dot, or empty string if no extension.
     */
    public String getExtension(String key) {
        if (key == null || !key.contains(".")) {
            return "";
        }
        return key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Checks if the file key likely represents an image based on its extension.
     * Made public for use by other services.
     * @param key The S3 object key.
     * @return true if the extension is a known image type, false otherwise.
     */
    public boolean isImageFile(String key) {
        return isImageFileExtension(getExtension(key));
    }

    /**
     * Checks if the file key likely represents a file from which text can be extracted.
     * @param key The S3 object key.
     * @return true if the extension is a known text-extractable type, false otherwise.
     */
    public boolean isTextExtractableFile(String key) {
        return isTextExtractableFileExtension(getExtension(key));
    }

    // Private helpers using extensions
    private boolean isImageFileExtension(String ext) {
        return IMAGE_EXTENSIONS.contains(ext);
    }

    private boolean isTextExtractableFileExtension(String ext) {
        return TEXT_EXTRACTABLE_EXTENSIONS.contains(ext);
    }
}