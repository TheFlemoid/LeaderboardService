package com.tdberg.apps.leaderboard;

import static spark.Spark.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import spark.Request;

import java.util.Properties;

public class LeaderboardService {
    private static Logger logger = LogManager.getLogger(LeaderboardService.class);
    private DatabaseHandler dbHandler;
    private Properties cfg;

    private static String COMMAND_NOT_FOUND_ERROR = "ERROR: Request type not recognized";
    private static String ERROR = "ERROR";
    private static String OK = "OK";
    private static String CREATE_COMMAND = "CREATE";
    private static String ADD_COMMAND = "ADD";
    private static String GET_COMMAND = "GET";
    private static String DELETE_COMMAND = "DELETE";
    private static String CLEAR_COMMAND = "CLEAR";

    public LeaderboardService(Properties cfg) {
        this.cfg = cfg;
        dbHandler = new DatabaseHandler(cfg);

    }

    /**
     * Initializes the LeaderboardService class.  Currently this just initializes the dbHandler, I broke
     * this into its own method in case we wanted to add more functionality later.
     *
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        return dbHandler.initialize();
    }

    /**
     * Runs the Leaderboard service REST API, and commences waiting for HTTP REST requests.
     */
    public void runService() {
        enableCORS("*", "GET, OPTIONS", "Accept, X-Access-Token, X-Application-Name, X-Request-Sent-Time");

        get("/", (request, response) -> {
            response.status(200);

            return "Hello";
        });

        get("/lb/*", (request, response) -> {
            response.status(200);

            // This is a little gross, but I wasn't sure a better way to do it.  If the returned String is an ERROR,
            // we set the response to type HTML, otherwise it's a JSON response.
            String responseString = processLeaderboardRequest(request);
            if(responseString.contains(ERROR) || responseString.contains(OK)) {
                response.type("text/html");
            }else {
                response.type("application/json");
            }

            return responseString;
        });
    }

    /**
     * This method handles a received REST request.  It returns back a String, to be used as the requests response payload.
     * NOTE: In general, all HTTP REST requests received by the application flow down to here.  They are then flowed out
     *       to whatever business logic they are related to.  In performing business logic, the result can either be
     *       successful or error.  Either way, a String is generated describing the result (either HTTP text or JSON),
     *       and the result payload is flowed back up as the return of this method.
     *
     * @param request Spark HTTP Request
     * @return String String response to be used at the requests response payload
     */
    public String processLeaderboardRequest(final Request request) {
        // Spliting the PATH in this way will result in the following substrings:
        //      Index 0 will always be blank
        //      Index 1 will be the next forward slash terminated value.
        //      Index 2 etc
        //      ex.  /lb/ADD/<privkey>/<name>/<score>/<time>/<notes>
        //           /lb/GET/<pubkey>/json
        // NOTE The forward slashes will be removed when the String is split, we don't need to replace them out
        String[] pathArray = request.pathInfo().split("/");
        String retVal = COMMAND_NOT_FOUND_ERROR;

        if(pathArray.length == 3) {
            retVal = processServiceRequest(pathArray);
        }else if(pathArray.length > 3) {
            retVal = processLeaderboardModificationRequest(pathArray, request);
        }else {
            // Default case, currently just returns back request not recognized set in retVal
        }

        return retVal;
    }

    /**
     * This method handles 'service requests' (poorly named).  Specifically, these requests are not tied to a
     * specific leaderboad (eg. create leaderboard).
     *
     * @param pathArray String array of the URL request, split by '/' characters
     * @return String response for the request
     */
    public String processServiceRequest(final String[] pathArray) {
        String retVal = COMMAND_NOT_FOUND_ERROR;

        if(pathArray[2].equalsIgnoreCase(CREATE_COMMAND)) {
            retVal = dbHandler.createNewLeaderboard();
        }
        return retVal;
    }

    /**
     * This method handles 'leaderboard modification requests' (also poorly named).  These requests are aimed at
     * specific leaderboards, and either insert data into that leaderboard or read data from it.
     *
     * @param pathArray String array of the URL request, split by '/' characters
     * @param request The HTTP request (used to capture user request IP address data)
     * @return String response for the request
     */
    public String processLeaderboardModificationRequest(final String[] pathArray, final Request request) {
        String retVal = COMMAND_NOT_FOUND_ERROR;

        if(pathArray[2].equalsIgnoreCase(ADD_COMMAND)) {
            retVal = dbHandler.addRecordIntoLeaderboard(pathArray, request);
        }else if(pathArray[2].equalsIgnoreCase(GET_COMMAND)) {
            retVal = dbHandler.getRecordsFromLeaderboard(pathArray);
        }else if(pathArray[2].equalsIgnoreCase(DELETE_COMMAND)) {
            retVal = dbHandler.deleteRecordFromLeaderboard(pathArray);
        }else if(pathArray[2].equalsIgnoreCase(CLEAR_COMMAND)) {
            retVal = dbHandler.clearRecordsFromLeaderboard(pathArray);
        }

        return retVal;
    }

    /**
     * Enables CORS on requests. This method is an initialization method and should be called once.
     * @param origin String defining what origins should be allowed for CORS
     * @param methods String defining what HTTP methods should be allowed for CORS
     * @param headers String defining Access-Control headers that should be applied upon responses.
     */
    private static void enableCORS(final String origin, final String methods, final String headers) {

        options("/lb/*", (request, response) -> {

            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
        });
    }
}
