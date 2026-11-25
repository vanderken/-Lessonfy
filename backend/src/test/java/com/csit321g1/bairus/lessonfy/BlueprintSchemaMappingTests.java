package com.csit321g1.bairus.lessonfy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.csit321g1.bairus.lessonfy.entity.Collection;
import com.csit321g1.bairus.lessonfy.entity.Lesson;
import com.csit321g1.bairus.lessonfy.entity.Pin;
import com.csit321g1.bairus.lessonfy.entity.Section;
import com.csit321g1.bairus.lessonfy.entity.User;
import com.csit321g1.bairus.lessonfy.entity.UserSettings;

import jakarta.persistence.Column;
import jakarta.persistence.Table;

class BlueprintSchemaMappingTests {

    @Test
    void usersAndSettingsUseBlueprintTableNames() {
        assertEquals("core_user", User.class.getAnnotation(Table.class).name());
        assertEquals("core_usersettings", UserSettings.class.getAnnotation(Table.class).name());
    }

    @Test
    void lessonsAndSectionsUseBlueprintMapping() throws NoSuchFieldException {
        assertEquals("core_lesson", Lesson.class.getAnnotation(Table.class).name());
        assertEquals("core_panel", Section.class.getAnnotation(Table.class).name());

        Field orderField = Section.class.getDeclaredField("position");
        assertEquals("\"order\"", orderField.getAnnotation(Column.class).name());

        Field textField = Section.class.getDeclaredField("content");
        assertEquals("text", textField.getAnnotation(Column.class).name());
    }

    @Test
    void collectionsAndPinsUseBlueprintColumns() throws NoSuchFieldException {
        assertEquals("core_collection", Collection.class.getAnnotation(Table.class).name());
        assertEquals("core_pin", Pin.class.getAnnotation(Table.class).name());

        Field snippetField = Pin.class.getDeclaredField("selectedText");
        assertEquals("text_snippet", snippetField.getAnnotation(Column.class).name());

        Field collectionIdField = Pin.class.getDeclaredField("collection");
        assertEquals("collection_id", collectionIdField.getAnnotation(jakarta.persistence.JoinColumn.class).name());
    }
}
