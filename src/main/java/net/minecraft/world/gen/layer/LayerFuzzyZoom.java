package net.minecraft.world.gen.layer;

/** The first zoom: the diagonal cell is picked at random rather than by majority, which frays the coastline. */
public final class LayerFuzzyZoom extends LayerZoom {
    public LayerFuzzyZoom(long baseSeed, GenLayer parent) { super(baseSeed, parent); }

    @Override
    protected int pick(int topLeft, int topRight, int bottomLeft, int bottomRight) {
        return selectRandom(topLeft, topRight, bottomLeft, bottomRight);
    }
}
