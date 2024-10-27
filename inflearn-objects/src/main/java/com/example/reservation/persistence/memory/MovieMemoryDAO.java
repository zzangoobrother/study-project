package com.example.reservation.persistence.memory;

import com.example.reservation.domain.Movie;
import com.example.reservation.service.MovieDAO;

public class MovieMemoryDAO extends InMemoryDAO<Movie> implements MovieDAO {
    @Override
    public Movie selectMovie(Long movieId) {
        return findOne(movie -> movie.getId().equals(movieId));
    }
}
