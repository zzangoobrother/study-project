package sample.cafekiosk.learning;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GuavaLearningTest {

    @DisplayName("주어진 갯수만큼 List를 파티셔닝한다.")
    @Test
    void partitionLearningTest1() {
        // given
        List<Integer> inteagers = List.of(1, 2, 3, 4, 5, 6);

        // when
        List<List<Integer>> partition = Lists.partition(inteagers, 3);

        // then
        assertThat(partition).hasSize(2)
                .isEqualTo(List.of(
                        List.of(1, 2, 3),
                        List.of(4, 5, 6)
                ));
    }

    @DisplayName("주어진 갯수만큼 List를 파티셔닝한다.")
    @Test
    void partitionLearningTest2() {
        // given
        List<Integer> inteagers = List.of(1, 2, 3, 4, 5, 6);

        // when
        List<List<Integer>> partition = Lists.partition(inteagers, 4);

        // then
        assertThat(partition).hasSize(2)
                .isEqualTo(List.of(
                        List.of(1, 2, 3, 4),
                        List.of(5, 6)
                ));
    }

    @DisplayName("멀티맵 기능 확인")
    @Test
    void multiMapLearningTest() {
        // given
        Multimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("커피", "아메리카노");
        multimap.put("커피", "카페라떼");
        multimap.put("커피", "카푸티치");
        multimap.put("베이커리", "크루아상");
        multimap.put("베이커리", "식빵");

        // when
        Collection<String> Strings = multimap.get("커피");

        // then
        assertThat(Strings).hasSize(3)
                .isEqualTo(List.of("아메리카노", "카페라떼", "카푸티치"));
    }

    @DisplayName("멀티맵 기능 확인")
    @TestFactory
    Collection<DynamicTest> multiMapLearningTest2() {
        // given
        Multimap<String, String> multimap = ArrayListMultimap.create();
        multimap.put("커피", "아메리카노");
        multimap.put("커피", "카페라떼");
        multimap.put("커피", "카푸치노");
        multimap.put("베이커리", "크루아상");
        multimap.put("베이커리", "식빵");

        return List.of(
                DynamicTest.dynamicTest("1개 value 삭제", () -> {
                    multimap.remove("커피", "카푸치노");

                    Collection<String> result = multimap.get("커피");

                    assertThat(result).hasSize(2)
                            .isEqualTo(List.of("아메리카노", "카페라떼"));
                }),
                DynamicTest.dynamicTest("1개 key 삭제", () -> {
                    multimap.removeAll("커피");

                    Collection<String> result = multimap.get("커피");

                    assertThat(result).isEmpty();
                })
        );
    }
}
