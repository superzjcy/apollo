package com.ctrip.framework.apollo.portal.spi.defaultimpl;

import com.ctrip.framework.apollo.openapi.repository.ConsumerRoleRepository;
import com.ctrip.framework.apollo.portal.component.config.PortalConfig;
import com.ctrip.framework.apollo.portal.entity.bo.UserInfo;
import com.ctrip.framework.apollo.portal.entity.po.Permission;
import com.ctrip.framework.apollo.portal.entity.po.Role;
import com.ctrip.framework.apollo.portal.entity.po.RolePermission;
import com.ctrip.framework.apollo.portal.entity.po.UserRole;
import com.ctrip.framework.apollo.portal.repository.PermissionRepository;
import com.ctrip.framework.apollo.portal.repository.RolePermissionRepository;
import com.ctrip.framework.apollo.portal.repository.RoleRepository;
import com.ctrip.framework.apollo.portal.repository.UserRoleRepository;
import com.ctrip.framework.apollo.portal.service.RolePermissionService;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by timothy on 2017/4/26.
 */
public class DefaultRolePermissionService implements RolePermissionService {
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RolePermissionRepository rolePermissionRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PermissionRepository permissionRepository;
    @Autowired
    private PortalConfig portalConfig;
    @Autowired
    private ConsumerRoleRepository consumerRoleRepository;

    /**
     * Create role with permissions, note that role name should be unique
     */
    @Transactional
    public Role createRoleWithPermissions(Role role, Set<Long> permissionIds) {
        Role current = findRoleByRoleName(role.getRoleName());
        Preconditions.checkState(current == null, "Role %s already exists!", role.getRoleName());

        Role createdRole = roleRepository.save(role);

        if (!CollectionUtils.isEmpty(permissionIds)) {
            Iterable<RolePermission> rolePermissions = permissionIds.stream().map(permissionId -> {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(createdRole.getId());
                rolePermission.setPermissionId(permissionId);
                rolePermission.setDataChangeCreatedBy(createdRole.getDataChangeCreatedBy());
                rolePermission.setDataChangeLastModifiedBy(createdRole.getDataChangeLastModifiedBy());
                return rolePermission;
            }).collect(Collectors.toList());
            rolePermissionRepository.saveAll(rolePermissions);
        }

        return createdRole;
    }

    /**
     * Assign role to users
     *
     * @return the users assigned roles
     */
    @Transactional
    public Set<String> assignRoleToUsers(String roleName, Set<String> userIds,
                                         String operatorUserId) {
        Role role = findRoleByRoleName(roleName);
        Preconditions.checkState(role != null, "Role %s doesn't exist!", roleName);

        List<UserRole> existedUserRoles =
                userRoleRepository.findByUserIdInAndRoleId(userIds, role.getId());
        Set<String> existedUserIds =
            existedUserRoles.stream().map(UserRole::getUserId).collect(Collectors.toSet());

        Set<String> toAssignUserIds = Sets.difference(userIds, existedUserIds);

        Iterable<UserRole> toCreate = toAssignUserIds.stream().map(userId -> {
            UserRole userRole = new UserRole();
            userRole.setRoleId(role.getId());
            userRole.setUserId(userId);
            userRole.setDataChangeCreatedBy(operatorUserId);
            userRole.setDataChangeLastModifiedBy(operatorUserId);
            return userRole;
        }).collect(Collectors.toList());

        userRoleRepository.saveAll(toCreate);
        return toAssignUserIds;
    }

    /**
     * Remove role from users
     */
    @Transactional
    public void removeRoleFromUsers(String roleName, Set<String> userIds, String operatorUserId) {
        Role role = findRoleByRoleName(roleName);
        Preconditions.checkState(role != null, "Role %s doesn't exist!", roleName);

        List<UserRole> existedUserRoles =
                userRoleRepository.findByUserIdInAndRoleId(userIds, role.getId());

        for (UserRole userRole : existedUserRoles) {
            userRole.setDeleted(true);
            userRole.setDataChangeLastModifiedTime(new Date());
            userRole.setDataChangeLastModifiedBy(operatorUserId);
        }

        userRoleRepository.saveAll(existedUserRoles);
    }

    /**
     * Query users with role
     */
    public Set<UserInfo> queryUsersWithRole(String roleName) {
        Role role = findRoleByRoleName(roleName);

        if (role == null) {
            return Collections.emptySet();
        }

        List<UserRole> userRoles = userRoleRepository.findByRoleId(role.getId());

        Set<UserInfo> users = userRoles.stream().map(userRole -> {
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(userRole.getUserId());
            return userInfo;
        }).collect(Collectors.toSet());

        return users;
    }

    /**
     * Find role by role name, note that roleName should be unique
     */
    public Role findRoleByRoleName(String roleName) {
        return roleRepository.findTopByRoleName(roleName);
    }

