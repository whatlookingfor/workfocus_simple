/**
 * Copyright &copy; 2012-2014 <a href="https://github.com/thinkgem/jeesite">JeeSite</a> All rights reserved.
 */
package com.mfnets.workfocus.modules.sys.security;

import java.io.Serializable;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.mfnets.workfocus.common.servlet.ValidateCodeServlet;
import com.mfnets.workfocus.common.utils.Encodes;
import com.mfnets.workfocus.common.utils.SpringContextHolder;
import com.mfnets.workfocus.common.web.Servlets;
import com.mfnets.workfocus.modules.sys.entity.Menu;
import com.mfnets.workfocus.modules.sys.entity.Role;
import com.mfnets.workfocus.modules.sys.entity.User;
import com.mfnets.workfocus.modules.sys.service.SystemService;
import com.mfnets.workfocus.modules.sys.utils.LogUtils;
import com.mfnets.workfocus.modules.sys.utils.UserUtils;
import com.mfnets.workfocus.modules.sys.web.LoginController;

/**
 * 系统安全认证实现类
 * @author ThinkGem
 * @version 2014-7-5
 */
@Service
//@DependsOn({"userDao","roleDao","menuDao"})
public class SystemAuthorizingRealm extends AuthorizingRealm {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private SystemService systemService;

	public boolean supports(AuthenticationToken token) {
		//仅支持StatelessToken类型的Token
		return token instanceof UsernamePasswordToken;
	}

	/**
	 * 认证回调函数, 登录时调用
	 */
	@Override
	protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authcToken) {
		UsernamePasswordToken token = (UsernamePasswordToken) authcToken;
		
//		int activeSessionSize = getSystemService().getSessionDao().getActiveSessions(false).size();
//		if (logger.isDebugEnabled()){
//			logger.debug("login submit, active session size: {}, username: {}", activeSessionSize, token.getUsername());
//		}
		
		// 校验登录验证码
		if (LoginController.isValidateCodeLogin(token.getUsername(), false, false)){
			Session session = UserUtils.getSession();
			String code = (String)session.getAttribute(ValidateCodeServlet.VALIDATE_CODE);
			if (token.getCaptcha() == null || !token.getCaptcha().toUpperCase().equals(code)){
				throw new AuthenticationException("msg:验证码错误, 请重试.");
			}
		}
		
		// 校验用户名密码
		User user = getSystemService().getUserByLoginName(token.getUsername());
		if (user != null) {
//			if (Global.NO.equals(user.getLoginFlag())){
//				throw new AuthenticationException("msg:该已帐号禁止登录.");
//			}
			byte[] salt = Encodes.decodeHex(user.getPassword().substring(0,16));
			System.out.println(ByteSource.Util.bytes(salt));
			System.out.println(getName());
			return new SimpleAuthenticationInfo(new Principal(user),
					user.getPassword().substring(16), ByteSource.Util.bytes(salt), getName());
		} else {
			return null;
		}
	}

	/**
	 * 授权查询回调函数, 进行鉴权但缓存中无用户的授权信息时调用
	 */
	@Override
	protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
		Principal principal = (Principal) getAvailablePrincipal(principals);
		// 获取当前已登录的用户
//		if (!Global.TRUE.equals(Global.getConfig("user.multiAccountLogin"))){
//			Collection<Session> sessions = getSystemService().getSessionDao().getActiveSessions(true, principal, UserUtils.getSession());
//			if (sessions.size() > 0){
//				// 如果是登录进来的，则踢出已在线用户
//				if (UserUtils.getSubject().isAuthenticated()){
//					for (Session session : sessions){
//						getSystemService().getSessionDao().delete(session);
//					}
//				}
//				// 记住我进来的，并且当前用户已登录，则退出当前用户提示信息。
//				else{
//					UserUtils.getSubject().logout();
//					throw new AuthenticationException("msg:账号已在其它地方登录，请重新登录。");
//				}
//			}
//		}
		User user = getSystemService().getUserByLoginName(principal.getLoginName());
		if (user != null) {
			SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
			List<Menu> list = UserUtils.getMenuList();
			for (Menu menu : list){
				if (StringUtils.isNotBlank(menu.getPermission())){
					// 添加基于Permission的权限信息
					for (String permission : StringUtils.split(menu.getPermission(),",")){
						info.addStringPermission(permission);
					}
				}
			}
			// 添加用户权限
			info.addStringPermission("user");
			// 添加用户角色信息
			for (Role role : user.getRoleList()){
				info.addRole(role.getEname());
			}
			// 更新登录IP和时间
			getSystemService().updateUserLoginInfo(user);
			// 记录登录日志
			LogUtils.saveLog(Servlets.getRequest(), "系统登录");
			return info;
		} else {
			return null;
		}
	}


	
	@Override
	public boolean isPermitted(PrincipalCollection principals, Permission permission) {
		System.out.println(principals.fromRealm(getName()));
		if (principals.fromRealm(getName()).isEmpty()) {
			return false;
		}else{
			return super.isPermitted(principals, permission);
		}

	}


	/**
	 * {@inheritDoc}
	 * 重写函数，如果不是web app登录，则用无状态授权
	 */
	@Override
	public boolean hasRole(PrincipalCollection principals, String roleIdentifier) {
		if (principals.fromRealm(getName()).isEmpty()) {
			logger.debug("not web app  auth");
			return false;
		}
		else {
			return super.hasRole(principals, roleIdentifier);
		}
	}


	/**
	 * 设定密码校验的Hash算法与迭代次数
	 */
	@PostConstruct
	public void initCredentialsMatcher() {
		HashedCredentialsMatcher matcher = new HashedCredentialsMatcher(SystemService.HASH_ALGORITHM);
		matcher.setHashIterations(SystemService.HASH_INTERATIONS);
		setCredentialsMatcher(matcher);
	}
	
//	/**
//	 * 清空用户关联权限认证，待下次使用时重新加载
//	 */
//	public void clearCachedAuthorizationInfo(Principal principal) {
//		SimplePrincipalCollection principals = new SimplePrincipalCollection(principal, getName());
//		clearCachedAuthorizationInfo(principals);
//	}



	/**
	 * 获取系统业务对象
	 */
	public SystemService getSystemService() {
		if (systemService == null){
			systemService = SpringContextHolder.getBean(SystemService.class);
		}
		return systemService;
	}
	
	/**
	 * 授权用户信息
	 */
	public static class Principal implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private String id; // 编号
		private String loginName; // 登录名
		private String name; // 姓名
		
//		private Map<String, Object> cacheMap;

		public Principal(User user) {
			this.id = user.getId();
			this.loginName = user.getLoginName();
			this.name = user.getName();
		}

		public String getId() {
			return id;
		}

		public String getLoginName() {
			return loginName;
		}

		public String getName() {
			return name;
		}


//		@JsonIgnore
//		public Map<String, Object> getCacheMap() {
//			if (cacheMap==null){
//				cacheMap = new HashMap<String, Object>();
//			}
//			return cacheMap;
//		}

		/**
		 * 获取SESSIONID
		 */
		public String getSessionid() {
			try{
				return (String) UserUtils.getSession().getId();
			}catch (Exception e) {
				return "";
			}
		}
		
		@Override
		public String toString() {
			return id;
		}

	}
}
