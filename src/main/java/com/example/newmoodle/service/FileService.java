package com.example.newmoodle.service;


import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {
    @Value("${file.upload.directory}")
    private String uploadDirectory;

    public String saveFile(MultipartFile file) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String fileName = originalFileName;
        int counter = 1;

        Path filePath = Paths.get(uploadDirectory, fileName);
        while (Files.exists(filePath)) {
            String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
            String extension = originalFileName.substring(originalFileName.lastIndexOf('.'));
            fileName = baseName + "_" + counter + extension;
            filePath = Paths.get(uploadDirectory, fileName);
            counter++;
        }

        Files.copy(file.getInputStream(), filePath);
        return fileName;
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = Paths.get(uploadDirectory, fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                return null;
            }
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    public String extractText(String fileName) throws Exception {

        Resource resource = loadFileAsResource(fileName);
        String originalFilename = resource.getFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

        try (InputStream inputStream = resource.getInputStream()) {
            switch (fileExtension) {
                case "pdf":
                    return extractTextFromPdf(inputStream);
                case "docx":
                    return extractTextFromDocx(inputStream);
                case "txt": //for the purpose of demonstration
                    return new String(inputStream.readAllBytes());
                case "jpg":
                case "png":
                case "jpeg":
                    return extractTextFromImage(resource);
                default:
                    throw new IllegalArgumentException("Unsupported file type: " + fileExtension);
            }
        }
    }

    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document =  Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(document);
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws Exception {
        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(inputStream);
        MainDocumentPart documentPart = wordMLPackage.getMainDocumentPart();
        return documentPart.getContent().toString();
    }

    private String extractTextFromImage(Resource imageResource) throws IOException {
        Logger logger = LoggerFactory.getLogger(FeedbackService.class); //or your class
        List<AnnotateImageRequest> requests = new ArrayList<>();

        // Get an InputStream from the Resource
        try (InputStream inputStream = imageResource.getInputStream()) {
            ByteString imgBytes = ByteString.copyFrom(inputStream.readAllBytes());

            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build();
            AnnotateImageRequest request =
                    AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
            requests.add(request);

            // Uses Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS environment variable)
            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                BatchAnnotateImagesResponse response = client.batchAnnotateImages(requests);
                List<AnnotateImageResponse> responses = response.getResponsesList();

                StringBuilder text = new StringBuilder();
                for (AnnotateImageResponse res : responses) {
                    if (res.hasError()) {
                        logger.error("Google Cloud Vision API Error: {}", res.getError().getMessage());
                        return "Error: " + res.getError().getMessage(); // User-friendly message
                    }

                    // Extract the full text annotation
                    text.append(res.getFullTextAnnotation().getText());
                }

                if (text.length() == 0) {
                    return "No text found in image."; // Handle the case where no text is detected
                }
                return text.toString();
            }
        } catch (Exception e) {
            logger.error("Failed to extract text from image using Google Cloud Vision API", e);
            return "Failed to extract text: " + e.getMessage(); // User-friendly message for general exceptions
        }
    }
    public void renameFile(String oldFileName, String newFileName) throws IOException {
        Path oldFilePath = Paths.get(uploadDirectory, oldFileName);
        Path newFilePath = Paths.get(uploadDirectory, newFileName);
        Files.move(oldFilePath, newFilePath);
    }

    public void deleteFile(String fileName) throws IOException {
        Path filePath = Paths.get(uploadDirectory, fileName);
        Files.delete(filePath);
    }
}
