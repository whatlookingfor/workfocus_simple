/**
 * Copyright &copy; 2012-2016 <a href="https://github.com/whatlookingfor">whatlookingfor</a> All rights reserved.
 */
package com.mfnets.workfocus.modules.sys.web;

import com.google.common.collect.Maps;
import com.mfnets.workfocus.common.config.Global;
import com.mfnets.workfocus.common.mapper.JsonMapper;
import com.mfnets.workfocus.common.servlet.ValidateCodeServlet;
import com.mfnets.workfocus.common.utils.CacheUtils;
import com.mfnets.workfocus.common.utils.CookieUtils;
import com.mfnets.workfocus.common.utils.IdGen;
import com.mfnets.workfocus.common.utils.StringUtils;
import com.mfnets.workfocus.common.web.BaseController;
import com.mfnets.workfocus.common.web.Servlets;
import com.mfnets.workfocus.modules.sys.security.FormAuthenticationFilter;
import com.mfnets.workfocus.modules.sys.security.Principal;
import com.mfnets.workfocus.modules.sys.utils.UserUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * 登录的Controller
 * @author Jonathan
 * @version 2013-5-31
 */
@Controller
public class LoginController extends BaseController{
	
//	@Autowired
//	private SessionDAO sessionDAO;
	
	/**
	 * 管理登录
	 */
	@RequestMapping(value = "${adminPath}/login", method = RequestMethod.GET)
	public String login(HttpServletRequest request, HttpServletResponse response, Model model) {
		Principal principal = UserUtils.getPrincipal();
		
//		if (logger.isDebugEnabled()){
//			logger.debug("login, active session size: {}", sessionDAO.getActiveSessions(false).size());
//		}
		// 如果已登录，再次访问主页，则退出原账号。
		if (Global.TRUE.equals(Global.getConfig("notAllowRefreshIndex"))){
			CookieUtils.setCookie(response, "LOGINED", "false");
		}

		// 如果已经登录，则跳转到管理首页
		if(principal != null){
			return "redirect:" + adminPath;
		}
		return "modules/sys/login";
	}

	/**
	 * 登录失败，真正登录的POST请求由Filter完成
	 */
	@RequestMapping(value = "${adminPath}/login", method = RequestMethod.POST)
	public String loginFail(HttpServletRequest request, HttpServletResponse response, Model model) {
		Principal principal = UserUtils.getPrincipal();
		
		// 如果已经登录，则跳转到管理首页
		if(principal != null){
			return "redirect:" + adminPath;
		}

		String username = WebUtils.getCleanParam(request, FormAuthenticationFilter.DEFAULT_USERNAME_PARAM);
		boolean rememberMe = WebUtils.isTrue(request, FormAuthenticationFilter.DEFAULT_REMEMBER_ME_PARAM);
		String exception = (String)request.getAttribute(FormAuthenticationFilter.DEFAULT_ERROR_KEY_ATTRIBUTE_NAME);
		String message = (String)request.getAttribute(FormAuthenticationFilter.DEFAULT_MESSAGE_PARAM);
		
		if (StringUtils.isBlank(message) || StringUtils.equals(message, "null")){
			message = "用户或密码错误, 请重试.";
		}

		model.addAttribute(FormAuthenticationFilter.DEFAULT_USERNAME_PARAM, username);
		model.addAttribute(FormAuthenticationFilter.DEFAULT_REMEMBER_ME_PARAM, rememberMe);
		model.addAttribute(FormAuthenticationFilter.DEFAULT_ERROR_KEY_ATTRIBUTE_NAME, exception);
		model.addAttribute(FormAuthenticationFilter.DEFAULT_MESSAGE_PARAM, message);
		
//		if (logger.isDebugEnabled()){
//			logger.debug("login fail, active session size: {}, message: {}, exception: {}",
//					sessionDAO.getActiveSessions(false).size(), message, exception);
//		}
		
		// 非授权异常，登录失败，验证码加1。
		if (!UnauthorizedException.class.getName().equals(exception)){
			model.addAttribute("isValidateCodeLogin", isValidateCodeLogin(username, true, false));
		}
		
		// 验证失败清空验证码
		request.getSession().setAttribute(ValidateCodeServlet.VALIDATE_CODE, IdGen.uuid());

		
		return "modules/sys/login";
	}

