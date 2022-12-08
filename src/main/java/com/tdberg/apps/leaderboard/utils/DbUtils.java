package com.tdberg.apps.leaderboard.utils;

import com.tdberg.apps.leaderboard.objects.Leaderboard;
import com.tdberg.apps.leaderboard.objects.Record;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A collection of static methods used to create connections with and perform queries/statements on MySQL database instances.
 */
public class DbUtils {
    private static int MAX_LEADERBOARD_SIZE = 1000;

    private static String INSERT_LEADERBOARD_COMMAND = "INSERT INTO leaderboards (privkey, pubkey, last_query) " +
                                                     "VALUES (?, ?, ?)";
    private static String INSERT_RECORD_COMMAND = "INSERT INTO records (board_id, name, score, time, notes, ip_address, submission_time) " +
                                                "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static String GET_ALL_LEADERBOARDS_COMMAND = "SELECT * FROM leaderboards";
    private static String GET_LEADER_FROM_PUBKEY_TEMPLATE = "SELECT * FROM leaderboards WHERE pubkey='%s'";
    private static String GET_LEADER_FROM_PRIVKEY_TEMPLATE = "SELECT * FROM leaderboards WHERE privkey='%s'";
    private static String UPDATE_LEADERBOARD_MODTIME_TEMPLATE = "UPDATE leaderboards set last_query=? WHERE board_id=?";
    private static String MODIFY_RECORD_TEMPLATE = "UPDATE records set name=?, score=?, time=?, notes=?, ip_address=?, " +
                                                 "submission_time=? WHERE record_id=?";
    private static String DELETE_RECORD_TEMPLATE = "DELETE FROM records WHERE record_id=%d";
    private static String DELETE_BOARD_TEMPLATE = "DELETE FROM leaderboards WHERE board_id=%d";
    private static String GET_RECORDS_FROM_BOARD_ID_TEMPLATE = "SELECT * FROM records WHERE board_id=%d ORDER BY score DESC";
    private static String GET_RECORD_BY_BOARD_ID_AND_RECORD_ID_TEMPLATE = "SELECT * FROM records WHERE board_id=? AND record_id=?";

    private static final Logger logger = LogManager.getLogger(DbUtils.class);

    /**
     * Prepares a connection to the MySQL database defined by the parameter values.
     *
     * @param url String representing the URL/IP address of the database to connect to
     * @param port String representing the port to connect to on the param database
     * @param dbName String name of the datatbase to connect to, if connection to a specific database is desired
     *               NOTE: Can be null, in which case we prepare a connection to the param MySQL instance
     * @param user Username to authenticate to the param database
     * @param password Password to authenticate to the param database
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    public static Connection prepareDbConnection(final String url, final String port, final String dbName, 
            final String user, final String password) throws ClassNotFoundException, SQLException {

        Class.forName("com.mysql.cj.jdbc.Driver");

        String dbUrl = "";

        if(dbName == null) {
            dbUrl = "jdbc:mysql://" + url + ":" + port + "?useUnicode=true&characterEncoding=UTF-8&user=" + 
                            user + "&password=" + password;
        }else {
            dbUrl = "jdbc:mysql://" + url + ":" + port + "/" + dbName + 
                            "?useUnicode=true&characterEncoding=UTF-8&user=" + user + "&password=" + password;
        }

        return DriverManager.getConnection(dbUrl);
    }

    /**
     * Creates the param database if it does not already exist.
     *
     * @param dbName String representing the name of the database to create.
     * @param conn Connection to the underlying SQL instance.
     * @throws SQLException
     */
    public static void checkAndCreateDatabase(final String dbName, final Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        String sql = "CREATE DATABASE IF NOT EXISTS " + dbName;
        stmt.executeUpdate(sql);
    }

