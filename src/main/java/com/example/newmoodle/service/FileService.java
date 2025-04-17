package com.example.newmoodle.service;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
// lombok.RequiredArgsConstructor убран, т.к. нужен @Autowired конструктор
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // Используем Autowired
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner; // Импорт Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest; // Импорты для presign
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
// Убраны неиспользуемые импорты java.nio.file.* и java.io.File

@Service
@RequiredArgsConstructor
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class); // Инициализация логгера

    private final S3Client s3Client;
    private final S3Presigner s3Presigner; // Добавлено поле Presigner

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-duration:15}") // Значение по умолчанию 15 минут
    private long presignedUrlDurationMinutes;


    /**
     * Загружает файл в R2/S3.
     * @param file Файл для загрузки.
     * @return Ключ объекта в R2/S3.
     * @throws IOException Ошибка ввода-вывода при загрузке.
     */
    public String uploadFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String key = UUID.randomUUID().toString() + "_" + originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");

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

    /**
     * Возвращает InputStream для объекта из R2/S3.
     * Вызывающий код ОБЯЗАН закрыть этот InputStream!
     * @param key Ключ объекта в R2/S3.
     * @return ResponseInputStream или null при ошибке/не нахождении.
     */
    public ResponseInputStream<GetObjectResponse> downloadFileAsStream(String key) {
        // Переименован из downloadFile для ясности
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

    /**
     * Удаляет файл (объект) из R2/S3.
     * @param key Ключ объекта для удаления.
     */
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
            // Решите, нужно ли пробрасывать исключение дальше
        }
    }

    /**
     * Загружает файл из R2/S3 как Resource, используя временный pre-signed URL.
     * Подходит для скачивания файлов через контроллеры.
     *
     * @param key Ключ объекта в R2/S3 (ранее был fileName).
     * @return Resource, указывающий на файл через временный URL, или null при ошибке.
     */
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

            // Создаем UrlResource БЕЗ ПРОВЕРКИ isReadable()
            // Контроллер, использующий этот ресурс, будет отвечать за его чтение.
            Resource resource = new UrlResource(url);
            return resource; // Просто возвращаем созданный ресурс

        } catch (S3Exception e) {
            logger.error("S3 Error generating pre-signed URL for key {}: {}", key, e.awsErrorDetails().errorMessage(), e);
            return null; // Ошибка при генерации URL
        } catch (Exception e) {
            // Лучше ловить более специфичные исключения, если возможно
            logger.error("Unexpected error generating pre-signed URL or Resource for key {}: {}", key, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Извлекает текст из файла, хранящегося в R2/S3, на основе его расширения.
     *
     * @param key Ключ объекта в R2/S3 (ранее был fileName).
     * @return Извлеченный текст.
     * @throws Exception Если извлечение текста не удалось или тип файла не поддерживается.
     */
    public String extractText(String key) throws Exception { // Параметр переименован в key
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("R2/S3 object key cannot be null or empty");
        }

        // Извлекаем расширение из ключа
        String fileExtension = key.contains(".") ? key.substring(key.lastIndexOf(".") + 1).toLowerCase() : "";
        logger.info("Attempting text extraction for key: {}, detected extension: '{}'", key, fileExtension);

        // Получаем InputStream напрямую из R2/S3, используя новый метод
        // Используем try-with-resources для автоматического закрытия потока
        try (InputStream inputStream = downloadFileAsStream(key)) {
            // Проверяем, что поток успешно получен
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
                    return new String(inputStream.readAllBytes());
                case "jpg":
                case "png":
                case "jpeg":
                    // Читаем поток в байты для Google Vision API
                    byte[] imageBytes = inputStream.readAllBytes();
                    // inputStream теперь закрыт, передаем байты
                    return extractTextFromImage(imageBytes, key); // Передаем байты и ключ
                default:
                    logger.warn("Unsupported file type for text extraction: {}", fileExtension);
                    throw new IllegalArgumentException("Unsupported file type for text extraction: " + fileExtension);
            }
        } catch (IOException ioException) {
            logger.error("IOException during text extraction for key {}: {}", key, ioException.getMessage(), ioException);
            throw ioException;
        } catch (Exception e) {
            logger.error("Failed to extract text for key {}: {}", key, e.getMessage(), e);
            throw new Exception("Failed to extract text from file (key: " + key + "): " + e.getMessage(), e);
        }
    }

    // --- Вспомогательные методы извлечения текста ---

    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
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
            String text = documentPart.getContent().toString();
            logger.debug("Successfully extracted text from DOCX");
            return text;
        } catch (Exception e) {
            logger.error("Error extracting text from DOCX: {}", e.getMessage(), e);
            throw new Exception("Failed to parse DOCX content: " + e.getMessage(), e);
        }
    }

    // Изменен для приема byte[] и key
    private String extractTextFromImage(byte[] imageBytes, String key) throws IOException {
        // Используем логгер класса
        List<AnnotateImageRequest> requests = new ArrayList<>();

        try {
            ByteString imgBytes = ByteString.copyFrom(imageBytes);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request =
                    AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
            requests.add(request);

            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                logger.debug("Sending request to Google Vision API for key: {}", key);
                BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
                List<AnnotateImageResponse> responses = response.getResponsesList();
                logger.debug("Received response from Google Vision API for key: {}", key);

                StringBuilder text = new StringBuilder();
                boolean hasErrors = false;
                for (AnnotateImageResponse res : responses) {
                    if (res.hasError()) {
                        logger.error("Google Cloud Vision API Error for key {}: {}", key, res.getError().getMessage());
                        text.append("Error processing image (Vision API): ").append(res.getError().getMessage()).append("\n");
                        hasErrors = true;
                    } else if (res.hasFullTextAnnotation()) {
                        text.append(res.getFullTextAnnotation().getText());
                        logger.debug("Successfully extracted text from image using Vision API for key: {}", key);
                    } else {
                        logger.debug("No text annotation found by Vision API for key: {}", key);
                    }
                }

                if (!hasErrors && text.length() == 0) {
                    logger.info("No text found in image for key: {}", key);
                    return "No text found in image.";
                }
                return text.toString();
            }
        } catch (Exception e) {
            logger.error("Failed to extract text from image using Google Cloud Vision API for key {}", key, e);
            throw new IOException("Failed to process image with Vision API (key: " + key + "): " + e.getMessage(), e);
        }
    }
}