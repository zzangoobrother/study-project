package com.example.pricecompareredis.service;

import java.util.Set;

public interface LowestPriceService {

    Set getZSetValue(String key);
}
