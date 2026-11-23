package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeistelPermutationTest {

    private static final long N = FeistelPermutation.SHORT_KEY_SPACE;

    @Test
    void smallDomainIsBijection() {
        FeistelPermutation permutation = new FeistelPermutation(3844, 12, 42L);
        Set<Long> results = new HashSet<>();

        for (long x = 0; x < 3844; x++) {
            long y = permutation.permute(x);
            assertThat(y).isBetween(0L, 3843L);
            results.add(y);
            assertThat(permutation.inverse(y)).isEqualTo(x);
        }

        assertThat(results).hasSize(3844);
    }

    @Test
    void shortKeySpaceRoundTrip() {
        FeistelPermutation permutation = FeistelPermutation.forShortKey(42L);
        List<Long> samples = new ArrayList<>(List.of(0L, 1L, N - 1, N / 2));
        Random random = new Random(7);
        for (int i = 0; i < 10_000; i++) {
            samples.add(Math.floorMod(random.nextLong(), N));
        }

        for (long x : samples) {
            long y = permutation.permute(x);
            assertThat(y).isGreaterThanOrEqualTo(0L).isLessThan(N);
            assertThat(permutation.inverse(y)).isEqualTo(x);
        }
    }

    @Test
    void consecutiveInputsNeverCollide() {
        FeistelPermutation permutation = FeistelPermutation.forShortKey(42L);
        Set<Long> results = new HashSet<>();

        for (long x = 0; x < 300_000; x++) {
            results.add(permutation.permute(x));
        }

        assertThat(results).hasSize(300_000);
    }

    @Test
    void consecutiveInputsAreScrambled() {
        FeistelPermutation permutation = FeistelPermutation.forShortKey(42L);
        int adjacent = 0;

        for (long i = 0; i < 999; i++) {
            if (Math.abs(permutation.permute(i + 1) - permutation.permute(i)) == 1) {
                adjacent++;
            }
        }

        assertThat(adjacent).isLessThan(10);
    }

    @Test
    void differentSecretGivesDifferentOrder() {
        FeistelPermutation first = FeistelPermutation.forShortKey(1L);
        FeistelPermutation second = FeistelPermutation.forShortKey(2L);
        List<Long> firstOrder = new ArrayList<>();
        List<Long> secondOrder = new ArrayList<>();

        for (long x = 0; x < 100; x++) {
            firstOrder.add(first.permute(x));
            secondOrder.add(second.permute(x));
        }

        assertThat(firstOrder).isNotEqualTo(secondOrder);
    }

    @Test
    void rejectsOutOfRangeInput() {
        FeistelPermutation permutation = FeistelPermutation.forShortKey(42L);

        assertThatThrownBy(() -> permutation.permute(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> permutation.permute(N)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> permutation.inverse(N)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThatThrownBy(() -> new FeistelPermutation(3844, 13, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeistelPermutation(5000, 12, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
