package reservation.persistence;

import reservation.domain.Movie;

public interface MovieDAO {

    Movie selectMovie(Long movieId);

    void insert(Movie movie);
}
