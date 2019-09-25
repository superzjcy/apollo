package com.ctrip.framework.apollo.portal.constant;

public interface PermissionType {

  /**
   * 系统级别权限
   */
  String CREATE_APPLICATION = "CreateApplication";
  String MANAGE_APP_MASTER = "ManageAppMaster";

  /**
   * APP级别权限
   */

  String CREATE_NAMESPACE = "CreateNamespace";

  String CREATE_CLUSTER = "CreateCluster";

  /**
   * 分配用户权限的权限
   */
  String ASSIGN_ROLE = "AssignRole";

  /**
   * Namespace级别权限
   */

  String MODIFY_NAMESPACE = "ModifyNamespace";

  String RELEASE_NAMESPACE = "ReleaseNamespace";


}