    /**
     * Check whether user has the permission
     */
    public boolean userHasPermission(String userId, String permissionType, String targetId) {

        // 根据权限类型和targetId查询权限
        Permission permission =
                permissionRepository.findTopByPermissionTypeAndTargetId(permissionType, targetId);
        if (permission == null) {
            return false;
        }

        // 超级用户则具有所有权限
        if (isSuperAdmin(userId)) {
            return true;
        }

        // 查询用户的角色列表
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (CollectionUtils.isEmpty(userRoles)) {
            return false;
        }

        // 角色Id去重
        Set<Long> roleIds =
            userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());

        // 查询RolePemission表
        List<RolePermission> rolePermissions = rolePermissionRepository.findByRoleIdIn(roleIds);
        if (CollectionUtils.isEmpty(rolePermissions)) {
            return false;
        }

        // 检查用户角色对应权限的id和需要的权限对应的id是否匹配
        for (RolePermission rolePermission : rolePermissions) {
            if (rolePermission.getPermissionId() == permission.getId()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<Role> findUserRoles(String userId) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        if (CollectionUtils.isEmpty(userRoles)) {
            return Collections.emptyList();
        }

        Set<Long> roleIds = userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());

        return Lists.newLinkedList(roleRepository.findAllById(roleIds));
    }

    public boolean isSuperAdmin(String userId) {
        return portalConfig.superAdmins().contains(userId);
    }

    /**
     * Create permission, note that permissionType + targetId should be unique
     */
    @Transactional
    public Permission createPermission(Permission permission) {
        String permissionType = permission.getPermissionType();
        String targetId = permission.getTargetId();
        Permission current =
                permissionRepository.findTopByPermissionTypeAndTargetId(permissionType, targetId);
        Preconditions.checkState(current == null,
                "Permission with permissionType %s targetId %s already exists!", permissionType, targetId);

        return permissionRepository.save(permission);
    }

    /**
     * Create permissions, note that permissionType + targetId should be unique
     */
    @Transactional
    public Set<Permission> createPermissions(Set<Permission> permissions) {
        Multimap<String, String> targetIdPermissionTypes = HashMultimap.create();
        for (Permission permission : permissions) {
            targetIdPermissionTypes.put(permission.getTargetId(), permission.getPermissionType());
        }

        for (String targetId : targetIdPermissionTypes.keySet()) {
            Collection<String> permissionTypes = targetIdPermissionTypes.get(targetId);
            List<Permission> current =
                    permissionRepository.findByPermissionTypeInAndTargetId(permissionTypes, targetId);
            Preconditions.checkState(CollectionUtils.isEmpty(current),
                    "Permission with permissionType %s targetId %s already exists!", permissionTypes,
                    targetId);
        }

        Iterable<Permission> results = permissionRepository.saveAll(permissions);
        return StreamSupport.stream(results.spliterator(), false).collect(Collectors.toSet());
    }

    @Transactional
    @Override
    public void deleteRolePermissionsByAppId(String appId, String operator) {
        List<Long> permissionIds = permissionRepository.findPermissionIdsByAppId(appId);

        if (!permissionIds.isEmpty()) {
            // 1. delete Permission
            permissionRepository.batchDelete(permissionIds, operator);

            // 2. delete Role Permission
            rolePermissionRepository.batchDeleteByPermissionIds(permissionIds, operator);
        }

        List<Long> roleIds = roleRepository.findRoleIdsByAppId(appId);

        if (!roleIds.isEmpty()) {
            // 3. delete Role
            roleRepository.batchDelete(roleIds, operator);

            // 4. delete User Role
            userRoleRepository.batchDeleteByRoleIds(roleIds, operator);

            // 5. delete Consumer Role
            consumerRoleRepository.batchDeleteByRoleIds(roleIds, operator);
        }
    }

    @Transactional
    @Override
    public void deleteRolePermissionsByAppIdAndNamespace(String appId, String namespaceName, String operator) {
        List<Long> permissionIds = permissionRepository.findPermissionIdsByAppIdAndNamespace(appId, namespaceName);

        if (!permissionIds.isEmpty()) {
            // 1. delete Permission
            permissionRepository.batchDelete(permissionIds, operator);

            // 2. delete Role Permission
            rolePermissionRepository.batchDeleteByPermissionIds(permissionIds, operator);
        }

        List<Long> roleIds = roleRepository.findRoleIdsByAppIdAndNamespace(appId, namespaceName);

        if (!roleIds.isEmpty()) {
            // 3. delete Role
            roleRepository.batchDelete(roleIds, operator);

            // 4. delete User Role
            userRoleRepository.batchDeleteByRoleIds(roleIds, operator);

            // 5. delete Consumer Role
            consumerRoleRepository.batchDeleteByRoleIds(roleIds, operator);
        }
    }
}
