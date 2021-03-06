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

package lavalink.client.io;

import lavalink.client.player.IPlayer;
import lavalink.client.player.LavalinkPlayer;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.VoiceChannel;
import net.dv8tion.jda.core.entities.impl.JDAImpl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class Lavalink {

    private final int numShards;
    private final Function<Integer, JDA> jdaProvider;
    private final ConcurrentHashMap<String, String> connectedChannels = new ConcurrentHashMap<>(); // Key is guild id
    private final ConcurrentHashMap<String, LavalinkPlayer> players = new ConcurrentHashMap<>(); // Key is guild id
    private final List<LavalinkSocket> nodes = new CopyOnWriteArrayList<>();
    private final LavalinkLoadBalancer loadBalancer = new LavalinkLoadBalancer(this);

    public Lavalink(int numShards, Function<Integer, JDA> jdaProvider) {
        this.numShards = numShards;
        this.jdaProvider = jdaProvider;
    }

    public void addNode(URI serverUri, String password) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", password);
        headers.put("Num-Shards", Integer.toString(numShards));
        nodes.add(new LavalinkSocket(this, serverUri, new Draft_6455(), headers));
    }

    public void openVoiceConnection(VoiceChannel channel) {
        JSONObject json = new JSONObject();
        json.put("op", "connect");
        json.put("guildId", channel.getGuild().getId());
        json.put("channelId", channel.getId());
        loadBalancer.getSocket(channel.getGuild()).send(json.toString());
        connectedChannels.put(channel.getGuild().getId(), channel.getId());
    }

    public void closeVoiceConnection(Guild guild) {
        JSONObject json = new JSONObject();
        json.put("op", "disconnect");
        json.put("guildId", guild.getId());
        loadBalancer.getSocket(guild).send(json.toString());
        connectedChannels.remove(guild.getId());
    }

    public VoiceChannel getConnectedChannel(Guild guild) {
        String id = connectedChannels.getOrDefault(guild.getId(), null);
        if (id != null) {
            return guild.getVoiceChannelById(id);
        }
        return null;
    }

    public void interceptJdaAudio(JDA jda) {
        ((JDAImpl) jda).getClient().getHandlers().put("VOICE_SERVER_UPDATE", new VoiceServerUpdateInterceptor(this, (JDAImpl) jda));
    }

    public IPlayer getPlayer(String guildId) {
        return players.computeIfAbsent(guildId, __ -> new LavalinkPlayer(loadBalancer.getSocket(guildId), guildId));
    }

    public void shutdown() {
        nodes.forEach(WebSocketClient::close);
    }

    LavalinkSocket getSocket(String guildId) {
        return loadBalancer.getSocket(guildId);
    }

    JDA getShard(int num) {
        return jdaProvider.apply(num);
    }

    public int getNumShards() {
        return numShards;
    }

    public List<LavalinkSocket> getNodes() {
        return nodes;
    }
}
