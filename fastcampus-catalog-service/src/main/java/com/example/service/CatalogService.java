package com.example.service;

import com.example.cassandra.entity.Product;
import com.example.cassandra.repository.ProductRepository;
import com.example.dto.ProductTagsDto;
import com.example.mysql.entity.SellerProduct;
import com.example.mysql.repository.SellerProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final SellerProductRepository sellerProductRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public CatalogService(ProductRepository productRepository, SellerProductRepository sellerProductRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.productRepository = productRepository;
        this.sellerProductRepository = sellerProductRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Product registerProduct(Long sellerId, String name, String description, Long price, Long stockCount, List<String> tags) throws JsonProcessingException {
        SellerProduct sellerProduct = new SellerProduct(sellerId);
        sellerProductRepository.save(sellerProduct);

        Product product = new Product(sellerProduct.getId(), sellerId, name, description, price, stockCount, tags);

        ProductTagsDto dto = new ProductTagsDto(product.getId(), tags);
        ObjectMapper mapper = new ObjectMapper();
        kafkaTemplate.send("product_tags_added", mapper.writeValueAsString(dto));

        return productRepository.save(product);
    }

    public void deleteProduct(Long productId) throws JsonProcessingException {
        Optional<Product> product = productRepository.findById(productId);
        if (product.isPresent()) {
            ProductTagsDto dto = new ProductTagsDto(productId, product.get().getTags());
            ObjectMapper mapper = new ObjectMapper();
            kafkaTemplate.send("product_tags_removed", mapper.writeValueAsString(dto));
        }

        productRepository.deleteById(productId);
        sellerProductRepository.deleteById(productId);
    }

    public List<Product> getProductsBySellerId(Long sellerId) {
        List<SellerProduct> sellerProducts = sellerProductRepository.findBySellerId(sellerId);

        List<Product> products = new ArrayList<>();
        for (SellerProduct sellerProduct : sellerProducts) {
            Optional<Product> findProduct = productRepository.findById(sellerProduct.getId());
            findProduct.ifPresent(products::add);
        }

        return products;
    }

    public Product getProductById(Long productId) {
        return productRepository.findById(productId).orElseThrow();
    }

    public Product decreaseStockCount(Long productId, Long decreaseCount) {
        Product product = productRepository.findById(productId).orElseThrow();
        product.decreaseStockCount(decreaseCount);

        return productRepository.save(product);
    }
}
