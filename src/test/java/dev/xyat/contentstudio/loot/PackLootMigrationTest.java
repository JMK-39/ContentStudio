package dev.xyat.contentstudio.loot;

import com.google.gson.JsonParser;
import dev.xyat.contentstudiovalidation.PackLootMigration;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PackLootMigrationTest {
    @Test void nativeSnapshotPreservesEveryRepresentableSpellFilterField() {
        assertEquals(JsonParser.parseString("{}"),PackLootMigration.spellFilterJson(null,List.of(),false));
        assertEquals(JsonParser.parseString("{\"school\":\"example:school\",\"force\":true}"),
                PackLootMigration.spellFilterJson("example:school",List.of(),true));
        assertThrows(IllegalStateException.class,()->PackLootMigration.spellFilterJson("example:school",List.of("example:spell"),true));
        assertEquals(JsonParser.parseString("{\"spells\":[\"example:first\",\"example:second\"],\"force\":true}"),
                PackLootMigration.spellFilterJson(null,List.of("example:first","example:second"),true));
        assertEquals(JsonParser.parseString("{\"spells\":[],\"force\":true}"),PackLootMigration.spellFilterJson(null,List.of(),true));
    }
}