	/**
	 * 登录成功，进入管理首页
	 */
	@RequiresPermissions("user")
	@RequestMapping(value = "${adminPath}")
	public String index(HttpServletRequest request, HttpServletResponse response) {
		Principal principal = UserUtils.getPrincipal();

		// 登录成功后，验证码计算器清零
		isValidateCodeLogin(principal.getLoginName(), false, true);
		
//		if (logger.isDebugEnabled()){
//			logger.debug("show index, active session size: {}", sessionDAO.getActiveSessions(false).size());
//		}
		
		// 如果已登录，再次访问主页，则退出原账号。
		if (Global.TRUE.equals(Global.getConfig("notAllowRefreshIndex"))){
			String logined = CookieUtils.getCookie(request, "LOGINED");
			if (StringUtils.isBlank(logined) || "false".equals(logined)){
				CookieUtils.setCookie(response, "LOGINED", "true");
			}else if (StringUtils.equals(logined, "true")){
				UserUtils.getSubject().logout();
				return "redirect:" + adminPath + "/login";
			}
		}
		return "modules/sys/sysIndex";
	}


//	@RequestMapping(value = "**")
//	public String pageNotFound(HttpServletRequest request,HttpServletResponse response) throws IOException {
//		Map<String,Object> map = Maps.newHashMap();
//		map.put("status",404);
//		map.put("msg","page not found");
//		map.put("timestamp", System.currentTimeMillis());
//		response.setStatus(404);
//		if(Servlets.isAjaxRequest(request)){
//			response.setContentType("application/json");
//			response.setCharacterEncoding("utf-8");
//			response.getWriter().print(JsonMapper.toJsonString(map));
//			return null;
//		}else {
//			return "error/404";
//		}
//	}

//	@RequestMapping(value = "*/*")
//	public String pageNotFound1(HttpServletRequest request,HttpServletResponse response){
//		Map<String,Object> map = Maps.newHashMap();
//		map.put("status",404);
//		map.put("msg","page not found");
//		map.put("timestamp", System.currentTimeMillis());
//		if(Servlets.isAjaxRequest(request)){
//			return renderString(response,map);
//		}else {
//			return "error/404";
//		}
//	}
//
//	@RequestMapping(value = "*/*/*")
//	public String pageNotFound2(HttpServletRequest request,HttpServletResponse response){
//		Map<String,Object> map = Maps.newHashMap();
//		map.put("status",404);
//		map.put("msg","page not found");
//		map.put("timestamp", System.currentTimeMillis());
//		if(Servlets.isAjaxRequest(request)){
//			return renderString(response,map);
//		}else {
//			return "error/404";
//		}
//	}




	/**
	 * 获取主题方案
	 */
	@RequestMapping(value = "/theme/{theme}")
	public String getThemeInCookie(@PathVariable String theme, HttpServletRequest request, HttpServletResponse response){
		if (StringUtils.isNotBlank(theme)){
			CookieUtils.setCookie(response, "theme", theme);
		}else{
			theme = CookieUtils.getCookie(request, "theme");
		}
		return "redirect:"+request.getParameter("url");
	}
	
	/**
	 * 是否是验证码登录
	 * @param username 用户名
	 * @param isFail 计数加1
	 * @param clean 计数清零
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static boolean isValidateCodeLogin(String username, boolean isFail, boolean clean){
		Map<String, Integer> loginFailMap = (Map<String, Integer>)CacheUtils.get("loginFailMap");
		if (loginFailMap==null){
			loginFailMap = Maps.newHashMap();
			CacheUtils.put("loginFailMap", loginFailMap);
		}
		Integer loginFailNum = loginFailMap.get(username);
		if (loginFailNum==null){
			loginFailNum = 0;
		}
		if (isFail){
			loginFailNum++;
			loginFailMap.put(username, loginFailNum);
		}
		if (clean){
			loginFailMap.remove(username);
		}
		return loginFailNum >= 3;
	}
}