    /**
     * Checks for existence of 'leaderboards' and 'records' tables within the database held by the param Connection,
     * and creates those tables with the correct columns if they do not already exist.
     *
     * @param conn Connection to the underlying SQL instance and database
     * @throws SQLException
     */
    public static void checkAndCreateTables(final Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();

        // Check if the 'leaderboards' table exists in the database, and if it does not then create
        // table and populate it with the proper columns.
        ResultSet resultSet = metaData.getTables(null, null, "leaderboards", new String[] {"TABLE"});
        if(!resultSet.next()) {
            Statement stmt = conn.createStatement();
            String createLeaderTableSql = "CREATE TABLE leaderboards " +
                    "(board_id INTEGER NOT NULL AUTO_INCREMENT, " +
                    "privkey VARCHAR(31) NOT NULL, " +
                    "pubkey VARCHAR(20) NOT NULL, " +
                    "last_query TIMESTAMP, " +
                    "PRIMARY KEY (board_id))";
            stmt.executeUpdate(createLeaderTableSql);

            logger.info("Created table 'leaderboards' with command : " + createLeaderTableSql);
        }

        // Check if the 'records' table exists in the database, and if it does not then create
        // table and populate it with the proper columns.
        resultSet = metaData.getTables(null, null, "records", new String[] {"TABLE"});
        if(!resultSet.next()) {
            Statement stmt = conn.createStatement();
            String createRecordTableSql = "CREATE TABLE records " +
                                          "(board_id INTEGER NOT NULL, " +
                                          "record_id INTEGER NOT NULL AUTO_INCREMENT, " +
                                          "name VARCHAR(30), " +
                                          "score INTEGER, " +
                                          "time INTEGER, " +
                                          "notes TINYTEXT, " +
                                          "ip_address VARCHAR(15), " +
                                          "submission_time TIMESTAMP, " +
                                          "PRIMARY KEY (record_id), " +
                                          "FOREIGN KEY (board_id) REFERENCES leaderboards(board_id))";
            stmt.executeUpdate(createRecordTableSql);

            logger.info("Created table 'records' with command : " + createRecordTableSql);
        }
    }

    /**
     * Performs all of the work necessary to initialize the SQL database entity.  Does not overwrite/delete
     * any data that may exist, so this method is safe to always call on application startup.  Connects to the
     * SQL instance, creates the param DB if it does not exist, and creates the 'leaderboards' and 'records' tables
     * with the correct columns if they do not exist.
     *
     * @param url String representing the URL for the SQL instance
     * @param port String representing the port to use for the SQL instance
     * @param dbName String name of the SQL database
     * @param user To use to authenticate to the SQL instance
     * @param password To use to authenticate to the SQL instance
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    public static void initializeDatabase(final String url, final String port, final String dbName, 
            final String user, final String password) throws ClassNotFoundException, SQLException {

        // Connect to the SQL instance and create our DB if it doesn't already exist (we do not overwrite here)
        Connection conn = prepareDbConnection(url, port, null, user, password); 
        checkAndCreateDatabase(dbName, conn);
        conn.close();

        // Check if the 'leaderboards' and 'records' tables exist in the DB and create them if they don't
        conn = prepareDbConnection(url, port, dbName, user, password);
        checkAndCreateTables(conn);
        conn.close();
    }

    /**
     * Inserts the param leaderboard as a new leaderboard entry into the leaderboards DB table.
     *
     * @param leaderboard Leaderboard to insert into database
     * @param conn Connection to use to connect to database
     * @throws SQLException
     */
    public static void insertLeaderboard(final Leaderboard leaderboard, final Connection conn) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(INSERT_LEADERBOARD_COMMAND);

        preparedStatement.setString(1, leaderboard.getPrivKey());
        preparedStatement.setString(2, leaderboard.getPubKey());
        // NOTE: Just creating and setting a new time stamp.  May want to change this later.
        preparedStatement.setTimestamp(3, new Timestamp(new Date().getTime()));

