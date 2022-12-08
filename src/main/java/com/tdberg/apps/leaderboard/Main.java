package com.tdberg.apps.leaderboard;

import com.tdberg.apps.leaderboard.utils.Version;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(Main.class);
        Properties cfg;
        LeaderboardService leaderboardService;

        if(args.length != 1) {
            System.out.println("You must provide a properties configuration file as the only parameter for this application.  Exiting.");
            logger.error("Application start attempted, but too few or too many parameters detected.  Exiting.");
            return;
        }else {
            logger.info("Starting leaderboard service, version " + Version.getVersion());
            cfg = new Properties();
            try {
                cfg.load(new FileInputStream(args[0]));
            }catch(IOException e) {
                System.out.println("Failure to load properties configuration file.  Exiting.");
                logger.error("Failed to load properties configuration file: " + args[0]);
                return;
            }
        }

        leaderboardService = new LeaderboardService(cfg);

        if(!leaderboardService.initialize()) {
            System.out.println("Failed to connect to / initialize leaderboard.  Exiting.");
            return;
        }

        leaderboardService.runService();
    }
}
