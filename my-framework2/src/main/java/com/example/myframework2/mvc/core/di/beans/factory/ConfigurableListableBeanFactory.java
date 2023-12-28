package com.example.myframework2.mvc.core.di.beans.factory;

public interface ConfigurableListableBeanFactory extends BeanFactory {
    void preInstantiateSinglonetons();
}
