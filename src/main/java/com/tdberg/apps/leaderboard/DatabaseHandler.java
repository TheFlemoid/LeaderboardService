package com.tdberg.apps.leaderboard;

import com.tdberg.apps.leaderboard.objects.Leaderboard;
import com.tdberg.apps.leaderboard.objects.Record;
import com.tdberg.apps.leaderboard.utils.DbUtils;

import com.google.gson.Gson;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import spark.Request;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

/**
 * This creates, initializes, and handles connections to the MySQL database associated with this application instance.
 */
public class DatabaseHandler {
    private static Logger logger = LogManager.getLogger(DatabaseHandler.class);

    private Properties cfg;
    private Gson gson;
    private Connection connection;
    private String databaseUrl;
    private String databasePort;
    private String databaseName;
    private String databaseUser;
    private String databasePassword;

    private static String DATABASE_ERROR = "ERROR: Internal error while processing request";
    private static String KEY_NOT_FOUND = "ERROR: The key associated with this request could not be found";
    private static String RECORD_NOT_FOUND = "ERROR: The requested record could not be found";
    private static String INVALID_REQUEST = "ERROR: Invalid request";
    private static String JSON = "JSON";
    private static String OK = "OK";
    private static String NO_NAME = "NONAME";
    private static String LB_SERVICE_RQT_TEMPLATE = "{\"tdberg\": {\"leaderboard\": %s}}";
    private static String LB_RETRIEVE_RQT_TEMPLATE = "{\"tdberg\": {\"leaderboard\": {\"entry\": %s}}}";

    /**
     * Default constructor
     *
     * @param cfg Properties file detailing configurable values to use for this application
     */
    public DatabaseHandler(final Properties cfg) {
        this.gson = new Gson();
        this.cfg = cfg;
        databaseUrl = cfg.getProperty("databaseUrl", "localhost");
        databasePort = cfg.getProperty("databasePort", "3306");
        databaseName = cfg.getProperty("databaseName", "global_leaderboard");
        databaseUser = cfg.getProperty("databaseUser");
        databasePassword = cfg.getProperty("databasePassword");
    }

    /**
     * Initializes the SQL database for this application
     *
     * @return true if the database was successfully initialized, false otherwise
     */
    public boolean initialize() {
        try {
            DbUtils.initializeDatabase(databaseUrl, databasePort, databaseName, databaseUser, databasePassword);

            return true;
        }catch(ClassNotFoundException e) {
            logger.error("ClassNotFound error when attempting initialize database connection : " + e.getMessage());
            return false;
        }catch(SQLException e) {
            logger.error("SQL error when attempting initialize database connection : " + e.getMessage());
            return false;
        }
    }

    public Connection getDbConnection() {
        try {
            connection = DbUtils.prepareDbConnection(databaseUrl, databasePort, databaseName, databaseUser, databasePassword);
        }catch(SQLException e) {
            logger.error("SQL error when attempting to create database connection : " + e.getMessage());
        }catch(ClassNotFoundException e) {
            logger.error("ClassNotFound error when attempting to create database connection : " + e.getMessage());
        }

        return connection;
    }

    public void closeDbConnection(Connection connection) {
        try {
            connection.close();
        }catch(SQLException e) {
            logger.error("SQL error when attempting to close database connection : " + e.getMessage());
        }
    }

    /**
     * Creates a new leaderboard and returns the leaderboard as a JSON String.
     *
     * @return String describing the result of the request command
     */
    public String createNewLeaderboard() {
        try {
            Connection dbConnection = getDbConnection();
            Leaderboard leaderboard = Leaderboard.createNewLeaderboard(connection);
            DbUtils.insertLeaderboard(leaderboard, dbConnection);
            String jsonResp = gson.toJson(leaderboard.toUserFacingLeaderboard());
            closeDbConnection(dbConnection);

            return String.format(LB_SERVICE_RQT_TEMPLATE, jsonResp);
        }catch(SQLException e) {
            logger.error("SQL error when attempting to create new leaderboard : " + e.getMessage());
            return DATABASE_ERROR;
        }
    }

