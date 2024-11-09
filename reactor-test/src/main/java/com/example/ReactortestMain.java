package com.example;

public class ReactortestMain {

    public static void main(String[] args) {
        Publisher publisher = new Publisher();

//        publisher.startFlux()
//                .subscribe(System.out::println);

//        publisher.startMono()
//                .subscribe();

        publisher.startMono2()
                .subscribe();
    }
}
