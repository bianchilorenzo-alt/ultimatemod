package it.miamod;

public class Waypoint {
	public String name;
	public int x;
	public int y;
	public int z;
	public String dimension;

	public Waypoint() {
	}

	public Waypoint(String name, int x, int y, int z, String dimension) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
	}
}
