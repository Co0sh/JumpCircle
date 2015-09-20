# JumpCircle

Silly Bukkit plugin which allows you to set a circle and count how many times
your players run around it.

I usually have an unregular, wooden stockade on which I am jumping in circles
when talking to people over Skype. This plugin lets me count how many full laps
(without falling down) I have done, what was the longes streak, how fast was
the fastest lap and how long am I jumping on the walls.

The plugin does only show notifications about doing a lap when you actually do
a full one, so my players don't even know it does exist until they join me in
jumping. When a player gets 3 full laps, he is allowed to use "/jump" command,
which shows current stats and rankings. All other players see a default
"Unknown command. Type "/help" for help." message, so they probably don't
believe those who are boasting around.

## Setup

The configuration looks like that:

* `world` name of the world on which your circle will be.
* `height` height of the ground. When the player falls below this Y value,
  the counter will reset. Set it one block under the lowest place on your track.
* `radius` is a radius around a checkpoint where the player must be
* `maxTime` maximum time (in seconds) between checkpoints. Set the
  checkpoints in even distances! The players who are too slow (for example
  because they are too caucious) will not get the laps.
* `command` name of the command. You can set it to whatever you like, but not
  a command which is used by some other plugin.
* `command_laps` how many laps a player must do in a streak in order to be
  allowed to use the command.
* `ranking_top` how many players will be shown in rankings. Setting it too
  high will result in your screen being spammed by informations.

Locations are just coordinates separated with semicolon. They need to be in
order, because the plugin will not accept checkpoints done without any order.
It does not matter if the player jumps clockwise or not, the plugin can handle
both directions.

Don't touch "players" tree, it's storing the data.

If you want to edit the configuration manually, you must stop the server. It is
so simple that it does not have a reloading command. It can however handle
general _/reload_ command, but still, it will overwrite your changes in the
config.

## BetonQuest

Like all of my plugins, this hooks into BetonQuest. You can use `maxlaps`,
`mintime`, `totallaps` and `totaltime` conditions in your quests.
All of them accept only a single argument, a number. First one is about maximum
lap amount in a single streak, second is about fastest lap in seconds, third
about total laps ever made and last about total time of jumping in minutes.

Don't use this plugin for race quests, that's what `location` objective is
for!


## Compiling

You need JDK 1.7 and Maven 3. Download the source and issue `mvn install`
command inside the root directory. The compiled .jar will appear in _target_
directory.

## License

The plugin is licensed under GPLv3 (or above).