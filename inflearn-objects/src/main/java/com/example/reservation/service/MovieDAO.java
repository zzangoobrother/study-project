package com.example.reservation.service;

import com.example.reservation.domain.Movie;

public interface MovieDAO {

    Movie selectMovie(Long movieId);

    void insert(Movie movie);
}
