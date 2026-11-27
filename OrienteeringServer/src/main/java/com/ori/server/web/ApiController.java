package com.ori.server.web;

import com.ori.server.entity.CheckinLog;
import com.ori.server.entity.Checkpoint;
import com.ori.server.entity.Competition;
import com.ori.server.entity.Enrollment;
import com.ori.server.entity.RaceRecord;
import com.ori.server.entity.TrackPoint;
import com.ori.server.entity.User;
import com.ori.server.repo.CheckinLogRepo;
import com.ori.server.repo.CheckpointRepo;
import com.ori.server.repo.CompetitionRepo;
import com.ori.server.repo.EnrollmentRepo;
import com.ori.server.repo.RaceRecordRepo;
import com.ori.server.repo.TrackPointRepo;
import com.ori.server.repo.UserRepo;
import com.ori.server.security.LoginGuard;
import com.ori.server.security.PasswordHasher;
import com.ori.server.security.TokenAuth;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 定向越野助手 全部 REST 接口
 *
 * 约定：
 * - 单对象返回 {ok: true/false, msg, ...}
 * - 列表返回 {ok: true, data: [...]}
 * - 历史成绩只增不删（对齐"历史参赛记录不能删"的需求）
 *
 * 安全约定（v1.1 起）：
 * - 除注册/登录外，全部接口要求 Authorization: Bearer <token>
 * - 身份一律取自令牌（uid），不再信任请求体里的 userId/ownerId
 * - 赛事写操作仅发起者可执行；读操作要求发起者或已报名成员
 * - 所有外部输入做长度、格式、数值范围校验
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    // 输入上限：防止超长字符串与超大数组造成的资源消耗
    private static final int MAX_USERNAME = 20;
    private static final int MAX_PASSWORD = PasswordHasher.MAX_LEN;
    private static final int MAX_NAME = 20;
    private static final int MAX_CLAZZ = 30;
    private static final int MAX_COMP_NAME = 50;
    private static final int MAX_DESC = 500;
    private static final int MAX_CHECKPOINTS = 200;
    private static final int MAX_MEMBERS = 100;
    private static final int MAX_CHECKINS = 200;
    private static final int MAX_TRACK_POINTS = 5000;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern JOIN_KEY_PATTERN = Pattern.compile("^\\d{6,7}$");

    private final UserRepo userRepo;
    private final CompetitionRepo competitionRepo;
    private final CheckpointRepo checkpointRepo;
    private final EnrollmentRepo enrollmentRepo;
    private final RaceRecordRepo raceRecordRepo;
    private final CheckinLogRepo checkinLogRepo;
    private final TrackPointRepo trackPointRepo;
    private final TokenAuth tokenAuth;
    private final LoginGuard loginGuard;

    public ApiController(UserRepo userRepo, CompetitionRepo competitionRepo,
                         CheckpointRepo checkpointRepo, EnrollmentRepo enrollmentRepo,
                         RaceRecordRepo raceRecordRepo, CheckinLogRepo checkinLogRepo,
                         TrackPointRepo trackPointRepo, TokenAuth tokenAuth, LoginGuard loginGuard) {
        this.userRepo = userRepo;
        this.competitionRepo = competitionRepo;
        this.checkpointRepo = checkpointRepo;
        this.enrollmentRepo = enrollmentRepo;
        this.raceRecordRepo = raceRecordRepo;
        this.checkinLogRepo = checkinLogRepo;
        this.trackPointRepo = trackPointRepo;
        this.tokenAuth = tokenAuth;
        this.loginGuard = loginGuard;
    }

    // ==================== 用户 ====================

    /** 注册（name/clazz 可选，登录后可在"我的"里补填） */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (username.isEmpty() || password.isEmpty()) {
            return fail("用户名和密码不能为空");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return fail("用户名需为 3-20 位字母、数字或下划线");
        }
        if (password.length() < PasswordHasher.MIN_LEN || password.length() > MAX_PASSWORD) {
            return fail("密码长度需在 " + PasswordHasher.MIN_LEN + "-" + MAX_PASSWORD + " 位之间");
        }
        if (userRepo.findByUsername(username).isPresent()) {
            return fail("该用户名已被注册");
        }
        User user = new User();
        user.username = username;
        user.password = PasswordHasher.hash(password);   // 只存哈希
        user.name = clip(str(body.get("name")), MAX_NAME);
        user.clazz = clip(str(body.get("clazz")), MAX_CLAZZ);
        user.createdAt = System.currentTimeMillis();
        userRepo.save(user);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("id", user.id);
        result.put("username", user.username);
        result.put("name", nz(user.name));
        result.put("clazz", nz(user.clazz));
        return result;
    }

    /** 登录：校验哈希 → 签发令牌 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        if (username.isEmpty() || password.isEmpty()) {
            return fail("用户名和密码不能为空");
        }
        if (password.length() > MAX_PASSWORD) {
            return fail("用户名或密码错误");
        }
        if (loginGuard.isLocked(username)) {
            return fail("尝试次数过多，请 " + loginGuard.lockSecondsLeft(username) + " 秒后再试");
        }
        User u = userRepo.findByUsername(username).orElse(null);
        if (u == null || !PasswordHasher.matches(password, u.password)) {
            loginGuard.onFailure(username);
            return fail("用户名或密码错误");
        }
        loginGuard.onSuccess(username);
        // 历史明文口令：校验通过后立刻升级为哈希
        if (!PasswordHasher.isHashed(u.password)) {
            u.password = PasswordHasher.hash(password);
        }
        String token = tokenAuth.issue(u);
        Map<String, Object> ok = new HashMap<>();
        ok.put("ok", true);
        ok.put("id", u.id);
        ok.put("username", u.username);
        ok.put("name", nz(u.name));
        ok.put("clazz", nz(u.clazz));
        ok.put("token", token);
        ok.put("expiresIn", TokenAuth.ttlMillis());
        return ok;
    }

    /** 登出：作废当前令牌 */
    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestAttribute(value = TokenAuth.ATTR_UID, required = false) Long uid,
                                      @RequestAttribute(value = "currentUser", required = false) User current) {
        if (current != null) {
            tokenAuth.revoke(current);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 当前登录用户资料（客户端启动时校验令牌是否仍有效） */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        User u = userRepo.findById(uid).orElse(null);
        if (u == null) {
            return fail("用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("id", u.id);
        result.put("username", u.username);
        result.put("name", nz(u.name));
        result.put("clazz", nz(u.clazz));
        return result;
    }

    /** 查询用户资料（仅本人） */
    @GetMapping("/users/{id}")
    public Map<String, Object> getUser(@PathVariable long id,
                                       @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        if (id != uid) {
            return fail("无权查看他人资料");
        }
        User u = userRepo.findById(id).orElse(null);
        if (u == null) {
            return fail("用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("id", u.id);
        result.put("username", u.username);
        result.put("name", nz(u.name));
        result.put("clazz", nz(u.clazz));
        return result;
    }

    /** 修改用户资料（姓名 / 班级，仅本人） */
    @PostMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable long id,
                                          @RequestBody Map<String, Object> body,
                                          @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        if (id != uid) {
            return fail("无权修改他人资料");
        }
        User u = userRepo.findById(id).orElse(null);
        if (u == null) {
            return fail("用户不存在");
        }
        if (body.containsKey("name")) {
            u.name = clip(str(body.get("name")), MAX_NAME);
        }
        if (body.containsKey("clazz")) {
            u.clazz = clip(str(body.get("clazz")), MAX_CLAZZ);
        }
        userRepo.save(u);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("name", nz(u.name));
        result.put("clazz", nz(u.clazz));
        return result;
    }

    // ==================== 赛事 ====================

    /**
     * 发起者创建赛事（连同检查点、初始成员一次性上传）
     * body: { name, mode, date, description, timeLimit(分钟),
     *         validMinutes(积分赛有效时间), penaltyPerMin(积分赛每分钟扣分),
     *         startTime, endTime(预约毫秒时间戳,可选),
     *         members: ["学号1","学号2"],   // 可选，创建时直接拉人
     *         checkpoints: [{code,name,seq,points,type,required}] }
     * 发起者身份取自令牌，不接受请求体里的 ownerId
     */
    @PostMapping("/competitions")
    public Map<String, Object> createCompetition(@RequestBody Map<String, Object> body,
                                                 @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        String name = clip(str(body.get("name")), MAX_COMP_NAME);
        if (name.isEmpty()) {
            return fail("赛事名称不能为空");
        }
        if (!validCheckpointCount(body.get("checkpoints"))) {
            return fail("检查点数量不能超过 " + MAX_CHECKPOINTS + " 个");
        }
        Competition comp = new Competition();
        comp.name = name;
        comp.mode = clip(str(body.get("mode")), 20);
        comp.date = clip(str(body.get("date")), 40);
        comp.description = clip(str(body.get("description")), MAX_DESC);
        comp.status = "未开始";
        comp.timeLimit = clamp(toInt(body.get("timeLimit"), 60), 1, 1440);
        comp.validMinutes = clamp(toInt(body.get("validMinutes"), 0), 0, 1440);
        comp.penaltyPerMin = clamp(toInt(body.get("penaltyPerMin"), 0), 0, 1000);
        comp.startTime = toLongOrZero(body.get("startTime"));
        comp.endTime = toLongOrZero(body.get("endTime"));
        comp.ownerId = uid;
        comp.createdAt = System.currentTimeMillis();
        competitionRepo.save(comp);
        comp.joinKey = generateJoinKey();
        competitionRepo.save(comp);
        saveCheckpoints(body.get("checkpoints"), comp.id);
        enrollOwnerAndMembers(comp, body.get("members"));
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("id", comp.id);
        result.put("joinKey", comp.joinKey);
        return result;
    }

    /** 生成全局唯一的 6 位数字加入码 */
    private String generateJoinKey() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 20; i++) {
            String key = String.valueOf(100000 + random.nextInt(900000));
            if (competitionRepo.findByJoinKey(key).isEmpty()) {
                return key;
            }
        }
        // 极小概率冲突时退化为更长数字
        return String.valueOf(1000000 + random.nextInt(9000000));
    }

    /**
     * 运动员自助加入赛事：输入 6 位加入码即以当前账号参赛
     * body: { key }
     */
    @PostMapping("/competitions/join")
    public Map<String, Object> joinByKey(@RequestBody Map<String, Object> body,
                                         @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        String key = str(body.get("key"));
        if (!JOIN_KEY_PATTERN.matcher(key).matches()) {
            return fail("加入码应为 6 位数字，请与发起者核对");
        }
        Competition comp = competitionRepo.findByJoinKey(key).orElse(null);
        if (comp == null) {
            return fail("加入码无效，请与发起者核对");
        }
        if (enrollmentRepo.findByCompetitionIdAndUserId(comp.id, uid).isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("msg", "你已加入过该赛事");
            result.putAll(compSummary(comp));
            return result;
        }
        Enrollment e = new Enrollment();
        e.competitionId = comp.id;
        e.userId = uid;
        e.joinedAt = System.currentTimeMillis();
        enrollmentRepo.save(e);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.putAll(compSummary(comp));
        return result;
    }

    /**
     * 批量创建组别赛事（同一场跑图，男子甲/乙/丙/丁、女子甲乙丙丁等组别并行）
     * body: { name, mode, ..., groups: ["男子甲组","女子乙组"],
     *         checkpoints: [...], members: [...] }
     */
    @PostMapping("/competitions/batch")
    public Map<String, Object> createBatchCompetitions(@RequestBody Map<String, Object> body,
                                                       @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Object rawGroups = body.get("groups");
        if (!(rawGroups instanceof List<?> groups) || groups.isEmpty()) {
            return fail("组别列表不能为空");
        }
        if (groups.size() > 50) {
            return fail("一次最多创建 50 个组别");
        }
        if (!validCheckpointCount(body.get("checkpoints"))) {
            return fail("检查点数量不能超过 " + MAX_CHECKPOINTS + " 个");
        }
        String baseName = clip(str(body.get("name")), MAX_COMP_NAME);
        if (baseName.isEmpty()) {
            return fail("赛事名称不能为空");
        }
        List<Map<String, Object>> created = new ArrayList<>();
        for (Object g : groups) {
            String groupName = clip(str(g), 20);
            if (groupName.isEmpty()) {
                continue;
            }
            Competition comp = new Competition();
            comp.name = baseName + " · " + groupName;
            comp.mode = clip(str(body.get("mode")), 20);
            comp.date = clip(str(body.get("date")), 40);
            comp.description = clip(str(body.get("description")), MAX_DESC);
            comp.status = "未开始";
            comp.timeLimit = clamp(toInt(body.get("timeLimit"), 60), 1, 1440);
            comp.validMinutes = clamp(toInt(body.get("validMinutes"), 0), 0, 1440);
            comp.penaltyPerMin = clamp(toInt(body.get("penaltyPerMin"), 0), 0, 1000);
            comp.startTime = toLongOrZero(body.get("startTime"));
            comp.endTime = toLongOrZero(body.get("endTime"));
            comp.ownerId = uid;
            comp.createdAt = System.currentTimeMillis();
            competitionRepo.save(comp);
            comp.joinKey = generateJoinKey();
            competitionRepo.save(comp);
            saveCheckpoints(body.get("checkpoints"), comp.id);
            enrollOwnerAndMembers(comp, body.get("members"));
            created.add(compSummary(comp));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("data", created);
        return result;
    }

    /** 赛事简要信息（id/名称/加入码/模式） */
    private Map<String, Object> compSummary(Competition comp) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", comp.id);
        m.put("name", comp.name);
        m.put("mode", comp.mode);
        m.put("joinKey", comp.joinKey);
        return m;
    }

    /** 发起者修改赛事（字段 + 检查点整体替换 + 追加成员） */
    @PostMapping("/competitions/{id}")
    public Map<String, Object> updateCompetition(@PathVariable long id,
                                                 @RequestBody Map<String, Object> body,
                                                 @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!isOwner(comp, uid)) {
            return fail("只有赛事发起者可以修改");
        }
        if (body.containsKey("name")) {
            String name = clip(str(body.get("name")), MAX_COMP_NAME);
            if (name.isEmpty()) {
                return fail("赛事名称不能为空");
            }
            comp.name = name;
        }
        if (body.containsKey("mode")) comp.mode = clip(str(body.get("mode")), 20);
        if (body.containsKey("date")) comp.date = clip(str(body.get("date")), 40);
        if (body.containsKey("description")) comp.description = clip(str(body.get("description")), MAX_DESC);
        if (body.containsKey("status")) comp.status = clip(str(body.get("status")), 20);
        if (body.containsKey("timeLimit")) comp.timeLimit = clamp(toInt(body.get("timeLimit"), comp.timeLimit), 1, 1440);
        if (body.containsKey("validMinutes")) comp.validMinutes = clamp(toInt(body.get("validMinutes"), 0), 0, 1440);
        if (body.containsKey("penaltyPerMin")) comp.penaltyPerMin = clamp(toInt(body.get("penaltyPerMin"), 0), 0, 1000);
        if (body.containsKey("startTime")) comp.startTime = toLongOrZero(body.get("startTime"));
        if (body.containsKey("endTime")) comp.endTime = toLongOrZero(body.get("endTime"));
        competitionRepo.save(comp);
        if (body.containsKey("checkpoints")) {
            if (!validCheckpointCount(body.get("checkpoints"))) {
                return fail("检查点数量不能超过 " + MAX_CHECKPOINTS + " 个");
            }
            for (Checkpoint cp : checkpointRepo.findByCompetitionIdOrderBySeqAsc(id)) {
                checkpointRepo.delete(cp);
            }
            saveCheckpoints(body.get("checkpoints"), id);
        }
        if (body.get("members") instanceof List<?> list) {
            if (list.size() > MAX_MEMBERS) {
                return fail("单次最多添加 " + MAX_MEMBERS + " 名成员");
            }
            for (Object o : list) {
                addMemberInternal(id, str(o));
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("id", comp.id);
        return result;
    }

    /** 发起者删除赛事（删赛事+检查点+成员关系；历史成绩保留不可删） */
    @DeleteMapping("/competitions/{id}")
    public Map<String, Object> deleteCompetition(@PathVariable long id,
                                                 @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!isOwner(comp, uid)) {
            return fail("只有赛事发起者可以删除");
        }
        for (Checkpoint cp : checkpointRepo.findByCompetitionIdOrderBySeqAsc(id)) {
            checkpointRepo.delete(cp);
        }
        for (Enrollment e : enrollmentRepo.findByCompetitionIdOrderByIdAsc(id)) {
            enrollmentRepo.delete(e);
        }
        competitionRepo.delete(comp);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 我发起的赛事（旧赛事没有加入码时自动补发） */
    @GetMapping("/competitions/owned")
    public Map<String, Object> ownedCompetitions(@RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        List<Competition> list = competitionRepo.findByOwnerIdOrderByIdDesc(uid);
        for (Competition comp : list) {
            if (comp.joinKey == null || comp.joinKey.isEmpty()) {
                comp.joinKey = generateJoinKey();
                competitionRepo.save(comp);
            }
        }
        return okList(list);
    }

    /** 我参加的赛事（被发起者拉进"群"的） */
    @GetMapping("/competitions/joined")
    public Map<String, Object> joinedCompetitions(@RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (Enrollment e : enrollmentRepo.findByUserIdOrderByIdDesc(uid)) {
            Competition comp = competitionRepo.findById(e.competitionId).orElse(null);
            if (comp == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", comp.id);
            item.put("name", comp.name);
            item.put("mode", comp.mode);
            item.put("date", comp.date);
            item.put("description", comp.description);
            item.put("timeLimit", comp.timeLimit);
            item.put("validMinutes", comp.validMinutes);
            item.put("penaltyPerMin", comp.penaltyPerMin);
            item.put("startTime", comp.startTime);
            item.put("endTime", comp.endTime);
            item.put("joinKey", comp.joinKey);
            item.put("status", comp.status);
            item.put("ownerName", userRepo.findById(comp.ownerId)
                    .map(ApiController::displayName).orElse(""));
            item.put("teamName", nz(e.teamName));
            item.put("joinedAt", e.joinedAt);
            data.add(item);
        }
        return okList(data);
    }

    /** 赛事详情（含检查点，供成员下载到本地跑比赛） */
    @GetMapping("/competitions/{id}")
    public Map<String, Object> competitionDetail(@PathVariable long id,
                                                 @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!canAccess(comp, uid)) {
            return fail("你还没有加入该赛事");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("id", comp.id);
        result.put("name", comp.name);
        result.put("mode", comp.mode);
        result.put("date", comp.date);
        result.put("description", comp.description);
        result.put("timeLimit", comp.timeLimit);
        result.put("validMinutes", comp.validMinutes);
        result.put("penaltyPerMin", comp.penaltyPerMin);
        result.put("startTime", comp.startTime);
        result.put("endTime", comp.endTime);
        result.put("joinKey", comp.joinKey);
        result.put("status", comp.status);
        result.put("ownerId", comp.ownerId);
        result.put("ownerName", userRepo.findById(comp.ownerId)
                .map(ApiController::displayName).orElse(""));
        result.put("checkpoints", checkpointRepo.findByCompetitionIdOrderBySeqAsc(comp.id));
        return result;
    }

    // ==================== 参赛成员管理（像建群一样） ====================

    /** 添加成员（按学号/用户名，仅发起者） */
    @PostMapping("/competitions/{id}/members")
    public Map<String, Object> addMember(@PathVariable long id,
                                         @RequestBody Map<String, Object> body,
                                         @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!isOwner(comp, uid)) {
            return fail("只有赛事发起者可以添加成员");
        }
        String username = str(body.get("username"));
        if (username.isEmpty() || username.length() > MAX_USERNAME) {
            return fail("成员用户名不合法");
        }
        if (enrollmentRepo.findByCompetitionIdOrderByIdAsc(id).size() >= MAX_MEMBERS) {
            return fail("单个赛事成员上限 " + MAX_MEMBERS + " 人");
        }
        Map<String, Object> r = addMemberInternal(id, username);
        if (r.get("ok") instanceof Boolean b && b) {
            User u = userRepo.findByUsername(username).orElse(null);
            if (u != null) {
                r.put("name", nz(u.name));
                r.put("clazz", nz(u.clazz));
            }
        }
        return r;
    }

    /** 移除成员（仅发起者） */
    @DeleteMapping("/competitions/{id}/members")
    public Map<String, Object> removeMember(@PathVariable long id,
                                            @RequestParam long userId,
                                            @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!isOwner(comp, uid)) {
            return fail("只有赛事发起者可以移除成员");
        }
        enrollmentRepo.deleteByCompetitionIdAndUserId(id, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 成员列表（含发起者，带姓名班级与队伍；仅发起者与成员可见） */
    @GetMapping("/competitions/{id}/members")
    public Map<String, Object> members(@PathVariable long id,
                                       @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!canAccess(comp, uid)) {
            return fail("你还没有加入该赛事");
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Enrollment e : enrollmentRepo.findByCompetitionIdOrderByIdAsc(id)) {
            User u = userRepo.findById(e.userId).orElse(null);
            if (u == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("userId", u.id);
            item.put("username", u.username);
            item.put("name", nz(u.name));
            item.put("clazz", nz(u.clazz));
            item.put("teamName", nz(e.teamName));
            item.put("joinedAt", e.joinedAt);
            data.add(item);
        }
        return okList(data);
    }

    // ==================== 团队赛：分队 ====================

    /** 加入/切换队伍（body: {teamName}，只能改自己的队伍） */
    @PostMapping("/competitions/{id}/teams")
    public Map<String, Object> joinTeam(@PathVariable long id,
                                        @RequestBody Map<String, Object> body,
                                        @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        String teamName = clip(str(body.get("teamName")), 20);
        if (teamName.isEmpty()) {
            return fail("队伍名称不能为空");
        }
        Enrollment e = enrollmentRepo.findByCompetitionIdAndUserId(id, uid).orElse(null);
        if (e == null) {
            return fail("你还没有加入该赛事，请联系发起者添加");
        }
        e.teamName = teamName;
        enrollmentRepo.save(e);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("teamName", teamName);
        return result;
    }

    /**
     * 团队赛况总览（发起者与成员均可看）：
     * 每队：成员状态、必达点每人是否按序完成、自由点全队覆盖、队伍成绩（最后完赛队员时间）
     */
    @GetMapping("/competitions/{id}/teams")
    public Map<String, Object> teamOverview(@PathVariable long id,
                                            @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!canAccess(comp, uid)) {
            return fail("你还没有加入该赛事");
        }
        // 自由点 / 必达点代码集合
        List<String> freeCodes = new ArrayList<>();
        List<String> mandatoryCodes = new ArrayList<>();
        for (Checkpoint cp : checkpointRepo.findByCompetitionIdOrderBySeqAsc(id)) {
            if (cp.type == 2) {
                if (cp.required) {
                    mandatoryCodes.add(cp.code);
                } else {
                    freeCodes.add(cp.code);
                }
            }
        }
        // 每个成员最新成绩 + 打卡记录
        Map<Long, RaceRecord> latestByUser = new HashMap<>();
        for (RaceRecord r : raceRecordRepo.findByCompetitionIdOrderByIdDesc(id)) {
            latestByUser.putIfAbsent(r.userId, r);
        }
        Map<Long, List<String>> userCodes = new HashMap<>();
        for (RaceRecord r : latestByUser.values()) {
            List<String> codes = new ArrayList<>();
            for (CheckinLog c : checkinLogRepo.findByRaceIdOrderByTsAsc(r.id)) {
                codes.add(c.code);
            }
            userCodes.put(r.userId, codes);
        }
        // 按队伍聚合
        Map<String, List<Enrollment>> teams = new LinkedHashMap<>();
        List<Enrollment> noTeam = new ArrayList<>();
        for (Enrollment e : enrollmentRepo.findByCompetitionIdOrderByIdAsc(id)) {
            if (e.teamName == null || e.teamName.isEmpty()) {
                noTeam.add(e);
            } else {
                teams.computeIfAbsent(e.teamName, k -> new ArrayList<>()).add(e);
            }
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map.Entry<String, List<Enrollment>> entry : teams.entrySet()) {
            data.add(buildTeamSummary(entry.getKey(), entry.getValue(),
                    latestByUser, userCodes, freeCodes));
        }
        // 未分队的成员单独给一条，方便发起者提醒
        if (!noTeam.isEmpty()) {
            data.add(buildTeamSummary("（未分队）", noTeam,
                    latestByUser, userCodes, freeCodes));
        }
        // 排序：有效在前，队伍用时升序；未分队垫底
        data.sort((a, b) -> {
            boolean ua = (boolean) a.get("teamValid");
            boolean ub = (boolean) b.get("teamValid");
            if (ua != ub) return ua ? -1 : 1;
            long ta = toLong(a.get("teamFinishTime"));
            long tb = toLong(b.get("teamFinishTime"));
            return Long.compare(ta, tb);
        });
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("mode", comp.mode);
        result.put("freeTotal", freeCodes.size());
        result.put("data", data);
        return result;
    }

    /** 组装一个队伍的汇总 */
    private Map<String, Object> buildTeamSummary(String teamName,
                                                 List<Enrollment> members,
                                                 Map<Long, RaceRecord> latestByUser,
                                                 Map<Long, List<String>> userCodes,
                                                 List<String> freeCodes) {
        List<Map<String, Object>> memberList = new ArrayList<>();
        List<String> covered = new ArrayList<>();
        boolean allFinished = !members.isEmpty();
        boolean allValid = true;
        long finish = 0;
        for (Enrollment e : members) {
            User u = userRepo.findById(e.userId).orElse(null);
            if (u == null) continue;
            RaceRecord r = latestByUser.get(e.userId);
            boolean finished = r != null && r.finished;
            boolean valid = finished && r.valid;
            allFinished &= finished;
            allValid &= valid;
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.id);
            m.put("username", u.username);
            m.put("name", nz(u.name));
            m.put("clazz", nz(u.clazz));
            m.put("finished", finished);
            m.put("valid", valid);
            m.put("elapsed", finished ? r.elapsed : 0);
            m.put("message", finished && r.message != null ? r.message : "");
            memberList.add(m);
            if (finished && r.endTime != null) {
                finish = Math.max(finish, r.endTime);
            }
            List<String> codes = userCodes.getOrDefault(e.userId, List.of());
            for (String code : freeCodes) {
                if (codes.contains(code) && !covered.contains(code)) {
                    covered.add(code);
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String code : freeCodes) {
            if (!covered.contains(code)) missing.add(code);
        }
        boolean teamValid = allFinished && allValid && missing.isEmpty();
        Map<String, Object> t = new HashMap<>();
        t.put("teamName", teamName);
        t.put("members", memberList);
        t.put("memberCount", memberList.size());
        t.put("allFinished", allFinished);
        t.put("teamFinishTime", finish);
        t.put("freeCovered", covered.size());
        t.put("freeTotal", freeCodes.size());
        t.put("freeMissing", missing);
        t.put("teamValid", teamValid);
        return t;
    }

    // ==================== 成绩与轨迹 ====================

    /**
     * 上传一次完赛成绩（含打卡明细和 GPS 轨迹）
     * body: { competitionId, username, name, teamName, mode,
     *         startTime, endTime, elapsed, score, valid, message,
     *         checkins: [{code,seq,ts,valid}], track: [{lat,lng,ts}] }
     * 身份取自令牌；必须先加入该赛事
     */
    @PostMapping("/results")
    public Map<String, Object> uploadResult(@RequestBody Map<String, Object> body,
                                            @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        long competitionId = toLong(body.get("competitionId"));
        if (competitionId == 0) {
            return fail("competitionId 不能为空");
        }
        Competition comp = competitionRepo.findById(competitionId).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (enrollmentRepo.findByCompetitionIdAndUserId(competitionId, uid).isEmpty()) {
            return fail("你还没有加入该赛事，无法上传成绩");
        }
        if (!validCount(body.get("checkins"), MAX_CHECKINS)) {
            return fail("打卡记录数量异常");
        }
        if (!validCount(body.get("track"), MAX_TRACK_POINTS)) {
            return fail("轨迹点数量异常（上限 " + MAX_TRACK_POINTS + "）");
        }
        User me = userRepo.findById(uid).orElse(null);
        RaceRecord r = new RaceRecord();
        r.competitionId = competitionId;
        r.userId = uid;                                  // 不信请求体里的 userId
        r.username = me != null ? me.username : "";
        r.name = me != null ? clip(nz(me.name), MAX_NAME) : clip(str(body.get("name")), MAX_NAME);
        r.teamName = clip(str(body.get("teamName")), 20);
        r.mode = clip(str(body.get("mode")), 20);
        r.startTime = toLong(body.get("startTime"));
        r.endTime = toLong(body.get("endTime"));
        r.elapsed = clamp(toLong(body.get("elapsed")), 0, 7L * 24 * 3600 * 1000);
        r.score = clamp(toInt(body.get("score"), 0), 0, 1000000);
        Object valid = body.get("valid");
        r.valid = valid instanceof Boolean && (Boolean) valid;
        r.finished = true;
        r.message = clip(str(body.get("message")), 200);
        r.uploadedAt = System.currentTimeMillis();
        raceRecordRepo.save(r);

        // 打卡明细
        Object rawCheckins = body.get("checkins");
        if (rawCheckins instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    CheckinLog c = new CheckinLog();
                    c.raceId = r.id;
                    c.code = clip(str(m.get("code")), 20);
                    c.seq = clamp(toInt(m.get("seq"), 0), 0, MAX_CHECKINS);
                    c.ts = toLong(m.get("ts"));
                    Object v = m.get("valid");
                    c.valid = !(v instanceof Boolean) || (Boolean) v;
                    checkinLogRepo.save(c);
                }
            }
        }
        // GPS 轨迹
        Object rawTrack = body.get("track");
        if (rawTrack instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    TrackPoint p = new TrackPoint();
                    p.raceId = r.id;
                    Object lat = m.get("lat");
                    Object lng = m.get("lng");
                    if (lat instanceof Number && lng instanceof Number) {
                        double la = ((Number) lat).doubleValue();
                        double ln = ((Number) lng).doubleValue();
                        if (la < -90 || la > 90 || ln < -180 || ln > 180) {
                            continue;   // 丢弃非法坐标
                        }
                        p.lat = la;
                        p.lng = ln;
                        p.ts = toLong(m.get("ts"));
                        trackPointRepo.save(p);
                    }
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("id", r.id);
        return result;
    }

    /** 某场赛事的全部完赛成绩（发起者和成员都能看） */
    @GetMapping("/competitions/{id}/results")
    public Map<String, Object> competitionResults(@PathVariable long id,
                                                  @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        Competition comp = competitionRepo.findById(id).orElse(null);
        if (comp == null) {
            return fail("赛事不存在");
        }
        if (!canAccess(comp, uid)) {
            return fail("你还没有加入该赛事");
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (RaceRecord r : raceRecordRepo.findByCompetitionIdOrderByIdDesc(id)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.id);
            item.put("userId", r.userId);
            item.put("username", r.username);
            item.put("name", nz(r.name));
            item.put("clazz", userRepo.findById(r.userId)
                    .map(u -> nz(u.clazz)).orElse(""));
            item.put("teamName", nz(r.teamName));
            item.put("mode", r.mode);
            item.put("startTime", r.startTime);
            item.put("endTime", r.endTime);
            item.put("elapsed", r.elapsed);
            item.put("score", r.score);
            item.put("valid", r.valid);
            item.put("message", r.message);
            data.add(item);
        }
        return okList(data);
    }

    /** 某次成绩的打卡明细（时间轴），仅本人或该赛事成员可见 */
    @GetMapping("/results/{raceId}/checkins")
    public Map<String, Object> resultCheckins(@PathVariable long raceId,
                                              @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        if (!canAccessRace(raceId, uid)) {
            return fail("无权查看该成绩");
        }
        return okList(checkinLogRepo.findByRaceIdOrderByTsAsc(raceId));
    }

    /** 某次成绩的 GPS 轨迹（地图画线），仅本人或该赛事成员可见 */
    @GetMapping("/results/{raceId}/track")
    public Map<String, Object> resultTrack(@PathVariable long raceId,
                                           @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        if (!canAccessRace(raceId, uid)) {
            return fail("无权查看该轨迹");
        }
        return okList(trackPointRepo.findByRaceIdOrderByTsAsc(raceId));
    }

    /** 某用户参加过的全部历史成绩（跨赛事，仅本人） */
    @GetMapping("/users/{userId}/results")
    public Map<String, Object> userResults(@PathVariable long userId,
                                           @RequestAttribute(TokenAuth.ATTR_UID) long uid) {
        if (userId != uid) {
            return fail("无权查看他人成绩");
        }
        List<Map<String, Object>> data = new ArrayList<>();
        for (RaceRecord r : raceRecordRepo.findByUserIdOrderByIdDesc(userId)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", r.id);
            item.put("competitionId", r.competitionId);
            item.put("competitionName", competitionRepo.findById(r.competitionId)
                    .map(c -> c.name).orElse(""));
            item.put("mode", r.mode);
            item.put("teamName", nz(r.teamName));
            item.put("startTime", r.startTime);
            item.put("elapsed", r.elapsed);
            item.put("score", r.score);
            item.put("valid", r.valid);
            item.put("message", r.message);
            data.add(item);
        }
        return okList(data);
    }

    // ==================== 权限判断 ====================

    private static boolean isOwner(Competition comp, long uid) {
        return comp.ownerId != null && comp.ownerId == uid;
    }

    /** 发起者或已报名成员可访问赛事详情类数据 */
    private boolean canAccess(Competition comp, long uid) {
        if (isOwner(comp, uid)) {
            return true;
        }
        return enrollmentRepo.findByCompetitionIdAndUserId(comp.id, uid).isPresent();
    }

    /** 成绩/轨迹：本人，或该成绩所属赛事的发起者/成员 */
    private boolean canAccessRace(long raceId, long uid) {
        RaceRecord r = raceRecordRepo.findById(raceId).orElse(null);
        if (r == null) {
            return false;
        }
        if (r.userId != null && r.userId == uid) {
            return true;
        }
        Competition comp = competitionRepo.findById(r.competitionId).orElse(null);
        return comp != null && canAccess(comp, uid);
    }

    // ==================== 内部工具 ====================

    private Map<String, Object> addMemberInternal(long competitionId, String username) {
        User user = userRepo.findByUsername(username).orElse(null);
        if (user == null) {
            return fail("用户「" + clip(username, MAX_USERNAME) + "」不存在，请对方先注册");
        }
        if (enrollmentRepo.findByCompetitionIdAndUserId(competitionId, user.id).isPresent()) {
            return fail("成员「" + username + "」已在赛事中");
        }
        Enrollment e = new Enrollment();
        e.competitionId = competitionId;
        e.userId = user.id;
        e.joinedAt = System.currentTimeMillis();
        enrollmentRepo.save(e);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("userId", user.id);
        result.put("username", user.username);
        return result;
    }

    /** 发起者自动成为第一个成员，并按用户名批量拉人 */
    private void enrollOwnerAndMembers(Competition comp, Object membersRaw) {
        Enrollment self = new Enrollment();
        self.competitionId = comp.id;
        self.userId = comp.ownerId;
        self.joinedAt = System.currentTimeMillis();
        enrollmentRepo.save(self);
        if (membersRaw instanceof List<?> list) {
            int added = 0;
            for (Object o : list) {
                if (added++ >= MAX_MEMBERS) {
                    break;
                }
                String username = str(o);
                if (!username.isEmpty() && username.length() <= MAX_USERNAME) {
                    addMemberInternal(comp.id, username);
                }
            }
        }
    }

    private void saveCheckpoints(Object raw, Long competitionId) {
        if (!(raw instanceof List<?> list)) {
            return;
        }
        int saved = 0;
        for (Object o : list) {
            if (saved++ >= MAX_CHECKPOINTS) {
                break;
            }
            if (o instanceof Map<?, ?> m) {
                String code = clip(str(m.get("code")), 20);
                if (code.isEmpty()) {
                    continue;
                }
                Checkpoint cp = new Checkpoint();
                cp.competitionId = competitionId;
                cp.code = code;
                cp.name = clip(str(m.get("name")), 30);
                cp.seq = clamp(toInt(m.get("seq"), 1), 1, MAX_CHECKPOINTS);
                cp.points = clamp(toInt(m.get("points"), 10), 0, 10000);
                cp.type = clamp(toInt(m.get("type"), 2), 1, 3);
                Object req = m.get("required");
                cp.required = !(req instanceof Boolean) || (Boolean) req;
                cp.lat = toDoubleOrNull(m.get("lat"));
                cp.lng = toDoubleOrNull(m.get("lng"));
                if (cp.lat != null && (cp.lat < -90 || cp.lat > 90)) {
                    cp.lat = null;
                }
                if (cp.lng != null && (cp.lng < -180 || cp.lng > 180)) {
                    cp.lng = null;
                }
                checkpointRepo.save(cp);
            }
        }
    }

    /** 展示名：有姓名用姓名，否则用学号 */
    private static String displayName(User u) {
        if (u.name != null && !u.name.isEmpty()) {
            return u.name + "（" + u.username + "）";
        }
        return u.username;
    }

    private static boolean validCheckpointCount(Object raw) {
        return !(raw instanceof List<?>) || ((List<?>) raw).size() <= MAX_CHECKPOINTS;
    }

    private static boolean validCount(Object raw, int max) {
        return !(raw instanceof List<?>) || ((List<?>) raw).size() <= max;
    }

    // ==================== 工具方法 ====================

    private static Map<String, Object> fail(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", false);
        result.put("msg", msg);
        return result;
    }

    private static Map<String, Object> okList(List<?> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("data", data);
        return result;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    /** null 安全转空串（避免把 null 传到客户端） */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 截断到指定长度，防止超长文本入库 */
    private static String clip(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static long clamp(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(str(o));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static Long toLongOrZero(Object o) {
        long v = toLong(o);
        return v > 0 ? v : 0L;
    }

    private static int toInt(Object o, int defaultValue) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(str(o));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Double toDoubleOrNull(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }
}
