package com.wereen.competitionplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wereen.competitionplatform.common.UserRole;
import com.wereen.competitionplatform.exception.BusinessException;
import com.wereen.competitionplatform.mapper.UserMapper;
import com.wereen.competitionplatform.mapper.UserWalletMapper;
import com.wereen.competitionplatform.model.entity.User;
import com.wereen.competitionplatform.model.entity.UserWallet;
import com.wereen.competitionplatform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserWalletMapper userWalletMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final WalletService walletService;

    /**
     * 用户注册
     */
    @Transactional(rollbackFor = Exception.class)
    public User register(String username, String email, String password) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        // 检查邮箱是否已存在
        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("邮箱已被注册");
        }

        // 创建用户
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setKycStatus(0); // 未认证
        user.setRiskFlag(0); // 正常
        user.setStatus(1); // 启用
        user.setRole(UserRole.USER); // 默认角色为普通用户

        int rows = userMapper.insert(user);
        if (rows == 0) {
            throw new BusinessException("注册失败");
        }

        // 创建用户钱包
        walletService.getOrCreateBalance(user.getId(), "CNY");

        log.info("用户注册成功: userId={}, username={}", user.getId(), username);
        return user;
    }

    /**
     * 用户登录并绑定钱包
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(String username, String password, String walletAddress) {
        // 查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 检查用户状态
        if (user.getStatus() == 0) {
            throw new BusinessException("账户已被禁用");
        }

        // 钱包地址可选：有则绑定，无则跳过
        if (StringUtils.hasText(walletAddress)) {
            bindWalletAddress(user, normalizeWalletAddress(walletAddress));
        }

        // 生成 Token (包含角色信息)
        String role = user.getRole() != null ? user.getRole() : UserRole.USER;
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("email", user.getEmail());
        result.put("role", role);
        result.put("walletAddress", user.getWalletAddress());

        log.info("用户登录成功: userId={}, username={}, role={}", user.getId(), username, role);
        return result;
    }

    /**
     * 根据ID查询用户
     */
    public User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 根据用户名查询用户
     */
    public User getUserByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    /**
     * 批量查询用户并返回Map
     */
    public Map<Long, User> getUserMapByIds(Collection<Long> userIds) {
        Map<Long, User> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        for (User user : users) {
            result.put(user.getId(), user);
        }
        return result;
    }

    /**
     * 更新用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(User user) {
        if (user.getId() == null) {
            throw new BusinessException("用户ID不能为空");
        }

        User existing = getUserById(user.getId());
        if (existing == null) {
            throw new BusinessException("用户不存在");
        }

        int rows = userMapper.updateById(user);
        if (rows == 0) {
            throw new BusinessException("更新用户信息失败");
        }

        log.info("更新用户信息成功: userId={}", user.getId());
        return user;
    }

    private void bindWalletAddress(User user, String walletAddress) {
        if (!StringUtils.hasText(walletAddress)) {
            return;
        }

        String existing = user.getWalletAddress();
        if (existing != null && existing.equalsIgnoreCase(walletAddress)) {
            user.setWalletAddress(existing.toLowerCase());
            return;
        }

        if (StringUtils.hasText(existing) && !existing.equalsIgnoreCase(walletAddress)) {
            throw new BusinessException("当前账户已绑定其他钱包地址，请使用原钱包登录");
        }

        // 检查钱包是否被其他用户绑定
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getWalletAddress, walletAddress);
        User another = userMapper.selectOne(wrapper);
        if (another != null && !another.getId().equals(user.getId())) {
            throw new BusinessException("该钱包地址已绑定其他账户");
        }

        User update = new User();
        update.setId(user.getId());
        update.setWalletAddress(walletAddress);
        int rows = userMapper.updateById(update);
        if (rows == 0) {
            throw new BusinessException("绑定钱包失败，请稍后重试");
        }

        user.setWalletAddress(walletAddress);
        log.info("用户绑定钱包成功: userId={}, wallet={}", user.getId(), walletAddress);

        // 绑定链上钱包
        bindChainWallet(user.getId(), walletAddress);
    }

    private void bindChainWallet(Long userId, String walletAddress) {
        UserWallet wallet = userWalletMapper.selectByUserId(userId);
        if (wallet == null) {
            wallet = new UserWallet();
            wallet.setUserId(userId);
            wallet.setAddress(walletAddress);
            wallet.setBalance(BigDecimal.ZERO);
            userWalletMapper.insert(wallet);
            log.info("创建用户链上钱包: userId={}, wallet={}", userId, walletAddress);
        } else if (!wallet.getAddress().equalsIgnoreCase(walletAddress)) {
            wallet.setAddress(walletAddress);
            userWalletMapper.updateById(wallet);
            log.info("更新用户链上钱包: userId={}, wallet={}", userId, walletAddress);
        }
    }

    private String normalizeWalletAddress(String walletAddress) {
        String value = walletAddress.trim();
        if (!value.startsWith("0x") || value.length() != 42) {
            throw new BusinessException("钱包地址格式不正确");
        }
        if (!HEX_PATTERN.matcher(value.substring(2)).matches()) {
            throw new BusinessException("钱包地址格式不正确");
        }
        return value.toLowerCase();
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{40}$");
}
