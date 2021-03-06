/*
 * Copyright (c) 2017 Frederik Ar. Mikkelsen & NoobLance
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package lavalink.server.io;

import lavalink.server.player.Player;
import lavalink.server.util.Util;
import net.dv8tion.jda.manager.AudioManager;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static lavalink.server.io.WSCodes.AUTHORIZATION_REJECTED;
import static lavalink.server.io.WSCodes.INTERNAL_ERROR;

public class SocketServer extends WebSocketServer {

    private static final Logger log = LoggerFactory.getLogger(SocketServer.class);
    private static final Map<WebSocket, SocketContext> contextMap = new HashMap<>();
    private final String password;

    public SocketServer(String password) {
        this.password = password;
    }

    @Override
    public void onOpen(WebSocket webSocket, ClientHandshake clientHandshake) {
        try {
            int shardCount = Integer.parseInt(clientHandshake.getFieldValue("Num-Shards"));

            if (clientHandshake.getFieldValue("Authorization").equals(password)) {
                log.info("Connection opened from " + webSocket.getRemoteSocketAddress() + " with protocol " + webSocket.getDraft());
                contextMap.put(webSocket, new SocketContext(webSocket, shardCount));
            } else {
                log.error("Authentication failed from " + webSocket.getRemoteSocketAddress() + " with protocol " + webSocket.getDraft());
                webSocket.close(AUTHORIZATION_REJECTED, "Authorization rejected");
            }
        } catch (Exception e) {
            log.error("Error when opening websocket", e);
            webSocket.close(INTERNAL_ERROR, e.getMessage());
        }
    }

    @Override
    public void onClose(WebSocket webSocket, int i, String s, boolean b) {
        log.info("Connection closed from " + webSocket.getRemoteSocketAddress().toString() + " with protocol " + webSocket.getDraft());
        contextMap.get(webSocket).shutdown();
        contextMap.remove(webSocket);
    }

    @Override
    public void onMessage(WebSocket webSocket, String s) {
        JSONObject json = new JSONObject(s);

        log.info(s);

        if (webSocket.isClosing()) {
            log.error("Ignoring closing websocket: " + webSocket.getRemoteSocketAddress().toString());
        }

        switch (json.getString("op")) {
            /* JDAA ops */
            case "connect":
                AudioManager manager = contextMap.get(webSocket).getCore(getShardId(webSocket, json))
                        .getAudioManager(json.getString("guildId"));

                if (manager.isConnected() || manager.isAttemptingToConnect()) {
                    manager.closeAudioConnection();
                    log.info("Closing the audio connection for guild " + json.getString("guildId")
                            + " so we can reconnect.");
                }

                manager.openAudioConnection(json.getString("channelId"));
                break;
            case "voiceUpdate":
                contextMap.get(webSocket).getCore(getShardId(webSocket, json)).provideVoiceServerUpdate(
                        json.getString("sessionId"),
                        json.getJSONObject("event")
                );
                break;
            case "disconnect":
                contextMap.get(webSocket).getCore(getShardId(webSocket, json)).getAudioManager(json.getString("guildId"))
                        .closeAudioConnection();
                break;
            case "validationRes":
                ((CoreClientImpl) contextMap.get(webSocket).getCore(getShardId(webSocket, json)).getClient()).provideValidation(
                        json.getString("guildId"),
                        json.optString("channelId"),
                        json.getBoolean("valid")
                );
                break;
            case "isConnectedRes":
                ((CoreClientImpl) contextMap.get(webSocket).getCore(json.getInt("shardId")).getClient()).provideIsConnected(
                        json.getBoolean("connected")
                );
                break;

            /* Player ops */
            case "play":
                try {
                    Player player = contextMap.get(webSocket).getPlayer(json.getString("guildId"));
                    player.play(Util.toAudioTrack(json.getString("track")));

                    SocketContext context = contextMap.get(webSocket);

                    context.getCore(getShardId(webSocket, json)).getAudioManager(json.getString("guildId"))
                            .setSendingHandler(context.getPlayer(json.getString("guildId")));
                    sendPlayerUpdate(webSocket, player);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case "stop":
                Player player = contextMap.get(webSocket).getPlayer(json.getString("guildId"));
                player.stop();
                break;
            case "pause":
                Player player2 = contextMap.get(webSocket).getPlayer(json.getString("guildId"));
                player2.setPause(json.getBoolean("pause"));
                sendPlayerUpdate(webSocket, player2);
                break;
            case "seek":
                Player player3 = contextMap.get(webSocket).getPlayer(json.getString("guildId"));
                player3.seekTo(json.getLong("position"));
                sendPlayerUpdate(webSocket, player3);
                break;
            case "volume":
                Player player4 = contextMap.get(webSocket).getPlayer(json.getString("guildId"));
                player4.setVolume(json.getInt("volume"));
                break;
            default:
                log.warn("Unexpected operation: " + json.getString("op"));
                break;
        }
    }

    @Override
    public void onError(WebSocket webSocket, Exception e) {
        log.error("Caught exception in websocket", e);
    }

    @Override
    public void onStart() {
        log.info("Started WS server");
    }

    public static void sendPlayerUpdate(WebSocket webSocket, Player player) {
        JSONObject json = new JSONObject();
        json.put("op", "playerUpdate");
        json.put("guildId", player.getGuildId());
        json.put("state", player.getState());

        webSocket.send(json.toString());
    }

    //Shorthand method
    private int getShardId(WebSocket webSocket, JSONObject json) {
        return Util.getShardFromSnowflake(json.getString("guildId"), contextMap.get(webSocket).getShardCount());
    }

}