        preparedStatement.executeUpdate();
        logger.info("Inserted new leaderboard pubkey: " + leaderboard.getPubKey());
    }

    /**
     * Inserts the param record as a new record entry into the records DB table.
     *
     * @param record Record to insert into database
     * @param conn Connection to use to connect to database
     * @throws SQLException
     */
    public static void insertRecord(final Record record, final Connection conn) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(INSERT_RECORD_COMMAND);

        preparedStatement.setInt(1, record.getBoardId());
        if(record.getName() != null) {
            preparedStatement.setString(2, record.getName());
        }else {
            preparedStatement.setNull(2, Types.VARCHAR);
        }
        preparedStatement.setInt(3, record.getScore());
        preparedStatement.setInt(4, record.getTime());
        if(record.getNotes() != null) {
            preparedStatement.setString(5, record.getNotes());
        }else {
            preparedStatement.setNull(5, Types.VARCHAR);
        }
        if(record.getIpAddress() != null) {
            preparedStatement.setString(6, record.getIpAddress());
        }else {
            preparedStatement.setNull(6, Types.VARCHAR);
        }
        // NOTE: Just creating and setting a new time stamp.  May want to change this later.
        preparedStatement.setTimestamp(7, new Timestamp(new Date().getTime()));

        preparedStatement.executeUpdate();
        logger.info("Inserted new record for leaderboard ID:" + record.getBoardId());
    }

    /**
     * Updates the param record to new values.
     * NOTE: The recordId in the param Record should be the ID that we want to update.  The boardId is not modified.
     *       The rest of the fields will all be modified.
     * @param record Record to update
     * @param conn Connection to the database to use for the query
     * @throws SQLException
     */
    public static void updateRecord(final Record record, final Connection conn) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(MODIFY_RECORD_TEMPLATE);
        // Name is allowed to be null, so we check for that here
        if(record.getName() != null) {
            preparedStatement.setString(1, record.getName());
        }else {
            preparedStatement.setNull(1, Types.VARCHAR);
        }
        preparedStatement.setInt(2, record.getScore());
        preparedStatement.setInt(3, record.getTime());
        // Notes is allowed to be null
        if(record.getNotes() != null) {
            preparedStatement.setString(4, record.getNotes());
        }else {
            preparedStatement.setNull(4, Types.VARCHAR);
        }
        // IP address is allowed to be null
        if(record.getIpAddress() != null) {
            preparedStatement.setString(5, record.getIpAddress());
        }else {
            preparedStatement.setNull(5, Types.VARCHAR);
        }
        preparedStatement.setTimestamp(6, new Timestamp(new Date().getTime()));
        preparedStatement.setInt(7, record.getRecordId());

        preparedStatement.executeUpdate();
        logger.info("Updated record id: " + record.getRecordId());
    }

    /**
     * Return a Leaderboard from the database based on the param public key
     *
     * @param pubkey String public key to check against
     * @param conn Connection to the database to use for query
     * @return Leaderboard associated with the param pubkey, or null if Leaderboard doesn't exist
     * @throws SQLException
     */
    public static Leaderboard getLeaderboardFromPubKey(final String pubkey, final Connection conn) throws SQLException {
        Leaderboard retVal = null;
        String getFromPubKeyCommand = String.format(GET_LEADER_FROM_PUBKEY_TEMPLATE, pubkey);

        Statement selectStatement = conn.createStatement();
        ResultSet rs = selectStatement.executeQuery(getFromPubKeyCommand);

        // If the result set doesn't contain any rows, the while loop skips and we just return null.  Otherwise, we
        // parse the result set for the resultant object.
        while(rs.next()) {
            retVal = new Leaderboard();
            retVal.setBoardId(rs.getInt(1));
            retVal.setPrivKey(rs.getString(2));
            retVal.setPubKey(rs.getString(3));
            retVal.setLastQueryTime(rs.getTimestamp(4));
        }

        return retVal;
    }

    /**
     * Return a Leaderboard from the database based on the param public key
     *
     * @param privkey String public key to check against
     * @param conn Connection to the database to use for query
     * @return Leaderboard associated with the param pubkey, or null if Leaderboard doesn't exist
     * @throws SQLException
     */
    public static Leaderboard getLeaderboardFromPrivKey(final String privkey, final Connection conn) throws SQLException {
        Leaderboard retVal = null;
        String getFromPrivKeyCommand = String.format(GET_LEADER_FROM_PRIVKEY_TEMPLATE, privkey);

        Statement selectStatement = conn.createStatement();
        ResultSet rs = selectStatement.executeQuery(getFromPrivKeyCommand);

        // If the result set doesn't contain any rows, the while loop skips and we just return null.  Otherwise, we
        // parse the result set for the resultant object.
        while(rs.next()) {
            retVal = new Leaderboard();
            retVal.setBoardId(rs.getInt(1));
            retVal.setPrivKey(rs.getString(2));
            retVal.setPubKey(rs.getString(3));
            retVal.setLastQueryTime(rs.getTimestamp(4));
        }

        return retVal;
    }

    /**
     * Deletes the param Record from the database
     * NOTE: This function works by deleting the record located at the param Record recordId
     *
     * @param record Record to delete
     * @param conn Connection to the database to use for query
     * @throws SQLException
     */
    public static void deleteRecord(final Record record, final Connection conn) throws SQLException {
        String deleteCommand = String.format(DELETE_RECORD_TEMPLATE, record.getRecordId());

        Statement deleteStatement = conn.createStatement();
        deleteStatement.executeUpdate(deleteCommand);
        logger.info("Deleted record: " + record.getRecordId());
    }

    /**
     * Deletes the Record located at the param recordId from the database
     *
     * @param recordId recordId for the Record to delete
     * @param conn Connection to the database to use for query
     * @throws SQLException
     */
    public static void deleteRecord(final int recordId, final Connection conn) throws SQLException {
        String deleteCommand = String.format(DELETE_RECORD_TEMPLATE, recordId);

        Statement deleteStatement = conn.createStatement();
        deleteStatement.executeUpdate(deleteCommand);
        logger.info("Deleted record: " + recordId);
    }

    /**
     * Deletes the param Leaderboard from the database, and deletes all Records associated with that
     * Leaderboard from the database.
     *
     * @param privKey Private API key of the Leaderboard to delete from database
     * @param conn Connection to the database to use for query
     * @throws SQLException
     */
    public static void deleteLeaderboard(final String privKey, final Connection conn) throws SQLException {
        Leaderboard boardFromDb = getLeaderboardFromPrivKey(privKey, conn);
        if(boardFromDb == null) {
            return;
        }

        List<Record> boardRecords = getAllRecordsFromPubKey(boardFromDb.getPubKey(), conn);

        System.out.println("Records found for deletion:");
        for(int i=0; i<boardRecords.size(); i++) {
            System.out.println(boardRecords.get(i).toString());
            deleteRecord(boardRecords.get(i), conn);
        }

        String deleteLbQuery = String.format(DELETE_BOARD_TEMPLATE, boardFromDb.getBoardId());
        Statement deleteLbStatement = conn.createStatement();
        deleteLbStatement.executeUpdate(deleteLbQuery);
    }

    /**
     * Updates the param Leaderboards last query time to the current time.
     *
     * @param boardId boardId of the Leaderboard that we should update
     * @param conn Connection to the database to use for the query
     * @throws SQLException
     */
    public static void updateLeaderboardQueryTime(final int boardId, final Connection conn) throws SQLException {
        PreparedStatement preparedStatement = conn.prepareStatement(UPDATE_LEADERBOARD_MODTIME_TEMPLATE);
        preparedStatement.setTimestamp(1, new Timestamp(new Date().getTime()));
        preparedStatement.setInt(2, boardId);
        preparedStatement.executeUpdate();
    }

    /**
     * Returns a List containing all Leaderboards in the param database
     *
     * @param conn Connection to the database to use for query
     * @return List containing all Leaderboards in the database
     * @throws SQLException
     */
    public static List<Leaderboard> getAllLeaderboards(final Connection conn) throws SQLException {
        List<Leaderboard> leaderboardList = new ArrayList<>();

        Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(GET_ALL_LEADERBOARDS_COMMAND);

        Leaderboard leaderboard = null;

        while(rs.next()) {
            leaderboard = new Leaderboard();
            leaderboard.setBoardId(rs.getInt(1));
            leaderboard.setPrivKey(rs.getString(2));
            leaderboard.setPubKey(rs.getString(3));
            leaderboard.setLastQueryTime(rs.getTimestamp(4));
            leaderboardList.add(leaderboard);
        }

        return leaderboardList;
    }

    /**
     * Returns all records associated with the param public API key
     *
     * @param pubkey Public API key of leaderboard
     * @param conn Connection to the database to use for query
     * @return List containing all records associated with the param public key
     * @throws SQLException
     */
    public static List<Record> getAllRecordsFromPubKey(final String pubkey, final Connection conn) throws SQLException {
        List<Record> recordList = new ArrayList<>();
        Leaderboard leaderboard = getLeaderboardFromPubKey(pubkey, conn);

        if(leaderboard == null) {
            return recordList;
        }
        String getRecordsQuery = String.format(GET_RECORDS_FROM_BOARD_ID_TEMPLATE, leaderboard.getBoardId());

        Statement statement = conn.createStatement();
        ResultSet rs = statement.executeQuery(getRecordsQuery);

        Record record = null;

        while(rs.next()) {
            record = new Record(rs.getInt(1),        // board_id
                                rs.getInt(2),        // record_id
                                rs.getString(3),     // name
                                rs.getInt(4),        // score
                                rs.getInt(5),        // time
                                rs.getString(6),     // notes
                                rs.getTimestamp(8),  // submission_time
                                rs.getString(7));    // ip_address
            recordList.add(record);
        }

        return recordList;
    }

    /**
     * Returns all records associated with the param private API key
     *
     * @param privkey Public API key of leaderboard
     * @param conn Connection to the database to use for query
     * @return List containing all records associated with the param public key
     * @throws SQLException
     */
    public static List<Record> getAllRecordsFromPrivKey(final String privkey, final Connection conn) throws SQLException {
        List<Record> recordList = new ArrayList<>();
        Leaderboard leaderboard = getLeaderboardFromPrivKey(privkey, conn);

        if(leaderboard == null) {
            return recordList;
        }
        return getAllRecordsFromPubKey(leaderboard.getPubKey(), conn);
    }

    /**
     * Returns the Record associated with the param board ID and param record ID.
     * NOTE: Will return null if the record is not found
     *
     * @param boardId BoardId of the target record
     * @param recordId RecordId of the target record
     * @param conn Connection to the database to use for query
     * @return Target Record as a Record object, or null if Record not found
     * @throws SQLException
     */
    public static Record getRecordFromBoardIdAndRecordId(final int boardId, final int recordId, final Connection conn) throws SQLException {
        Record record = null;

        PreparedStatement preparedStatement = conn.prepareStatement(GET_RECORD_BY_BOARD_ID_AND_RECORD_ID_TEMPLATE);
        preparedStatement.setInt(1, boardId);
        preparedStatement.setInt(2, recordId);
        ResultSet rs = preparedStatement.executeQuery();

        while(rs.next()) {
            record = new Record(rs.getInt(1),   // board_id
                    rs.getInt(2),               // record_id
                    rs.getString(3),            // name
                    rs.getInt(4),               // score
                    rs.getInt(5),               // time
                    rs.getString(6),            // notes
                    rs.getTimestamp(8),         // submission_time
                    rs.getString(7));           // ip_address
        }

        return record;
    }

    /**
     * Checks if the Leaderboard defined by the param private API key is over the maximum allowed Leaderboard size,
     * and if so removes the lowest scoring records until the leaderboard is back within the allowable size limit.
     *
     * @param privKey Private API key of the leaderboard
     * @param conn Connection to the database to user for query
     * @throws SQLException
     */
    public static void pruneLeaderboardByPrivKey(final String privKey, final Connection conn) throws SQLException {
        List<Record> recordList = new ArrayList<>();
        recordList = getAllRecordsFromPrivKey(privKey, conn);

        if(recordList.size() > MAX_LEADERBOARD_SIZE) {
            for(int i=MAX_LEADERBOARD_SIZE; i<recordList.size(); i++) {
                deleteRecord(recordList.get(i).getRecordId(), conn);
            }
        }
    }
}