/**
 *  Copyright 2016-2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.antheminc.oss.nimbus.domain.cmd.exec.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.antheminc.oss.nimbus.domain.AbstractFrameworkIngerationPersistableTests;
import com.antheminc.oss.nimbus.domain.cmd.Action;
import com.antheminc.oss.nimbus.domain.cmd.exec.CommandExecution.MultiOutput;
import com.antheminc.oss.nimbus.domain.cmd.exec.CommandExecution.Output;
import com.antheminc.oss.nimbus.domain.model.state.EntityState.Param;
import com.antheminc.oss.nimbus.domain.session.SessionProvider;
import com.antheminc.oss.nimbus.entity.client.access.ClientAccessEntity;
import com.antheminc.oss.nimbus.entity.client.access.ClientUserRole;
import com.antheminc.oss.nimbus.entity.client.user.ClientUser;
import com.antheminc.oss.nimbus.entity.user.UserRole;
import com.antheminc.oss.nimbus.support.Holder;
import com.antheminc.oss.nimbus.test.domain.support.utils.ExtractResponseOutputUtils;
import com.antheminc.oss.nimbus.test.domain.support.utils.MockHttpRequestBuilder;
import com.antheminc.oss.nimbus.test.scenarios.s0.core.SampleCoreEntityAccess;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Rakesh Patel
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AccessConditionalStateEventHandlerHttpTest extends AbstractFrameworkIngerationPersistableTests {
	
	@Autowired SessionProvider sessionProvider;


	@Test
	public void t03_accessConditionalReadOnly() throws Exception {
		String userLoginId = createClientUserWithRoles("harvey","intake");
		
		Param<?> p = excuteNewConfigCore(userLoginId);
		assertNotNull(p);
		
		Param<?> accessParam = p.findParamByPath("/accessConditional_WhenAuthorities_Hidden1");
		
		assertFalse(accessParam.isVisible());
		assertFalse(accessParam.isEnabled());
	}
	
	@Test
	public void t04_accessConditionalHidden() throws Exception {
		String userLoginId = createClientUserWithRoles("batman","intake","clinician");
		
		Param<?> p = excuteNewConfigCore(userLoginId);
		assertNotNull(p);
		
		Param<?> accessParam = p.findParamByPath("/accessConditional_WhenAuthorities_Read2");
		
		assertFalse(accessParam.isVisible());
		assertFalse(accessParam.isEnabled());
	}
	
	@Test
	public void t04_accessConditionalGridLinkHidden() throws Exception {
		String userLoginId = createClientUserWithRoles("superman","intake","clinician");
		
		
		SampleCoreEntityAccess scea = new SampleCoreEntityAccess();
		scea.setAttr_String("test1");
		
		SampleCoreEntityAccess scea2 = new SampleCoreEntityAccess();
		scea2.setAttr_String("test2");
		mongo.save(scea, "sample_core_access");
		mongo.save(scea2, "sample_core_access");
		
		
		Param<?> p = excuteNewConfigView(userLoginId);
		assertNotNull(p);
		
		String refId = p.findStateByPath("/.m/id");
		
		final MockHttpServletRequest gridRequest = MockHttpRequestBuilder
				.withUri(VIEW_PARAM_ACCESS_ROOT)
				.addRefId(refId)
				.addNested("/vpSampleCoreEntityAccess/vtSampleCoreEntityAccess/vsSampleCoreEntityAccess/vgSampleCoreEntities")
				.addAction(Action._get)
				.getMock();
		final Object gridResponse = controller.handleGet(gridRequest, null);
		assertNotNull(gridResponse);
		
		List<Output<?>> outputs = MultiOutput.class.cast(Holder.class.cast(gridResponse).getState()).getOutputs();
		
		assertNotNull(outputs);
		
		for(Output<?> op: outputs) {
			if(op.getValue() instanceof Param<?>) {
				Param<?> param = (Param<?>)op.getValue();
				
				Param<?> attrStringParam = param.findParamByPath("/0/attr_String"); // READ
				assertNotNull(attrStringParam);
				assertTrue(attrStringParam.isVisible());
				assertFalse(attrStringParam.isEnabled());
				
				Param<?> viewLinkParam = param.findParamByPath("/0/viewLink"); // HIDDEN
				assertNotNull(viewLinkParam);
				assertFalse(viewLinkParam.isVisible());
				assertFalse(viewLinkParam.isEnabled());
				
			}
		}
		
	}
	
	//TODO Rakesh 02/14/2018- test once the grid return type of page is available in the framework
	@Ignore
	public void t05_accessConditionalGridPagination() throws Exception {
		String userLoginId = createClientUserWithRoles("superman","intake","clinician");
		
		SampleCoreEntityAccess scea = new SampleCoreEntityAccess();
		scea.setAttr_String("test1");
		
		SampleCoreEntityAccess scea2 = new SampleCoreEntityAccess();
		scea2.setAttr_String("test2");
		mongo.save(scea, "sample_core_access");
		mongo.save(scea2, "sample_core_access");
		
		
		Param<?> p = excuteNewConfigView(userLoginId);
		assertNotNull(p);
		
		String refId = p.findStateByPath("/.m/id");
		
		final MockHttpServletRequest gridRequest = MockHttpRequestBuilder
				.withUri(VIEW_PARAM_ACCESS_ROOT)
				.addRefId(refId)
				.addNested("/vpSampleCoreEntityAccess/vtSampleCoreEntityAccess/vsSamplePageCoreEntityAccess/vgSamplePageCoreEntities")
//				.addParam("pageSize", "1")
//				.addParam("page", "0")
//				.addParam("sortBy", "attr_String,asc")
				.addParam("pageCriteria", "pageSize=5&page=0&sortBy=attr_String,DESC")
				.addAction(Action._get)
				.getMock();
		final Object gridResponse = controller.handleGet(gridRequest, null);
		assertNotNull(gridResponse);
		
		
		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(gridResponse);
		System.out.println(json);
		
		List<Output<?>> outputs = MultiOutput.class.cast(Holder.class.cast(gridResponse).getState()).getOutputs();
		
		assertNotNull(outputs);
		
		for(Output<?> op: outputs) {
			if(op.getValue() instanceof Param<?>) {
				Param<?> param = (Param<?>)op.getValue();
				
				Param<?> attrStringParam = param.findParamByPath("/0/attr_String"); // READ
				assertNotNull(attrStringParam);
				assertTrue(attrStringParam.isVisible());
				assertFalse(attrStringParam.isEnabled());
				
				Param<?> viewLinkParam = param.findParamByPath("/0/viewLink"); // HIDDEN
				assertNotNull(viewLinkParam);
				assertFalse(viewLinkParam.isVisible());
				assertFalse(viewLinkParam.isEnabled());
				
			}
		}
		
	}

	@SuppressWarnings("unchecked")
	private Param<?> excuteNewConfigCore(String userLoginId) {
		final MockHttpServletRequest fetchUser = MockHttpRequestBuilder.withUri(USER_PARAM_ROOT)
				.addAction(Action._search)
				.addParam("fn", "query")
				.addParam("where", "clientuser.loginId.eq('"+userLoginId+"')")
				.addParam("fetch","1")
				.getMock();
		
		Holder<MultiOutput> holder = (Holder<MultiOutput>) controller.handlePost(fetchUser, null);
		MultiOutput output = holder.getState();
		ClientUser clientuser = (ClientUser) output.getSingleResult();
		assertNotNull(clientuser);
		sessionProvider.setAttribute("client-user-key", clientuser);
		
		createResolvedAccessEntities(clientuser);

		final MockHttpServletRequest req = MockHttpRequestBuilder.withUri(CORE_PARAM_ACCESS_ROOT)
				.addAction(Action._new)
				.getMock();

		final Object resp = controller.handleGet(req, null);
		Param<?> p = ExtractResponseOutputUtils.extractOutput(resp);
		return p;
	}
	
	@SuppressWarnings("unchecked")
	private Param<?> excuteNewConfigView(String userLoginId) {
		final MockHttpServletRequest fetchUser = MockHttpRequestBuilder.withUri(USER_PARAM_ROOT)
				.addAction(Action._search)
				.addParam("fn", "query")
				.addParam("where", "clientuser.loginId.eq('"+userLoginId+"')")
				.addParam("fetch","1")
				.getMock();
		
		Holder<MultiOutput> holder = (Holder<MultiOutput>) controller.handlePost(fetchUser, null);
		MultiOutput output = holder.getState();
		ClientUser clientuser = (ClientUser) output.getSingleResult();
		assertNotNull(clientuser);
		sessionProvider.setAttribute("client-user-key", clientuser);
		
		createResolvedAccessEntities(clientuser);

		final MockHttpServletRequest req = MockHttpRequestBuilder.withUri(VIEW_PARAM_ACCESS_ROOT)
				.addAction(Action._new)
				.getMock();

		final Object resp = controller.handleGet(req, null);
		Param<?> p = ExtractResponseOutputUtils.extractOutput(resp);
		return p;
	}

	private void createResolvedAccessEntities(ClientUser clientuser) {
		Holder<MultiOutput> holder;
		MultiOutput output;
		Set<String> userRoleCodes = clientuser.getRoles().stream().map(UserRole::getRoleId).collect(Collectors.toSet());
		
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for(String userRole: userRoleCodes) {
			if(i == userRoleCodes.size() - 1) {
				sb.append("\"").append(userRole).append("\"");
			}
			else{
				sb.append("\"").append(userRole).append("\"").append(",");
			}
			i++;
			
		}
		
		final MockHttpServletRequest fetchUserRole = MockHttpRequestBuilder.withUri(USEREOLE_PARAM_ROOT)
				.addAction(Action._search)
				.addParam("fn", "query")
				.addParam("where", "userrole.code.in("+sb.toString()+")")
				.getMock();
		
		holder = (Holder<MultiOutput>) controller.handlePost(fetchUserRole, null);
		output = holder.getState();
		List<ClientUserRole> clientuserRoles = (List<ClientUserRole>) output.getSingleResult();
		assertNotNull(clientuserRoles);
		
		Set<String> allAuthorityIds = new HashSet<>();
		
		for(ClientUserRole cUserRole: clientuserRoles) {
			allAuthorityIds.addAll(cUserRole.getAccessEntities());
		}
		
		StringBuilder sb2 = new StringBuilder();
		int j = 0;
		for(String authorityId: allAuthorityIds) {
			if(j == allAuthorityIds.size() - 1) {
				sb2.append("\"").append(authorityId).append("\"");
			}
			else{
				sb2.append("\"").append(authorityId).append("\"").append(",");
			}
			i++;
			
		}
		
		final MockHttpServletRequest fetchAuthorities = MockHttpRequestBuilder.withUri("/hooli/thebox/p/authorities")
				.addAction(Action._search)
				.addParam("fn", "query")
				.addParam("where", "authorities.code.in("+sb2.toString()+")")
				.getMock();
		
		holder = (Holder<MultiOutput>) controller.handlePost(fetchAuthorities, null);
		output = holder.getState();
		List<ClientAccessEntity> authorities = (List<ClientAccessEntity>) output.getSingleResult();
		assertNotNull(authorities);
		
		
		List<String> resolvedAccessEntities = new ArrayList<>();
		//authorities.forEach((r) -> resolvedAccessEntities.addAll(r.getAccessEntities()));
		
		clientuser.setResolvedAccessEntities(authorities);
	}
	
	private String createClientUserWithRoles(String loginId, String... roles) {
		ClientUser cu = new ClientUser();
		cu.setLoginId(loginId);
		
		List<UserRole> userRoles = new ArrayList<>();
		Arrays.asList(roles).forEach((r) -> {
			
			ClientUserRole userRole = new ClientUserRole();
			userRole.setCode(r);
			userRole.setEffectiveDate(LocalDate.now());
			
			List<String> accessEntities = new ArrayList<>();
			
			ClientAccessEntity accessEntity = new ClientAccessEntity();
			accessEntity.setCode("member_management");
			
			mongo.save(accessEntity, "authorities");
			
			accessEntities.add(accessEntity.getCode());
			
			userRole.setAccessEntities(accessEntities);
			
			mongo.save(userRole, "userrole");
			
			UserRole role = new UserRole();
			role.setRoleId(userRole.getCode());
			role.setTerminationDate(LocalDate.now());
			userRoles.add(role);
		});
		cu.setRoles(userRoles);
		
		mongo.save(cu, "clientuser");
		
		return loginId;
	}
}
