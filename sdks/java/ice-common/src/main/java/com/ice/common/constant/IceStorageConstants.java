package com.ice.common.constant;

/**
 * File system storage related constants.
 *
 * @author waitmoon
 */
public final class IceStorageConstants {

    private IceStorageConstants() {
    }

    // Directory names
    public static final String DIR_APPS = "apps";
    public static final String DIR_BASES = "bases";
    public static final String DIR_CONFS = "confs";
    public static final String DIR_UPDATES = "updates";
    public static final String DIR_VERSIONS = "versions";
    public static final String DIR_HISTORY = "history";
    public static final String DIR_CLIENTS = "clients";

    // File names
    public static final String FILE_APP_ID = "_id.txt";
    public static final String FILE_BASE_ID = "_base_id.txt";
    public static final String FILE_CONF_ID = "_conf_id.txt";
    public static final String FILE_PUSH_ID = "_push_id.txt";
    public static final String FILE_VERSION = "version.txt";

    // File suffixes
    public static final String SUFFIX_JSON = ".json";
    public static final String SUFFIX_TMP = ".tmp";
    public static final String SUFFIX_UPD = "_upd.json";

    // Status values
    public static final byte STATUS_ONLINE = 1;
    public static final byte STATUS_OFFLINE = 0;
    public static final byte STATUS_DELETED = -1;

    // Default configuration values
    public static final int DEFAULT_POLL_INTERVAL_SECONDS = 5;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 10;
    public static final int DEFAULT_CLIENT_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_VERSION_RETENTION = 1000;
}

