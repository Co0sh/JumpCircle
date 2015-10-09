/**
 * JumpCircle - secretly count jumping in circles
 * Copyright (C) 2015 Jakub "Co0sh" Sapalski
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pl.betoncraft.jumpcircle;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.InstructionParseException;
import pl.betoncraft.betonquest.api.Condition;
import pl.betoncraft.betonquest.utils.PlayerConverter;

/**
 * Simple plugin which allows for counting laps on jumping track.
 * 
 * @author Jakub Sapalski
 */
public class JumpCircle extends JavaPlugin implements Listener {

	private double height; 
	private double maxTime;
	private double radius;
	private int foodLevel;
	private ArrayList<Location> locs = new ArrayList<>();
	private double minX, maxX, minZ, maxZ;
	private HashMap<UUID, PlayerData> playerData = new HashMap<>();
	
	/**
	 * Loads all data and registers BetonQuest conditions.
	 */
	@Override
	public void onEnable() {
		saveDefaultConfig();
		try {
			World world = Bukkit.getWorld(getConfig().getString("world"));
			height = getConfig().getDouble("height");
			radius = getConfig().getDouble("radius");
			maxTime = getConfig().getDouble("max_time") * 1000;
			foodLevel = getConfig().getInt("food_level");
			// creates a cuboid in which onMove checking will work
			List<String> rawLocs = getConfig().getStringList("locs");
			boolean first = true;
			for (String rawLoc : rawLocs) {
				String[] parts = rawLoc.split(";");
				Location loc = new Location(world,
						Double.parseDouble(parts[0]), 
						Double.parseDouble(parts[1]), 
						Double.parseDouble(parts[2]));
				locs.add(loc);
				if (first) {
					first = false;
					minX = loc.getX() - radius;
					maxX = loc.getX() + radius;
					minZ = loc.getZ() - radius;
					maxZ = loc.getZ() + radius;
				} else {
					if (minX > loc.getX() - radius) minX = loc.getX() - radius;
					if (maxX < loc.getX() + radius) maxX = loc.getX() + radius;
					if (minZ > loc.getZ() - radius) minZ = loc.getZ() - radius;
					if (maxZ < loc.getZ() + radius) maxZ = loc.getZ() + radius;
				}
			}
		} catch (Exception e) {
			getLogger().severe("Error in configuration: " + e.getMessage());
			setEnabled(false);
			return;
		}
		Bukkit.getPluginManager().registerEvents(this, this);
		// loads the data in case of server reloading
		for (Player player : Bukkit.getOnlinePlayers()) {
			playerData.put(player.getUniqueId(), new PlayerData(player));
		}
		if (Bukkit.getPluginManager().isPluginEnabled("BetonQuest")) {
			BetonQuest instance = BetonQuest.getInstance();
			instance.registerConditions("maxlaps", MaxLapsCondition.class);
			instance.registerConditions("mintime", MinTimeCondition.class);
			instance.registerConditions("totallaps", TotalLapsCondition.class);
			instance.registerConditions("totaltime", TotalTimeCondition.class);
		}
	}
	
	/**
	 * Saves data of all players.
	 */
	@Override
	public void onDisable() {
		for (PlayerData data : playerData.values()) data.save();
		saveConfig();
	}
	
