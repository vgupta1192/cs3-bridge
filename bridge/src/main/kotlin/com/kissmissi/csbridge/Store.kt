package com.kissmissi.csbridge

import java.sql.DriverManager
import java.io.File

object Store {
    private val db by lazy {
        Cfg.dataDir.mkdirs()
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite:${File(Cfg.dataDir, "csbridge.sqlite3").absolutePath}")
        c.createStatement().use { st ->
            st.executeUpdate("CREATE TABLE IF NOT EXISTS kv (k TEXT PRIMARY KEY, v TEXT NOT NULL, ts INTEGER NOT NULL)")
            st.executeUpdate("CREATE INDEX IF NOT EXISTS kv_ts ON kv(ts)")
        }
        c
    }


    fun get(k: String, ttlMs: Long): String? = synchronized(db) {
        db.prepareStatement("SELECT v, ts FROM kv WHERE k = ?").use { ps ->
            ps.setString(1, k)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return@use null
                if (System.currentTimeMillis() - rs.getLong(2) > ttlMs) return@use null
                rs.getString(1)
            }
        }
    }


    fun put(k: String, v: String) {
        synchronized(db) {
            db.prepareStatement("INSERT OR REPLACE INTO kv (k, v, ts) VALUES (?, ?, ?)").use { ps ->
                ps.setString(1, k)
                ps.setString(2, v)
                ps.setLong(3, System.currentTimeMillis())
                ps.executeUpdate()
            }
        }
    }


    fun cleanup() {
        synchronized(db) {
            val cutoff = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
            db.prepareStatement("DELETE FROM kv WHERE ts < ?").use { it.setLong(1, cutoff); it.executeUpdate() }
        }
    }
}
