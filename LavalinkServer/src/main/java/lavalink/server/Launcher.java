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

package lavalink.server;

import lavalink.server.io.SocketServer;
import lavalink.server.nas.NativeAudioSendFactory;
import net.dv8tion.jda.audio.AudioConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@Controller
public class Launcher {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    public final static long startTime = System.currentTimeMillis();
    public static Config config;
    public final SocketServer socketServer;

    @Autowired
    public Launcher(Config config, SocketServer socketServer) {
        Launcher.config = config;
        this.socketServer = socketServer;
    }

    public static void main(String[] args) {
        SpringApplication sa = new SpringApplication(Launcher.class);
        sa.setWebEnvironment(true);
        sa.run(args);

        String os = System.getProperty("os.name");

        log.info("OS: " + System.getProperty("os.name") + ", Arch: " + System.getProperty("os.arch"));

        if ((os.contains("Windows") || os.contains("Linux"))
                && !System.getProperty("os.arch").equalsIgnoreCase("arm")
                && !System.getProperty("os.arch").equalsIgnoreCase("arm-linux")
                ) {
            AudioConnection.setAudioSendFactory(new NativeAudioSendFactory());
            log.info("JDA-NAS supported system detected. Enabled native audio sending.");
        } else {
            log.warn("This system and architecture appears to not support native audio sending! "
                    + "GC pauses may cause your bot to stutter during playback.");
        }
    }

    @Bean
    static SocketServer socketServer(@Value("${lavalink.server.password}") String password) {
        SocketServer ss = new SocketServer(password);
        ss.start();
        return ss;
    }

}
