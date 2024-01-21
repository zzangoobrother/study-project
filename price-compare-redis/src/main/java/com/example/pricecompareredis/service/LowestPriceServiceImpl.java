package com.example.pricecompareredis.service;

import com.example.pricecompareredis.vo.Keyword;
import com.example.pricecompareredis.vo.Product;
import com.example.pricecompareredis.vo.ProductGroup;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class LowestPriceServiceImpl implements LowestPriceService {

    private final RedisTemplate myProdPriceRedis;

    public LowestPriceServiceImpl(RedisTemplate myProdPriceRedis) {
        this.myProdPriceRedis = myProdPriceRedis;
    }

    @Override
    public Set getZSetValue(String key) {
        Set myTempSet;
        myTempSet = myProdPriceRedis.opsForZSet().rangeWithScores(key, 0, 9);
        return myTempSet;
    }

    @Override
    public int setNewProduct(Product newProduct) {
        int rank;
        myProdPriceRedis.opsForZSet().add(newProduct, newProduct.getProductGroupId(), newProduct.getPrice());
        rank = myProdPriceRedis.opsForZSet().rank(newProduct, newProduct.getProductGroupId()).intValue();
        return rank;
    }

    @Override
    public int setNewProductGroup(ProductGroup productGroup) {
        List<Product> products = productGroup.getProducts();
        String productId = products.get(0).getProductId();
        int price = products.get(0).getPrice();
        myProdPriceRedis.opsForZSet().add(productGroup.getProductGroupId(), productId, price);
        return myProdPriceRedis.opsForZSet().zCard(productGroup.getProductGroupId()).intValue();
    }

    @Override
    public int setNewProductGroupToKeyword(String keyword, String productGroupId, double score) {
        myProdPriceRedis.opsForZSet().add(keyword, productGroupId, score);
        return myProdPriceRedis.opsForZSet().rank(keyword, productGroupId).intValue();
    }

    @Override
    public Keyword getLowestPriceProductByKeyword(String keyword) {
        Keyword result = new Keyword();
        List<ProductGroup> productGroups = getProductGroupUsingKeyword(keyword);

        result.setKeyword(keyword);
        result.setProductGroups(productGroups);
        return result;
    }

    private List<ProductGroup> getProductGroupUsingKeyword(String keyword) {
        List<ProductGroup> result = new ArrayList<>();
        List<String> productGroupIds = List.copyOf(myProdPriceRedis.opsForZSet().reverseRange(keyword, 0, 9));

        List<Product> products = new ArrayList<>();
        for (String productGroupId : productGroupIds) {
            ProductGroup productGroup = new ProductGroup();

            Set productPriceList = myProdPriceRedis.opsForZSet().rangeWithScores(productGroupId, 0, 9);
            Iterator<Object> productPrice = productPriceList.iterator();

            while (productPrice.hasNext()) {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, String> productPriceMap = objectMapper.convertValue(productPrice.next(), Map.class);

                Product product = new Product();
                product.setProductId(productPriceMap.get("value"));
                product.setPrice(Integer.parseInt(productPriceMap.get("score")));

                products.add(product);
            }

            productGroup.setProductGroupId(productGroupId);
            productGroup.setProducts(products);
            result.add(productGroup);
        }

        return result;
    }
}
