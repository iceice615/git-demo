package com.example.orienteeringassistant.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.orienteeringassistant.model.Checkin;
import com.example.orienteeringassistant.model.Checkpoint;
import com.example.orienteeringassistant.model.Competition;
import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.model.TrackPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 数据库帮助类
 * 共 5 张表（账号体系在云端，本地不存用户与口令）：
 *   competitions 赛事表
 *   checkpoints  检查点表（属于某场赛事，有起点/检查点/终点类型）
 *   race_records 比赛成绩表（finished=0 表示比赛进行中，可断点续赛）
 *   checkins     打卡记录表（属于某次比赛，成绩详情时间轴的数据源）
 *
 * v2 变更：checkpoints 增加 type，competitions 增加 time_limit，
 *          race_records 增加 mode / score / finished
 * v3 变更：增加 track_points（GPS 轨迹）
 * v4 变更：competitions 增加 cloud_id
 * v5 变更：competitions 增加 valid_minutes / penalty_per_min / start_time / end_time，
 *          checkpoints 增加 required（必达/自由），race_records 增加 team_name。
 *          升级采用 ALTER TABLE，不再清空历史数据。
 * v6 变更：competitions 增加 join_key（6位加入码），race_records 增加 cloud_race_id
 *          （云端成绩id，本地轨迹缺失时可从云端拉取）。
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "orienteering.db";
    private static final int DB_VERSION = 7;

    private static DBHelper instance;

    /** 单例获取，避免重复打开数据库 */
    public static synchronized DBHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DBHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 本地不再建 users 表：账号体系在云端，手机里不留任何口令
        createBusinessTables(db);
    }

    /** 业务表（升级时只重建这些，保留赛事与成绩数据） */
    private void createBusinessTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE competitions(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "mode TEXT NOT NULL," +
                "date TEXT," +
                "description TEXT," +
                "status TEXT DEFAULT '未开始'," +
                "time_limit INTEGER DEFAULT 60," +
                "valid_minutes INTEGER DEFAULT 0," +
                "penalty_per_min INTEGER DEFAULT 0," +
                "start_time INTEGER DEFAULT 0," +
                "end_time INTEGER DEFAULT 0," +
                "cloud_id INTEGER," +
                "join_key TEXT," +
                "created_at INTEGER)");

        // 同一场赛事内检查点代码唯一
        db.execSQL("CREATE TABLE checkpoints(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "competition_id INTEGER NOT NULL," +
                "code TEXT NOT NULL," +
                "name TEXT NOT NULL," +
                "seq INTEGER NOT NULL," +
                "points INTEGER DEFAULT 10," +
                "type INTEGER DEFAULT 2," +
                "required INTEGER DEFAULT 1," +
                "lat REAL," +
                "lng REAL," +
                "UNIQUE(competition_id, code))");

        db.execSQL("CREATE TABLE race_records(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "competition_id INTEGER NOT NULL," +
                "user_id INTEGER NOT NULL," +
                "username TEXT," +
                "mode TEXT," +
                "team_name TEXT," +
                "start_time INTEGER," +
                "end_time INTEGER," +
                "elapsed INTEGER," +
                "score INTEGER DEFAULT 0," +
                "valid INTEGER DEFAULT 0," +
                "finished INTEGER DEFAULT 0," +
                "uploaded INTEGER DEFAULT 0," +
                "cloud_race_id INTEGER DEFAULT 0," +
                "message TEXT)");

        db.execSQL("CREATE TABLE checkins(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "race_id INTEGER NOT NULL," +
                "checkpoint_id INTEGER," +
                "code TEXT," +
                "seq INTEGER," +
                "ts INTEGER," +
                "valid INTEGER DEFAULT 1)");

        // GPS 轨迹点（WGS84 坐标）
        db.execSQL("CREATE TABLE track_points(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "race_id INTEGER NOT NULL," +
                "lat REAL NOT NULL," +
                "lng REAL NOT NULL," +
                "ts INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // v1（无业务表结构差异太大）直接重建业务表
            dropBusinessTables(db);
            createBusinessTables(db);
            return;
        }
        // v2 -> v3：加轨迹表
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS track_points(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "race_id INTEGER NOT NULL," +
                    "lat REAL NOT NULL," +
                    "lng REAL NOT NULL," +
                    "ts INTEGER NOT NULL)");
        }
        // v3 -> v4：competitions 加 cloud_id
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE competitions ADD COLUMN cloud_id INTEGER");
        }
        // v4 -> v5：积分赛规则、预约时间、必达/自由点、队伍名（保留历史数据）
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE competitions ADD COLUMN valid_minutes INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE competitions ADD COLUMN penalty_per_min INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE competitions ADD COLUMN start_time INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE competitions ADD COLUMN end_time INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE checkpoints ADD COLUMN required INTEGER DEFAULT 1");
            db.execSQL("ALTER TABLE race_records ADD COLUMN team_name TEXT");
        }
        // v5 -> v6：加入码 + 云端成绩id（保留历史数据）
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE competitions ADD COLUMN join_key TEXT");
            db.execSQL("ALTER TABLE race_records ADD COLUMN cloud_race_id INTEGER DEFAULT 0");
        }
        // v6 -> v7：删除本地明文口令表（旧版本把账号密码存在手机里，属隐私隐患）
        if (oldVersion < 7) {
            db.execSQL("DROP TABLE IF EXISTS users");
        }
    }

    private void dropBusinessTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS track_points");
        db.execSQL("DROP TABLE IF EXISTS checkins");
        db.execSQL("DROP TABLE IF EXISTS race_records");
        db.execSQL("DROP TABLE IF EXISTS checkpoints");
        db.execSQL("DROP TABLE IF EXISTS competitions");
    }

    // ==================== 赛事 ====================

    public long insertCompetition(Competition comp) {
        ContentValues values = competitionValues(comp);
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("competitions", null, values);
    }

    public int updateCompetition(Competition comp) {
        return getWritableDatabase().update("competitions", competitionValues(comp),
                "_id=?", new String[]{String.valueOf(comp.getId())});
    }

    private ContentValues competitionValues(Competition comp) {
        ContentValues values = new ContentValues();
        values.put("name", comp.getName());
        values.put("mode", comp.getMode());
        values.put("date", comp.getDate());
        values.put("description", comp.getDescription());
        values.put("status", comp.getStatus());
        values.put("time_limit", comp.getTimeLimit());
        values.put("valid_minutes", comp.getValidMinutes());
        values.put("penalty_per_min", comp.getPenaltyPerMin());
        values.put("start_time", comp.getStartTime());
        values.put("end_time", comp.getEndTime());
        if (comp.getCloudId() != null) {
            values.put("cloud_id", comp.getCloudId());
        } else {
            values.putNull("cloud_id");
        }
        if (comp.getJoinKey() != null) {
            values.put("join_key", comp.getJoinKey());
        } else {
            values.putNull("join_key");
        }
        return values;
    }

    /** 删除赛事，同时删除其检查点、成绩与打卡记录 */
    public void deleteCompetition(long competitionId) {
        SQLiteDatabase db = getWritableDatabase();
        String id = String.valueOf(competitionId);
        db.execSQL("DELETE FROM checkins WHERE race_id IN " +
                "(SELECT _id FROM race_records WHERE competition_id=" + id + ")");
        db.delete("race_records", "competition_id=?", new String[]{id});
        db.delete("checkpoints", "competition_id=?", new String[]{id});
        db.delete("competitions", "_id=?", new String[]{id});
    }

    public List<Competition> getCompetitions() {
        List<Competition> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM competitions ORDER BY _id DESC", null);
        while (c.moveToNext()) {
            list.add(cursorToCompetition(c));
        }
        c.close();
        return list;
    }

    public Competition getCompetition(long id) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM competitions WHERE _id=?",
                new String[]{String.valueOf(id)});
        Competition comp = null;
        if (c.moveToFirst()) {
            comp = cursorToCompetition(c);
        }
        c.close();
        return comp;
    }

    /**
     * 同组赛事：组别批量创建时名称为「主名称 · 组别名」，按主名称前缀匹配。
     * 返回除自身外的所有同组赛事（含主赛事）。
     */
    public List<Competition> findSiblings(Competition comp) {
        List<Competition> list = new ArrayList<>();
        String name = comp.getName() == null ? "" : comp.getName();
        int idx = name.indexOf(" · ");
        String prefix = idx > 0 ? name.substring(0, idx) : name;
        if (prefix.isEmpty()) {
            return list;
        }
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM competitions WHERE (name=? OR name LIKE ?) AND _id<>? ORDER BY _id ASC",
                new String[]{prefix, prefix + " · %", String.valueOf(comp.getId())});
        while (c.moveToNext()) {
            list.add(cursorToCompetition(c));
        }
        c.close();
        return list;
    }

    /** 按云端赛事 id 查本地缓存（成员下载赛道用） */
    public Competition findCompetitionByCloudId(long cloudId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM competitions WHERE cloud_id=?",
                new String[]{String.valueOf(cloudId)});
        Competition comp = null;
        if (c.moveToFirst()) {
            comp = cursorToCompetition(c);
        }
        c.close();
        return comp;
    }

    /** 清空某赛事的全部检查点（重新下载赛道前用） */
    public void deleteCheckpointsByCompetition(long competitionId) {
        getWritableDatabase().delete("checkpoints", "competition_id=?",
                new String[]{String.valueOf(competitionId)});
    }

    private Competition cursorToCompetition(Cursor c) {
        Competition comp = new Competition();
        comp.setId(c.getLong(c.getColumnIndexOrThrow("_id")));
        comp.setName(c.getString(c.getColumnIndexOrThrow("name")));
        comp.setMode(c.getString(c.getColumnIndexOrThrow("mode")));
        comp.setDate(c.getString(c.getColumnIndexOrThrow("date")));
        comp.setDescription(c.getString(c.getColumnIndexOrThrow("description")));
        comp.setStatus(c.getString(c.getColumnIndexOrThrow("status")));
        comp.setTimeLimit(c.getInt(c.getColumnIndexOrThrow("time_limit")));
        comp.setValidMinutes(c.getInt(c.getColumnIndexOrThrow("valid_minutes")));
        comp.setPenaltyPerMin(c.getInt(c.getColumnIndexOrThrow("penalty_per_min")));
        comp.setStartTime(c.getLong(c.getColumnIndexOrThrow("start_time")));
        comp.setEndTime(c.getLong(c.getColumnIndexOrThrow("end_time")));
        int cloudIndex = c.getColumnIndexOrThrow("cloud_id");
        comp.setCloudId(c.isNull(cloudIndex) ? null : c.getLong(cloudIndex));
        int keyIndex = c.getColumnIndex("join_key");
        if (keyIndex >= 0 && !c.isNull(keyIndex)) {
            comp.setJoinKey(c.getString(keyIndex));
        }
        return comp;
    }

    // ==================== 检查点 ====================

    /** 新增检查点，代码重复时返回 -1 */
    public long insertCheckpoint(Checkpoint cp) {
        return getWritableDatabase().insert("checkpoints", null, checkpointValues(cp));
    }

    public int updateCheckpoint(Checkpoint cp) {
        return getWritableDatabase().update("checkpoints", checkpointValues(cp),
                "_id=?", new String[]{String.valueOf(cp.getId())});
    }

    public void deleteCheckpoint(long checkpointId) {
        getWritableDatabase().delete("checkpoints", "_id=?",
                new String[]{String.valueOf(checkpointId)});
    }

    /** 按顺序号 seq 升序返回某赛事的全部检查点 */
    public List<Checkpoint> getCheckpoints(long competitionId) {
        List<Checkpoint> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM checkpoints WHERE competition_id=? ORDER BY seq ASC",
                new String[]{String.valueOf(competitionId)});
        while (c.moveToNext()) {
            list.add(cursorToCheckpoint(c));
        }
        c.close();
        return list;
    }

    public Checkpoint getCheckpoint(long checkpointId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM checkpoints WHERE _id=?",
                new String[]{String.valueOf(checkpointId)});
        Checkpoint cp = null;
        if (c.moveToFirst()) {
            cp = cursorToCheckpoint(c);
        }
        c.close();
        return cp;
    }

    /** 扫码后按代码查找检查点 */
    public Checkpoint getCheckpointByCode(long competitionId, String code) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM checkpoints WHERE competition_id=? AND code=?",
                new String[]{String.valueOf(competitionId), code});
        Checkpoint cp = null;
        if (c.moveToFirst()) {
            cp = cursorToCheckpoint(c);
        }
        c.close();
        return cp;
    }

    private ContentValues checkpointValues(Checkpoint cp) {
        ContentValues values = new ContentValues();
        values.put("competition_id", cp.getCompetitionId());
        values.put("code", cp.getCode());
        values.put("name", cp.getName());
        values.put("seq", cp.getSeq());
        values.put("points", cp.getPoints());
        values.put("type", cp.getType());
        values.put("required", cp.isRequired() ? 1 : 0);
        if (cp.getLat() != null) {
            values.put("lat", cp.getLat());
        } else {
            values.putNull("lat");
        }
        if (cp.getLng() != null) {
            values.put("lng", cp.getLng());
        } else {
            values.putNull("lng");
        }
        return values;
    }

    private Checkpoint cursorToCheckpoint(Cursor c) {
        Checkpoint cp = new Checkpoint();
        cp.setId(c.getLong(c.getColumnIndexOrThrow("_id")));
        cp.setCompetitionId(c.getLong(c.getColumnIndexOrThrow("competition_id")));
        cp.setCode(c.getString(c.getColumnIndexOrThrow("code")));
        cp.setName(c.getString(c.getColumnIndexOrThrow("name")));
        cp.setSeq(c.getInt(c.getColumnIndexOrThrow("seq")));
        cp.setPoints(c.getInt(c.getColumnIndexOrThrow("points")));
        cp.setType(c.getInt(c.getColumnIndexOrThrow("type")));
        int reqIndex = c.getColumnIndex("required");
        cp.setRequired(reqIndex < 0 || c.getInt(reqIndex) == 1);
        int latIndex = c.getColumnIndexOrThrow("lat");
        int lngIndex = c.getColumnIndexOrThrow("lng");
        cp.setLat(c.isNull(latIndex) ? null : c.getDouble(latIndex));
        cp.setLng(c.isNull(lngIndex) ? null : c.getDouble(lngIndex));
        return cp;
    }

    // ==================== 比赛成绩与打卡 ====================

    /** 开始比赛：插入一条成绩记录（finished=0 表示进行中），返回记录 id */
    public long insertRaceRecord(long competitionId, long userId, String username,
                                 long startTime, String mode, String teamName) {
        ContentValues values = new ContentValues();
        values.put("competition_id", competitionId);
        values.put("user_id", userId);
        values.put("username", username);
        values.put("mode", mode);
        values.put("team_name", teamName);
        values.put("start_time", startTime);
        values.put("score", 0);
        values.put("valid", 0);
        values.put("finished", 0);
        values.put("message", "比赛进行中");
        return getWritableDatabase().insert("race_records", null, values);
    }

    /**
     * 查询该用户在这场赛事中尚未结束的比赛（断点续赛用）
     * 没有则返回 null
     */
    public RaceRecord getOngoingRace(long userId, long competitionId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT r.*, c.name AS comp_name FROM race_records r " +
                        "LEFT JOIN competitions c ON r.competition_id = c._id " +
                        "WHERE r.user_id=? AND r.competition_id=? AND r.finished=0 " +
                        "ORDER BY r._id DESC LIMIT 1",
                new String[]{String.valueOf(userId), String.valueOf(competitionId)});
        RaceRecord r = null;
        if (c.moveToFirst()) {
            r = cursorToRecord(c);
        }
        c.close();
        return r;
    }

    /** 查询该用户当前是否有任意未完赛的比赛（首页提示用） */
    public RaceRecord getOngoingRaceAny(long userId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT r.*, c.name AS comp_name FROM race_records r " +
                        "LEFT JOIN competitions c ON r.competition_id = c._id " +
                        "WHERE r.user_id=? AND r.finished=0 " +
                        "ORDER BY r._id DESC LIMIT 1",
                new String[]{String.valueOf(userId)});
        RaceRecord r = null;
        if (c.moveToFirst()) {
            r = cursorToRecord(c);
        }
        c.close();
        return r;
    }

    /** 结束比赛：回填结束时间、用时、积分与成绩有效性 */
    public void finishRace(long raceId, long endTime, long elapsed,
                           boolean valid, String message, int score) {
        ContentValues values = new ContentValues();
        values.put("end_time", endTime);
        values.put("elapsed", elapsed);
        values.put("valid", valid ? 1 : 0);
        values.put("finished", 1);
        values.put("score", score);
        values.put("message", message);
        getWritableDatabase().update("race_records", values,
                "_id=?", new String[]{String.valueOf(raceId)});
    }

    /** 积分赛实时更新积分 */
    public void updateRaceScore(long raceId, int score) {
        ContentValues values = new ContentValues();
        values.put("score", score);
        getWritableDatabase().update("race_records", values,
                "_id=?", new String[]{String.valueOf(raceId)});
    }

    /** 标记成绩已成功上报云端，并记下云端成绩 id（轨迹可从云端回看） */
    public void markRaceUploaded(long raceId, long cloudRaceId) {
        ContentValues values = new ContentValues();
        values.put("uploaded", 1);
        if (cloudRaceId > 0) {
            values.put("cloud_race_id", cloudRaceId);
        }
        getWritableDatabase().update("race_records", values,
                "_id=?", new String[]{String.valueOf(raceId)});
    }

    /** 记录一次打卡（valid=false 表示顺序错误等无效打卡） */
    public long insertCheckin(long raceId, long checkpointId, String code, int seq, boolean valid) {
        ContentValues values = new ContentValues();
        values.put("race_id", raceId);
        values.put("checkpoint_id", checkpointId);
        values.put("code", code);
        values.put("seq", seq);
        values.put("ts", System.currentTimeMillis());
        values.put("valid", valid ? 1 : 0);
        return getWritableDatabase().insert("checkins", null, values);
    }

    /** 是否已经打过这个点（积分赛去重、顺序赛提示用） */
    public boolean hasCheckin(long raceId, long checkpointId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id FROM checkins WHERE race_id=? AND checkpoint_id=? AND valid=1 LIMIT 1",
                new String[]{String.valueOf(raceId), String.valueOf(checkpointId)});
        boolean exists = c.moveToFirst();
        c.close();
        return exists;
    }

    /** 已有效打卡数量（续赛时恢复进度） */
    public int countValidCheckins(long raceId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM checkins WHERE race_id=? AND valid=1",
                new String[]{String.valueOf(raceId)});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    /** 打卡明细（成绩详情时间轴，按打卡时间升序） */
    public List<Checkin> getCheckinsByRace(long raceId) {
        List<Checkin> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT i.*, p.name AS cp_name FROM checkins i " +
                        "LEFT JOIN checkpoints p ON i.checkpoint_id = p._id " +
                        "WHERE i.race_id=? AND i.valid=1 ORDER BY i.ts ASC",
                new String[]{String.valueOf(raceId)});
        while (c.moveToNext()) {
            Checkin ci = new Checkin();
            ci.setId(c.getLong(c.getColumnIndexOrThrow("_id")));
            ci.setRaceId(c.getLong(c.getColumnIndexOrThrow("race_id")));
            ci.setCheckpointId(c.getLong(c.getColumnIndexOrThrow("checkpoint_id")));
            ci.setCode(c.getString(c.getColumnIndexOrThrow("code")));
            ci.setSeq(c.getInt(c.getColumnIndexOrThrow("seq")));
            ci.setTs(c.getLong(c.getColumnIndexOrThrow("ts")));
            ci.setValid(c.getInt(c.getColumnIndexOrThrow("valid")) == 1);
            ci.setCheckpointName(c.getString(c.getColumnIndexOrThrow("cp_name")));
            list.add(ci);
        }
        c.close();
        return list;
    }

    /** 单条成绩记录（成绩详情页用） */
    public RaceRecord getRaceRecord(long raceId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT r.*, c.name AS comp_name FROM race_records r " +
                        "LEFT JOIN competitions c ON r.competition_id = c._id " +
                        "WHERE r._id=?", new String[]{String.valueOf(raceId)});
        RaceRecord r = null;
        if (c.moveToFirst()) {
            r = cursorToRecord(c);
        }
        c.close();
        return r;
    }

    /** 查询某用户的全部比赛记录（新到旧），并附上赛事名称 */
    public List<RaceRecord> getRecordsByUser(long userId) {
        return queryRecords("WHERE r.user_id=? ORDER BY r._id DESC",
                new String[]{String.valueOf(userId)});
    }

    /** 某场赛事的排名：已完赛优先、有效成绩按用时升序 */
    public List<RaceRecord> getRanking(long competitionId) {
        return queryRecords(
                "WHERE r.competition_id=? AND r.finished=1 ORDER BY r.valid DESC, r.elapsed ASC",
                new String[]{String.valueOf(competitionId)});
    }

    private List<RaceRecord> queryRecords(String where, String[] args) {
        List<RaceRecord> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT r.*, c.name AS comp_name FROM race_records r " +
                        "LEFT JOIN competitions c ON r.competition_id = c._id " + where, args);
        while (c.moveToNext()) {
            list.add(cursorToRecord(c));
        }
        c.close();
        return list;
    }

    // ==================== GPS 轨迹 ====================

    /** 保存一个轨迹点 */
    public void insertTrackPoint(long raceId, double lat, double lng, long ts) {
        ContentValues values = new ContentValues();
        values.put("race_id", raceId);
        values.put("lat", lat);
        values.put("lng", lng);
        values.put("ts", ts);
        getWritableDatabase().insert("track_points", null, values);
    }

    /** 查询某次比赛的完整轨迹（按时间升序） */
    public List<TrackPoint> getTrackPoints(long raceId) {
        List<TrackPoint> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM track_points WHERE race_id=? ORDER BY ts ASC",
                new String[]{String.valueOf(raceId)});
        while (c.moveToNext()) {
            TrackPoint p = new TrackPoint();
            p.setId(c.getLong(c.getColumnIndexOrThrow("_id")));
            p.setRaceId(c.getLong(c.getColumnIndexOrThrow("race_id")));
            p.setLat(c.getDouble(c.getColumnIndexOrThrow("lat")));
            p.setLng(c.getDouble(c.getColumnIndexOrThrow("lng")));
            p.setTs(c.getLong(c.getColumnIndexOrThrow("ts")));
            list.add(p);
        }
        c.close();
        return list;
    }

    private RaceRecord cursorToRecord(Cursor c) {
        RaceRecord r = new RaceRecord();
        r.setId(c.getLong(c.getColumnIndexOrThrow("_id")));
        r.setCompetitionId(c.getLong(c.getColumnIndexOrThrow("competition_id")));
        r.setUserId(c.getLong(c.getColumnIndexOrThrow("user_id")));
        r.setUsername(c.getString(c.getColumnIndexOrThrow("username")));
        r.setMode(c.getString(c.getColumnIndexOrThrow("mode")));
        r.setStartTime(c.getLong(c.getColumnIndexOrThrow("start_time")));
        r.setEndTime(c.getLong(c.getColumnIndexOrThrow("end_time")));
        r.setElapsed(c.getLong(c.getColumnIndexOrThrow("elapsed")));
        r.setScore(c.getInt(c.getColumnIndexOrThrow("score")));
        r.setValid(c.getInt(c.getColumnIndexOrThrow("valid")) == 1);
        r.setFinished(c.getInt(c.getColumnIndexOrThrow("finished")) == 1);
        r.setUploaded(c.getInt(c.getColumnIndexOrThrow("uploaded")) == 1);
        r.setMessage(c.getString(c.getColumnIndexOrThrow("message")));
        int teamIndex = c.getColumnIndex("team_name");
        r.setTeamName(teamIndex >= 0 ? c.getString(teamIndex) : null);
        int cloudRaceIndex = c.getColumnIndex("cloud_race_id");
        r.setCloudRaceId(cloudRaceIndex >= 0 ? c.getLong(cloudRaceIndex) : 0);
        r.setCompetitionName(c.getString(c.getColumnIndexOrThrow("comp_name")));
        return r;
    }
}
