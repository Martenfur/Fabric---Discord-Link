package fr.arthurbambou.fblink.discordstuff;

import com.vdurmont.emoji.EmojiParser;
import fr.arthurbambou.fblink.FBLink;
import net.fabricmc.fabric.api.event.server.ServerStartCallback;
import net.fabricmc.fabric.api.event.server.ServerStopCallback;
import net.fabricmc.fabric.api.event.server.ServerTickCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.ServerChannel;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;

public class DiscordBot
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	private FBLink.Config _config;
	private boolean _hasChatChannels;
	private boolean _hasLogChannels;
	private MessageCreateEvent _messageCreateEvent;
	private boolean _hasReceivedMessage;
	private String _lastMessageD;
	private DiscordApi _api = null;
	private long _startTime;
	
	private String _forbiddenBoi = "Saving is already turned on";
	
	public DiscordBot(String token, FBLink.Config config)
	{
		_lastMessageD = "";
		if (token == null)
		{
			FBLink.regenConfig();
			return;
		}
		
		if (token.isEmpty())
		{
			LOGGER.error("[FDLink] Please add a bot token to the config file!");
			return;
		}
		
		if (config.chatChannels.isEmpty())
		{
			LOGGER.info("[FDLink] Please add a game chat channel to the config file!");
			_hasChatChannels = false;
		}
		else
		{
			_hasChatChannels = true;
		}
		
		if (config.logChannels.isEmpty())
		{
			LOGGER.info("[FDLink] Please add a log channel to the config file!");
			_hasLogChannels = false;
		}
		else
		{
			_hasLogChannels = true;
		}
		
		if (!_hasLogChannels && !_hasChatChannels)
		{
			return;
		}
		
		config.logChannels.removeIf(id -> config.chatChannels.contains(id));
		
		_config = config;
		DiscordApi api = new DiscordApiBuilder().setToken(token).login().join();
		api.addMessageCreateListener((event ->
		{
			if (event.getMessageAuthor().isBotUser() && _config.ignoreBots)
			{
				return;
			}
			if (!_hasChatChannels)
			{
				return;
			}
			if (event.getMessageAuthor().isYourself())
			{
				return;
			}
			if (!_config.chatChannels.contains(event.getChannel().getIdAsString()))
			{
				return;
			}
			_messageCreateEvent = event;
			_hasReceivedMessage = true;
		}));
		_api = api;
		
		sendToAllChannels(_config.minecraftToDiscord.messages.serverStarting);
		
		ServerStartCallback.EVENT.register((server ->
		{
			_startTime = server.getServerStartTime();
			sendToAllChannels(_config.minecraftToDiscord.messages.serverStarted);
		}));
		
		ServerStopCallback.EVENT.register((server ->
		{
			sendToAllChannels(config.minecraftToDiscord.messages.serverStopped);
			_api.disconnect();
		}));
		
		ServerTickCallback.EVENT.register((server ->
		{
			int playerNumber = server.getPlayerManager().getPlayerList().size();
			int maxPlayer = server.getPlayerManager().getMaxPlayerCount();
			if (_hasReceivedMessage)
			{
				if (_messageCreateEvent.getMessageContent().startsWith("!list"))
				{
					StringBuilder playerlist = new StringBuilder();
					for (PlayerEntity playerEntity : server.getPlayerManager().getPlayerList())
					{
						playerlist.append(playerEntity.getName().getString()).append("\n");
					}
					if (playerlist.toString().endsWith("\n"))
					{
						int a = playerlist.lastIndexOf("\n");
						playerlist = new StringBuilder(playerlist.substring(0, a));
					}
					_messageCreateEvent.getChannel().sendMessage("Players : " + server.getPlayerManager().getPlayerList().size() + "/" + server.getPlayerManager().getMaxPlayerCount() + "\n\n" + playerlist);
				}
				if (_messageCreateEvent.getMessageContent().startsWith("!salahip"))
				{
					_messageCreateEvent.getChannel().sendMessage("Current Salah ip: " + getFakeIp());
				}
				
				_lastMessageD = _config.discordToMinecraft
								.replace("%player", _messageCreateEvent.getMessageAuthor().getDisplayName())
								.replace("%message", EmojiParser.parseToAliases(_messageCreateEvent.getMessageContent()));
				server.getPlayerManager().sendToAll(new LiteralText(_lastMessageD));
				
				_hasReceivedMessage = false;
			}
			if (_hasChatChannels && _config.minecraftToDiscord.booleans.customChannelDescription)
			{
				int totalUptimeSeconds = (int) (Util.getMeasuringTimeMs() - _startTime) / 1000;
				final int uptimeH = totalUptimeSeconds / 3600;
				final int uptimeM = (totalUptimeSeconds % 3600) / 60;
				final int uptimeS = totalUptimeSeconds % 60;
				
				for (String id : _config.chatChannels)
				{
					_api.getServerTextChannelById(id).ifPresent(channel ->
									channel.updateTopic(String.format(
													"player count : %d/%d,\n" +
																	"uptime : %d h %d min %d second",
													playerNumber, maxPlayer, uptimeH, uptimeM, uptimeS
									)));
				}
			}
		}));
	}
	
	public void sendMessage(Text text)
	{
		if (text.toString().contains(_forbiddenBoi))
		{
			return;
		}
		if (_api == null || (!_hasChatChannels && !_hasLogChannels))
		{
			return;
		}
		if (text.asString().equals(_lastMessageD))
		{
			return;
		}
		
		if (!(text instanceof TranslatableText))
		{
			sendToLogChannels(text.getString());
			return;
		}
		
		String key = ((TranslatableText) text).getKey();
		String message = text.getString();
		message = message.replaceAll("§[b0931825467adcfeklmnor]", "");
		LOGGER.debug(_config.toString());
		if (key.equals("chat.type.text") && _config.minecraftToDiscord.booleans.PlayerMessages)
		{
			// Handle normal chat
			if (_config.minecraftToDiscord.booleans.MCtoDiscordTag)
			{
				for (User user : _api.getCachedUsers())
				{
					ServerChannel serverChannel = (ServerChannel) _api.getServerChannels().toArray()[0];
					Server server = serverChannel.getServer();
					message = message
									.replace(user.getName(), user.getMentionTag())
									.replace(user.getDisplayName(server), user.getMentionTag())
									.replace(user.getName().toLowerCase(), user.getMentionTag())
									.replace(user.getDisplayName(server).toLowerCase(), user.getMentionTag());
				}
			}
			sendToAllChannels(message);
			
		}
		else if (key.equals("chat.type.emote") || key.equals("chat.type.announcement") // Handling /me and /say command
						|| (key.startsWith("multiplayer.player.") && _config.minecraftToDiscord.booleans.JoinAndLeftMessages)
						|| (key.startsWith("chat.type.advancement.") && _config.minecraftToDiscord.booleans.AdvancementMessages)
						|| (key.startsWith("death.") && _config.minecraftToDiscord.booleans.DeathMessages)
		)
		{
			sendToAllChannels(message);
			
		}
		else if (key.equals("chat.type.admin"))
		{
			sendToLogChannels(message);
			
		}
		else
		{
			LOGGER.info("[FDLink] Unhandled text \"{}\":{}", key, message);
		}
	}
	
	private void sendToAllChannels(String message)
	{
		if (message.contains(_forbiddenBoi))
		{
			return;
		}
		if (_hasLogChannels)
		{
			for (String id : _config.logChannels)
			{
				_api.getServerTextChannelById(id).ifPresent(channel -> channel.sendMessage(message));
			}
		}
		sendToChatChannels(message);
	}
	
	/**
	 * This method will send to chat channel as fallback if no log channel is present
	 *
	 * @param message the message to send
	 */
	public void sendToLogChannels(String message)
	{
		if (message.contains(_forbiddenBoi))
		{
			return;
		}
		if (_hasLogChannels)
		{
			for (String id : _config.logChannels)
			{
				_api.getServerTextChannelById(id).ifPresent(channel -> channel.sendMessage(message));
			}
		}
		else
		{
			sendToChatChannels(message);
		}
	}
	
	private void sendToChatChannels(String message)
	{
		if (message.contains(_forbiddenBoi))
		{
			return;
		}
		if (_hasChatChannels)
		{
			for (String id : _config.chatChannels)
			{
				_api.getServerTextChannelById(id).ifPresent(channel -> channel.sendMessage(message));
			}
		}
	}
	
	private String getFakeIp()
	{
		byte[] ip = new byte[4];
		int swapIndex = (int)(Math.random() * 4);
		for(int i = 0; i < 4; i += 1)
		{
			if (i == swapIndex)
			{
				ip[i] = (byte)(Math.random() * 256);
			}
			else
			{
				ip[i] = (byte)(256 + Math.random() * 32);
			}
		}
		return ip[0] + "." + ip[1] + "." + ip[2] + "." + ip[3];
	}
}
