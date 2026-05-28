package com.yodawife.easyll.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserWordHistoryTest {

    @Test
    void emptyHistoryHasNoEntries() {
        UserWordHistory h = new UserWordHistory();
        assertThat(h.entries()).isEmpty();
        assertThat(h.encoded()).isEmpty();
    }

    @Test
    void appendAddsEntries() {
        UserWordHistory h = new UserWordHistory();
        h.append("S");
        h.append("F");
        assertThat(h.entries()).containsExactly("S", "F");
        assertThat(h.encoded()).isEqualTo("S,F");
    }

    @Test
    void fifoCapAt12() {
        UserWordHistory h = new UserWordHistory();
        for (int i = 0; i < 14; i++) {
            h.append("S");
        }
        assertThat(h.entries()).hasSize(12);
    }

    @Test
    void fifoEvictsOldestFirst() {
        UserWordHistory h = new UserWordHistory(List.of("F", "F", "F", "F", "F", "F", "F", "F", "F", "F", "F", "F")); // 12 F's
        h.append("S"); // should evict first F
        assertThat(h.entries()).hasSize(12);
        assertThat(h.entries().getLast()).isEqualTo("S");
        assertThat(h.entries().getFirst()).isEqualTo("F");
    }

    @Test
    void invalidSymbolThrows() {
        UserWordHistory h = new UserWordHistory();
        assertThatThrownBy(() -> h.append("X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X");
    }

    @Test
    void initialEntriesLoadedCorrectly() {
        UserWordHistory h = new UserWordHistory(List.of("S", "F", "S"));
        assertThat(h.entries()).containsExactly("S", "F", "S");
    }
}
