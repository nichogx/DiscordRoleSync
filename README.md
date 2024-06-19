# DiscordRoleSync

View this plugin on [SpigotMC](https://www.spigotmc.org/resources/discord-role-sync.78829/)

## Contributing
As I don't really have time to work on this and add new features, I've made it open source. I will warn you this was written a while ago and it's not the best code. Merge requests are welcome :)

The source code can be found on [GitLab](https://gitlab.com/nichogx/DiscordRoleSync).

I consider this plugin done, and no new features are planned.
If needed, you can reach me via GitLab or [Discord](https://discord.com/invite/JBNejsW).

## Installation
You should first install [Vault](https://www.spigotmc.org/resources/vault.34315/) as this plugin will not work without it.

Just copy the .jar to the plugins folder and run the server once to generate the config files. The plugin will error out due to no config, but that's expected on the first run. Go to the config.yml file and add your Discord bot token. To get one:

1. Go to https://discord.com/developers/applications
2. Click "new application" and put a name for your bot
3. In the left sidebar, click "Bot"
4. Click "add bot" and confirm
5. In this page, you can change your bot's picture and username.
6. Turn on "Server Members Intent" and "Message Content Intent" - it won't work without this as the bot needs to work with the member list and listen to commands
7. Click copy token and paste it in the config file. Anyone with your token can login as your bot so you should keep it secret as if it were a password.
8. Go back to the Discord developer website and on the left sidebar, click "OAuth2"
9. Select "bot" as a scope then copy the link
10. Go to the link you just copied to add the bot to your server

Don't forget to put all roles in the configs. You should put the role IDs, not the role names. To get the ID you should enable developer mode in Discord, then right click the role and copy ID (it's a number of approximately 18 digits). The same thing applies for the server ID and channel IDs.

## How it Works
It's pretty simple: all your users should use the -link command in Discord to link their Minecraft account:

```
-link myMCusername
```

As soon as they do this, their roles will keep synchronized while they are in the server. When your users get the Discord role, they will automatically get the permission plugin group. Updates are instant.

Staff can also use the `-admlink` command to force link a user:

```
-admlink discordID mcUsername
```

The Discord ID of the user is a 17 to 20 digit number.

The `-info` and `-unlink` commands are available for staff use. Both accept Discord IDs or Minecraft usernames.
The users need to remain in the Discord server to keep their roles. If they leave the server or are unlinked with the -unlink command, they are removed from the whitelist and every role is removed from them.

If verification is enabled, users will either get the code when they are kicked by whitelist (if whitelist management is enabled) or by typing /drs verify in the Minecraft chat (if it is disabled).

## Languages and Translation
Currently, the languages supported (and bundled with the plugin) are:
- English (en_US)
- Portuguese (pt_BR)
- Italian (it_IT)
- French (fr_FR)
- Spanish (es_ES)
- Turkish (tr_TR)
- German (de_DE)
- Japanese (ja_JP)
- Russian (ru_RU)

If you want to add your language, you can copy the en_US.yml file and edit it to your language. If you are interested in having your translation bundled with the plugin, please contact me via Discord or make a merge request on GitLab, I'll be happy to include it :D

## Permission Plugins
This plugin should work with every permission plugin that supports Vault.

Tested and confirmed working: 
- [LuckPerms](https://www.spigotmc.org/resources/luckperms.28140/) (recommended)
- [Ultra Permissions](https://www.spigotmc.org/resources/ultra-permissions.42678/)
- [PermissionsEX](https://github.com/PEXPlugins/PermissionsEx/releases) (deprecated)

A note on PermissionsEX: I do not recommend using PermissionsEX as it is deprecated and does not support asynchronous permission adding. Performance might be worse when using it in large servers.

## Online and Offline Server Mode
This plugin has limited support for offline servers. Some limitations include:

- In offline mode servers, linking usernames is case-sensitive
- In offline mode servers, the `-info` command will NOT return the username, only the UUID of the linked Minecraft account.
- If you swap between offline and online (or vice-versa), you'll need do delete the database. If you are using SQLite, just delete the database.db file. If you are using MySQL, execute `DROP TABLE syncbot_discordmcusers;`, replacing `syncbot` with the prefix in your config.yml file if you have changed it.

## Permission Nodes

- `discordrolesync.reload`: Use the /drs reload command
- `discordrolesync.botrestart`: Use the /drs botrestart command
- `discordrolesync.notifyupdates`: Users with this permission will be notified when they join if an update is available.

All users are permitted to use the /drs verify command

## Known Issues and Warnings

- The plugin might not work if installed with some plugins that also use the JDA library, if they use an older version and are loaded before (such as Minecord).
- No config file validation. You should be fine as long as you keep the format of the default file. Please don't remove any properties from the config file as it will result in null pointer exceptions :p
