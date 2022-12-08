These are just notes for now, will make full README with API documentation later.

- All queries are handled via GET requst.
- All queries are prepended by '/lb/'.
- To create a leaderboard, the request is: /lb/create
- To add a record, the command is: /lb/add/<private_key>/name/score/time/notes
- To retrieve records from the database, the command is: /lb/get/<public_key>/json
- To delete a record from the database, the command is: /lb/delete/<private_key>/record_id
- When adding a record, name, time, and notes is allowed to be null.  For time and notes to be null, just do not include those fields in the request.  For name to be null, the keyword 'NONAME' must be inserted into the name field.
- For named records (with a name associated with them), only one record per name is allowed.  If a record is submitted for a name that already exists on that leaderboard, the record with the higher SCORE value is kept.
- A leaderboard can only have up to 1000 records associated with it (defined by a static integer within the DbUtils class).  If records are added so that the leaderboard would have more then the maximum allowable records, then upon record insertion records are deleted from the leaderboard so that the leaderboard only contains the maximum allowable number of records.  Records are deleted starting with the records with the lowest score.