    /**
     * Creates a new Record based on values in the param pathArray, and adds it to the leaderboard
     * described by index 3 of the param pathArray.
     *
     * @param pathArray ADD REST request String, broken at '/' characters
     * @param request The HTTP request (used to gather IP information regarding the requester)
     * @return String describing the result of the request command
     */
    public String addRecordIntoLeaderboard(String[] pathArray, final Request request) {
        try {
            // If the request path has less than 6 elements, then this request is invalid
            // (request must at least have a name string and a score)
            if(pathArray.length < 6) {
                return INVALID_REQUEST;
            }

            Connection dbConnection = getDbConnection();
            // If we cannot resolve the API private key from the database, then return key not found
            Leaderboard leaderboard = DbUtils.getLeaderboardFromPrivKey(pathArray[3], dbConnection);
            if(leaderboard == null) {
                closeDbConnection(dbConnection);
                return KEY_NOT_FOUND;
            }

            Record record = new Record();
            record.setBoardId(leaderboard.getBoardId());
            record.setIpAddress(request.ip());
            // If the name is 'NONAME', then we don't set a value for the record name
            if(!pathArray[4].equalsIgnoreCase(NO_NAME)) {
                record.setName(pathArray[4]);
            }
            record.setScore(Integer.valueOf(pathArray[5]));

            // If the path has more than 6 elements, then element index 6 is supposed to be the time, so we assign it as such
            if(pathArray.length > 6) {
                record.setTime(Integer.valueOf(pathArray[6]));
            }

            // If the path has more than 7 elements, then element index 7 is supposed to be the notes, so we assign it as such
            if(pathArray.length > 7) {
                record.setNotes(pathArray[7]);
            }

            // If the record has no name field, we add the record
            if(record.getName() == null) {
                DbUtils.insertRecord(record, dbConnection);
                DbUtils.pruneLeaderboardByPrivKey(leaderboard.getPrivKey(), dbConnection);
                DbUtils.updateLeaderboardQueryTime(leaderboard.getBoardId(), dbConnection);
                closeDbConnection(dbConnection);
                return OK;
            }

            // We do not allow two Records to have the same 'Name' field (unless that is null) for a single leaderboard.
            // Before adding a new Record, we check the existing records to see if there is one with the same 'Name'.
            Record duplicateNameRecord = null;
            List<Record> recordList = DbUtils.getAllRecordsFromPrivKey(leaderboard.getPrivKey(), dbConnection);
            for(int i=0; i<recordList.size(); i++) {
                if(record.getName().equals(recordList.get(i).getName())) {
                    duplicateNameRecord = recordList.get(i);
                }
            }

            // If we've found another Record with the same name as this record, then we keep the record with the higher score.
            if(duplicateNameRecord != null) {
                if(record.getScore() >= duplicateNameRecord.getScore()) {
                    record.setRecordId(duplicateNameRecord.getRecordId());
                    DbUtils.updateRecord(record, dbConnection);
                }else {
                    logger.info("Received request to update record: " + record.getRecordId() + " but new score was lower than highest.");
                }
            }else {
                DbUtils.insertRecord(record, dbConnection);
            }

            DbUtils.pruneLeaderboardByPrivKey(leaderboard.getPrivKey(), dbConnection);
            DbUtils.updateLeaderboardQueryTime(leaderboard.getBoardId(), dbConnection);
            closeDbConnection(dbConnection);
            return OK;
        }catch(SQLException e) {
            logger.error("SQL error while attempting to ADD record : " + e.getMessage());
            return DATABASE_ERROR;
        }
    }

