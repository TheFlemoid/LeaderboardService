package com.tdberg.apps.leaderboard.objects;

import java.util.Date;
import java.sql.Timestamp;

/**
 * Class contains a Hi-Score record that is stored in the application database.
 * NOTE: This is a private facing instance of a record, since it contains the boardId primary key (which shouldn't be
 *       sent publicly).
 */
public class Record {
    private int boardId;
    private int recordId;
    private String name;
    private int score;
    private int time;
    private String notes;
    private Timestamp submissionTime;
    private String ipAddress;

    /**
     * Default constructor
     */
    public Record() {
    }

    /**
     * Filled out constructor
     *
     * @param boardId Leaderboard ID (primary key) for this record
     * @param recordId Record ID (unique identifier) for this record
     * @param name Player name for this record
     * @param score Player score for this record
     * @param time Player time for this record
     * @param notes Notes string for this record
     * @param submissionTime Time that this record was submitted to the service
     * @param ipAddress String representing the IP address that this record submission was received from
     */
    public Record(final int boardId, final int recordId, final String name, final int score, final int time, final String notes,
                  final Timestamp submissionTime, final String ipAddress) {
        this.boardId = boardId;
        this.recordId = recordId;
        this.name = name;
        this.score = score;
        this.time = time;
        this.notes = notes;
        this.submissionTime = submissionTime;
        this.ipAddress = ipAddress;
    }

    public void setBoardId(final int boardId) {
        this.boardId = boardId;
    }

    public int getBoardId() {
        return boardId;
    }

    public void setRecordId(final int recordId) {
        this.recordId = recordId;
    }

    public int getRecordId() {
        return recordId;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setScore(final int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }

    public void setTime(final int time) {
        this.time = time;
    }

    public int getTime() {
        return time;
    }

    public void setNotes(final String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }

    public void setSubmissionTime(final Timestamp submissionTime) {
        this.submissionTime = submissionTime;
    }

    public Date getSubmissionTime() {
        return submissionTime;
    }

    public void setIpAddress(final String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Returns a UserFacingRecord, serializable object that contains the user releasable fields from this Record.
     *
     * @return a UserFacingRecord object that contains the user releasable fields from this Record.
     */
    public UserFacingRecord toUserFacingRecord() {
        return new UserFacingRecord(recordId, name, score, time, notes, submissionTime);
    }

    @Override
    public String toString() {
        return "Record " + recordId + " on Leaderboard " + boardId + " name: " + name + " score: " + score +
               " time: " + time + " IP address " + ipAddress + " time entered " + submissionTime.toString();
    }

    /**
     * This public inner class contains only the Record fields that should be released to the user via REST.
     * This is intended to be serialized into JSON and sent to the user.
     */
    public class UserFacingRecord {
        private int recordId;
        private String name;
        private int score;
        private int time;
        private String notes;
        private Timestamp recordTime;

        public UserFacingRecord(final int recordId, final String name, final int score, final int time,
                                final String notes, final Timestamp recordTime) {
            this.recordId = recordId;
            this.name = name;
            this.score = score;
            this.time = time;
            this.notes = notes;
            this.recordTime = recordTime;
        }
    }
}