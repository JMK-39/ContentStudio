package dev.xyat.contentstudiovalidation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/** Fixture-only native snapshot helpers; no conversion feature is shipped in the mod. */
public final class PackLootMigration {
    public static final String PREFIX="contentstudio_pack_migration/";
    private PackLootMigration() { }

    public static JsonObject spellFilterJson(String school,List<String> spells,boolean force) {
        if(school!=null&&!spells.isEmpty())throw new IllegalStateException("Native Iron filter cannot represent both school and explicit spells");
        JsonObject filter=new JsonObject();
        if(school!=null)filter.addProperty("school",school);
        else if(!spells.isEmpty()||force){JsonArray ids=new JsonArray();spells.forEach(ids::add);filter.add("spells",ids);}
        if(force)filter.addProperty("force",true);
        return filter;
    }
}
