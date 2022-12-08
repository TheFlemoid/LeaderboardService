package com.tdberg.apps.leaderboard.utils;

public class Version {
    /**
     * Returns the version of the software as a String.
     * Note: The version is maintained as the "Implementation-Version" field in the JARs manifest.
     *
     * @return The software's version as a String
     */
    public static String getVersion() {
        Package p = Version.class.getPackage();
        return p.getImplementationVersion();
    }
}
