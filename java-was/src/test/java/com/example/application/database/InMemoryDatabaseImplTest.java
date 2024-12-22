package com.example.application.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryDatabaseImplTest {

    private InMemoryDatabaseImpl<String> database;

    @BeforeEach
    void setUp() {
        database = new TestDatabase();
    }

    @Test
    void 일반적인_데이터_저장() {
        String data = "data1";

        long id = database.save(data);

        assertThat(database.findById(id)).isPresent()
                .contains(data);
    }

    @Test
    void Null_데이터_저장시_예외_발생() {
        String data = null;

        assertThatThrownBy(() -> database.save(data))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("데이터가 null입니다.");
    }

    @Test
    void 여러_데이터_저장() {
        String data1 = "data1";
        String data2 = "data2";

        long id1 = database.save(data1);
        long id2 = database.save(data2);

        assertThat(database.findById(id1)).isPresent()
                .contains(data1);
        assertThat(database.findById(id2)).isPresent()
                .contains(data2);
    }

    @Test
    void 빈_문자열_저장() {
        String data1 = "";

        long id1 = database.save(data1);

        assertThat(database.findById(id1)).isPresent()
                .contains(data1);
    }

    @Test
    void 중복_데이터_저장() {
        String data = "data";

        database.save(data);
        long id = database.save(data);

        assertThat(database.findById(id)).isPresent()
                .contains(data);
    }

    @Test
    void 일반적인_데이터_조회() {
        String data = "data";

        long id = database.save(data);

        Optional<String> retriebeData = database.findById(id);

        assertThat(retriebeData).isPresent()
                .contains(data);
    }

    @Test
    void 존재하지_않는_ID_조회시_비어있는_Optional_반환() {
        long nonExistentId = 999L;

        Optional<String> retriebeData = database.findById(nonExistentId);

        assertThat(retriebeData).isNotPresent();
    }

    @Test
    void 여러_데이터_조회() {
        String data1 = "data1";
        String data2 = "data2";

        long id1 = database.save(data1);
        long id2 = database.save(data2);

        Optional<String> retrieveData1 = database.findById(id1);
        Optional<String> retrieveData2 = database.findById(id2);

        assertThat(retrieveData1).isPresent()
                .contains(data1);
        assertThat(retrieveData2).isPresent()
                .contains(data2);
    }

    static class TestDatabase extends InMemoryDatabaseImpl<String> {
    }
}
