package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.JsonElement;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.Fluid;
import java.util.List;

/** Optional native fluid/chemical sprites. No recipe-viewer API or fabricated item is involved. */
final class NativeRecipeVisuals {
    record Icon(KineticTexture texture,int tint,int frameWidth,String id) {
        void draw(KineticGraphics g,int x,int y) {
            g.push();
            try {g.translate(x,y);g.scale(16f/frameWidth,16f/frameWidth);g.texture(texture,0,0,0,0,frameWidth,frameWidth,tint);}
            finally {g.pop();}
        }
    }
    private NativeRecipeVisuals() { }
    static Icon read(JsonElement value) {
        if(!value.isJsonObject())return null;
        var object=value.getAsJsonObject();
        for(String key:List.of("gas","slurry","infuse_type","infuseType","pigment","chemical")) {
            var id=object.get(key);
            if(id!=null && id.isJsonPrimitive()) {
                var parsed=KineticResourceIds.tryParse(id.getAsString());if(parsed==null)continue;
                for(String registry:List.of("chemical","gas","slurry","infuse_type","pigment")) {
                    var view=KineticRegistries.custom(KineticResourceIds.of("mekanism",registry));
                    if(view.isEmpty() || !view.get().contains(parsed))continue;
                    var chemical=view.get().get(parsed);
                    try {return texture(chemical.getClass().getMethod("getIcon").invoke(chemical).toString(),
                            0xFF000000 | (int)chemical.getClass().getMethod("getTint").invoke(chemical),parsed.toString());}
                    catch(ReflectiveOperationException unavailable){ }
                }
            }
        }
        JsonElement id=object.get("FluidName");
        if(id==null)id=object.get("fluid");
        if(id!=null && id.isJsonObject())id=id.getAsJsonObject().get("id");
        if(id==null && (object.has("amount") || object.has("Amount")))id=object.get("id");
        if(id==null || !id.isJsonPrimitive())return null;
        var parsed=KineticResourceIds.tryParse(id.getAsString());
        if(parsed==null || !KineticRegistries.fluids().contains(parsed))return null;
        var fluid=KineticRegistries.fluids().get(parsed);
        //? if >=26.1 {
        /*// Fluid appearance moved from loader extensions to native baked fluid models.
        var state=fluid.defaultFluidState();
        var client=Minecraft.getInstance();
        var model=client.getModelManager().getFluidStateModelSet().get(state);
        var source=model.tintSource();
        int tint=source==null?0xFFFFFFFF:client.level!=null && client.player!=null
                ?source.colorInWorld(state.createLegacyBlock(),client.level,client.player.blockPosition())
                :source.color(state.createLegacyBlock());
        return texture(model.stillMaterial().sprite().contents().name().toString(),
                tint,parsed.toString());
        *///?} else {
        for(String prefix:List.of("net.minecraftforge","net.neoforged.neoforge"))try {
            var extensions=Class.forName(prefix+".client.extensions.common.IClientFluidTypeExtensions");
            var extension=extensions.getMethod("of",Fluid.class).invoke(null,fluid);
            var sprite=extensions.getMethod("getStillTexture").invoke(extension);
            if(sprite!=null)return texture(sprite.toString(),(int)extensions.getMethod("getTintColor").invoke(extension),parsed.toString());
        }catch(ReflectiveOperationException unavailable){ }
        return null;
        //?}
    }
    private static Icon texture(String sprite,int tint,String id) {
        var location=KineticResourceIds.tryParse(sprite);if(location==null)return null;
        String path="textures/"+location.getPath()+".png";
        var resource=Minecraft.getInstance().getResourceManager().getResource(KineticResourceIds.of(location.getNamespace(),path));
        if(resource.isEmpty())return null;
        try(var stream=resource.get().open()) {
            var image=javax.imageio.ImageIO.read(stream);if(image==null || image.getHeight()<image.getWidth())return null;
            return new Icon(KineticTexture.of(location.getNamespace(),path,image.getWidth(),image.getHeight()),tint,image.getWidth(),id);
        }catch(java.io.IOException unavailable){return null;}
    }
}
