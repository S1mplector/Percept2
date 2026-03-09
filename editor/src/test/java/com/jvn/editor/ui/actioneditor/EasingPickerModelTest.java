package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EasingPickerModelTest {

    @Test
    void filtersNamedAndSpringCurves() {
        List<EasingPickerModel.Option> hero = EasingPickerModel.filter("hero");
        assertEquals(1, hero.size());
        assertEquals("Hero Pop", hero.get(0).label());

        List<EasingPickerModel.Option> spring = EasingPickerModel.filter("spring");
        assertTrue(spring.stream().anyMatch(option -> "Spring".equals(option.label())));
        assertTrue(spring.stream().anyMatch(option -> "Damped Spring".equals(option.label())));
    }
}
