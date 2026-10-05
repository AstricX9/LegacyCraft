/**
 * The seam between the simulation and whoever is watching it. A client turns
 * input into {@link net.minecraft.sim.PlayerCommand}s; the simulation applies
 * them and reports what happened as {@link net.minecraft.sim.GameEvent}s. In
 * single-player the two sides are linked in memory; in multiplayer the same
 * messages travel over the network. See docs/SIMULATION_SEAM.md.
 */
package net.minecraft.sim;
