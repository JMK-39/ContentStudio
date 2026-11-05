package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeBrowserRefreshTest {
    @Test void saveThenBackShowsTheUpdatedCountAndEditedMarker() throws Exception {
        var browser=new NativeRecipeBrowserPage();var visible=attach(browser);
        beginRefresh(browser);
        deliver(browser,list("list","[{\"id\":\"pack:existing\",\"edited\":false,\"preview\":{\"count\":2}}]",1));
        visible.set(false); // The editor is current; the browser remains attached behind it.
        beginRefresh(browser);
        deliver(browser,list("list","[{\"id\":\"pack:existing\",\"edited\":true,\"preview\":{\"count\":4}}]",1));
        visible.set(true); // Back to the same, already-started browser.
        var row=rows(browser).get(0).getAsJsonObject();
        assertTrue(row.get("edited").getAsBoolean());
        assertEquals(4,row.getAsJsonObject("preview").get("count").getAsInt());
    }
    @Test void createThenBackShowsTheNewSavedRecipeAndTotal() throws Exception {
        var browser=NativeRecipeBrowserPage.saved();var visible=attach(browser);
        beginRefresh(browser);
        deliver(browser,list("saved","[{\"id\":\"pack:existing\"}]",1));
        visible.set(false);
        beginRefresh(browser);
        deliver(browser,list("saved","[{\"id\":\"pack:existing\"},{\"id\":\"pack:new\"}]",2));
        visible.set(true);
        assertEquals(2,rows(browser).size());assertEquals("pack:new",rows(browser).get(1).getAsJsonObject().get("id").getAsString());
        assertEquals(2,field(browser,"total"));
    }
    @Test void deleteThenBackRemovesTheSavedRecipeAndClearsItsTotal() throws Exception {
        var browser=NativeRecipeBrowserPage.saved();var visible=attach(browser);
        beginRefresh(browser);
        var initial=list("saved","[{\"id\":\"pack:deleted\"}]",1);
        deliver(browser,initial);visible.set(false);
        assertEquals(1,rows(browser).size());
        beginRefresh(browser);
        deliver(browser,list("saved","[]",0));visible.set(true);
        assertTrue(rows(browser).isEmpty());assertEquals(0,field(browser,"total"));
        assertEquals(0,field(browser,"page"));assertEquals(1,field(browser,"pages"));
    }
    @Test void hiddenBrowserIgnoresLateOpenAndTemplateResponses() throws Exception {
        var browser=new NativeRecipeBrowserPage();var visible=attach(browser);visible.set(false);
        for(String action:java.util.List.of("open","template")) {
            // Entering the editor branch would consume the missing recipe and fail; a hidden browser must ignore it.
            var body=new JsonObject();body.addProperty("action",action);
            assertDoesNotThrow(()->deliver(browser,body));
        }
    }
    @Test void detachedBrowserIgnoresListResponses() throws Exception {
        var browser=new NativeRecipeBrowserPage();attach(browser);
        beginRefresh(browser);
        deliver(browser,list("list","[{\"id\":\"pack:before\"}]",1));
        beginRefresh(browser);
        var host=KineticPage.class.getDeclaredField("host");host.setAccessible(true);host.set(browser,null);
        deliver(browser,list("list","[{\"id\":\"pack:late\"}]",1));
        assertEquals("pack:before",rows(browser).get(0).getAsJsonObject().get("id").getAsString());
    }
    @Test void completedListIgnoresADuplicateResponseEvenWhileHidden() throws Exception {
        for(boolean shown:java.util.List.of(true,false)) {
            var browser=new NativeRecipeBrowserPage();var visible=attach(browser);visible.set(shown);beginRefresh(browser);
            deliver(browser,list("list","[{\"id\":\"pack:current\"}]",1));
            deliver(browser,list("list","[{\"id\":\"pack:duplicate\"}]",1));
            assertEquals("pack:current",rows(browser).get(0).getAsJsonObject().get("id").getAsString());
            assertFalse((boolean)field(browser,"listPending"));
            assertFalse((boolean)field(browser,"resumeList"),"completed hidden replies must not be re-requested on Back");
        }
    }
    private static AtomicBoolean attach(NativeRecipeBrowserPage browser) throws Exception {
        var visible=new AtomicBoolean(true);
        // The headless host substitutes rendering. beginRefresh supplies the transport's pending-request boundary;
        // real NativeRecipeClient correlation and browser response application stay under test. The runtime fixture
        // exercises actual refresh/open/Back packets and the resulting request-side lifecycle.
        var hostType=Class.forName("dev.xyat.kineticcore.internal.client.gui.page.PageHost");
        var host=Proxy.newProxyInstance(hostType.getClassLoader(),new Class<?>[]{hostType},(proxy,method,args)->switch(method.getName()) {
            case "isOpen" -> visible.get();
            case "rebuild" -> null;
            default -> throw new AssertionError("Unexpected GUI operation: "+method.getName());
        });
        var attachment=KineticPage.class.getDeclaredField("host");attachment.setAccessible(true);attachment.set(browser,host);
        var started=NativeRecipeBrowserPage.class.getDeclaredField("started");started.setAccessible(true);started.setBoolean(browser,true);
        return visible;
    }
    private static void beginRefresh(NativeRecipeBrowserPage browser) throws Exception {
        var page=NativeRecipeBrowserPage.class.getDeclaredField("page");page.setAccessible(true);page.setInt(browser,0);
        var pending=NativeRecipeBrowserPage.class.getDeclaredField("listPending");pending.setAccessible(true);pending.setBoolean(browser,true);
        var resume=NativeRecipeBrowserPage.class.getDeclaredField("resumeList");resume.setAccessible(true);resume.setBoolean(browser,!browser.isOpen());
    }
    private static JsonObject list(String action,String rows,int total) {
        var body=new JsonObject();body.addProperty("action",action);body.add("mods",JsonParser.parseString("[\"minecraft\"]"));
        var entries=JsonParser.parseString(rows).getAsJsonArray();
        for(var value:entries) {
            var row=value.getAsJsonObject();row.addProperty("type","minecraft:crafting_shapeless");row.addProperty("icon","minecraft:diamond");
            if(!row.has("edited"))row.addProperty("edited",false);
            if(row.has("preview"))row.getAsJsonObject("preview").addProperty("id","minecraft:diamond");
        }
        body.add("rows",entries);body.addProperty("page",0);body.addProperty("pages",1);body.addProperty("total",total);
        return body;
    }
    @SuppressWarnings("unchecked")
    private static void deliver(NativeRecipeBrowserPage browser,JsonObject body) throws Exception {
        var callback=(Consumer<NativeRecipeNetwork.Response>)field(browser,"callback");
        NativeRecipeClient.expect(41,callback);
        try {NativeRecipeClient.accept(new NativeRecipeNetwork.Response(41,true,body.toString()));}
        finally {NativeRecipeClient.cancel(callback);}
    }
    private static JsonArray rows(NativeRecipeBrowserPage browser) throws Exception {return (JsonArray)field(browser,"rows");}
    private static Object field(NativeRecipeBrowserPage browser,String name) throws Exception {
        var field=NativeRecipeBrowserPage.class.getDeclaredField(name);field.setAccessible(true);return field.get(browser);
    }
}
