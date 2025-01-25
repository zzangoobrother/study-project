package algorithm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {
        Pizza[] menu = {
                new Pizza("greek", 7, 5, 10),
                new Pizza("texas", 8, 9, 13),
                new Pizza("european", 5, 10, 13),
        };
        OrderItem[] order = {
                new OrderItem("texas", "Medium", 1),
                new OrderItem("european", "Small", 2)
        };
    }

    private static int sum1() {

    }

    static class Pizza {
        public String name;
        public int price_S;
        public int price_M;
        public int priceL;

        public Pizza(String name, int price_S, int price_M, int priceL) {
            this.name = name;
            this.price_S = price_S;
            this.price_M = price_M;
            this.priceL = priceL;
        }
    }

    static class OrderItem {
        public String name;
        public String size;
        public int quantity;

        public OrderItem(String name, String size, int quantity) {
            this.name = name;
            this.size = size;
            this.quantity = quantity;
        }
    }
}
