package com.mmo.service;

import com.mmo.entity.Product;
import com.mmo.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;

    // CHỈ KHI BÁO @Value Ở ĐÂY - KHÔNG CÓ Ở CHỖ KHÁC
    @Value("${upload.path:uploads/products}")
    private String uploadPath;

    public List<Product> findAll() {
        return productRepository.findByIsDeleteFalse();
    }

    public List<Product> findBySellerId(Long sellerId) {
        return productRepository.findBySellerIdAndIsDeleteFalse(sellerId);
    }

    public List<Product> findBySellerAndCategory(Long sellerId, Long categoryId) {
        return productRepository.findBySellerIdAndCategoryIdAndIsDeleteFalse(sellerId, categoryId);
    }

    public List<Product> searchProductsBySeller(Long sellerId, String keyword) {
        return productRepository.findBySellerIdAndNameContainingIgnoreCaseAndIsDeleteFalse(sellerId, keyword);
    }

    public Product findById(Long id) {
        return productRepository.findByIdAndIsDeleteFalse(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }

    public void softDelete(Long id, Long deletedBy) {
        Product product = findById(id);
        product.setDelete(true);
        product.setDeletedBy(deletedBy);
        product.setUpdatedAt(new Date());
        productRepository.save(product);
    }

    // KHÔNG CÓ @Value Ở ĐÂY
    public String saveImage(MultipartFile file) throws IOException {
        // Lấy đường dẫn gốc project
        String projectDir = System.getProperty("user.dir");
        String fullUploadPath = projectDir + File.separator + uploadPath;

        Path uploadDir = Paths.get(fullUploadPath);

        System.out.println("Upload directory: " + uploadDir.toAbsolutePath());

        // Tạo thư mục nếu chưa có
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        // Tạo tên file unique
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(fileName);

        // Lưu file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/products/" + fileName;
    }

    public void deleteImage(String imagePath) {
        try {
            if (imagePath != null && !imagePath.isEmpty()) {
                String projectDir = System.getProperty("user.dir");
                Path path = Paths.get(projectDir + imagePath);
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}