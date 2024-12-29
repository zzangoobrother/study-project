package com.example.service;

import com.example.cassandra.entity.Product;
import com.example.cassandra.repository.ProductRepository;
import com.example.dto.ProductTagsDto;
import com.example.feign.SearchClient;
import com.example.mysql.entity.SellerProduct;
import com.example.mysql.repository.SellerProductRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class CatalogService {

    private final ProductRepository productRepository;
    private final SellerProductRepository sellerProductRepository;
    private final SearchClient searchClient;

    public CatalogService(ProductRepository productRepository, SellerProductRepository sellerProductRepository, SearchClient searchClient) {
        this.productRepository = productRepository;
        this.sellerProductRepository = sellerProductRepository;
        this.searchClient = searchClient;
    }

    public Product registerProduct(Long sellerId, String name, String description, Long price, Long stockCount, List<String> tags) {
        SellerProduct sellerProduct = new SellerProduct(sellerId);
        sellerProductRepository.save(sellerProduct);

        Product product = new Product(sellerProduct.getId(), sellerId, name, description, price, stockCount, tags);

        ProductTagsDto dto = new ProductTagsDto(product.getId(), tags);
        searchClient.addTagCache(dto);

        return productRepository.save(product);
    }

    public void deleteProduct(Long productId) {
        Optional<Product> product = productRepository.findById(productId);
        if (product.isPresent()) {
            ProductTagsDto dto = new ProductTagsDto(productId, product.get().getTags());
            searchClient.removeTagCache(dto);
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