    /**
     * Returns a JSON String describing all records from a leaderboard.
     * Leaderboard is described by the param public key held at index 3 of the param pathArray.
     *
     * @param pathArray GET REST request String, broken at '/' characters
     * @return String describing the result of the request command
     */
    public String getRecordsFromLeaderboard(String[] pathArray) {
        String retVal = "";

        try {
            // If the request path has less than 5 elements, then this request is invalid
            // (request must at least have a public key and a format type)
            if (pathArray.length < 5 || !pathArray[4].equalsIgnoreCase(JSON)) {
                return INVALID_REQUEST;
            }

            // If index 4 does not contain a valid format type (right now the only valid format type
            // is 'json') then the request is invalid.
            if(!pathArray[4].equalsIgnoreCase(JSON)) {
                return INVALID_REQUEST;
            }

            // If index 5 is present (which indicates that the user wants a subset of the leaderboard scores), but
            // that index value is 0 or negative, then the request is invalid.
            if((pathArray.length > 5) && (Integer.parseInt(pathArray[5]) < 1)) {
                return INVALID_REQUEST;
            }

            Connection dbConnection = getDbConnection();
            // If we cannot resolve the API private key from the database, then return key not found
            Leaderboard leaderboard = DbUtils.getLeaderboardFromPubKey(pathArray[3], dbConnection);
            if (leaderboard == null) {
                closeDbConnection(dbConnection);
                return KEY_NOT_FOUND;
            }

            List<Record> recordList = DbUtils.getAllRecordsFromPubKey(leaderboard.getPubKey(), dbConnection);

            if(pathArray.length > 5) {
                int scoresRequested = Integer.valueOf(pathArray[5]);

                // If the path array has 6 elements, then the element at index 6 should be the number of hiscores that
                // the user wants to receive with their request (instead of receiving the full leaderboard).  For this
                // case we send back the highest 'x' scores.
                for(int i=recordList.size()-1; i>=scoresRequested; i--) {
                    recordList.remove(i);
                }
            }

            List<Record.UserFacingRecord> userFacingRecordList = new ArrayList<>();
            for(int i=0; i<recordList.size(); i++) {
                userFacingRecordList.add(recordList.get(i).toUserFacingRecord());
            }
            DbUtils.updateLeaderboardQueryTime(leaderboard.getBoardId(), dbConnection);

            logger.info("Handled request to retrieve and send records for leaderboard id : " + leaderboard.getBoardId());
            closeDbConnection(dbConnection);
            return String.format(LB_RETRIEVE_RQT_TEMPLATE, gson.toJson(userFacingRecordList));

        }catch(SQLException e) {
            logger.error("SQL error while attempting to GET records : " + e.getMessage());
            return DATABASE_ERROR;
        }
    }

    /**
     * Deletes a record from the database.  The deleted record is determined by the record ID, which should be
     * held at index 4 of the REST request.  If the record is not found, or if the record is not associated with the
     * private key described at index 3 of the REST request, the command will fail and return an error.
     *
     * @param pathArray DELETE REST request String, broken at '/' characters
     * @return String describing the result of the request command
     */
    public String deleteRecordFromLeaderboard(String[] pathArray) {
        try {
            Connection dbConnection = getDbConnection();
            // If we cannot resolve the API private key from the database, then return key not found
            Leaderboard leaderboard = DbUtils.getLeaderboardFromPrivKey(pathArray[3], dbConnection);
            if(leaderboard == null) {
                closeDbConnection(dbConnection);
                return KEY_NOT_FOUND;
            }

            int recordId = Integer.parseInt(pathArray[4]);
            Record record = DbUtils.getRecordFromBoardIdAndRecordId(leaderboard.getBoardId(), recordId, dbConnection);

            if(record == null) {
                closeDbConnection(dbConnection);
                return RECORD_NOT_FOUND;
            }

            DbUtils.deleteRecord(record.getRecordId(), dbConnection);
            DbUtils.updateLeaderboardQueryTime(leaderboard.getBoardId(), dbConnection);

            logger.info("Handled request to delete record: " + recordId + " from leaderboard: " + leaderboard.getBoardId());
            closeDbConnection(dbConnection);
            return OK;
        }catch(SQLException e) {
            logger.error("SQL error while attempting to DELETE a record : " + e.getMessage());
            return DATABASE_ERROR;
        }
    }

    /**
     * Clears a Leaderboard of all records.  The correct Leaderboard private key must be provided,
     * otherwise the command will fail and return an error.
     *
     * @param pathArray CLEAR REST request String, broken at '/' characters
     * @return String desciring the result of the request command
     */
    public String clearRecordsFromLeaderboard(String[] pathArray) {
        try {
            Connection dbConnection = getDbConnection();
            // If we cannot resolve the API private key from the database, then return key not found
            Leaderboard leaderboard = DbUtils.getLeaderboardFromPrivKey(pathArray[3], dbConnection);
            if(leaderboard == null) {
                closeDbConnection(dbConnection);
                return KEY_NOT_FOUND;
            }

            List<Record> recordList = DbUtils.getAllRecordsFromPrivKey(leaderboard.getPrivKey(), dbConnection);
            for(int i=0; i<recordList.size(); i++) {
                DbUtils.deleteRecord(recordList.get(i).getRecordId(), dbConnection);
            }
            DbUtils.updateLeaderboardQueryTime(leaderboard.getBoardId(), dbConnection);
            logger.info("Handled a request to CLEAR all records from leaderboard id: " + leaderboard.getBoardId());

            closeDbConnection(dbConnection);
            return OK;
        }catch(SQLException e) {
            logger.error("SQL error while attempting to CLEAR a Leaderboard of records : " + e.getMessage());
            return DATABASE_ERROR;
        }
    }
}