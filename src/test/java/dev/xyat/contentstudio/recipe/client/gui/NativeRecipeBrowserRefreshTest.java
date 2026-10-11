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
        deliver(browser,list("list","[{\"id\":\"pack:existing\",\"edited\":false,\"preview\":{\"count\":2}}]",1));
        visible.set(false); // The editor is current; the browser remains attached behind it.
        deliver(browser,list("list","[{\"id\":\"pack:existing\",\"edited\":true,\"preview\":{\"count\":4}}]",1));
        visible.set(true); // Back to the same, already-started browser.
        var row=rows(browser).get(0).getAsJsonObject();
        assertTrue(row.get("edited").getAsBoolean());
        assertEquals(4,row.getAsJsonObject("preview").get("count").getAsInt());
    }
    @Test void createThenBackShowsTheNewSavedRecipeAndTotal() throws Exception {
        var browser=NativeRecipeBrowserPage.saved();var visible=attach(browser);
        deliver(browser,list("saved","[{\"id\":\"pack:existing\"}]",1));
        visible.set(false);
        deliver(browser,list("saved","[{\"id\":\"pack:existing\"},{\"id\":\"pack:new\"}]",2));
        visible.set(true);
        assertEquals(2,rows(browser).size());assertEquals("pack:new",rows(browser).get(1).getAsJsonObject().get("id").getAsString());
        assertEquals(2,field(browser,"total"));
    }
    @Test void deleteThenBackRemovesTheSavedRecipeAndUpdatesPagination() throws Exception {
        var browser=NativeRecipeBrowserPage.saved();var visible=attach(browser);
        var initial=list("saved","[{\"id\":\"pack:deleted\"}]",97);initial.addProperty("page",1);initial.addProperty("pages",2);
        deliver(browser,initial);visible.set(false);
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
        deliver(browser,list("list","[{\"id\":\"pack:before\"}]",1));
        var host=KineticPage.class.getDeclaredField("host");host.setAccessible(true);host.set(browser,null);
        deliver(browser,list("list","[{\"id\":\"pack:late\"}]",1));
        assertEquals("pack:before",rows(browser).get(0).getAsJsonObject().get("id").getAsString());
    }
    private static AtomicBoolean attach(NativeRecipeBrowserPage browser) throws Exception {
        var visible=new AtomicBoolean(true);
        // Only the graphics host is substituted. Production response routing and browser state remain real.
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
    private static JsonObject list(String action,String rows,int total) {
        var body=new JsonObject();body.addProperty("action",action);body.add("mods",JsonParser.parseString("[\"minecraft\"]"));
        body.add("rows",JsonParser.parseString(rows));body.addProperty("page",0);body.addProperty("pages",1);body.addProperty("total",total);
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