	/**
	 * Loads data on join.
	 */
	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		playerData.put(e.getPlayer().getUniqueId(),
				new PlayerData(e.getPlayer()));
	}
	
	/**
	 * Saves data on quit.
	 */
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		playerData.remove(e.getPlayer().getUniqueId()).save();
		saveConfig();
	}
	
	/**
	 * Handles all moves of the player and adds checkpoints.
	 */
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		if (e.getPlayer().isFlying()) return;
		if (e.getPlayer().isInsideVehicle()) return;
		Location loc = e.getTo();
		// check the player is inside the cuboid
		if (loc.getX() > minX && loc.getX() < maxX &&
			loc.getZ() > minZ && loc.getZ() < maxZ) {
			PlayerData data = playerData.get(e.getPlayer().getUniqueId());
			// this will reset the counter when the player falls
			if (loc.getY() < height) {
				data.reset();
			}
			// find if he's in range of a location
			for (Location jumpLoc : locs) {
				if (loc.distanceSquared(jumpLoc) < radius*radius) {
					data.checkPoint(jumpLoc);
					return;
				}
			}
		}
	}
	
	/**
	 * Handler for the main (only) command.
	 */
	@EventHandler
	public void onCommand(PlayerCommandPreprocessEvent e) {
		if (!e.getMessage().equalsIgnoreCase("/" + getConfig()
				.getString("command"))) return;
		Player player = e.getPlayer();
		PlayerData data = playerData.get(player.getUniqueId());
		// don't display command if the player did not jump
		if (!data.command()) return;
		for (PlayerData save : playerData.values()) save.save();
		player.sendMessage(getConfig().getString("stats").replace('&', '§'));
		player.sendMessage(getConfig().getString("totals").replace('&', '§')
				.replace("%laps%", String.valueOf(data.getTotalLaps()))
				.replace("%streak%", String.valueOf(data.getMaxLaps()))
				.replace("%time%", String.valueOf(data.getTotalTime() / 60000))
				.replace("%fastest%", String.format("%.2f", ((double)
						data.getMinTime()) / 1000)));
		// for each ranking type, sort players
		String[] rankings = new String[]{
				"min_time", "max_laps", "total_laps", "total_time"};
		Set<String> ids = getConfig().getConfigurationSection("players")
				.getKeys(false);
		int top = getConfig().getInt("ranking_top");
		for (String ranking : rankings) {
			player.sendMessage(getConfig().getString(ranking)
					.replace('&', '§'));
			String[] names = new String[top];
			long[] numbers = new long[top];
			// set all numbers to less than 0, so the swapping works
			for (int i = 0; i < top; i++) {
				numbers[i] = -1;
			}
			// check every player in the configuration
			for (String id : ids) {
				long number = getConfig().getLong("players." + id + "."
						+ ranking);
				// if it's less than 0 then the player should not appear
				if (number <= 0) continue;
				String name = id;
				for (int i = 0; i < top; i++) {
					// check if the places should be swapped
					boolean swap = false;
					if (numbers[i] < 0) {
						swap = true;
					} else if (ranking.equals("min_time")) {
						if (numbers[i] > number) swap = true;
					} else {
						if (numbers[i] < number) swap = true;
					}
					if (swap) {
						// swapping
						long tempNumber = number;
						String tempName = name;
						number = numbers[i];
						name = names[i];
						numbers[i] = tempNumber;
						names[i] = tempName;
					}
				}
			}
			for (int i = 0; i < top; i++) {
				if (names[i] == null) break;
				String number = null;
				if (ranking.equals("min_time")) number = String.format("%.2f",
						((double) numbers[i]) / 1000) + "s";
				else if (ranking.equals("max_laps")) {
					number = String.valueOf(numbers[i]);
				} else if (ranking.equals("total_laps")) {
					number = String.valueOf(numbers[i] / locs.size());
				} else number = String.valueOf(numbers[i] / 60000) + "m";
				player.sendMessage(getConfig().getString("ranking_lines")
						.replace('&', '§')
						.replace("%place%", String.valueOf(i+1))
						.replace("%player%", Bukkit.getOfflinePlayer(
								UUID.fromString(names[i])).getName())
						.replace("%number%", number));
			}
		}
		e.setCancelled(true);
	}
	
	/**
	 * @return the map with players' data
	 */
	public HashMap<UUID, PlayerData> getPlayerData() {
		return playerData;
	}

	/**
	 * Stores all data of the player.
	 */
	public class PlayerData {
		
		private Player player;
		private long minTime;
		private int maxLaps;
		private long totalTime;
		private int totalLaps;
		private int curLaps = 0;
		private Location lastLoc = null;
		private Location firstLoc = null;
		private long timeStarted = -1;
		private long timeOfLast = -1;
		private Direction dir;
		
		/**
		 * Loads the data of this player.
		 * 
		 * @param player
		 *            the player, whose data needs to be loaded
		 */
		private PlayerData(Player player) {
			this.player = player;
			minTime = getConfig().getLong("players." + player.getUniqueId()
					+ ".min_time", -1);
			maxLaps = getConfig().getInt("players." + player.getUniqueId()
					+ ".max_laps", 0);
			totalLaps = getConfig().getInt("players." + player.getUniqueId()
					+ ".total_laps", 0);
			totalTime = getConfig().getLong("players." + player.getUniqueId()
					+ ".total_time", 0);
		}

		/**
		 * @return the fastest lap time
		 */
		public long getMinTime() {
			return minTime;
		}

		/**
		 * @return total jumping time
		 */
		public long getTotalTime() {
			return totalTime;
		}

		/**
		 * @return greates amount of laps in single streak
		 */
		public int getMaxLaps() {
			return maxLaps;
		}

		/**
		 * @return total amount of full laps
		 */
		public int getTotalLaps() {
			return totalLaps / locs.size();
		}

		/**
		 * Handles the checkpoint.
		 * 
		 * @param loc
		 *            location of the checkpoint
		 */
		private void checkPoint(Location loc) {
			long now = new Date().getTime();
			// check if it's beginning
			if (lastLoc == null) {
				firstLoc = loc;
				lastLoc = loc;
				timeStarted = now;
				timeOfLast = now;
				return;
			}
			// check if it's the same checkpoint as before
			if (loc.equals(lastLoc)) {
				return;
			}
			// check if the checkpoint is near the last one
			int lastIndex = locs.indexOf(lastLoc);
			int currIndex = locs.indexOf(loc);
			if (((currIndex == 0 && lastIndex == locs.size() - 1)
					|| (currIndex - 1 == lastIndex))) {
				// if it's second one, set direction
				if (lastLoc.equals(firstLoc)) {
					dir = Direction.PLUS;
				}
				// if direction is incorrect, reset
				if (dir != Direction.PLUS) {
					reset();
					return;
				}
			} else if (((currIndex == locs.size() - 1 && lastIndex == 0)
					|| (currIndex + 1 == lastIndex))) {
				// if it's second one, set direction
				if (lastLoc.equals(firstLoc)) {
					dir = Direction.MINUS;
				}
				// if direction is incorrect, reset
				if (dir != Direction.MINUS) {
					reset();
					return;
				}
			} else {
				// it's some other checkpoint, reset
				reset();
				return;
			}
			// if the time between checkpoints is too long, reset
			if (now - timeOfLast > maxTime) {
				reset();
				return;
			}
			// everything alright, continue
			totalTime += now - timeOfLast;
			totalLaps++;
			lastLoc = loc;
			timeOfLast = now;
			// if it's the first loc again, we have a lap
			if (loc.equals(firstLoc)) {
				// this feeds the player, so he does not need to stop
				if (player.getFoodLevel() < foodLevel) {
					player.setFoodLevel(foodLevel);
				}
				curLaps++;
				long curTime = now - timeStarted;
				player.sendMessage(getConfig().getString("next_lap")
						.replace('&', '§')
						.replace("%lap%", String.valueOf(curLaps))
						.replace("%time%", String.format("%.2f",
								((double) curTime) / 1000)));
				if (curLaps > maxLaps) maxLaps = curLaps;
				if (curLaps == getConfig().getInt("command_laps")) {
					player.sendMessage(getConfig().getString("command_info")
							.replace('&', '§')
							.replace("%cmd%", getConfig(
									).getString("command")));
				}
				if (minTime < 0 || curTime < minTime) minTime = curTime;
				timeStarted = now;
				return;
			}
		}
		
		/**
		 * @return true if the player can use main (only) command
		 */
		private boolean command() {
			return maxLaps >= getConfig().getInt("command_laps");
		}
		
		/**
		 * Resets the counter.
		 */
		private void reset() {
			lastLoc = null;
			timeStarted = -1;
			timeOfLast = -1;
			curLaps = 0;
		}
		
		/**
		 * Saves the player to the configuration.
		 */
		public void save() {
			if (minTime < maxTime || maxLaps > 0) {
				getConfig().set("players." + player.getUniqueId()
						+ ".min_time",
						minTime);
				getConfig().set("players." + player.getUniqueId()
						+ ".max_laps",
						maxLaps);
				getConfig().set("players." + player.getUniqueId()
						+ ".total_laps",
						totalLaps);
				getConfig().set("players." + player.getUniqueId()
						+ ".total_time",
						totalTime);
				
			}
		}
	}
	
	/**
	 * Direction of jumping.
	 */
	private enum Direction {
		PLUS, MINUS
	}
	
	/**
	 * BetonQuest condition of having long streak.
	 */
	public static class MaxLapsCondition extends Condition {
		
		private int laps;

		public MaxLapsCondition(String packName, String instructions)
				throws InstructionParseException {
			super(packName, instructions);
			String[] parts = instructions.split(" ");
			if (parts.length < 2) {
				throw new InstructionParseException("Not enough arguments");
			}
			try {
				laps = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				throw new InstructionParseException("Could not parse amount");
			}
			
		}

		@Override
		public boolean check(String playerID) {
			return JumpCircle.getPlugin(JumpCircle.class).getPlayerData()
					.get(PlayerConverter.getPlayer(playerID).getUniqueId())
					.getMaxLaps() >= laps;
		}
		
	}
	
	/**
	 * BetonQuest condition of fastes lap.
	 */
	public static class MinTimeCondition extends Condition {
		
		private long time;

		public MinTimeCondition(String packName, String instructions)
				throws InstructionParseException {
			super(packName, instructions);
			String[] parts = instructions.split(" ");
			if (parts.length < 2) {
				throw new InstructionParseException("Not enough arguments");
			}
			try {
				time = Long.parseLong(parts[1]) * 1000;
			} catch (NumberFormatException e) {
				throw new InstructionParseException("Could not parse amount");
			}
			
		}

		@Override
		public boolean check(String playerID) {
			return JumpCircle.getPlugin(JumpCircle.class).getPlayerData()
					.get(PlayerConverter.getPlayer(playerID).getUniqueId())
					.getMinTime() <= time;
		}
		
	}
	
	/**
	 * BetonQuest condition of total amount of full laps.
	 */
	public static class TotalLapsCondition extends Condition {
		
		private int laps;

		public TotalLapsCondition(String packName, String instructions)
				throws InstructionParseException {
			super(packName, instructions);
			String[] parts = instructions.split(" ");
			if (parts.length < 2) {
				throw new InstructionParseException("Not enough arguments");
			}
			try {
				laps = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				throw new InstructionParseException("Could not parse amount");
			}
			
		}

		@Override
		public boolean check(String playerID) {
			return JumpCircle.getPlugin(JumpCircle.class).getPlayerData()
					.get(PlayerConverter.getPlayer(playerID).getUniqueId())
					.getTotalLaps() >= laps;
		}
		
	}
	
	/**
	 * BetonQuest condition of total jumping time.
	 */
	public static class TotalTimeCondition extends Condition {
		
		private long time;

		public TotalTimeCondition(String packName, String instructions)
				throws InstructionParseException {
			super(packName, instructions);
			String[] parts = instructions.split(" ");
			if (parts.length < 2) {
				throw new InstructionParseException("Not enough arguments");
			}
			try {
				time = Long.parseLong(parts[1]) * 1000 * 60;
			} catch (NumberFormatException e) {
				throw new InstructionParseException("Could not parse amount");
			}
			
		}

		@Override
		public boolean check(String playerID) {
			return JumpCircle.getPlugin(JumpCircle.class).getPlayerData()
					.get(PlayerConverter.getPlayer(playerID).getUniqueId())
					.getTotalTime() >= time;
		}
		
	}

}
