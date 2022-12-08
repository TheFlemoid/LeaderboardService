package com.tdberg.apps.leaderboard.objects;

import com.tdberg.apps.leaderboard.utils.ApiKey;
import com.tdberg.apps.leaderboard.utils.DbUtils;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * This class contains a Leaderboard object that describes an entry in the Leaderboards SQL table.
 * NOTE: This object should NOT be serialized and sent to the user, as it contains the boardId and lastQuery time
 *       which is not public.
 */
public class Leaderboard {
    private int boardId;
    private String privKey;
    private String pubKey;
    private Timestamp lastQuery;

    private static String getLeaderboardsQuery = "SELECT * FROM leaderboards";

    /**
     * Default contructor
     */
    public Leaderboard() {
    }

    /**
     * Filled out constructor
     *
     * @param boardId Leaderboard ID (primary key) for this Leaderboard
     * @param privKey Private API key for this leaderboard
     * @param pubKey Public API key for this leaderboard
     * @param lastQuery Date indicating the last query time for this leaderboard
     */
    public Leaderboard(final int boardId, final String privKey, final String pubKey, final Timestamp lastQuery) {
        this.boardId = boardId;
        this.privKey = privKey;
        this.pubKey = pubKey;
        this.lastQuery = lastQuery;
    }

    public void setBoardId(final int boardId) {
        this.boardId = boardId;
    }

    public int getBoardId() {
        return boardId;
    }

    public void setPrivKey(final String privKey) {
        this.privKey = privKey;
    }

    public String getPrivKey() {
        return privKey;
    }

    public void setPubKey(final String pubKey) {
        this.pubKey = pubKey;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setLastQueryTime(final Timestamp lastQuery) {
        this.lastQuery = lastQuery;
    }

    public Timestamp getLastQueryTime() {
        return lastQuery;
    }

    /**
     * Returns a UserFacingLeaderboard object that contains the user releasable fields of this Leaderboard
     *
     * @return a UserFacingLeaderboard object that contains the user releasable fields of this Leaderboard
     */
    public UserFacingLeaderboard toUserFacingLeaderboard() {
        return new UserFacingLeaderboard(privKey, pubKey);
    }

    @Override
    public String toString() {
        return "Leaderboard: " + boardId + " PubKey: " + pubKey + " PrivKey: " + privKey + 
               " Last Modified: " + lastQuery.toString();
    }

    /**
     * Creates a new Leaderboard with unique private and public API keys.  This requires a DB Connection, as this
     * method checks the generated public and private API keys against those in the DB to ensure uniqueness.
     *
     * @param conn Connection to the SQL database
     * @return a new Leaderboard with unique private and public API keys
     * @throws SQLException
     */
    public static Leaderboard createNewLeaderboard(Connection conn) throws SQLException {
        Leaderboard retVal = new Leaderboard();
        List<Leaderboard> leaderboardList = DbUtils.getAllLeaderboards(conn);
        String privKey = "";
        String pubKey = "";
        boolean foundMatch = true;

        // This generates a new private key, and then verifies that that private key isn't already assigned to another
        // leaderboard.  (There is an EXTREMELLY small chance of that happening (less than 1 in a billion), but I
        // wanted to check anyway.
        while(foundMatch) {
            privKey = ApiKey.createPrivateKey();
            foundMatch = false;

            for(int i=0; i<leaderboardList.size() && !foundMatch; i++) {
                if(privKey.equals(leaderboardList.get(i).getPrivKey())) {
                    foundMatch = true;
                }
            }
        }

        // Same check, but for the public key
        foundMatch = true;
        while(foundMatch) {
            pubKey = ApiKey.createPublicKey();
            foundMatch = false;

            for(int i=0; i<leaderboardList.size() && !foundMatch; i++) {
                if(pubKey.equals(leaderboardList.get(i).getPubKey())) {
                    foundMatch = true;
                }
            }
        }

        retVal.setPrivKey(privKey);
        retVal.setPubKey(pubKey);
        retVal.setLastQueryTime(new Timestamp(new Date().getTime()));

        return retVal;
    }

    /**
     * This public inner class contains only the Leaderboard fields that should be released to the user via REST.
     * This is intended to be serialized into JSON and sent to the user.
     */
    public class UserFacingLeaderboard {
        private String privateKey;
        private String publicKey;

        public UserFacingLeaderboard(final String privateKey, final String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }
}