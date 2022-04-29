package org.tgforever.tgfcombat;

import com.google.common.collect.ImmutableMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the SQLite database
 */
public class Database {
    private static Database instance;

    private final JavaPlugin plugin;
    private final Path dbPath;
    private Connection connection;

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS toggle_states (" +
            "uuid VARCHAR(32) PRIMARY KEY," +
            "state BOOLEAN" +
            ")";

    public static Database getInstance(JavaPlugin plugin) {
        if (instance == null) instance = new Database(plugin);
        return instance;
    }

    private Database(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder().toPath().resolve("toggle.db");

        connection = getConnection();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE);
        } catch (SQLException e) {
            plugin.getLogger().warning("could not create table for toggle.db");
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (!Files.exists(dbPath)) Files.createFile(dbPath);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create database toggle.db");
        }

        try {
            if (connection != null && !connection.isClosed()) return connection;
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            return connection;
        } catch (SQLException e) {
            plugin.getLogger().warning("encountered an exception: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public Map<UUID, Boolean> getStates() {
        try (Statement statement = getConnection().createStatement()) {
            ImmutableMap.Builder<UUID, Boolean> builder = ImmutableMap.builder();

            statement.execute("SELECT * FROM toggle_states");
            ResultSet set = statement.getResultSet();
            while (set.next()) {
                builder.put(UUID.fromString(set.getString("uuid")), set.getBoolean("state"));
            }

            return builder.build();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not fetch data from the database");
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    public void edit(UUID uuid, boolean state) {
        try (PreparedStatement statement = getConnection().prepareStatement("INSERT OR REPLACE INTO toggle_states (uuid, state) VALUES (?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setBoolean(2, state);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save data in the database");
            e.printStackTrace();
        }
    }
}
