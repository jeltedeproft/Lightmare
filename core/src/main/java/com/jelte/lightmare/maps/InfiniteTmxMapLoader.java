package com.jelte.lightmare.maps;

import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer.Cell;
import com.badlogic.gdx.maps.tiled.TiledMapTileSets;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.XmlReader.Element;

/**
 * libGDX 1.14's TmxMapLoader doesn't support Tiled's "infinite" maps — it calls
 * data.getText().split(",") on the &lt;data&gt; element, which is empty for
 * infinite maps because the tile IDs live inside &lt;chunk&gt; children.
 * This subclass overrides loadTileLayer to detect chunks and stitch them into
 * a single TiledMapTileLayer sized to the chunks' bounding box. Falls back to
 * the parent implementation for finite layers.
 */
public class InfiniteTmxMapLoader extends TmxMapLoader {

    @Override
    protected void loadTileLayer(TiledMap map, MapLayers parentLayers, Element element) {
        if (!element.getName().equals("layer")) return;

        Element data = element.getChildByName("data");
        Array<Element> chunks = data == null ? null : data.getChildrenByName("chunk");

        if (chunks == null || chunks.size == 0) {
            // Finite map — let the standard path handle it.
            super.loadTileLayer(map, parentLayers, element);
            return;
        }

        String encoding = data.getAttribute("encoding", null);
        if (encoding == null || !encoding.equals("csv")) {
            throw new GdxRuntimeException("InfiniteTmxMapLoader only supports CSV-encoded chunk data; got: " + encoding);
        }

        // Bounding box of all chunks (in tile units; chunk coords can be negative).
        int minTileX = Integer.MAX_VALUE, minTileY = Integer.MAX_VALUE;
        int maxTileX = Integer.MIN_VALUE, maxTileY = Integer.MIN_VALUE;
        for (Element chunk : chunks) {
            int cx = chunk.getIntAttribute("x");
            int cy = chunk.getIntAttribute("y");
            int cw = chunk.getIntAttribute("width");
            int ch = chunk.getIntAttribute("height");
            if (cx < minTileX) minTileX = cx;
            if (cy < minTileY) minTileY = cy;
            if (cx + cw > maxTileX) maxTileX = cx + cw;
            if (cy + ch > maxTileY) maxTileY = cy + ch;
        }

        int layerWidth = maxTileX - minTileX;
        int layerHeight = maxTileY - minTileY;
        int tileWidth = map.getProperties().get("tilewidth", Integer.class);
        int tileHeight = map.getProperties().get("tileheight", Integer.class);

        TiledMapTileLayer layer = new TiledMapTileLayer(layerWidth, layerHeight, tileWidth, tileHeight);
        loadBasicLayerInfo(layer, element);

        // If chunks start at negative tile coords, shift the layer in world space
        // so chunk(minTileX, minTileY) sits at layer cell (0, 0).
        if (minTileX != 0 || minTileY != 0) {
            layer.setOffsetX(layer.getOffsetX() + minTileX * tileWidth);
            // World Y is up, TMX Y is down — flipY mirrors the offset direction.
            float yOffset = minTileY * tileHeight;
            layer.setOffsetY(layer.getOffsetY() + (flipY ? -yOffset : yOffset));
        }

        TiledMapTileSets tilesets = map.getTileSets();
        for (Element chunk : chunks) {
            int cx = chunk.getIntAttribute("x") - minTileX;
            int cy = chunk.getIntAttribute("y") - minTileY;
            int cw = chunk.getIntAttribute("width");
            int ch = chunk.getIntAttribute("height");
            String text = chunk.getText();
            if (text == null) continue;

            String[] tokens = text.split(",");
            for (int j = 0; j < ch; j++) {
                for (int i = 0; i < cw; i++) {
                    int tokenIdx = j * cw + i;
                    if (tokenIdx >= tokens.length) break;
                    String t = tokens[tokenIdx].trim();
                    if (t.length() == 0) continue;
                    int id = (int) Long.parseLong(t);

                    boolean flipH = (id & FLAG_FLIP_HORIZONTALLY) != 0;
                    boolean flipV = (id & FLAG_FLIP_VERTICALLY) != 0;
                    boolean flipD = (id & FLAG_FLIP_DIAGONALLY) != 0;

                    TiledMapTile tile = tilesets.getTile(id & ~MASK_CLEAR);
                    if (tile == null) continue;

                    Cell cell = createTileLayerCell(flipH, flipV, flipD);
                    cell.setTile(tile);

                    int layerX = cx + i;
                    int layerY = cy + j;
                    layer.setCell(layerX, flipY ? layerHeight - 1 - layerY : layerY, cell);
                }
            }
        }

        Element properties = element.getChildByName("properties");
        if (properties != null) {
            loadProperties(layer.getProperties(), properties);
        }
        parentLayers.add(layer);
    }
}
